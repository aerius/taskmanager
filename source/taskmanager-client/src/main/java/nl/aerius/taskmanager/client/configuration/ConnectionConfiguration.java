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
package nl.aerius.taskmanager.client.configuration;

import org.apache.commons.lang3.StringUtils;

import com.google.auto.value.AutoValue;

/**
 * Configuration object for different (queue) properties.
 */
@AutoValue
public abstract class ConnectionConfiguration {

  /**
   * RabbitMQ default port.
   */
  private static final int DEFAULT_BROKER_PORT = 5672;

  /**
   * RabbitMQ default virtualHost (root).
   */
  private static final String DEFAULT_BROKER_VIRTUAL_HOST = "/";

  /**
   * RabbitMQ default management port.
   */
  private static final int DEFAULT_BROKER_MANAGEMENT_PORT = 15672;

  /**
   * Default refresh time in seconds.
   */
  private static final int DEFAULT_MANAGEMENT_REFRESH_RATE = 60; //seconds

  /**
   * Default wait time before retrying to connect.
   */
  private static final int DEFAULT_RETRY_WAIT_TIME = 60; //seconds

  public static Builder builder() {
    return new AutoValue_ConnectionConfiguration.Builder()
        .brokerPort(DEFAULT_BROKER_PORT)
        .brokerManagementPort(DEFAULT_BROKER_MANAGEMENT_PORT)
        .brokerVirtualHost(DEFAULT_BROKER_VIRTUAL_HOST)
        .brokerManagementRefreshRate(DEFAULT_MANAGEMENT_REFRESH_RATE)
        .brokerRetryWaitTime(DEFAULT_RETRY_WAIT_TIME);
  }

  /**
   * @return The host used to communicate with the broker
   */
  public abstract String getBrokerHost();

  /**
   * @return The port used to communicate with the broker (rabbitMQ default: 5672)
   */
  public abstract int getBrokerPort();

  /**
   * @return The username to be used while communicating with the broker
   */
  public abstract String getBrokerUsername();

  /**
   * @return The password to be used while communicating with the broker
   */
  public abstract String getBrokerPassword();

  /**
   * @return The virtual host to be used on the broker (rabbitMQ default: /)
   */
  public abstract String getBrokerVirtualHost();

  /**
   * @return The port used to communicate with the management interface of the broker (rabbitMQ default: 15672)
   */
  public abstract int getBrokerManagementPort();

  /**
   * @return The refresh rate in seconds the RabbitMQ management api is queried for status changes.
   */
  public abstract int getBrokerManagementRefreshRate();

  /**
   * @return The wait time in seconds before retrying to connect with the broker.
   */
  public abstract int getBrokerRetryWaitTime();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder brokerHost(String host);
    public abstract Builder brokerPort(int port);
    public abstract Builder brokerUsername(String username);
    public abstract Builder brokerPassword(String password);
    public abstract Builder brokerVirtualHost(String virtualHost);
    public abstract Builder brokerManagementPort(int managementPort);
    public abstract Builder brokerManagementRefreshRate(int managementRefreshRate);
    public abstract Builder brokerRetryWaitTime(int retryWaitTime);
    abstract ConnectionConfiguration autoBuild();  // not public

    public ConnectionConfiguration build() {
      final ConnectionConfiguration connectionConfiguration = autoBuild();

      if (StringUtils.isBlank(connectionConfiguration.getBrokerHost())) {
        throw new IllegalArgumentException("Broker host not allowed to be null or empty.");
      } else if (StringUtils.isBlank(connectionConfiguration.getBrokerUsername())) {
        throw new IllegalArgumentException("Broker username not allowed to be null or empty.");
      } else if (StringUtils.isBlank(connectionConfiguration.getBrokerPassword())) {
        throw new IllegalArgumentException("Broker password not allowed to be null or empty.");
      } else if (StringUtils.isBlank(connectionConfiguration.getBrokerVirtualHost())) {
        throw new IllegalArgumentException("Broker virtual host not allowed to be null or empty.");
      }
      return connectionConfiguration;
    }
  }
}
