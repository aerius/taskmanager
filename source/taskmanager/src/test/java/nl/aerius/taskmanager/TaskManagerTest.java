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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.aerius.taskmanager.TaskScheduler.TaskSchedulerFactory;
import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;
import nl.aerius.taskmanager.domain.RabbitMQQueueType;

/**
 * Test class for {@link TaskManager}.
 */
class TaskManagerTest {

  private static ExecutorService executor;
  private final PriorityTaskSchedulerFileHandler handler = new PriorityTaskSchedulerFileHandler();
  private PriorityTaskSchedule schedule;
  private TaskManager<PriorityTaskQueue, PriorityTaskSchedule> taskManager;

  @BeforeEach
  void setUp() throws IOException {
    executor = Executors.newCachedThreadPool();
    final AdaptorFactory factory = new MockAdaptorFactory();
    final TaskSchedulerFactory<PriorityTaskQueue, PriorityTaskSchedule> schedulerFactory = new FIFOTaskScheduler.FIFOSchedulerFactory();
    taskManager = new TaskManager<>(executor, factory, schedulerFactory, factory.createWorkerSizeProvider());
    schedule = handler.read(new File(getClass().getClassLoader().getResource("queue/priority-task-scheduler.ops.json").getFile()));
  }

  @AfterEach
  void after() throws InterruptedException {
    taskManager.shutdown();
    executor.shutdownNow();
    executor.awaitTermination(10, TimeUnit.MILLISECONDS);
  }

  @Test
  void testAddScheduler() throws InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    assertEquals(RabbitMQQueueType.STREAM, schedule.getQueueType(), "Should have queueType STREAM");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }

  @Test
  void testModifyQueue() throws InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    schedule.getQueues().get(0).setPriority(30);
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler updated");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }

  @Test
  void testRemoveQueue() throws InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    schedule.getQueues().remove(0);
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler updated");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }
}
