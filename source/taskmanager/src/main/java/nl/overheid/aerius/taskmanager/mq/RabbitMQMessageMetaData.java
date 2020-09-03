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

import nl.overheid.aerius.taskmanager.domain.MessageMetaData;

/**
 * Metadata data of a RabbitMQ message.
 */
public class RabbitMQMessageMetaData extends MessageMetaData {
  private final long deliveryTag;

  /**
   * Constructor
   *
   * @param queueName Name of the queue this meta data related to
   * @param deliveryTag RabbitMQ tag used to ack or nack a message to the RabbitMQ queue
   */
  public RabbitMQMessageMetaData(final String queueName, final long deliveryTag) {
    super(queueName);
    this.deliveryTag = deliveryTag;
  }

  public long getDeliveryTag() {
    return deliveryTag;
  }
}
