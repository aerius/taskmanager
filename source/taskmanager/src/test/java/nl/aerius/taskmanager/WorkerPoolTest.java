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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.aerius.taskmanager.domain.ForwardTaskHandler;
import nl.aerius.taskmanager.domain.Message;
import nl.aerius.taskmanager.domain.QueueConfig;
import nl.aerius.taskmanager.domain.Task;
import nl.aerius.taskmanager.domain.TaskConsumer;
import nl.aerius.taskmanager.domain.WorkerUpdateHandler;
import nl.aerius.taskmanager.exception.NoFreeWorkersException;
import nl.aerius.taskmanager.exception.TaskAlreadySentException;
import nl.aerius.taskmanager.mq.RabbitMQMessage;

/**
 * Test class for {@link WorkerPool}.
 */
@ExtendWith(MockitoExtension.class)
class WorkerPoolTest {

  private static final String WORKER_QUEUE_NAME_TEST = "TEST";

  private WorkerPool workerPool;
  private TaskConsumer taskConsumer;
  private RabbitMQMessage message;
  private @Mock WorkerUpdateHandler workerUpdateHandler;
  private int numberOfWorkers;


  @BeforeEach
  void setUp() throws IOException {
    numberOfWorkers = 0;
    lenient().doAnswer(inv -> {
      WorkerPoolTest.this.numberOfWorkers = inv.getArgument(0);
      return null;
    }).when(workerUpdateHandler).onWorkerPoolSizeChange(anyInt());
    workerPool = new WorkerPool(WORKER_QUEUE_NAME_TEST, new MockWorkerProducer(), workerUpdateHandler);
    taskConsumer = new TaskConsumerImpl(mock(ExecutorService.class), new QueueConfig("testqueue", false, false, null), mock(ForwardTaskHandler.class),
        new MockAdaptorFactory()) {
      @Override
      public void messageDelivered(final Message message) {
        WorkerPoolTest.this.message = (RabbitMQMessage) message;
      }
    };
  }

  @Test
  void testWorkerPoolSizing() throws IOException {
    assertSame(0, workerPool.getCurrentWorkerSize(), "Check if workerPool size is empty at start");
    workerPool.onNumberOfWorkersUpdate(10, 0);
    assertSame(10, workerPool.getCurrentWorkerSize(), "Check if workerPool size is changed after sizing");
    assertEquals(10, numberOfWorkers, "Check if workerPool change handler called.");
    workerPool.reserveWorker();
    assertSame(10, workerPool.getCurrentWorkerSize(), "Check if workerPool size is same after reserving 1 worker");
    final Task task = createTask();
    workerPool.sendTaskToWorker(task);
    assertSame(10, workerPool.getCurrentWorkerSize(), "Check if workerPool size is same after reserving 1 worker");
    workerPool.releaseWorker(task.getId());
    assertSame(10, workerPool.getCurrentWorkerSize(), "Check if workerPool size is same after releasing 1 worker");
  }

  @Test
  void testNoFreeWorkers() {
    assertThrows(NoFreeWorkersException.class, () -> workerPool.sendTaskToWorker(createTask()));
  }

  @Test
  void testWorkerPoolScaleDown() throws IOException {
    workerPool.onNumberOfWorkersUpdate(5, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    final Task task2 = createTask();
    workerPool.sendTaskToWorker(task2);
    final Task task3 = createTask();
    workerPool.sendTaskToWorker(task3);
    assertSame(5, workerPool.getCurrentWorkerSize(), "Check if workerPool size is same after 2 workers running");
    workerPool.onNumberOfWorkersUpdate(1, 0);
    assertSame(1, workerPool.getWorkerSize(), "Check if workerPool size is lower");
    assertSame(3, workerPool.getCurrentWorkerSize(), "Check if current workerPool size is same after decreasing # workers");
    workerPool.releaseWorker(task1.getId());
    assertSame(2, workerPool.getCurrentWorkerSize(), "Check if workerPool size is lower, but not yet same as total because still process running");
    workerPool.releaseWorker(task2.getId());
    assertSame(1, workerPool.getCurrentWorkerSize(), "Check if workerPool size is lower");
    workerPool.releaseWorker(task3.getId());
    assertSame(1, workerPool.getCurrentWorkerSize(), "Check if workerPool size should remain the same");
  }

  @Test
  void testReleaseTaskTwice() throws IOException {
    workerPool.onNumberOfWorkersUpdate(2, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    final String id = task1.getId();
    workerPool.releaseWorker(id);
    final int currentWorkerSize1 = workerPool.getCurrentWorkerSize();
    workerPool.releaseWorker(id);
    final int currentWorkerSize2 = workerPool.getCurrentWorkerSize();
    assertEquals(currentWorkerSize1, currentWorkerSize2, "Check if task is not sent twice");
    assertEquals(2, workerPool.getCurrentWorkerSize(), "Check if task worker size not decreased to much");
  }

  @Disabled("Exception is not thrown anymore, so test ignored for now")
  @Test
  void testSendSameTaskTwice() {
    assertThrows(TaskAlreadySentException.class, () -> {
      workerPool.onNumberOfWorkersUpdate(3, 0);
      final Task task1 = createTask();
      workerPool.sendTaskToWorker(task1);
      workerPool.sendTaskToWorker(task1);
    });
  }

  @Test
  void testMessageDeliverd() throws IOException {
    workerPool.onNumberOfWorkersUpdate(1, 0);
    final Task task1 = createTask();
    workerPool.sendTaskToWorker(task1);
    assertNotSame(0, message.getDeliveryTag(), "Check if message is delivered");
  }

  private Task createTask() {
    return new MockTask(taskConsumer);
  }
}
