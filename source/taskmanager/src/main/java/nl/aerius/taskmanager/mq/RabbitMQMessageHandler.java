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
package nl.aerius.taskmanager.mq;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.aerius.taskmanager.client.BrokerConnectionFactory;
import nl.aerius.taskmanager.client.WorkerResultSender;
import nl.aerius.taskmanager.mq.RabbitMQMessageConsumer.ConsumerCallback;

/**
 * RabbitMQ implementation of a {@link TaskMessageHandler}. RabbitMQ will starts listening to the queue and when messages are received they are send
 * to the {@link MessageReceivedHandler}.
 */
class RabbitMQMessageHandler implements TaskMessageHandler<RabbitMQMessageMetaData, RabbitMQMessage>, ConsumerCallback {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQMessageHandler.class);

  private static final int DEFAULT_RETRY_SECONDS = 10;

  private final BrokerConnectionFactory factory;
  private final String taskQueueName;

  private MessageReceivedHandler messageReceivedHandler;
  private RabbitMQMessageConsumer consumer;
  private boolean isShutdown;

  /**
   * Constructor.
   *
   * @param factory the factory to get the a RabbitMQ connection from
   * @param taskQueueName the name of the task queue
   * @throws IOException
   */
  public RabbitMQMessageHandler(final BrokerConnectionFactory factory, final String taskQueueName) throws IOException {
    this.factory = factory;
    this.taskQueueName = taskQueueName;
  }

  @Override
  public void addMessageReceivedHandler(final MessageReceivedHandler messageReceivedHandler) {
    this.messageReceivedHandler = messageReceivedHandler;
  }

  @Override
  public void start() throws IOException {
    tryStartConsuming();
  }

  @Override
  public void shutDown() throws IOException {
    isShutdown = true;
    consumer.stopConsuming();
  }

  @Override
  public void messageDeliveredToWorker(final RabbitMQMessageMetaData message) throws IOException {
    consumer.ack(message);
  }

  @Override
  public void messageDeliveryToWorkerFailed(final RabbitMQMessageMetaData message) throws IOException {
    consumer.nack(message);
  }

  @Override
  public void messageDeliveryAborted(final RabbitMQMessage message, final RuntimeException exception) throws IOException {
    final WorkerResultSender sender = new WorkerResultSender(factory.getConnection().createChannel(), message.getProperties());
    sender.sendIntermediateResult(exception);
    messageDeliveredToWorker(message.getMetaData());
  }

  @Override
  public void onMessageReceived(final RabbitMQMessage message) {
    if (messageReceivedHandler != null) {
      messageReceivedHandler.onMessageReceived(message);
    }
  }

  @Override
  public void onConsumerShutdown(final ShutdownSignalException sig) {
    if (!sig.isInitiatedByApplication()) {
      tryStartConsuming();
    }
  }

  private void tryStartConsuming() {
    boolean warn = true;
    while (!isShutdown) {
      try {
        stopAndStartConsumer();
        LOG.info("Succesfully (re)started consumer for {}", taskQueueName);
        break;
      } catch (final ShutdownSignalException | IOException e1) {
        if (warn) {
          LOG.warn("(Re)starting consumer for {} failed, retrying in a while", taskQueueName, e1);
          warn = false;
        }
        delayRetry(DEFAULT_RETRY_SECONDS);
      }
    }
  }

  private void delayRetry(final int retryTime) {
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(retryTime));
    } catch (final InterruptedException ex) {
      LOG.debug("Waiting interrupted", ex);
      Thread.currentThread().interrupt();
    }
  }

  private void stopAndStartConsumer() throws IOException {
    synchronized (this) {
      if (consumer != null) {
        consumer.stopConsuming();
      }
      consumer = new RabbitMQMessageConsumer(
          factory.getConnection().createChannel(),
          taskQueueName,
          this);

      consumer.startConsuming();
    }
  }
}
