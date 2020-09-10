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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Connection;

/**
 * Test class for {@link TaskManagerClientSender}.
 */
public class TaskManagerClientTest {

  private static final WorkerQueueType WORKER_TYPE_TEST = new WorkerQueueType("TEST");
  private static final String NORMAL_TASK_ID = "SomeTaskId";
  private static final String TASK_QUEUE_NAME = "taskmanagerclienttest.task";
  private static ExecutorService executor;
  private WorkerQueueType workerType;
  private TaskManagerClientSender taskManagerClient;
  private MockTaskResultHandler mockTaskResultHandler;

  @BeforeClass
  public static void setupClass() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterClass
  public static void afterClass() {
    executor.shutdown();
  }

  @Before
  public void setUp() throws Exception {
    mockTaskResultHandler = new MockTaskResultHandler();
    workerType = WORKER_TYPE_TEST;
    taskManagerClient = new TaskManagerClientSender(new BrokerConnectionFactory(executor) {
      @Override
      protected Connection createNewConnection() throws IOException {
        return new MockConnection();
      }
    });
  }

  @After
  public void tearDown() throws Exception {
    taskManagerClient.shutdown();
  }

  @Test
  public void testSendTask() throws IOException, InterruptedException {
    final Serializable input = new MockTaskInput();
    final String sendTaskId = UUID.randomUUID().toString();
    taskManagerClient.sendTask(input, sendTaskId, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertNotNull("sendTaskId", sendTaskId);
    mockTaskResultHandler.tryAcquire();
    assertEquals("Last correlation ID received by the result handler", sendTaskId, mockTaskResultHandler.getLastCorrelationId());
    final String secondSendTaskId = UUID.randomUUID().toString();
    taskManagerClient.sendTask(input, secondSendTaskId, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertNotNull("secondSendTaskId", secondSendTaskId);
    mockTaskResultHandler.tryAcquire();
    assertEquals("Last correlation ID received by the result handler", secondSendTaskId, mockTaskResultHandler.getLastCorrelationId());
    assertNotSame("Ensure the task ID's don't happen to be the same", sendTaskId, secondSendTaskId);
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  @Test
  public void testSendTasks() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  @Test
  public void testSendTasksWithNullId() throws IOException, InterruptedException {
    taskManagerClient.sendTask(new MockTaskInput(), null, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  @Test
  public void testSendTasksTwice() throws IOException, InterruptedException {
    testSendTask();
    testSendTask();
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTaskManagerClientWithoutConnectionConfiguration() throws IOException {
    taskManagerClient = new TaskManagerClientSender(null);
  }

  @Test
  public void testTaskManagerClientWithConnectionConfigurationBean() throws IOException, InterruptedException {
    testSendTask();
  }

  /**
   * Test method for {@link nl.aerius.taskmanager.client.TaskManagerClientSender#sendTask(Object, String, String)}.
   * @throws InterruptedException
   */
  @Test(expected = NotSerializableException.class)
  public void testSendUnserializableTask() throws IOException, InterruptedException {
    //anonymous inner type isn't serializable (even if the type is Serializable).
    final Serializable input = new Serializable() {

      private static final long serialVersionUID = 7681080846084936169L;

    };
    taskManagerClient.sendTask(input, NORMAL_TASK_ID, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
  }

  /**
   * Test method for {@link TaskManagerClientSender#shutdown()}.
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void testExit() throws IOException, InterruptedException {
    testSendTask();
    taskManagerClient.shutdown();
    assertFalse("Taskmanagerclient shouldn't be usable anymore.", taskManagerClient.isUsable());
  }

  /**
   * Test method for {@link TaskManagerClientSender#shutdown()}.
   * @throws InterruptedException
   */
  @Test(expected = AlreadyClosedException.class)
  public void testSendTaskAfterExit() throws IOException, InterruptedException {
    taskManagerClient.shutdown();
    testSendTask();
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testSendTaskToNullQueue() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, mockTaskResultHandler, workerType, null);
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testSendNullObjectAsTask() throws IOException {
    taskManagerClient.sendTask(null, null, mockTaskResultHandler, workerType, TASK_QUEUE_NAME);
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, String, String)}.
   */
  @Test
  public void testSendTaskWithNullResultHandler() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), NORMAL_TASK_ID, null, workerType, TASK_QUEUE_NAME);
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  /**
   * Test method for {@link TaskManagerClientSender#sendTask(Object, WorkerQueueType, String)}.
   */
  @Test
  public void testSendTaskWithoutResultHandler() throws IOException {
    taskManagerClient.sendTask(new MockTaskInput(), workerType, TASK_QUEUE_NAME);
    assertTrue("Taskmanagerclient should still be usable.", taskManagerClient.isUsable());
  }

  static class MockTaskInput implements Serializable {

    private static final long serialVersionUID = -2L;

  }
}
