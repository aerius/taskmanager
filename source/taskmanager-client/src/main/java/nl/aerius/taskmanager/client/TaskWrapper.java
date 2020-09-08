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

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

/**
 * Wrapper around a specific task, including the callback for that a task and it's id.
 * Should contain everything needed to send a task on the queue.
 */
class TaskWrapper {

  /**
   * Interface for a class that can send tasks to a queue.
   */
  interface TaskWrapperSender {
    void sendTask(TaskWrapper wrapper) throws IOException;
  }

  private final Optional<TaskResultCallback> resultCallback;
  private final Serializable task;
  private final String correlationId;
  private final String taskId;
  private final String queueName;
  private final WorkerQueueType naming;

  TaskWrapper(final Optional<TaskResultCallback> resultCallback, final Serializable task, final String correlationId, final String taskId,
      final String queueName, final WorkerQueueType naming) {
    this.resultCallback = resultCallback;
    this.task = task;
    this.correlationId = correlationId;
    this.taskId = taskId;
    this.queueName = queueName;
    this.naming = naming;
  }

  public Optional<TaskResultCallback> getResultCallback() {
    return resultCallback;
  }

  public Serializable getTask() {
    return task;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getQueueName() {
    return queueName;
  }

  public WorkerQueueType getNaming() {
    return naming;
  }
}
