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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * FIFO implementation of the task scheduler.
 */
class FIFOTaskScheduler implements TaskScheduler<PriorityTaskQueue> {

  private final BlockingQueue<Task> tasks = new LinkedBlockingQueue<Task>();

  @Override
  public void addTask(final Task task) {
    tasks.add(task);
  }

  @Override
  public void killTasks() {
    tasks.stream().forEach(Task::killTask);
  }

  @Override
  public Task getNextTask() throws InterruptedException {
    return tasks.take();
  }

  @Override
  public void updateQueue(final PriorityTaskQueue queue) {
    // Not used
  }

  @Override
  public void removeQueue(final String queueName) {
    // Not used
  }

  @Override
  public void onTaskFinished(final String queueName) {
    // Not used
  }

  @Override
  public void onWorkerPoolSizeChange(final int numberOfWorkers) {
    // Not used
  }

  public static class FIFOSchedulerFactory implements TaskSchedulerFactory<PriorityTaskQueue, PriorityTaskSchedule> {
    private final PriorityTaskSchedulerFileHandler handler = new PriorityTaskSchedulerFileHandler();

    @Override
    public FIFOTaskScheduler createScheduler(final String workerQueueName) {
      return new FIFOTaskScheduler();
    }

    @Override
    public PriorityTaskSchedulerFileHandler getHandler() {
      return handler;
    }
  }
}
