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
package nl.aerius.taskmanager.client.mq;

/**
 * Interface called when status information from RabbitMQ is received.
 */
public interface QueueUpdateHandler {

  /**
   * Gives the number of workers, total number of messages, and number of messages ready on the queue.
   *
   * @param queueName name of the queue the data is for
   * @param numberOfWorkers number of workers
   * @param numberOfMessages total number of messages
   * @param numberOfMessagesReady number of messages in ready state
   */
  void onQueueUpdate(String queueName, int numberOfWorkers, int numberOfMessages, int numberOfMessagesReady);
}
