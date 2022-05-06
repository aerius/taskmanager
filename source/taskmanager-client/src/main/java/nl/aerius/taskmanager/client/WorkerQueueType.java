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

import java.util.Locale;
import java.util.Objects;

/**
 * Contains the names of the group of queues.
 */
public class WorkerQueueType {
  /**
   * Main prefix for queue names.
   */
  private static final String NAMING_PREFIX = "aerius.";
  private static final char DOT = '.';

  private final String name;
  private final boolean persistent;

  public WorkerQueueType(final String name) {
    this(name, true);
  }

  /**
   * Constructor
   *
   * @param name name of the queue type
   * @param persistent true if the queue messages should be persistent
   */
  public WorkerQueueType(final String name, final boolean persistent) {
    this.name = name.toLowerCase(Locale.ENGLISH);
    this.persistent = persistent;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, persistent);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    return name.equals(((WorkerQueueType) obj).name) && persistent == ((WorkerQueueType) obj).persistent;
  }

  /**
   * @param taskName The name of the task to get the queueName for.
   * @return The queuename that should be used for declaring the queue.
   */
  public String getTaskQueueName(final String taskName) {
    return NAMING_PREFIX + propertyName() + DOT + taskName;
  }

  public String getWorkerQueueName() {
    return NAMING_PREFIX + "worker." + propertyName();
  }

  /**
   * @return True if the messages on this queue should be persisted.
   */
  public boolean isPersistent() {
    return persistent;
  }

  public String propertyName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
