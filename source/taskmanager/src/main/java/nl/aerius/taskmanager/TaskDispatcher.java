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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.exception.NoFreeWorkersException;
import nl.aerius.taskmanager.exception.TaskAlreadySentException;

/**
 * Control center for processing tasks. Task
 */
class TaskDispatcher implements ForwardTaskHandler, Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(TaskDispatcher.class);
  private static final int NUMBER_CONCURRENT_PREFETCHES = 1;

  /**
   * Represents the current state the TaskDispatcher is in.
   */
  public enum State {
    WAIT_FOR_WORKER, WAIT_FOR_TASK, DISPATCH_TASK;
  }

  private final ConcurrentHashMap<TaskConsumer, Semaphore> taskConsumerLocks = new ConcurrentHashMap<>();
  private final WorkerPool workerPool;
  private final TaskScheduler scheduler;

  private boolean running;
  private State state;
  private final String workerQueueName;

  public TaskDispatcher(final String workerQueueName, final TaskScheduler scheduler, final WorkerPool workerPool) {
    this.workerQueueName = workerQueueName;
    this.scheduler = scheduler;
    this.workerPool = workerPool;
  }

  @Override
  public void forwardTask(final Task task) {
    final TaskConsumer taskConsumer = task.getTaskConsumer();
    lockClient(taskConsumer);
    scheduler.addTask(task);
    LOG.trace("Task for {} added to scheduler {}", workerQueueName, task.getId());
  }

  /**
   * Returns the current state of the dispatcher.
   *
   * @return state of the dispatcher
   */
  public State getState() {
    return state;
  }

  /**
   * @return true if running
   */
  public boolean isRunning() {
    return running;
  }

  @Override
  public void run() {
    running = true;
    try {
      while (running) {
        state = State.WAIT_FOR_WORKER;
        LOG.trace("Wait for worker {}", workerQueueName);
        workerPool.reserveWorker();

        state = State.WAIT_FOR_TASK;
        LOG.trace("Wait for task {}", workerQueueName);
        final Task task = scheduler.getNextTask();
        LOG.trace("Send task to worker {}, ({})", workerQueueName, task.getId());
        state = State.DISPATCH_TASK;
        dispatch(task);
      }
    } catch (final RuntimeException e) {
      LOG.error("TaskDispatcher crashed with RuntimeException: {}", getState(), e);
    } catch (final InterruptedException e) {
      LOG.error("TaskDispatcher interrupted: {}", getState(), e);
      Thread.currentThread().interrupt();
    }
    running = false;
  }

  private void dispatch(final Task task) {
    try {
      workerPool.sendTaskToWorker(task);
      unLockClient(task);
    } catch (final NoFreeWorkersException e) {
      LOG.info("[NoFreeWorkersException] Workers decreased while waiting for task. Rescheduling task.");
      LOG.trace("NoFreeWorkersException thrown", e);
      scheduler.addTask(task);
    } catch (final TaskAlreadySentException e) {
      LOG.error("Duplicate task detected for worker queue: {}, from task queue: {}", e.getWorkerQueueName(), e.getTaskQueueName(), e);
      taskAbortedOnDuplicateMessageId(task);
    } catch (final IOException | ShutdownSignalException e) {
      LOG.error("Sending task to worker failed", e);
      taskDeliveryFailed(task);
      unLockClient(task);
    }
  }

  /**
   * Shutdown the task dispatcher.
   */
  public void shutdown() {
    running = false;
  }

  private void taskDeliveryFailed(final Task task) {
    try {
      workerPool.releaseWorker(task.getId(), task.getMessage().getMetaData().getQueueName());
      task.getTaskConsumer().messageDeliveryFailed(task.getMessage().getMetaData());
    } catch (final IOException | ShutdownSignalException e) {
      LOG.error("taskDeliveryFailed method failed", e);
    }
  }

  private void taskAbortedOnDuplicateMessageId(final Task task) {
    try {
      task.getTaskConsumer().messageDeliveryAborted(task.getMessage(),
          new RuntimeException("Duplicate messageId found for task" + task.getMessage().getMessageId()));
    } catch (final IOException | ShutdownSignalException e) {
      LOG.error("taskDeliveryFailed method failed", e);
    } finally {
      unLockClient(task);
    }
  }

  private void lockClient(final TaskConsumer taskConsumer) {
    synchronized (taskConsumerLocks) {
      if (!taskConsumerLocks.containsKey(taskConsumer)) {
        taskConsumerLocks.put(taskConsumer, new Semaphore(NUMBER_CONCURRENT_PREFETCHES));
      }
    }
    try {
      taskConsumerLocks.get(taskConsumer).acquire();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Returns true if the task consumer related to the given task is locked.
   * @param task to get task consumer from.
   * @return true if task is locked.
   */
  public boolean isLocked(final Task task) {
    return taskConsumerLocks.get(task.getTaskConsumer()).availablePermits() == 0;
  }

  private void unLockClient(final Task task) {
    taskConsumerLocks.get(task.getTaskConsumer()).release();
    LOG.trace("Taskconsumer {} released task {}", workerQueueName, task.getId());
  }
}
