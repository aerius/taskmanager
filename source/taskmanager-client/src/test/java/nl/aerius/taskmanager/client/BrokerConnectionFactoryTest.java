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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.aerius.taskmanager.client.configuration.ConnectionConfiguration;

/**
 * Test for class {@link BrokerConnectionFactory}.
 */
public class BrokerConnectionFactoryTest {

  private static ExecutorService executor;

  @BeforeClass
  public static void setupClass() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterClass
  public static void afterClass() {
    executor.shutdown();
  }

  @Test(expected = NullPointerException.class)
  public void testTaskManagerClientWithoutBrokerHost() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerHost(null);
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTaskManagerClientWithEmptyBrokerHost() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerHost("");
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = NullPointerException.class)
  public void testTaskManagerClientWithoutBrokerUsername() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerUsername(null);
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTaskManagerClientWithEmptyBrokerUsername() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerUsername("");
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = NullPointerException.class)
  public void testTaskManagerClientWithoutBrokerPassword() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerPassword(null);
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTaskManagerClientWithEmptyBrokerPassword() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerPassword("");
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = NullPointerException.class)
  public void testTaskManagerClientWithoutBrokerVirtualHost() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerVirtualHost(null);
    new BrokerConnectionFactory(executor, builder.build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTaskManagerClientWithEmptyBrokerVirtualHost() throws IOException {
    final ConnectionConfiguration.Builder builder = getFullConnectionConfigurationBuilder();
    builder.brokerVirtualHost("");
    new BrokerConnectionFactory(executor, builder.build());
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
