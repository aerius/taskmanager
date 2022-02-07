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
package nl.aerius.taskmanager;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Scheduler based on priorities. Tasks are scheduled based on priorities. Tasks with higher priority will be scheduled first. If 2 tasks have the
 * same priority the task on the queue with fewer jobs running will get higher priority. If a task queue has priority 0 an extra condition is that
 * more then 1 worker must be available or no tasks for that queue are running, unless there is only 1 worker in which case the tasks is handled just
 * like with other priorities.
 *
 */
class PriorityTaskScheduler implements TaskScheduler<PriorityTaskQueue>, Comparator<Task> {

  private static final Logger LOG = LoggerFactory.getLogger(PriorityTaskScheduler.class);
  private static final int INITIAL_SIZE = 20;

  private final Queue<Task> queue;
  private final Map<String, PriorityTaskQueue> taskQueueConfigurations = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> tasksOnWorkersPerQueue = new ConcurrentHashMap<>();
  private final Semaphore semaphore = new Semaphore(0);
  private final String workerQueueName;
  private int numberOfWorkers;
  private int tasksOnWorkers;

  /**
   * Constructs scheduler for given configuration.
   *
   * @param configuration scheduler configuration
   */
  PriorityTaskScheduler(final String workerQueueName) {
    this.workerQueueName = workerQueueName;
    queue = new PriorityQueue<>(INITIAL_SIZE, this);
  }

  @Override
  public void addTask(final Task task) {
    synchronized (this) {
      queue.add(task);
      semaphoreRelease();
    }
  }

  @Override
  public void killTasks() {
    synchronized (this) {
      queue.stream().forEach(Task::killTask);
      semaphore.release();
    }
  }

