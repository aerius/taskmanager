/*
 * Copyright the State of the Netherlands
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package nl.aerius.taskmanager.scheduler.priorityqueue;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.QueueWatchDog;
import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.Task;
import nl.aerius.taskmanager.domain.TaskRecord;
import nl.aerius.taskmanager.scheduler.TaskScheduler;

/**
 * Scheduler based on priorities. Tasks are scheduled based on priorities. Tasks with higher priority will be scheduled first. If 2 tasks have the
 * same priority the task on the queue with fewer jobs running will get higher priority. If a task queue has priority 0 an extra condition is that
 * more then 1 worker must be available or no tasks for that queue are running, unless there is only 1 worker in which case the tasks is handled just
 * like with other priorities.
 *
 */
class PriorityTaskScheduler implements TaskScheduler<PriorityTaskQueue>, Comparator<Task>, WorkerSizeObserver {

  private static final Logger LOG = LoggerFactory.getLogger(PriorityTaskScheduler.class);
  private static final int NEXT_TASK_MAX_WAIT_TIME_SECONDS = 120;

  private final QueueWatchDog watchDog = new QueueWatchDog();
  private final PriorityTaskSchedulerMetrics metrics = new PriorityTaskSchedulerMetrics();
  private final Queue<Task> queue;
  private final PriorityQueueMap<?> priorityQueueMap;
  private final Lock lock = new ReentrantLock();
  private final Condition nextTaskCondition = lock.newCondition();
  private final String workerQueueName;
  private int numberOfWorkers;
  private int tasksOnWorkers;

  /**
   * Constructs scheduler for given configuration.
   */
  PriorityTaskScheduler(final PriorityQueueMap<?> priorityQueueKeyMap, final Function<Comparator<Task>, Queue<Task>> queueCreator,
      final String workerQueueName) {
    this.priorityQueueMap = priorityQueueKeyMap;
    this.workerQueueName = workerQueueName;
    queue = queueCreator.apply(this);
  }

