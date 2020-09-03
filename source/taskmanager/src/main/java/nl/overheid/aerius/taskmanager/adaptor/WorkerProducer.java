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
package nl.overheid.aerius.taskmanager.adaptor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import nl.overheid.aerius.taskmanager.client.mq.QueueUpdateHandler;
import nl.overheid.aerius.taskmanager.domain.Message;

/**
 * Interface for communication service with worker. A WorkerProducer delivers messages to the worker queue.
 */
public interface WorkerProducer {

  /**
   * Sets the handler to call when a task is finished by a worker.
   * @param workerFinishedHandler handler.
   */
  void setWorkerFinishedHandler(WorkerFinishedHandler workerFinishedHandler);

  /**
   * Starts the worker producer.
   * @param executorService executor to use to start concurrent tasks to workers.
   * @param workerPool set number of workers available on worker pool when number of workers changes.
   * @throws IOException connection errors
   */
  void start(ExecutorService executorService, QueueUpdateHandler workerPool) throws IOException;

  /**
   * Forward a message to the worker.
   * @param message message to forward
   * @throws IOException connection errors
   */
  void forwardMessage(final Message<?> message) throws IOException;

  /**
   * Shuts down this worker producer.
   */
  void shutdown();

  /**
   * Interface for handling finished tasks from the communication layer send by the workers.
   */
  interface WorkerFinishedHandler {
    /**
     * Called when worker finished task.
     * @param taskId id of the task finished
     */
    void onWorkerFinished(String taskId);
  }
}
