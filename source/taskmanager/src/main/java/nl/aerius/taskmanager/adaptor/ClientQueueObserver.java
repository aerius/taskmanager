/*
 * Copyright (c) Contributors to the project
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
package nl.aerius.taskmanager.adaptor;

import nl.aerius.taskmanager.domain.RabbitMQQueueStatus;

/**
 * Observer that listens to client queue updates.
 */
public interface ClientQueueObserver {

  /**
   * Update for the given client queue.
   *
   * @param clientQueueName name of the client queue
   * @param status queue metrics
   */
  void onClientQueueUpdate(String clientQueueName, RabbitMQQueueStatus status);

  /**
   * Returns true if this observer should receive updates for the given client queue.
   *
   * @param clientQueueName client queue to check
   * @return true if should receive updates
   */
  boolean filter(String clientQueueName);
}
