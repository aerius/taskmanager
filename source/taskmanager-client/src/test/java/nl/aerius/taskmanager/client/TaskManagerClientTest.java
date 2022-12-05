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
package nl.aerius.taskmanager.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Connection;

import nl.aerius.taskmanager.test.MockConnection;

/**
 * Test class for {@link TaskManagerClientSender}.
 */
class TaskManagerClientTest {

  private static final WorkerQueueType WORKER_TYPE_TEST = new WorkerQueueType("TEST");
  private static final String NORMAL_TASK_ID = "SomeTaskId";
  private static final String TASK_QUEUE_NAME = "taskmanagerclienttest.task";
  private static ExecutorService EXECUTOR;
  private WorkerQueueType workerType;
  private TaskManagerClientSender taskManagerClient;
  private MockTaskResultHandler mockTaskResultHandler;

  @BeforeAll
  static void setupClass() {
    EXECUTOR = Executors.newSingleThreadExecutor();
  }

  @AfterAll
  static void afterClass() {
    EXECUTOR.shutdown();
  }

  @BeforeEach
  void setUp() throws Exception {
    mockTaskResultHandler = new MockTaskResultHandler();
    workerType = WORKER_TYPE_TEST;
    taskManagerClient = new TaskManagerClientSender(new BrokerConnectionFactory(EXECUTOR) {
      @Override
      protected Connection createNewConnection() throws IOException {
        return new MockConnection();
      }
    });
  }

  @AfterEach
  void tearDown() throws Exception {
    taskManagerClient.shutdown();
  }

  @Test
  void testSendTask() throws IOException, InterruptedException {
    final Serializable input = new MockTaskInput();
    final String sendTaskId = UUID.randomUUID().toString();
    taskManagerClient.sendTask(input, sendTaskId, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertNotNull(sendTaskId, "sendTaskId");
    mockTaskResultHandler.tryAcquire();
    assertEquals(sendTaskId, mockTaskResultHandler.getLastCorrelationId(), "Last correlation ID received by the result handler");
    final String secondSendTaskId = UUID.randomUUID().toString();
    taskManagerClient.sendTask(input, secondSendTaskId, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertNotNull(secondSendTaskId, "secondSendTaskId");
    mockTaskResultHandler.tryAcquire();
    assertEquals(secondSendTaskId, mockTaskResultHandler.getLastCorrelationId(), "Last correlation ID received by the result handler");
    assertNotSame(sendTaskId, secondSendTaskId, "Ensure the task ID's don't happen to be the same");
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  @Test
  void testSendTasks() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  @Test
  void testSendTasksWithNullId() throws IOException, InterruptedException {
    taskManagerClient.sendTask(new MockTaskInput(), null, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  @Test
  void testSendTasksTwice() throws IOException, InterruptedException {
    testSendTask();
    testSendTask();
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  @Test
  void testTaskManagerClientWithoutConnectionConfiguration() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> taskManagerClient = new TaskManagerClientSender(null));
  }

  @Test
  void testTaskManagerClientWithConnectionConfigurationBean() throws IOException, InterruptedException {
    testSendTask();
  }

  /**
   * Test method for {@link nl.aerius.taskmanager.client.TaskManagerClientSender#sendTask(Object, String, String)}.
   * @throws InterruptedException
   */
  @Test
  void testSendUnserializableTask() throws IOException, InterruptedException {
    assertThrows(NotSerializableException.class, () -> {
      //anonymous inner type isn't serializable (even if the type is Serializable).
      final Serializable input = new Serializable() {
        private static final long serialVersionUID = 7681080846084936169L;
      };
      taskManagerClient.sendTask(input, NORMAL_TASK_ID, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    });
  }

  /**
   * Test method for {@link TaskManagerClientSender#shutdown()}.
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  void testExit() throws IOException, InterruptedException {
    testSendTask();
    taskManagerClient.shutdown();
    assertFalse(taskManagerClient.isUsable(), "Taskmanagerclient shouldn't be usable anymore.");
  }

  /**
   * Test method for {@link TaskManagerClientSender#shutdown()}.
   * @throws InterruptedException
   */
  @Test
  void testSendTaskAfterExit() throws IOException, InterruptedException {
    assertThrows(IllegalStateException.class, () -> {
      taskManagerClient.shutdown();
      testSendTask();
    });
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test
  void testSendTaskToNullQueue() throws IOException {
    assertThrows(IllegalArgumentException.class,
        () -> taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, mockTaskResultHandler, workerType, null));
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test
  void testSendNullObjectAsTask() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> taskManagerClient.sendTask(null, null, mockTaskResultHandler, workerType, TASK_QUEUE_NAME));
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test
  void testSendTaskWithNullResultHandler() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, null, workerType, TASK_QUEUE_NAME);
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, WorkerQueueType, String)}.
   */
  @Test
  void testSendTaskWithoutResultHandler() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), workerType, TASK_QUEUE_NAME);
    assertTrue(taskManagerClient.isUsable(), "Taskmanagerclient should still be usable.");
  }

  static class MockTaskInput implements Serializable {

    private static final long serialVersionUID = -2L;

  }
}
