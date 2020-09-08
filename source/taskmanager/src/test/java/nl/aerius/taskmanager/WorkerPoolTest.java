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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.aerius.taskmanager.domain.MessageMetaData;
import nl.aerius.taskmanager.exception.NoFreeWorkersException;
import nl.aerius.taskmanager.exception.TaskAlreadySentException;
import nl.aerius.taskmanager.mq.RabbitMQMessageMetaData;

/**
 * Test class for {@link WorkerPool}.
 */
public class WorkerPoolTest {

  private static final String WORKER_QUEUE_NAME_TEST = "TEST";

  private WorkerPool workerPool;
  private TaskConsumer taskConsumer;
  private RabbitMQMessageMetaData message;
  private WorkerUpdateHandler workerUpdateHandler;
  private int numberOfWorkers;
  @Before
  public void setUp() throws IOException, InterruptedException {
    numberOfWorkers = 0;
    workerUpdateHandler = new MockTaskFinishedHandler() {
      @Override
      public void onWorkerPoolSizeChange(final int numberOfWorkers) {
        WorkerPoolTest.this.numberOfWorkers = numberOfWorkers;
      }
    };
    workerPool = new WorkerPool(WORKER_QUEUE_NAME_TEST, new MockWorkerProducer(), workerUpdateHandler);
    taskConsumer =
        new TaskConsumer("testqueue", new MockForwardTaskHandler(), new MockAdaptorFactory()) {
      @Override
      public void messageDelivered(final MessageMetaData message) {
        WorkerPoolTest.this.message = (RabbitMQMessageMetaData) message;
      }
    };
  }

  @Test
  public void testWorkerPoolSizing() throws InterruptedException, IOException {
    assertSame("Check if workerPool size is empty at start", 0, workerPool.getCurrentWorkerSize());
    workerPool.onQueueUpdate("", 10, 0, 0);
    assertSame("Check if workerPool size is changed after sizing", 10, workerPool.getCurrentWorkerSize());
    assertEquals("Check if workerPool change handler called.", 10, numberOfWorkers);
    workerPool.reserveWorker();
    assertSame("Check if workerPool size is same after reserving 1 worker", 10, workerPool.getCurrentWorkerSize());
    final Task task = createTask();
    workerPool.sendTaskToWorker(task);
    assertSame("Check if workerPool size is same after reserving 1 worker", 10, workerPool.getCurrentWorkerSize());
    workerPool.releaseWorker(task.getId());
    assertSame("Check if workerPool size is same after releasing 1 worker", 10, workerPool.getCurrentWorkerSize());
  }

  @Test(expected = NoFreeWorkersException.class)
  public void testNoFreeWorkers() throws IOException, InterruptedException {
    workerPool.sendTaskToWorker(createTask());
  }

  @Test
  public void testWorkerPoolScaleDown() throws IOException, InterruptedException {
    workerPool.onQueueUpdate("", 5, 0, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    final Task task2 = createTask();
    workerPool.sendTaskToWorker(task2);
    final Task task3 = createTask();
    workerPool.sendTaskToWorker(task3);
    assertSame("Check if workerPool size is same after 2 workers running", 5, workerPool.getCurrentWorkerSize());
    workerPool.onQueueUpdate("", 1, 0, 0);
    assertSame("Check if workerPool size is lower", 1, workerPool.getWorkerSize());
    assertSame("Check if current workerPool size is same after decreasing # workers", 3, workerPool.getCurrentWorkerSize());
    workerPool.releaseWorker(task1.getId());
    assertSame("Check if workerPool size is lower, but not yet same as total because still process running", 2, workerPool.getCurrentWorkerSize());
    workerPool.releaseWorker(task2.getId());
    assertSame("Check if workerPool size is lower", 1, workerPool.getCurrentWorkerSize());
    workerPool.releaseWorker(task3.getId());
    assertSame("Check if workerPool size should remain the same", 1, workerPool.getCurrentWorkerSize());
  }

  @Test
  public void testReleaseTaskTwice() throws IOException, InterruptedException {
    workerPool.onQueueUpdate("", 2, 0, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    final String id = task1.getId();
    workerPool.releaseWorker(id);
    final int currentWorkerSize1 = workerPool.getCurrentWorkerSize();
    workerPool.releaseWorker(id);
    final int currentWorkerSize2 = workerPool.getCurrentWorkerSize();
    assertEquals("Check if task is not sent twice", currentWorkerSize1, currentWorkerSize2);
    assertEquals("Check if task worker size not decreased to much", 2, workerPool.getCurrentWorkerSize());
  }

  @Ignore("Exception is not thrown anymore, so test ignored for now")
  @Test(expected = TaskAlreadySentException.class)
  public void testSendSameTaskTwice() throws IOException, InterruptedException {
    workerPool.onQueueUpdate("", 3, 0, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    workerPool.sendTaskToWorker(task1);
  }

  @Test
  public void testMessageDeliverd() throws IOException, InterruptedException {
    workerPool.onQueueUpdate("", 1, 0, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    assertNotSame("Check if message is delivered", 0, message.getDeliveryTag());
  }

  private Task createTask() {
    return new MockTask(taskConsumer, "calculator");
  }
}
