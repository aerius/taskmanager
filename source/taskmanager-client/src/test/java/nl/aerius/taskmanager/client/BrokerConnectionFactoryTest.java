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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;

import nl.aerius.taskmanager.client.configuration.ConnectionConfiguration;

/**
 * Test for class {@link BrokerConnectionFactory}.
 */
class BrokerConnectionFactoryTest {

  private static ExecutorService executor;

  @BeforeAll
  static void setupClass() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterAll
  static void afterClass() {
    executor.shutdown();
  }

  @Test
  void testTaskManagerClientWithoutBrokerHost() throws IOException {
    assertThrows(NullPointerException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerHost(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerHost() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerHost("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerUsername() throws IOException {
    assertThrows(NullPointerException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerUsername(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerUsername() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerUsername("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerPassword() throws IOException {
    assertThrows(NullPointerException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerPassword(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerPassword() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerPassword("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerVirtualHost() throws IOException {
    assertThrows(NullPointerException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerVirtualHost(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerVirtualHost() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerVirtualHost("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testGetConnectionWithShutdownlistener() throws IOException {
    final Connection mockConnection = mock(Connection.class);
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    final BrokerConnectionFactory factory = new BrokerConnectionFactory(executor, builder.build()) {
      @Override
      protected Connection createNewConnection() throws IOException {
        return mockConnection;
      }
    };
    final ShutdownListener listener = mock(ShutdownListener.class);
    final Connection connection = factory.getConnection(listener);
    assertNotNull(connection, "Should not return a null connection");
    verify(connection, times(2)).addShutdownListener(any());

    // Test if called again with open connection listener is not added.
    reset(mockConnection);
    doReturn(true).when(mockConnection).isOpen();
    final Connection connection2 = factory.getConnection(listener);
    verify(connection2, never()).addShutdownListener(any());
  }

  private ConnectionConfiguration.Builder getFullConnectionConfigurationBuilder() {
    final ConnectionConfiguration.Builder builder = ConnectionConfiguration.builder();
    builder.brokerHost("localhost");
    builder.brokerUsername("username");
    builder.brokerPassword("password");
    builder.brokerPort(1234);
    builder.brokerVirtualHost("/");
    return builder;
  }
}
