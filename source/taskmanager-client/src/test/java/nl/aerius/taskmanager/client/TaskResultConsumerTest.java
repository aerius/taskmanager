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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.client.TaskWrapper.TaskWrapperSender;
import nl.aerius.taskmanager.client.util.QueueHelper;
import nl.aerius.taskmanager.test.MockedChannelFactory;
import nl.aerius.taskmanager.test.MockedChannelFactory.MockChannel;

/**
 * Test class for {@link TaskResultConsumer}.
 */
class TaskResultConsumerTest {

  private static final WorkerQueueType WORKER_TYPE_TEST = new WorkerQueueType("TEST");
  private static final String CONSUMER_TAG = "Unimportant";
  private static final String FINAL_RESULT = "FINALLY! FREEDOM!";

  private MockTaskSender sender;
  private MockChannel channel;

  @BeforeEach
  void setUp() throws Exception {
    sender = new MockTaskSender();
    channel = MockedChannelFactory.create();
  }

  @Test
  void testHandleDeliveryNormalTask() throws IOException {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue(channel.isOpen(), "Before handling the channel should still be open");
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    //because it's a mockchannel, we can actually use the consumer to handle another delivery...
    //that's what the test on channel.isOpen is for.
    assertFalse(channel.isOpen(), "After handling the channel should be closed");

    assertNull(sender.wrapperSendAgain, "Shouldn't resend");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertEquals(resultObject, resultCallback.successObject, "Received result object");
    assertEquals(propsCorrelationId, resultCallback.correlationId, "Received task ID");
  }

  @Test
  void testHandleDeliveryException() throws IOException {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final Exception resultObject = new RuntimeException();

    assertTrue(channel.isOpen(), "Before handling the channel should still be open");
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    //because it's a mockchannel, we can actually use the consumer to handle another delivery...
    //that's what the test on channel.isOpen is for.
    assertFalse(channel.isOpen(), "After handling the channel should be closed");

    assertNull(sender.wrapperSendAgain, "Shouldn't resend");
    assertNull(resultCallback.successObject, "Received result object should be null");
    //after serializing/deserializing by default objects aren't exactly equal (unless they override equals in some way).
    assertTrue(resultCallback.receivedException instanceof RuntimeException, "Received exception");
    assertEquals(propsCorrelationId, resultCallback.correlationId, "Received task ID");
  }

  @Test
  void testHandleDeliveryMultipleResultsTask() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue(channel.isOpen(), "Before handling the channel should still be open");
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue(channel.isOpen(), "After handling the channel should still be open");

