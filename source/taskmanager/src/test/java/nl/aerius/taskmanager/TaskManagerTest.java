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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codahale.metrics.MetricRegistry;

import nl.aerius.metrics.MetricFactory;
import nl.aerius.taskmanager.TaskScheduler.TaskSchedulerFactory;
import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Test class for {@link TaskManager}.
 */
class TaskManagerTest {

  private static ExecutorService executor;
  private final PriorityTaskSchedulerFileHandler handler = new PriorityTaskSchedulerFileHandler();
  private PriorityTaskSchedule schedule;
  private TaskManager<PriorityTaskQueue, PriorityTaskSchedule> taskManager;

  @BeforeEach
  void setUp() throws IOException, InterruptedException {
    executor = Executors.newCachedThreadPool();
    MetricFactory.init(new Properties(), "test");
    final AdaptorFactory factory = new MockAdaptorFactory();
    final TaskSchedulerFactory<PriorityTaskQueue, PriorityTaskSchedule> schedulerFactory = new FIFOTaskScheduler.FIFOSchedulerFactory();
    taskManager = new TaskManager<>(executor, factory, schedulerFactory);
    schedule = handler.read(new File(getClass().getClassLoader().getResource("queue/priority-task-scheduler.ops.json").getFile()));
  }

  @AfterEach
  void after() throws InterruptedException {
    taskManager.shutdown();
    executor.shutdownNow();
    executor.awaitTermination(10, TimeUnit.MILLISECONDS);
    MetricFactory.getMetrics().removeMatching((name, metric) -> true);
  }

  @Test
  void testAddScheduler() throws IOException, InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }

  @Test
  void testModifyQueue() throws IOException, InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    schedule.getTaskQueues().get(0).setPriority(30);
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler updated");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }

  @Test
  void testRemoveQueue() throws IOException, InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    schedule.getTaskQueues().remove(0);
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler updated");
    taskManager.removeTaskScheduler(schedule.getWorkerQueueName());
  }

  @Test
  void testMetricAvailable() throws IOException, InterruptedException {
    assertTrue(taskManager.updateTaskScheduler(schedule), "TaskScheduler running");
    final MetricRegistry metrics = MetricFactory.getMetrics();
    assertEquals(3, metrics.getGauges((name, metric) -> name.startsWith("OPS")).size(), "There should be 3 gauges in a scheduler.");
  }
}
