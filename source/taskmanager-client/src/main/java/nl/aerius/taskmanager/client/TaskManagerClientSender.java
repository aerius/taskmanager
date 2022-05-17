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

import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import nl.aerius.taskmanager.client.TaskWrapper.TaskWrapperSender;
import nl.aerius.taskmanager.client.util.QueueHelper;

/**
 * Client to be used to communicate with the taskmanager (more importantly, send tasks to taskmanager). Usage example:
 *
 * <pre>
 * TaskManagerClientSender client = new TaskManagerClientSender(brokerConnectionFactory);
 * TaskResultCallback resultHandler = new SomeTaskResultCallbackImpl();
 * TaskInput task = new SomeTaskInput();
 * client.sendTask(task, resultHandler, task.getExchangeName(), task.getDefaultQueueName());
 * </pre>
 */
public class TaskManagerClientSender implements TaskWrapperSender {

  private static final Logger LOG = LoggerFactory.getLogger(TaskManagerClientSender.class);

  private static final boolean QUEUE_DURABLE = true;
  private static final boolean QUEUE_EXCLUSIVE = false;
  private static final boolean QUEUE_AUTO_DELETE = false;

  private static final int DELIVERY_MODE_NON_PERSISTENT = 1;
  private static final int DELIVERY_MODE_PERSISTENT = 2;

  private static final int CLIENT_START_RETRY_PERIOD = 5000;

  private final BrokerConnectionFactory factory;
  private boolean running = true;

  /**
   * Create a client that can be used to send 1 or more tasks to the taskmanager and in turn to the workers. All results from tasks send through this
   * client will be send to the resultHandler.
   *
   * @param factory Broker connection factory
   */
  public TaskManagerClientSender(final BrokerConnectionFactory factory) {
    if (factory == null) {
      throw new IllegalArgumentException("BrokerConnectionFactory not allowed to be null.");
    }
    this.factory = factory;
    factory.registerClient(this);
  }

  /**
   * Convenience method of {@link #sendTask(Serializable, String, TaskResultCallback, WorkerQueueType, String)}.
   * Should be used when results are not important and should not be waited for.
   *
   * @param input The input object which the worker needs to do the work.
   * @param queueNaming name of the queue
   * @param taskQueueName The name of the queue on which this task should be placed (should be known in taskmanager).
   * @throws IOException In case of errors communicating with queue.
   */
  public void sendTask(final Serializable input, final WorkerQueueType queueNaming, final String taskQueueName) throws IOException {
    final String uniqueId = UUID.randomUUID().toString();
    sendTask(input, uniqueId, queueNaming, taskQueueName);
  }

  /**
   * Convenience method of {@link #sendTask(Serializable, String, TaskResultCallback, WorkerQueueType, String)}.
   * Should be used when results are not important and should not be waited for.
   *
   * @param input The input object which the worker needs to do the work.
   * @param uniqueId The unique ID to use for this task. Can be used in worker to link results to this task
   * @param queueNaming name of the queue
   * @param taskQueueName The name of the queue on which this task should be placed (should be known in taskmanager).
   * @throws IOException In case of errors communicating with queue.
   */
  public void sendTask(final Serializable input, final String uniqueId, final WorkerQueueType queueNaming, final String taskQueueName)
      throws IOException {
    sendTask(new TaskWrapper(Optional.empty(), input, uniqueId, uniqueId, taskQueueName, queueNaming));
  }

  /**
   * Convenience method of {@link #sendTask(Serializable, String, TaskResultCallback, WorkerQueueType, String)}.
   *
   * @param input The input object which the worker needs to do the work.
   * @param resultCallback The resultCallback which will receive results. Can be null, in which case the messages are only send and no results can be
   *          retrieved.
   * @param queueNaming name of the queue
   * @param taskQueueName The name of the queue on which this task should be placed (should be known in taskmanager).
   * @return a random unique task ID. Will be used when a result is received to tell the TaskResultCallback which task was the cause.
   * @throws IOException In case of errors communicating with queue.
   */
  public String sendTask(final Serializable input, final TaskResultCallback resultCallback, final WorkerQueueType queueNaming,
      final String taskQueueName) throws IOException {
    final String uniqueId = UUID.randomUUID().toString();
    sendTask(input, uniqueId, resultCallback, queueNaming, taskQueueName);
    return uniqueId;
  }

  /**
   * Convenience method of {@link #sendTask(Serializable, String, String, TaskResultCallback, WorkerQueueType, String)}. Should be used when the callback wants to correspond a
   * result based on a unique ID.
   *
   * @param input The input object which the worker needs to do the work.
   * @param uniqueId The unique ID to use for this task. Will be used when a result is received to tell the TaskResultCallback which task was the
   *          cause.
   * @param resultCallback The resultCallback which will receive results. Can be null, in which case the messages are only send and no results can be
   *          retrieved.
   * @param queueNaming name of the queue
   * @param taskQueueName The name of the queue on which this task should be placed (should be known in taskmanager).
   * @throws IOException In case of errors communicating with queue.
   */
  public void sendTask(final Serializable input, final String uniqueId, final TaskResultCallback resultCallback, final WorkerQueueType queueNaming,
      final String taskQueueName) throws IOException {
    sendTask(new TaskWrapper(Optional.ofNullable(resultCallback), input, uniqueId, uniqueId, taskQueueName, queueNaming));
  }

