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

import nl.overheid.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.overheid.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.overheid.aerius.taskmanager.adaptor.WorkerProducer;
import nl.overheid.aerius.taskmanager.client.BrokerConnectionFactory;

/**
 * RabbitMQ implementation of the {@link AdaptorFactory}.
 */
public class RabbitMQAdaptorFactory implements AdaptorFactory {

  private final BrokerConnectionFactory factory;

  /**
   * Constructor, initialized with rabbitmq connection factory.
   * @param factory connection factory
   */
  public RabbitMQAdaptorFactory(final BrokerConnectionFactory factory) {
    this.factory = factory;
  }

  @Override
  public TaskMessageHandler createTaskMessageHandler(final String taskQueueName) throws IOException {
    return new RabbitMQMessageHandler(factory, taskQueueName);
  }

  @Override
  public WorkerProducer createWorkerProducer(final String workerQueueName) {
    return new RabbitMQWorkerProducer(factory, workerQueueName);
  }
}
