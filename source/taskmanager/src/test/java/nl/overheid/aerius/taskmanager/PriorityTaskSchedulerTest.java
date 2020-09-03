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
package nl.overheid.aerius.taskmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import nl.overheid.aerius.taskmanager.PriorityTaskScheduler.PriorityTaskSchedulerFactory;
import nl.overheid.aerius.taskmanager.domain.Message;
import nl.overheid.aerius.taskmanager.domain.MessageMetaData;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Test class for {@link PriorityTaskScheduler}.
 */
public class PriorityTaskSchedulerTest {

  private static final String QUEUE1 = "queue1";
  private static final String QUEUE2 = "queue2";
  private static final String QUEUE3 = "queue3";
  private static final double TEST_CAPACITY = 0.7;
  private static final PriorityTaskSchedulerFactory factory = new PriorityTaskSchedulerFactory();

  private TaskConsumer taskConsumer1;
  private TaskConsumer taskConsumer2;
  private Task task1;
  private Task task2a;
  private Task task2b;
  private Task task3;
  private PriorityTaskScheduler scheduler;

  @Before
  public void setUp() throws IOException, InterruptedException {
    taskConsumer1 = createMockTaskConsumer(QUEUE1);
    taskConsumer2 = createMockTaskConsumer(QUEUE2);
    final TaskConsumer taskConsumer3 = createMockTaskConsumer(QUEUE3);
    final PriorityTaskSchedule configuration = new PriorityTaskSchedule("TEST");
    final PriorityTaskQueue tc1 = new PriorityTaskQueue(QUEUE1, "", 0, TEST_CAPACITY);
    final PriorityTaskQueue tc2 = new PriorityTaskQueue(QUEUE2, "", 1, TEST_CAPACITY);
    final PriorityTaskQueue tc3 = new PriorityTaskQueue(QUEUE3, "", 1, TEST_CAPACITY);
    configuration.getTaskQueues().add(tc1);
    configuration.getTaskQueues().add(tc2);
    configuration.getTaskQueues().add(tc3);
    scheduler = (PriorityTaskScheduler) factory.createScheduler(configuration.getWorkerQueueName());
    configuration.getTaskQueues().forEach(scheduler::updateQueue);
    task1 = createTask(taskConsumer1, "1", QUEUE1);
    task2a = createTask(taskConsumer2, "2a", QUEUE2);
    task2b = createTask(taskConsumer2, "2b", QUEUE2);
    task3 = createTask(taskConsumer3, "3", QUEUE3);
  }

  @Test(timeout = 5000)
  public void testCompare() throws InterruptedException {
    assertTrue("Compare Ok", compare(task1, task2a, 1));
  }

  @Test(timeout = 5000)
  public void testCompareReverse() throws InterruptedException {
    assertTrue("Compare reserve Ok", compare(task2a, task1, -1));
  }

