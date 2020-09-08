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

import nl.aerius.taskmanager.domain.Message;
import nl.aerius.taskmanager.domain.MessageMetaData;

/**
 * Task manager internal data object to track messages. A Message is a generic interface which is used with a interface specific (e.g. RabbitMQ)
 * instances. This data object keeps track of the taskconsumer that send the message.
 */
class Task {
  private Message<?> data;
  private final TaskConsumer taskConsumer;

  public Task(final TaskConsumer taskConsumer) {
    this.taskConsumer = taskConsumer;
  }

  public String getId() {
    return data == null ? null : data.getMessageId();
  }

  @SuppressWarnings("unchecked")
  public <E extends MessageMetaData> Message<E> getMessage() {
    return (Message<E>) data;
  }

  public TaskConsumer getTaskConsumer() {
    return taskConsumer;
  }

  public void setData(final Message<?> data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Task [id=" + getId() + "]";
  }

}
