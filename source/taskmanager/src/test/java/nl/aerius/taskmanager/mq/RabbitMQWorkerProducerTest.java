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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.adaptor.WorkerProducer;
import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.domain.QueueConfig;

/**
 * Test class for {@link RabbitMQWorkerProducer}.
 */
class RabbitMQWorkerProducerTest extends AbstractRabbitMQTest {

  private static final String WORKER_QUEUE_NAME = "TEST";

  private @Mock WorkerSizeObserver queueSizeObserver;

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testForwardMessage() throws IOException, InterruptedException {
    final byte[] sendBody = "4321".getBytes();

    final WorkerProducer wp = createWorkerProducer();
    wp.start();
    final BasicProperties bp = new BasicProperties();
    wp.dispatchMessage(new RabbitMQMessage(WORKER_QUEUE_NAME, null, 4321, bp, sendBody) {
      @Override
      public String getMessageId() {
        return "1234";
      }
    });
    final Semaphore lock = new Semaphore(0);
    final DataDock data = new DataDock();
    mockChannel.basicConsume(WORKER_QUEUE_NAME, new DefaultConsumer(mockChannel) {
      @Override
      public void handleDelivery(final String consumerTag, final Envelope envelope, final BasicProperties properties, final byte[] body)
          throws IOException {
        data.setData(body);
        lock.release(1);
      }
    });
    lock.tryAcquire(1, 5, TimeUnit.SECONDS);
    assertArrayEquals(sendBody, data.getData(), "Test if body send");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testRestart() throws IOException {
    final Connection connection = factory.getConnection();
    final WorkerProducer wp = createWorkerProducer();

    if (wp instanceof final RabbitMQWorkerProducer rwp) {
      // First start which should create the channels.
      wp.start();
      verify(connection, times(1)).createChannel(); // worker channel
      verify(connection, times(1)).openChannel();   // worker reply channel
      // Second call if shutdown was done, but not by application so channels should be recreated.
      assertShutdownCreatesNewChannelsCorrectly(rwp, false);
      // Third call if shutdown was done by application, channels should not be recreated.
      assertShutdownCreatesNewChannelsCorrectly(rwp, true);
      // Forth call shutdown, after that it should also not create new channels.
      wp.shutdown();
      assertShutdownCreatesNewChannelsCorrectly(rwp, false);
    } else {
      fail("Expected worker producer to be of type RabbitMQWorkerProducer, but was: " + wp.getClass());
    }
  }

  private void assertShutdownCreatesNewChannelsCorrectly(final RabbitMQWorkerProducer wp, final boolean initiatedByApplication) throws IOException {
    final Connection connection = factory.getConnection();

    wp.shutdownCompleted(new ShutdownSignalException(false, initiatedByApplication, null, wp));
    verify(connection, times(2)).createChannel();
    verify(connection, times(2)).openChannel();
  }

  private WorkerProducer createWorkerProducer() {
    return adapterFactory.createWorkerProducer(new QueueConfig(WORKER_QUEUE_NAME, false, false, -1, null));
  }
}
