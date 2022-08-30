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
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.client.TaskWrapper.TaskWrapperSender;
import nl.aerius.taskmanager.client.util.QueueHelper;

/**
 * RabbitMQ implementation of a RabbitMQ {@link com.rabbitmq.client.Consumer}.
 * <p>On handleDelivery, the received bytes will be converted to a Java object,
 * which will be passed to the defined {@link TaskResultCallback}.
 */
class TaskResultConsumer extends DefaultConsumer implements TaskCancelListener {

  private static final Logger LOG = LoggerFactory.getLogger(TaskResultConsumer.class);

  private final TaskWrapperSender taskSender;
  private final TaskWrapper taskWrapper;
  private final TaskResultCallback callback;

  /**
   * @param channel
   * @param taskWrapper
   * @param taskSender
   */
  protected TaskResultConsumer(final Channel channel, final TaskWrapper taskWrapper, final TaskWrapperSender taskSender) {
    super(channel);
    this.taskWrapper = taskWrapper;
    this.taskSender = taskSender;

    this.callback = taskWrapper.getResultCallback()
        .orElseThrow(() -> new IllegalArgumentException("There should be a result callback when using TaskResultConsumer."));

    if (callback instanceof TaskMultipleResultCallback) {
      ((TaskMultipleResultCallback) callback).setTaskCancelListener(this);
    }
  }

  @Override
  public void handleDelivery(final String consumerTag, final Envelope envelope, final BasicProperties properties,
      final byte[] body) throws IOException {
    Object result = null;
    try {
      // convert the byte array to a workable Java object.
      result = QueueHelper.bytesToObject(body);
      // let the workerHandler do its job.
    } catch (final RuntimeException | ClassNotFoundException | IOException e) {
      LOG.error("Exception while deserializing result.", e);
      result = e;
    } finally {
      if (canCloseChannel(result)) {
        closeChannel();
      }
    }
    final String correlationId = properties.getCorrelationId();
    final String messageId = properties.getMessageId();
    if (result instanceof Exception) {
      callback.onFailure((Exception) result, correlationId, messageId);
    } else {
      callback.onSuccess(result, correlationId, messageId);
    }
  }

  @Override
  public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
    if (!sig.isInitiatedByApplication()) {
      LOG.error("Connection was shutdown", sig);
      try {
        //try sending the task again.
        //Unless there's a really good reason it was aborted this should cause a loop till the task is successfully send.
        taskSender.sendTask(taskWrapper);
      } catch (final IOException e) {
        callback.onFailure(e, null, null);
      }
    }
  }

  private boolean canCloseChannel(final Object result) {
    final boolean close;
    if (callback instanceof TaskMultipleResultCallback) {
      final TaskMultipleResultCallback multipleResultCallback = (TaskMultipleResultCallback) callback;
      close = result instanceof Exception || multipleResultCallback.isFinalResult(result);
    } else {
      close = true;
    }
    return close;
  }

  @Override
  public void cancelCurrentTask() {
    closeChannel();
  }

  private void closeChannel() {
    try {
      if (getChannel().isOpen()) {
        getChannel().close();
      }
    } catch (final IOException | TimeoutException e) {
      LOG.debug("Error while closing channel.", e);
    }
  }

}
