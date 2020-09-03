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
package nl.overheid.aerius.taskmanager.mq;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import nl.overheid.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.overheid.aerius.taskmanager.client.BrokerConnectionFactory;
import nl.overheid.aerius.taskmanager.client.MockChannel;
import nl.overheid.aerius.taskmanager.client.MockConnection;
import nl.overheid.aerius.taskmanager.client.configuration.ConnectionConfiguration;

/**
 * Abstract base class for RabbitMQ tests.
 */
public class AbstractRabbitMQTest {

  protected static ExecutorService executor;
  protected BrokerConnectionFactory factory;
  protected MockChannel mockChannel;
  protected AdaptorFactory adapterFactory;

  @BeforeClass
  public static void setupClass() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterClass
  public static void afterClass() throws InterruptedException {
    executor.shutdownNow();
    executor.awaitTermination(10, TimeUnit.MILLISECONDS);
  }

  @Before
  public void setUp() throws Exception {
    mockChannel = new MockChannel();
    final ConnectionConfiguration configuration = ConnectionConfiguration.builder()
        .brokerHost("localhost").brokerUsername("guest").brokerPassword("guest").build();
    factory = new BrokerConnectionFactory(executor, configuration) {
      @Override
      protected Connection createNewConnection() throws IOException {
        return new MockConnection() {
          @Override
          public Channel createChannel() throws IOException {
            return mockChannel;
          }
        };
      }
    };
    adapterFactory = new RabbitMQAdaptorFactory(factory);
  }

  protected class DataDock {
    private byte[] data;

    public byte[] getData() {
      return data;
    }

    public void setData(final byte[] data) {
      this.data = data;
    }
  }
}
