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

import java.io.Serializable;

/**
 * Classes that implement this interface can be used to start a Workerconsumer (through WorkerClient).
 * The interface should only be used by workers, not by calling clients.
 */
public interface WorkerHandler {

  /**
   * Handle a workload. This method will be called once a message is retrieved from the worker queue.
   * The result of the method is used as the response to the calling client (caller).
   * <p> Ensure both parties (caller and worker) know which objects can be used as input and output,
   * otherwise ClassNotFoundException will be returned/occur.
   * @param input The object to be handled by the workerhandler. The workerhandler should check for valid input.
   * @param resultSender The {@link WorkerIntermediateResultSender} that can be used to send intermediate results.
   * @param correlationId The correlation ID of the message.
   * @return The object to send as output to the caller.
   * <p>This can be an exception if needed, the calling client should be aware an exception can be returned
   * (like ClassNotFoundException)
   * @throws Exception An exception to indicate handling the work failed. Depending on the type of exception
   */
  Serializable handleWorkLoad(Serializable input, WorkerIntermediateResultSender resultSender, String correlationId) throws Exception;
}
