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
package nl.overheid.aerius.taskmanager.client;

import java.util.Locale;

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

  public WorkerQueueType(final String name) {
    this.name = name.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    return name.equals(((WorkerQueueType) obj).name);
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

  public String propertyName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
