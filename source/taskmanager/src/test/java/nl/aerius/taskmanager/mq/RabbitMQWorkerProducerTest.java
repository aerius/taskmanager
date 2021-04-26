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

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import nl.aerius.taskmanager.adaptor.WorkerProducer;
import nl.aerius.taskmanager.client.mq.QueueUpdateHandler;

/**
 * Test class for {@link RabbitMQWorkerProducer}.
 */
public class RabbitMQWorkerProducerTest extends AbstractRabbitMQTest {

  private static final String WORKER_QUEUE_NAME = "TEST";

  @Test
  @Timeout(5000)
  public void testForwardMessage() throws IOException, InterruptedException {
    final byte[] sendBody = "4321".getBytes();

    final WorkerProducer wp = adapterFactory.createWorkerProducer(WORKER_QUEUE_NAME);
    wp.start(executor, new QueueUpdateHandler() {
      @Override
      public void onQueueUpdate(final String queueName, final int numberOfWorkers, final int numberOfMessages, final int numberOfMessagesReady) {
        // nothing to do here
      }
    });
    final BasicProperties bp = new BasicProperties();
    wp.forwardMessage(new RabbitMQMessage(WORKER_QUEUE_NAME, null, 4321, bp, sendBody) {
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
}
