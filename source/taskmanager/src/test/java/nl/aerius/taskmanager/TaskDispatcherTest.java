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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import nl.aerius.taskmanager.TaskDispatcher.State;

/**
 * Test for {@link TaskDispatcher} class.
 */
public class TaskDispatcherTest {

  private static final String WORKER_QUEUE_NAME_TEST = "TEST";
  private static ExecutorService executor;

  private TaskDispatcher dispatcher;
  private WorkerPool workerPool;
  private TaskConsumer taskConsumer;
  private MockWorkerProducer workerProducer;
  private MockAdaptorFactory factory;

  @BeforeEach
  public void setUp() throws IOException, InterruptedException {
    executor = Executors.newCachedThreadPool();
    final FIFOTaskScheduler scheduler = new FIFOTaskScheduler();
    workerProducer = new MockWorkerProducer();
    workerPool = new WorkerPool(WORKER_QUEUE_NAME_TEST, workerProducer, scheduler);
    dispatcher = new TaskDispatcher(WORKER_QUEUE_NAME_TEST, scheduler, workerPool);
    factory = new MockAdaptorFactory();
    taskConsumer = new TaskConsumer("testqueue", dispatcher, factory);
  }

  @AfterEach
  public void after() throws InterruptedException {
    dispatcher.shutdown();
    executor.shutdownNow();
    executor.awaitTermination(10, TimeUnit.MILLISECONDS);
  }

  @Test
  @Timeout(3000)
  public void testNoFreeWorkers() throws InterruptedException {
    final AtomicBoolean unLocked = new AtomicBoolean(false);
    // Add Worker which will unlock
    workerPool.onQueueUpdate("", 1, 0, 0);
    executor.execute(dispatcher);
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_TASK);
    // Remove worker, 1 worker locked but at this point no actual workers available.
    workerPool.onQueueUpdate("", 0, 0, 0);
    // Send task, should get NoFreeWorkersException in dispatcher.
    forwardTask(unLocked);
    // Dispatcher should go back to wait for worker to become available.
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    assertEquals(0, workerPool.getCurrentWorkerSize(), "WorkerPool should be empty");
    workerPool.onQueueUpdate("", 1, 0, 0);
    assertEquals(1, workerPool.getCurrentWorkerSize(), "WorkerPool should have 1 running");
  }

  @Test
  @Timeout(3000)
  public void testForwardTest() throws InterruptedException {
    final AtomicBoolean unLocked = new AtomicBoolean(false);
    dispatcher.forwardTask(createTask());
    executor.execute(dispatcher);
    forwardTask(unLocked);
    assertFalse(unLocked.get(), "Taskconsumer must be locked at this point");
    workerPool.onQueueUpdate("", 1, 0, 0); //add worker which will unlock
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    assertTrue(unLocked.get(), "Taskconsumer must be unlocked at this point");
  }

  @Disabled("TaskAlreadySendexception error willl not be thrown")
  @Test
  @Timeout(3000)
  public void testForwardDuplicateTask() throws InterruptedException {
    final Task task = createTask();
    executor.execute(dispatcher);
    dispatcher.forwardTask(task);
    await().until(() -> dispatcher.isLocked(task));
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    workerPool.onQueueUpdate("", 2, 0, 0); //add worker which will unlock
    await().until(() -> !dispatcher.isLocked(task));
    // Now force the issue.
    assertSame(TaskDispatcher.State.WAIT_FOR_TASK, dispatcher.getState(), "Taskdispatcher must be waiting for task");
    // Forwarding same Task object, so same id.
    dispatcher.forwardTask(task);
    await().until(() -> factory.getMockTaskMessageHandler().getAbortedMessage() == null);
    await().until(() -> !dispatcher.isLocked(task));
    // Now test with a non-duplicate Task.
    dispatcher.forwardTask(createTask());
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
  }

  @Test
  @Timeout(3000)
  public void testExceptionDuringForward() throws InterruptedException {
    workerProducer.setShutdownExceptionOnForward(true);
    final Task task = createTask();
    executor.execute(dispatcher);
    dispatcher.forwardTask(task);
    await().until(() -> dispatcher.isLocked(task));
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    //now open up a worker
    workerPool.onQueueUpdate("", 1, 0, 0);
    // At this point the exception should be thrown. This could be the case when rabbitmq connection is lost for a second.
    // Wait for it to be unlocked again
    await().until(() -> !dispatcher.isLocked(task));
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_TASK);
    //simulate workerpool being reset
    workerPool.onQueueUpdate("", 0, 0, 0);
    //now stop throwing exception to indicate connection is restored again
    workerProducer.setShutdownExceptionOnForward(false);
    //simulate connection being restored by first forwarding task again
    dispatcher.forwardTask(task);
    await().until(() -> dispatcher.isLocked(task));
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    //now simulate the worker being back
    workerPool.onQueueUpdate("", 1, 0, 0);
    //should now be unlocked, but waiting for worker to be done
    await().until(() -> !dispatcher.isLocked(task));
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    workerPool.onWorkerFinished(task.getId());
    //should now again be ready to be used
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_TASK);
    // Check if we can send a task again.
    dispatcher.forwardTask(createTask());
    await().until(() -> dispatcher.getState() == State.WAIT_FOR_WORKER);
    // If state changed at this point we're reasonably sure the dispatcher is still functional.
  }

  private void forwardTask(final AtomicBoolean unLocked) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        dispatcher.forwardTask(createTask());
        unLocked.set(true);
      }
    });
  }

  private Task createTask() {
    return new MockTask(taskConsumer, "ops");
  }
}
