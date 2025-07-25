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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.adaptor.WorkerProducer;
import nl.aerius.taskmanager.client.BrokerConnectionFactory;
import nl.aerius.taskmanager.client.QueueConstants;
import nl.aerius.taskmanager.domain.Message;
import nl.aerius.taskmanager.domain.QueueConfig;
import nl.aerius.taskmanager.domain.RabbitMQQueueType;

/**
 * RabbitMQ implementation of a {@link WorkerProducer}.
 */
class RabbitMQWorkerProducer implements WorkerProducer {

  protected static final String WORKER_REPLY_AFFIX = ".reply";

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQWorkerProducer.class);

  private static final int DEFAULT_RETRY_SECONDS = 10;

  private final BrokerConnectionFactory factory;
  private final String workerQueueName;
  private final boolean durable;
  private final RabbitMQQueueType queueType;
  private final List<WorkerFinishedHandler> workerFinishedHandlers = new ArrayList<>();
  private Channel channel;

  private boolean isShutdown;

  private Channel replyChannel;

  public RabbitMQWorkerProducer(final BrokerConnectionFactory factory, final QueueConfig queueConfig) {
    this.factory = factory;
    this.workerQueueName = queueConfig.queueName();
    this.durable = queueConfig.durable();
    this.queueType = queueConfig.queueType();
  }

  @Override
  public void addWorkerFinishedHandler(final WorkerFinishedHandler workerFinishedHandler) {
    this.workerFinishedHandlers.add(workerFinishedHandler);
  }

  @Override
  public void start() {
    tryStartReplyConsumer();
  }

  @Override
  public void dispatchMessage(final Message message) throws IOException {
    final RabbitMQMessage rabbitMQMessage = (RabbitMQMessage) message;
    ensureChanne();
    final BasicProperties.Builder forwardBuilder = rabbitMQMessage.getProperties().builder();
    // new header map (even in case of existing headers, original can be a UnmodifiableMap)
    final Map<String, Object> headers = rabbitMQMessage.getProperties().getHeaders() == null
        ? new HashMap<>()
        : new HashMap<>(rabbitMQMessage.getProperties().getHeaders());

    // we want to be notified when a worker has finished it's job.
    // To do this, we set our own property, replyCC.
    // It's the worker implementation (through taskmanager client) to use this property to return a message.
    // (either through RabbitMQ CC-mechanism or by sending an empty message to the replyQueue)
    headers.put(QueueConstants.TASKMANAGER_REPLY_QUEUE, getWorkerReplyQueue());
    forwardBuilder.headers(headers);
    final BasicProperties forwardProperties = forwardBuilder.deliveryMode(2).build();
    channel.basicPublish("", workerQueueName, forwardProperties, rabbitMQMessage.getBody());
    workerFinishedHandlers.forEach(h -> h.onWorkDispatched(message.getMessageId(), headers));
  }

  private synchronized void ensureChanne() throws IOException {
    if (channel == null || !channel.isOpen()) {
      channel = factory.getConnection().createChannel();
      channel.queueDeclare(workerQueueName, durable, false, false, RabbitMQQueueUtil.queueDeclareArguments(durable, queueType));
    }
  }

  @Override
  public void shutdown() {
    isShutdown = true;
    closeChannel(channel);
    closeChannel(replyChannel);
  }

  private static void closeChannel(final Channel channel) {
    if (channel != null && channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException | TimeoutException e) {
        LOG.warn("Failed to close channel on shutdown");
      }
    }
  }

  private String getWorkerReplyQueue() {
    return workerQueueName + WORKER_REPLY_AFFIX;
  }

  private void tryStartReplyConsumer() {
    boolean warn = true;
    while (!isShutdown) {
      Connection connection = null;
      try {
        connection = factory.getConnection();
        if (startReplyConsumer(connection)) {
          LOG.info("Successfully (re)started reply consumer for queue {}", workerQueueName);
          return;
        }
      } catch (final ShutdownSignalException | IOException e1) {
        if (warn) {
          LOG.warn("(Re)starting reply consumer for queue {} failed, retrying in a while: {}", workerQueueName,
              Optional.ofNullable(e1.getMessage()).orElse(Optional.ofNullable(e1.getCause()).map(Throwable::getMessage).orElse("Unknown")));
          LOG.trace("(Re)starting failed with exception:", e1);
          warn = false;
        }
        if (!isShutdown) {
          delayRetry(DEFAULT_RETRY_SECONDS);
        }
      }
    }
  }

  private static void delayRetry(final int retryTime) {
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(retryTime));
    } catch (final InterruptedException ex) {
      LOG.debug("Waiting interrupted", ex);
      Thread.currentThread().interrupt();
    }
  }

  private boolean startReplyConsumer(final Connection connection) throws IOException {
    final Optional<Channel> replyChannelOptional = connection.openChannel();

    if (replyChannelOptional.isEmpty()) {
      return false;
    }
    replyChannel = replyChannelOptional.get();
    // Create an exclusive reply queue with predefined name (so we can set a replyCC header).
    // Queue will be deleted once taskmanager is down.
    // reply queue is not durable because the system will 'reboot' after connection problems anyway.
    // Making it durable would only make sense if we'd keep track of tasks-in-progress during shutdown/startup.
    final String workerReplyQueue = getWorkerReplyQueue();
    replyChannel.queueDeclare(workerReplyQueue, false, true, true, null);
    // ensure the worker queue is around as well (so we can retrieve number of customers later on).
    // Worker queue is durable and non-exclusive with autodelete off.
    replyChannel.queueDeclare(workerQueueName, durable, false, false, RabbitMQQueueUtil.queueDeclareArguments(durable, queueType));
    replyChannel.basicConsume(workerReplyQueue, true, workerReplyQueue, new DefaultConsumer(replyChannel) {
      @Override
      public void handleDelivery(final String consumerTag, final Envelope envelope, final BasicProperties properties, final byte[] body) {
        workerFinishedHandlers.forEach(h -> handleWorkFinished(h, properties));
      }
    });
    return true;
  }

  private void handleWorkFinished(final WorkerFinishedHandler handler, final BasicProperties properties) {
    try {
      handler.onWorkerFinished(properties.getMessageId(), properties.getHeaders());
    } catch (final RuntimeException e) {
      LOG.error("Runtime exception during handleWorkFinished of {}", handler.getClass(), e);
    }
  }
}