  @Override
  public Task getNextTask() throws InterruptedException {
    Task task;
    boolean taskPresent;
    do {
      synchronized (this) {
        task = queue.peek();
        if (task == null) { // if task is null, queueName can't be get so do this check first.
          taskPresent = false;
        } else {
          final String queueName = task.getTaskConsumer().getQueueName();
          taskPresent = isTaskNext(queueName);
          if (taskPresent) {
            tasksOnWorkers++;
            tasksOnWorkersPerQueue.get(queueName).incrementAndGet();
            task = queue.poll();
          }
        }
      }
      if (!taskPresent) { // only wait if no task present, but outside synchronized block
        semaphore.acquire();
      }
    } while (!taskPresent);
    return task;
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
   * @param queueName name of the queue
   * @return true if this task is next in line
   */
  private boolean isTaskNext(final String queueName) {
    final boolean taskNext = (numberOfWorkers == 1) || ((getFreeWorkers() > 1) && hasCapacityRemaining(queueName))
        || (tasksOnWorkersPerQueue.get(queueName).intValue() == 0);

    if (!taskNext) {
      LOG.trace("Task for queue '{}.{}' not scheduled: queueConfiguration:{}, numberOfWorkers:{}, tasksOnWorkers:{}, tasksForQueue:{}",
          workerQueueName, queueName, taskQueueConfigurations.get(queueName), numberOfWorkers, tasksOnWorkers,
          tasksOnWorkersPerQueue.get(queueName).intValue());
    }
    return taskNext;
  }

  private int getFreeWorkers() {
    return numberOfWorkers - tasksOnWorkers;
  }

  private boolean hasCapacityRemaining(final String queueName) {
    return (numberOfWorkers > 0)
        && ((tasksOnWorkersPerQueue.get(queueName).doubleValue() / numberOfWorkers) < taskQueueConfigurations.get(queueName).getMaxCapacityUse());
  }

  @Override
  public void onTaskFinished(final String queueName) {
    synchronized (this) {
      tasksOnWorkersPerQueue.get(queueName).decrementAndGet();
      tasksOnWorkers--;
      semaphoreRelease();
      // clean up queue if it has been removed.
      if (!taskQueueConfigurations.containsKey(queueName)) {
        tasksOnWorkersPerQueue.remove(queueName);
      }
    }
  }

  @Override
  public void onWorkerPoolSizeChange(final int numberOfWorkers) {
    synchronized (this) {
      final int oldNumberOfWorkers = this.numberOfWorkers;
      this.numberOfWorkers = numberOfWorkers;

      if (numberOfWorkers > oldNumberOfWorkers) {
        semaphoreRelease();
      }
    }
  }

  @Override
  public void updateQueue(final PriorityTaskQueue queue) {
    synchronized (this) {
      final String queueName = queue.getQueueName();

      final PriorityTaskQueue old = taskQueueConfigurations.put(queueName, queue);

      if (old != null && !old.equals(queue)) {
        LOG.info("Queue {} was updated with new values: {}", queueName, queue);
      }
      tasksOnWorkersPerQueue.computeIfAbsent(queueName, qn -> new AtomicInteger());
    }
  }

  @Override
  public void removeQueue(final String queueName) {
    synchronized (this) {
      taskQueueConfigurations.remove(queueName);
    }
  }

  @Override
  public final int compare(final Task task1, final Task task2) {
    final String qN1 = task1.getTaskConsumer().getQueueName();
    final String qN2 = task2.getTaskConsumer().getQueueName();
    int cmp;
    synchronized (this) {
      cmp = compareWith1Worker(qN1, qN2);
      if (cmp == 0) {
        cmp = compareCapacityRemaining(qN1, qN2);
      }
      if (cmp == 0) {
        cmp = comparePrioWithoutTask(qN1, qN2);
      }
      if (cmp == 0) {
        cmp = compareTaskOnQueue(qN2, qN1);
      }
    }
    return cmp;
  }

  private int compareWith1Worker(final String queueName1, final String queueName2) {
    int cmp = 0;
    if ((numberOfWorkers == 1) || (getFreeWorkers() == 1)) {
      cmp = compareTaskOnQueue(queueName1, queueName2);
      if (cmp == 0) {
        cmp = comparePriority(queueName1, queueName2);
      }
    }
    return cmp;
  }

  private int compareTaskOnQueue(final String queueName1, final String queueName2) {
    return Integer.compare(tasksOnWorkersPerQueue.get(queueName1).intValue(), tasksOnWorkersPerQueue.get(queueName2).intValue());
  }

  private int comparePriority(final String queueName1, final String queueName2) {
    return Integer.compare(taskQueueConfigurations.get(queueName2).getPriority(), taskQueueConfigurations.get(queueName1).getPriority());
  }

  private int compareCapacityRemaining(final String queueName1, final String queueName2) {
    final boolean capacityRemaining1 = hasCapacityRemaining(queueName1);
    final boolean capacityRemaining2 = hasCapacityRemaining(queueName2);
    return capacityRemaining1 == capacityRemaining2 ? 0 : (capacityRemaining1 ? -1 : 1);
  }

  private int comparePrioWithoutTask(final String queueName1, final String queueName2) {
    int cmp = comparePriority(queueName1, queueName2);

    if (cmp < 0) {
      cmp = (tasksOnWorkersPerQueue.get(queueName2).intValue() == 0) && (tasksOnWorkersPerQueue.get(queueName1).intValue() > 0) ? 1 : -1;
    } else if (cmp > 0) {
      cmp = (tasksOnWorkersPerQueue.get(queueName1).intValue() == 0) && (tasksOnWorkersPerQueue.get(queueName2).intValue() > 0) ? -1 : 1;
    }
    return cmp;
  }

  /**
   * Release semaphore, but only when someone is waiting.
   */
  private void semaphoreRelease() {
    if (semaphore.hasQueuedThreads()) {
      semaphore.release();
    }
  }

  /**
   * Factory to create a scheduler.
   */
  public static class PriorityTaskSchedulerFactory implements TaskSchedulerFactory<PriorityTaskQueue, PriorityTaskSchedule> {
    private final PriorityTaskSchedulerFileHandler handler = new PriorityTaskSchedulerFileHandler();

    @Override
    public TaskScheduler<PriorityTaskQueue> createScheduler(final String workerQueueName) {
      return new PriorityTaskScheduler(workerQueueName);
    }

    @Override
    public PriorityTaskSchedulerFileHandler getHandler() {
      return handler;
    }
  }
}
