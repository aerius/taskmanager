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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.rabbitmq.client.AMQP.BasicProperties;

import nl.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.aerius.taskmanager.adaptor.TaskMessageHandler.MessageReceivedHandler;
import nl.aerius.taskmanager.domain.Message;

/**
 * Test class for {@link RabbitMQMessageHandler}.
 */
public class RabbitMQMessageHandlerTest extends AbstractRabbitMQTest {

  @Test(timeout = 10000)
  public void testMessageReceivedHandler() throws IOException, InterruptedException {
    final String taskQueueName = "queue1";
    final byte[] receivedBody = "4321".getBytes();
    final TaskMessageHandler tmh = adapterFactory.createTaskMessageHandler(taskQueueName);
    final Semaphore lock = new Semaphore(0);
    final DataDock data = new DataDock();
    tmh.start();
    tmh.addMessageReceivedHandler(new MessageReceivedHandler() {
      @Override
      public boolean onMessageReceived(final Message message) {
        if (message instanceof RabbitMQMessage) {
          data.setData(((RabbitMQMessage) message).getBody());
        }
        lock.release(1);
        return true;
      }
    });
    mockChannel.basicPublish("", taskQueueName, new BasicProperties(), receivedBody);
    lock.tryAcquire(1, 5, TimeUnit.SECONDS);
    Assert.assertArrayEquals("Test if body received", receivedBody, data.getData());
  }
}
