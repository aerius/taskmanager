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
package nl.aerius.taskmanager;

import java.util.UUID;

import nl.aerius.taskmanager.domain.Message;
import nl.aerius.taskmanager.mq.RabbitMQMessageMetaData;

/**
 * Mock implementation of {@link Task}.
 */
public class MockTask extends Task {
  private final String id = UUID.randomUUID().toString();

  public MockTask(final TaskConsumer taskConsumer, final String queueName) {
    super(taskConsumer);
    setData(new Message<RabbitMQMessageMetaData>(new RabbitMQMessageMetaData(queueName, 2)) {
      @Override
      public RabbitMQMessageMetaData getMetaData() {
        return super.getMetaData();
      }

      @Override
      public String getMessageId() {
        return id;
      }
    });
  }
}
