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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.client.TaskWrapper.TaskWrapperSender;
import nl.aerius.taskmanager.client.util.QueueHelper;

/**
 *
 */
public class TaskResultConsumerTest {

  private static final WorkerQueueType WORKER_TYPE_TEST = new WorkerQueueType("TEST");
  private static final String CONSUMER_TAG = "Unimportant";
  private static final String FINAL_RESULT = "FINALLY! FREEDOM!";

  private MockTaskSender sender;
  private MockChannel channel;

  @Before
  public void setUp() throws Exception {
    sender = new MockTaskSender();
    channel = new MockChannel();
  }

  @Test
  public void testHandleDeliveryNormalTask() throws IOException {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue("Before handling the channel should still be open", channel.isOpen());
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    //because it's a mockchannel, we can actually use the consumer to handle another delivery...
    //that's what the test on channel.isOpen is for.
    assertFalse("After handling the channel should be closed", channel.isOpen());

    assertNull("Shouldn't resend", sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertEquals("Received result object", resultObject, resultCallback.successObject);
    assertEquals("Received task ID", propsCorrelationId, resultCallback.correlationId);
  }

  @Test
  public void testHandleDeliveryException() throws IOException {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final Exception resultObject = new RuntimeException();

    assertTrue("Before handling the channel should still be open", channel.isOpen());
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    //because it's a mockchannel, we can actually use the consumer to handle another delivery...
    //that's what the test on channel.isOpen is for.
    assertFalse("After handling the channel should be closed", channel.isOpen());

    assertNull("Shouldn't resend", sender.wrapperSendAgain);
    assertNull("Received result object should be null", resultCallback.successObject);
    //after serializing/deserializing by default objects aren't exactly equal (unless they override equals in some way).
    assertTrue("Received exception", resultCallback.receivedException instanceof RuntimeException);
    assertEquals("Received task ID", propsCorrelationId, resultCallback.correlationId);
  }

  @Test
  public void testHandleDeliveryMultipleResultsTask() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue("Before handling the channel should still be open", channel.isOpen());
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue("After handling the channel should still be open", channel.isOpen());

    assertNull("Shouldn't resend", sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertEquals("Received result object", resultObject, resultCallback.successObject);
    assertEquals("Received task ID", propsCorrelationId, resultCallback.correlationId);
    assertEquals("Cancel listener", consumer, resultCallback.cancelListener);

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue("After handling the channel still be open", channel.isOpen());

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(FINAL_RESULT));
    assertFalse("After handling the final object, the channel should be closed", channel.isOpen());

    assertNull("Shouldn't resend", sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertEquals("Received result object", FINAL_RESULT, resultCallback.successObject);
    assertEquals("Received task ID", propsCorrelationId, resultCallback.correlationId);
  }

  @Test
  public void testHandleDeliveryMultipleResultsTaskException() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);

    final String propsCorrelationId = UUID.randomUUID().toString();
    final BasicProperties props = new BasicProperties.Builder().correlationId(propsCorrelationId).build();
    final String resultObject = "Success!";

    assertTrue("Before handling the channel should still be open", channel.isOpen());
    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(resultObject));
    assertTrue("After handling the channel should still be open", channel.isOpen());

    assertNull("Shouldn't resend", sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertEquals("Received result object", resultObject, resultCallback.successObject);
    assertEquals("Received task ID", propsCorrelationId, resultCallback.correlationId);

    consumer.handleDelivery(CONSUMER_TAG, null, props, QueueHelper.objectToBytes(new RuntimeException()));
    assertFalse("After handling an exception the channel should close", channel.isOpen());
    assertTrue("Received exception", resultCallback.receivedException instanceof RuntimeException);

    //at this point we can retry handling another delivery, but since we're mocking the channel and such...
    //when the channel is closed, you can assume no more messages will be delivered.
  }

  @Test
  public void testCancelMultipleResultsTask() throws IOException {
    final MultipleResultCallback resultCallback = new MultipleResultCallback();
    assertNull("Cancel listener", resultCallback.cancelListener);
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, getExampleTaskWrapper(resultCallback), sender);
    assertEquals("Cancel listener after using it for a consumer", consumer, resultCallback.cancelListener);

    assertTrue("Before canceling, the channel should be open", channel.isOpen());
    resultCallback.cancelListener.cancelCurrentTask();
    assertFalse("After canceling, the channel should be closed", channel.isOpen());
  }

  private TaskWrapper getExampleTaskWrapper(final TaskResultCallback resultCallback) {
    //this ID actually doesn't matter (the consumer doesn't use it, it uses what it gets on retrieval).
    final String originalTaskId = UUID.randomUUID().toString();
    return new TaskWrapper(Optional.of(resultCallback), new TestTaskInput(), originalTaskId, originalTaskId, "Shouldn't Matter", WORKER_TYPE_TEST);
  }

  @Test
  public void testHandleShutdownSignalNotSelfInitiated() {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskWrapper taskWrapper = getExampleTaskWrapper(resultCallback);
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, taskWrapper, sender);
    consumer.handleShutdownSignal(CONSUMER_TAG, new ShutdownSignalException(true, false, "", ""));
    assertEquals("A retry on sending the task again should occur", taskWrapper, sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertNull("Received result should be null", resultCallback.successObject);
    assertNull("Received task ID should be null", resultCallback.correlationId);
  }

  @Test
  public void testHandleShutdownSignalSelfInitiated() {
    final SingleResultCallback resultCallback = new SingleResultCallback();
    final TaskWrapper taskWrapper = getExampleTaskWrapper(resultCallback);
    final TaskResultConsumer consumer = new TaskResultConsumer(channel, taskWrapper, sender);
    consumer.handleShutdownSignal(CONSUMER_TAG, new ShutdownSignalException(true, true, "", ""));
    assertNull("No retry, we shut ourselves down", sender.wrapperSendAgain);
    assertNull("Received exception should be null", resultCallback.receivedException);
    assertNull("Received result should be null", resultCallback.successObject);
    assertNull("Received task ID should be null", resultCallback.correlationId);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWrapperWithoutCallback() {
    final TaskWrapper taskWrapper = new TaskWrapper(Optional.empty(), new TestTaskInput(), "Other", "One", "Shouldn't Matter", WORKER_TYPE_TEST);
    new TaskResultConsumer(channel, taskWrapper, sender);
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
