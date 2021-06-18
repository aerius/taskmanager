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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.adaptor.WorkerSizeProviderProxy;
import nl.aerius.taskmanager.client.BrokerConnectionFactory;

/**
 * Watches the RabbitMQ event channel for changes in consumers to dynamically monitor the number of workers added or removed.
 */
class RabbitMQChannelQueueEventsWatcher {

  private static final String AMQ_RABBITMQ_EVENT = "amq.rabbitmq.event";
  private static final String CHANNEL_PATTERN = "consumer.*";
  private static final String HEADER_PARAM_QUEUE = "queue";
  private static final String CONSUMER_CREATED = "consumer.created";

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQChannelQueueEventsWatcher.class);

  private final BrokerConnectionFactory factory;
  private final WorkerSizeProviderProxy proxy;
  private Consumer consumer;
  private Channel channel;

  /**
   * Constructor.
   *
   * @param factory connection factory
   * @param proxy proxy to get observers for specific worker queues
   */
  public RabbitMQChannelQueueEventsWatcher(final BrokerConnectionFactory factory, final WorkerSizeProviderProxy proxy) {
    this.factory = factory;
    this.proxy = proxy;
  }

  /**
   * Start the watcher.
   *
   * @throws IOException
   */
  public void start() throws IOException {
    final Connection c = factory.getConnection();
    channel = c.createChannel();
    final String q = channel.queueDeclare().getQueue();
    channel.queueBind(q, AMQ_RABBITMQ_EVENT, CHANNEL_PATTERN);

    consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(final String consumerTag, final Envelope envelope, final AMQP.BasicProperties properties, final byte[] body)
          throws IOException {
        final String event = envelope.getRoutingKey();
        final Map<String, Object> headers = properties.getHeaders();
        final Object queue = headers.get(HEADER_PARAM_QUEUE);
        final String queueName = queue == null ? null : queue.toString();
        final WorkerSizeObserver observer = proxy.getWorkerSizeObserver(queueName);

        if (observer == null) {
          LOG.debug("No handler to watch channel changes for queue: " + queueName);
          return;
        }

        LOG.trace("Event: {} - queue: {}", event, queueName);
        if (CONSUMER_CREATED.equals(event)) {
          observer.onDeltaNumberOfWorkersUpdate(1);
        } else { // consumer.deleted is the only other possibility
          observer.onDeltaNumberOfWorkersUpdate(-1);
        }
      }

      @Override
      public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
        if (sig.isInitiatedByApplication()) {
          LOG.info("Channel watcher {} was shut down.", consumerTag);
        } else {
          LOG.debug("Channel watcher {} was shut down.", consumerTag);
          // restart
          try {
            try {
              channel.abort();
            } catch (final IOException e) {
              // Eat error when closing channel.
            }
            start();
            LOG.info("Restarted channel watcher; {}", consumerTag);
          } catch (final IOException e) {
            LOG.debug("Channel watcher restart failed", e);
          }
        }
      }
    };
    channel.basicConsume(q, true, consumer);
  }

  public void shutdown() {
    try {
      channel.close();
    } catch (final IOException e) {
      LOG.trace("Channel watcher shutdown failed", e);
    }
  }
}