  /**
   * @param input The input object which the worker needs to do the work.
   * @param correlationId The correlation ID to use for this task. Will be used when a result is received to
   *          tell the TaskResultCallback which task was the cause. Can be used to correlate a task in the callback.
   * @param messageId The message ID to use for this task. Will be used when a result is received to
   *          tell the TaskResultCallback which task was the cause. Should be unique.
   * @param resultCallback The resultCallback which will receive results.
   *          Can be null, in which case the messages are only send and no results can be retrieved.
   * @param queueNaming name of the queue
   * @param taskQueueName The name of the queue on which this task should be placed (should be known in taskmanager).
   * @throws IOException In case of errors communicating with queue.
   */
  public void sendTask(final Serializable input, final String correlationId, final String messageId, final TaskResultCallback resultCallback,
      final WorkerQueueType queueNaming, final String taskQueueName) throws IOException {
    sendTask(new TaskWrapper(Optional.ofNullable(resultCallback), input, correlationId, messageId, taskQueueName, queueNaming));
  }

  @Override
  public void sendTask(final TaskWrapper wrapper) throws IOException {
    if (wrapper.getTask() == null) {
      throw new IllegalArgumentException("input value of null not allowed.");
    }
    if (wrapper.getQueueName() == null || wrapper.getQueueName().isEmpty()) {
      throw new IllegalArgumentException("Blank taskQueueName not allowed.");
    }
    if (!running) {
      throw new AlreadyClosedException("Attempt to use closed connection", null);
    }
    boolean done = false;
    while (running && !done) {
      try {
        // Create a channel to send the message over.
        final Channel channel = getConnection().createChannel();
        final String queueName = wrapper.getNaming().getTaskQueueName(wrapper.getQueueName());
        final Serializable task = wrapper.getTask();
        // set a unique message ID.
        final String messageId = wrapper.getTaskId();
        // Ensure correlation ID is set, even if no taskId was supplied.
        final String correlationId = wrapper.getCorrelationId();
        // Set the right properties for the message (endurable, replyTo, etc).
        final BasicProperties.Builder builder = new BasicProperties.Builder().correlationId(correlationId).messageId(messageId)
            .deliveryMode(wrapper.getNaming().isPersistent() ? DELIVERY_MODE_PERSISTENT : DELIVERY_MODE_NON_PERSISTENT);

        if (wrapper.getResultCallback().isPresent()) {
          final String replyQueueName = startConsumer(wrapper);
          builder.replyTo(replyQueueName);
        }
        final BasicProperties props = builder.build();
        // Send the message to the taskmanager.
        channel.basicPublish("", queueName, props, QueueHelper.objectToBytes(task));
        // Close this channel.
        channel.close();
        // task has been send successfully, return.
        done = true;
      } catch (final ConnectException e) {
        // don't catch all IOExceptions, just ConnectExceptions.
        // exceptions like wrong host-name should cause a bigger disturbance.
        // those indicate that connection has temporarily been lost with RabbitMQ, though could be not that temporarily...
        LOG.error("Sending task (id: {}) failed, retrying in a bit.", wrapper.getTaskId(), e);
        try {
          Thread.sleep(CLIENT_START_RETRY_PERIOD);
        } catch (final InterruptedException e1) {
          // no need to log.
          Thread.currentThread().interrupt();
        }
        LOG.info("Retrying to send task (id: {})", wrapper.getTaskId());
      }
    }
  }

  private Connection getConnection() {
    return factory.getConnection();
  }

  /**
   * Starts a consumer, but only if a callback is specified.
   * @param taskWrapper task data
   * @return name of the reply queue
   * @throws IOException
   */
  private String startConsumer(final TaskWrapper taskWrapper) throws IOException {
    final Channel channel = getConnection().createChannel();
    final String replyQueueName = channel.queueDeclare().getQueue();
    final TaskResultConsumer resultConsumer = new TaskResultConsumer(channel, taskWrapper, this);
    channel.basicConsume(replyQueueName, true, resultConsumer);
    return replyQueueName;
  }

  /**
   * Exit this client, making it impossible to send new tasks or retrieve any more results.
   * Should be used by a TaskResultCallback when all the results are in (to free up resources).
   */
  public void shutdown() {
    synchronized (this) {
      running = false;
      if (factory != null) {
        factory.deRegisterClient(this);
      }
    }
  }

  /**
   *
   * @return True if tasks can be sent through this client.
   * @throws IOException message bus exception
   */
  public boolean isUsable() throws IOException {
    return running && factory.isOpen();
  }
}
