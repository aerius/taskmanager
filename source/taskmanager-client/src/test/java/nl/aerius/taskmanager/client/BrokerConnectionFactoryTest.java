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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
  void testTaskManagerClientWithoutBrokerHost() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerHost(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerHost() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerHost("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerUsername() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerUsername(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerUsername() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerUsername("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerPassword() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerPassword(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerPassword() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerPassword("");
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithoutBrokerVirtualHost() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerVirtualHost(null);
      new BrokerConnectionFactory(executor, builder.build());
    });
  }

  @Test
  void testTaskManagerClientWithEmptyBrokerVirtualHost() {
    assertThrows(IllegalArgumentException.class, () -> {
      final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
      builder.brokerVirtualHost("");
      new BrokerConnectionFactory(executor, builder.build());
    });
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