  @Override
  public void addTask(final Task task) {
    lock.lock();
    try {
      queue.add(task);
      signalNextTask();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void killTasks() {
    lock.lock();
    try {
      queue.stream().forEach(Task::killTask);
      signalNextTask();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Task getNextTask() throws InterruptedException {
    Task task;
    boolean taskPresent;
    lock.lock();
    try {
      do {
        task = queue.peek();
        if (task == null) { // if task is null, queueName can't be get so do this check first.
          taskPresent = false;
        } else {
          taskPresent = isTaskNext(task.getTaskRecord());
          if (taskPresent) {
            obtainTask();
          }
        }
        // If no task present, await till we get a signal that there could be a new one (or a max time to avoid 'deadlocks')
        if (!taskPresent) {
          final boolean receivedSignal = nextTaskCondition.await(NEXT_TASK_MAX_WAIT_TIME_SECONDS, TimeUnit.SECONDS);
          if (!receivedSignal) {
            LOG.trace("Waited long enough for next task, trying again");
          }
        }
      } while (!taskPresent);
    } finally {
      lock.unlock();
    }
    return task;
  }

  private void obtainTask() {
    tasksOnWorkers++;
    final Task task = queue.poll();

    priorityQueueMap.incrementOnWorker(task.getTaskRecord());
    if (task.getContext() != null) {
      task.getContext().makeCurrent();
    }
  }

  /**
   * Last check to avoid the queue is clogged with slow processes. The following conditions are checked:
   * <ul>
   * <li>number of workers == 1: in that case any task should be run.
   * <li>Or if priority > 0 it should always run.
   * <li>Or if priority == 0, and more then 1 worker available, it may only run if the maximum capacity for the queue is not reached yet.
   * <li>Or if priority == 0, and only 1 worker available, but no tasks for specific queue are running.
   * </ul>
   *
   * @param taskRecord name of the queue
   * @return true if this task is next in line
   */
  private boolean isTaskNext(final TaskRecord taskRecord) {
    final boolean taskNext = (numberOfWorkers == 1) || ((getFreeWorkers() > 1) && hasCapacityRemaining(taskRecord))
        || (priorityQueueMap.onWorker(taskRecord) == 0);

    if (!taskNext) {
      LOG.trace("Task for queue '{}.{}' not scheduled: queueConfiguration:{}, numberOfWorkers:{}, tasksOnWorkers:{}, tasksForQueue:{}",
          workerQueueName, taskRecord, priorityQueueMap.get(taskRecord), numberOfWorkers, tasksOnWorkers,
          priorityQueueMap.onWorker(taskRecord));
    }
    return taskNext;
  }

  private int getFreeWorkers() {
    return numberOfWorkers - tasksOnWorkers;
  }

  private boolean hasCapacityRemaining(final TaskRecord taskRecord) {
    return (numberOfWorkers > 0)
        && ((((double) priorityQueueMap.onWorker(taskRecord)) / numberOfWorkers) < priorityQueueMap.get(taskRecord).getMaxCapacityUse());
  }

  @Override
  public void onTaskFinished(final TaskRecord taskRecord) {
    lock.lock();
    try {
      priorityQueueMap.decrementOnWorker(taskRecord);
      tasksOnWorkers--;
      signalNextTask();
      // clean up queue if it has been removed.
      if (!priorityQueueMap.containsKey(taskRecord.queueName())) {
        metrics.removeMetric(taskRecord.queueName());
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onWorkerPoolSizeChange(final int numberOfWorkers) {
    lock.lock();
    try {
      final int oldNumberOfWorkers = this.numberOfWorkers;
      this.numberOfWorkers = numberOfWorkers;

      if (numberOfWorkers > oldNumberOfWorkers) {
        signalNextTask();
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void updateQueue(final PriorityTaskQueue queue) {
    lock.lock();
    try {
      final String queueName = queue.getQueueName();
      if (!priorityQueueMap.containsKey(queueName)) {
        metrics.addMetric(() -> priorityQueueMap.onWorkerTotal(queueName), workerQueueName, queueName);
      }
      final PriorityTaskQueue old = priorityQueueMap.put(queueName, queue);

      if (old != null && !old.equals(queue)) {
        LOG.info("Queue {} was updated with new values: {}", queueName, queue);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void removeQueue(final String queueName) {
    lock.lock();
    try {
      priorityQueueMap.remove(queueName);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final int compare(final Task task1, final Task task2) {
    final TaskRecord tr1 = task1.getTaskRecord();
    final TaskRecord tr2 = task2.getTaskRecord();
    int cmp;
    lock.lock();
    try {
      cmp = compareWith1Worker(tr1, tr2);
      if (cmp == 0) {
        cmp = compareCapacityRemaining(tr1, tr2);
      }
      if (cmp == 0) {
        cmp = comparePrioWithoutTask(tr1, tr2);
      }
      if (cmp == 0) {
        cmp = compareTaskOnQueue(tr2, tr1);
      }
    } finally {
      lock.unlock();
    }
    return cmp;
  }

  private int compareWith1Worker(final TaskRecord taskRecord1, final TaskRecord taskRecord2) {
    int cmp = 0;
    if ((numberOfWorkers == 1) || (getFreeWorkers() == 1)) {
      cmp = compareTaskOnQueue(taskRecord1, taskRecord2);
      if (cmp == 0) {
        cmp = comparePriority(taskRecord1, taskRecord2);
      }
    }
    return cmp;
  }

  private int compareTaskOnQueue(final TaskRecord taskRecord1, final TaskRecord taskRecord2) {
    return Integer.compare(priorityQueueMap.onWorker(taskRecord1), priorityQueueMap.onWorker(taskRecord2));
  }

  private int comparePriority(final TaskRecord taskRecord1, final TaskRecord taskRecord2) {
    return Integer.compare(priorityQueueMap.get(taskRecord2).getPriority(), priorityQueueMap.get(taskRecord1).getPriority());
  }

  private int compareCapacityRemaining(final TaskRecord taskRecord1, final TaskRecord taskRecord2) {
    final boolean capacityRemaining1 = hasCapacityRemaining(taskRecord1);
    final boolean capacityRemaining2 = hasCapacityRemaining(taskRecord2);
    return capacityRemaining1 == capacityRemaining2 ? 0 : (capacityRemaining1 ? -1 : 1);
  }

  private int comparePrioWithoutTask(final TaskRecord taskRecord1, final TaskRecord taskRecord2) {
    final int cmpPriority = comparePriority(taskRecord1, taskRecord2);
    final int cmp;

    if (cmpPriority < 0) {
      cmp = (priorityQueueMap.onWorker(taskRecord2) == 0) && (priorityQueueMap.onWorker(taskRecord1) > 0) ? 1 : -1;
    } else if (cmpPriority > 0) {
      cmp = (priorityQueueMap.onWorker(taskRecord1) == 0) && (priorityQueueMap.onWorker(taskRecord2) > 0) ? -1 : 1;
    } else {
      cmp = cmpPriority;
    }
    return cmp;
  }

  /**
   * Signal that the next task process can check again..
   */
  private void signalNextTask() {
    try {
      nextTaskCondition.signalAll();
    } catch (final IllegalMonitorStateException e) {
      LOG.error("Caller of signalNextTask did not wrap call with lock field.", e);
    }
  }

  @Override
  public void onNumberOfWorkersUpdate(final int numberOfWorkers, final int numberOfMessages) {
    if (watchDog.isItDead(tasksOnWorkers > 0, numberOfMessages)) {
      LOG.info("It looks like some tasks are zombies on {} worker queue in priority scheduler, so all tasks currently in state running are released.", workerQueueName);
      reset();
    }
  }

  /**
   * Resets the internal state. Called in case a difference was detected that internally it still has messages as being on the queue,
   * while the queue is empty.
   */
  void reset() {
    lock.lock();
    try {
      tasksOnWorkers = 0;
      priorityQueueMap.reset();
      signalNextTask();
    } finally {
      lock.unlock();
    }
  }
}
