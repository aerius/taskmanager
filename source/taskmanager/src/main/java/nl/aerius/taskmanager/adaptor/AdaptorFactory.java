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
package nl.aerius.taskmanager.adaptor;

import java.io.IOException;

/**
 * Interface between the task manager and implementing communication service.
 */
public interface AdaptorFactory {

  /**
   * Creates a new worker producer for the given worker type.
   * @param workerQueueName name of queue of the worker
   * @return new worker producer object
   */
  WorkerProducer createWorkerProducer(String workerQueueName);

  /**
   * Creates a new TaksMessageHandler for the given worker type and queue.
   * @param taskQueueName queue name
   * @return new TaksMessageHandler object
   * @throws IOException error in case or connection problems
   */
  TaskMessageHandler createTaskMessageHandler(String taskQueueName) throws IOException;
}