  private boolean compare(final Task taskA, final Task taskB, final int returnResult) throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(3);
    assertEquals("Task with higher priority and no tasks running should be returned", returnResult,
        scheduler.compare(taskA, taskB));
    scheduler.addTask(task2a);
    assertSame("Should get task2a back.", task2a, scheduler.getNextTask());
    assertEquals("Task with lower priority, but no task run yet should be done returned", -returnResult,
        scheduler.compare(taskA, taskB));
    scheduler.addTask(task1);
    assertSame("Should get task1 back.", task1, scheduler.getNextTask());
    assertEquals("Task with higher priority should be returned when both with tasks running ", returnResult,
        scheduler.compare(taskA, taskB));
    return true;
  }

  @Test(timeout = 5000)
  public void testCompareSame() throws InterruptedException {
    assertTrue("Compare same Ok", compareSame(task2a, task3, -1));
  }

  @Test(timeout = 5000)
  public void testCompareSameReverse() throws InterruptedException {
    assertTrue("Compare same reserve Ok", compareSame(task3, task2a, 1));
  }

  private boolean compareSame(final Task taskA, final Task taskB, final int returnResult) throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(4);
    assertEquals("Task with same priority and no tasks running return 0", 0,
        scheduler.compare(taskA, taskB));
    scheduler.addTask(task2a);
    assertSame("Should get task2a back.", task2a, scheduler.getNextTask());
    assertEquals("Task with same priority, but no task run yet should be done returned", returnResult,
        scheduler.compare(taskA, taskB));
    scheduler.addTask(task3);
    assertSame("Should get task3 back.", task3, scheduler.getNextTask());
    assertEquals("Task with same priority should return 0", 0,
        scheduler.compare(taskA, taskB));
    scheduler.addTask(task2b);
    assertSame("Should get task2b back.", task2b, scheduler.getNextTask());
    assertEquals("Task with same priority, but one with more running, should run with less running", -returnResult,
        scheduler.compare(taskA, taskB));
    return true;
  }

  @Test(timeout = 7000)
  public void testGetTaskWith1WorkerAvailable() throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(1);
    final Task task1 = createTask(taskConsumer1, "1", QUEUE1); //add task with priority 0.
    scheduler.addTask(task1);
    final AtomicInteger chkCounter = new AtomicInteger();
    final ExecutorService es = waitForTask(task1, chkCounter);
    es.awaitTermination(1, TimeUnit.SECONDS);
    assertEquals("Counter should be 1 when only one slot available", 1, chkCounter.intValue());
  }

  /**
   * Test if getNextTask correctly gets new task.
   * If only 1 slot available and the next task is a low priority of the queue on which already another process is running,
   * it should not be run until that more than one or the task of that queue is finished.
   * @throws InterruptedException
   */
  @Test(timeout = 7000)
  public void testGetTask() throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(2);
    final Task task1a = createTask(taskConsumer1, "1a", QUEUE1);
    scheduler.addTask(task1a);
    final Task task1b = createTask(taskConsumer1, "1b", QUEUE1);
    scheduler.addTask(task1b);
    scheduler.addTask(task2a);
    assertSame("Should get task2a back.", task2a, scheduler.getNextTask());
    assertSame("Should get task1a back.", task1a, scheduler.getNextTask());
    final AtomicInteger chkCounter = new AtomicInteger();
    final ExecutorService es = waitForTask(task1b, chkCounter);
    es.awaitTermination(1, TimeUnit.SECONDS);
    assertEquals("Counter should still be zero", 0, chkCounter.intValue());
    scheduler.onTaskFinished(task2a.getMessage().getMetaData().getQueueName());
    es.awaitTermination(1, TimeUnit.SECONDS);
    // task2a finished, but task1b may still not be executed, because only 1 slot available.
    assertEquals("Counter should still be zero when 1 slot priorty available", 0, chkCounter.intValue());
    scheduler.onTaskFinished(task1a.getMessage().getMetaData().getQueueName());
    es.awaitTermination(1, TimeUnit.SECONDS);
    // task1a finished, now task1b may be executed.
    assertEquals("Counter should still be 1", 1, chkCounter.intValue());
  }

  /**
   * Test if getNextTask correctly gets new task in case of a big worker pool.
   * If capacity is reached for a task, it should not run unless a task of the same queue is returned as finished.
   * In the meanwhile, other tasks can start/finish (as long as there is a capacity for those tasks).
   * @throws InterruptedException
   */
  @Test(timeout = 7000)
  public void testGetTaskBigPool() throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(10);
    final List<Task> tasks = new ArrayList<>();
    final List<Task> sendTasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      final Task task = createTask(taskConsumer2, "1", QUEUE2);
      scheduler.addTask(task);
      tasks.add(task);
    }
    for (int i = 0; i < 7; i++) {
      final Task sendTask = scheduler.getNextTask();
      sendTasks.add(sendTask);
    }
    scheduler.addTask(task1);
    assertSame("Should still get task 1", task1, scheduler.getNextTask());
    final Task task1b = createTask(taskConsumer1, "1b", QUEUE1);
    scheduler.addTask(task1b);
    assertSame("Should still get task 1b", task1b, scheduler.getNextTask());
    final AtomicInteger chkCounter = new AtomicInteger();
    final ExecutorService es = waitForTask(null, chkCounter);
    es.awaitTermination(1, TimeUnit.SECONDS);
    assertEquals("Counter should still be zero", 0, chkCounter.intValue());
    scheduler.onTaskFinished(task1.getMessage().getMetaData().getQueueName());
    scheduler.onTaskFinished(task1b.getMessage().getMetaData().getQueueName());
    es.awaitTermination(1, TimeUnit.SECONDS);
    // task1's are finished, but tasks on 2 may still not be executed, because not enough slots available.
    assertEquals("Counter should still be zero when capacity not reached", 0, chkCounter.intValue());
    scheduler.onTaskFinished(sendTasks.get(0).getMessage().getMetaData().getQueueName());
    es.awaitTermination(1, TimeUnit.SECONDS);
    // One of the task2's is now finished, another task2 may be executed.
    assertEquals("Counter should increase because there is enough capacity now", 1, chkCounter.intValue());
  }

  /**
   * If 2 workers available and 1 task (from queue 2) already handled by worker then when 2 tasks are added, one for queue 2 and one for queue 3 then
   * the task of queue 3 should be selected.
   * @throws InterruptedException
   */
  @Test(timeout = 1000)
  public void testCompare2Workers() throws InterruptedException {
    scheduler.onWorkerPoolSizeChange(2);
    scheduler.addTask(task2a);
    scheduler.getNextTask();
    assertEquals("Scheduler should prefer task3", 1, scheduler.compare(task2b, task3));
    scheduler.addTask(task2b);
    scheduler.addTask(task3);
    assertSame("Scheduler should prefer task3", task3, scheduler.getNextTask());
  }

  private ExecutorService waitForTask(final Task task, final AtomicInteger chkCounter) {
    final ExecutorService es = Executors.newCachedThreadPool();
    es.execute(new Runnable() {
      @Override
      public void run() {
        try {
          if (task == null) {
            assertNotNull("Should get any task back", scheduler.getNextTask());
          } else {
            assertSame("Should get task back.", task, scheduler.getNextTask());
          }
          chkCounter.incrementAndGet();
        } catch (final InterruptedException e) {
        }
      }
    });
    es.shutdown();
    return es;
  }

  private TaskConsumer createMockTaskConsumer(final String taskQueueName) throws IOException {
    return new TaskConsumer(taskQueueName, new MockForwardTaskHandler(), new MockAdaptorFactory()) {
      @Override
      public void messageDelivered(final MessageMetaData messageMetaData) {
        //no-op.
      }
    };
  }

  private Task createTask(final TaskConsumer tc, final String message, final String queue) {
    final Task task = new Task(tc);
    task.setData(new Message(new MessageMetaData(queue) {}) {
      @Override
      public String getMessageId() {
        return message;
      }
    });
    return task;
  }
}