    assertNull(sender.wrapperSendAgain, "Shouldn't resend");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertEquals(resultObject, resultCallback.successObject, "Received result object");
    assertEquals(propsCorrelationId, resultCallback.correlationId, "Received task ID");
    assertEquals(consumer, resultCallback.cancelListener, "Cancel listener");

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue(channel.isOpen(), "After handling the channel still be open");

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(FINAL_RESULT));
    assertFalse(channel.isOpen(), "After handling the final object, the channel should be closed");

    assertNull(sender.wrapperSendAgain, "Shouldn't resend");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertEquals(FINAL_RESULT, resultCallback.successObject, "Received result object");
    assertEquals(propsCorrelationId, resultCallback.correlationId, "Received task ID");
  }

  @Test
  void testHandleDeliveryMultipleResultsTaskException() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue(channel.isOpen(), "Before handling the channel should still be open");
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue(channel.isOpen(), "After handling the channel should still be open");

    assertNull(sender.wrapperSendAgain, "Shouldn't resend");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertEquals(resultObject, resultCallback.successObject, "Received result object");
    assertEquals(propsCorrelationId, resultCallback.correlationId, "Received task ID");

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(new RuntimeException()));
    assertFalse(channel.isOpen(), "After handling an exception the channel should close");
    assertTrue(resultCallback.receivedException instanceof RuntimeException, "Received exception");

    //at this point we can retry handling another delivery, but since we're mocking the channel and such...
    //when the channel is closed, you can assume no more messages will be delivered.
  }

  @Test
  void testCancelMultipleResultsTask() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    assertNull(resultCallback.cancelListener, "Cancel listener");
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);
    assertEquals(consumer, resultCallback.cancelListener, "Cancel listener after using it for a consumer");

    assertTrue(channel.isOpen(), "Before canceling, the channel should be open");
    resultCallback.cancelListener.cancelCurrentTask();
    assertFalse(channel.isOpen(), "After canceling, the channel should be closed");
  }

  private TaskWrapper getExampleTaskWrapper(final TaskResultCallback resultCallback) {
    //this ID actually doesn't matter (the consumer doesn't use it, it uses what it gets on retrieval).
    final String originalTaskId = UUID.randomUUID().toString();
    return new TaskWrapper(Optional.of(resultCallback), new TestTaskInput(), originalTaskId, originalTaskId, "Shouldn't Matter", WORKER_TYPE_TEST);
  }

  @Test
  void testHandleShutdownSignalNotSelfInitiated() {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskWrapper taskWrapper = getExampleTaskWrapper(resultCallback);
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, taskWrapper, sender);
    consumer.handleShutdownSignal(CONSUMER_TAG, new ShutdownSignalException(true, false, "", ""));
    assertEquals(taskWrapper, sender.wrapperSendAgain, "A retry on sending the task again should occur");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertNull(resultCallback.successObject, "Received result should be null");
    assertNull(resultCallback.correlationId, "Received task ID should be null");
  }

  @Test
  void testHandleShutdownSignalSelfInitiated() {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskWrapper taskWrapper = getExampleTaskWrapper(resultCallback);
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, taskWrapper, sender);
    consumer.handleShutdownSignal(CONSUMER_TAG, new ShutdownSignalException(true, true, "", ""));
    assertNull(sender.wrapperSendAgain, "No retry, we shut ourselves down");
    assertNull(resultCallback.receivedException, "Received exception should be null");
    assertNull(resultCallback.successObject, "Received result should be null");
    assertNull(resultCallback.correlationId, "Received task ID should be null");
  }

  @Test
  void testWrapperWithoutCallback() {
    assertThrows(IllegalArgumentException.class, () -> {
      final TaskWrapper taskWrapper = new TaskWrapper(Optional.empty(), new TestTaskInput(), "Other", "One", "Shouldn't Matter", WORKER_TYPE_TEST);
      new TaskResultConsumer(channel, taskWrapper, sender);
    });
  }

  private static class SingleResultCallback implements TaskResultCallback {

    Object successObject;
    Exception receivedException;
    String correlationId;

    @Override
    public void onSuccess(final Object value, final String correlationId, final String messageId) {
      this.successObject = value;
      this.correlationId = correlationId;
    }

    @Override
    public void onFailure(final Exception exception, final String correlationId, final String messageId) {
      this.receivedException = exception;
      this.correlationId = correlationId;
    }

  }

  private static class MultipleResultCallback implements TaskMultipleResultCallback {

    Object successObject;
    Exception receivedException;
    String correlationId;
    TaskCancelListener cancelListener;

    @Override
    public void onSuccess(final Object value, final String correlationId, final String messageId) {
      this.successObject = value;
      this.correlationId = correlationId;
    }

    @Override
    public void onFailure(final Exception exception, final String correlationId, final String messageId) {
      this.receivedException = exception;
      this.correlationId = correlationId;
    }

    @Override
    public boolean isFinalResult(final Object value) {
      return value instanceof String && FINAL_RESULT.equals(value);
    }

    @Override
    public void setTaskCancelListener(final TaskCancelListener listener) {
      this.cancelListener = listener;
    }

  }

  private static class MockTaskSender implements TaskWrapperSender {

    TaskWrapper wrapperSendAgain;

    @Override
    public void sendTask(final TaskWrapper wrapper) throws IOException {
      this.wrapperSendAgain = wrapper;
    }

  }

  class TestTaskInput implements Serializable {

    private static final long serialVersionUID = -3219757676475152784L;

  }

}
