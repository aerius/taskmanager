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

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.aerius.taskmanager.adaptor.TaskMessageHandler.MessageReceivedHandler;
import nl.aerius.taskmanager.domain.Message;
import nl.aerius.taskmanager.domain.MessageMetaData;

/**
 * Task manager part of retrieving tasks from the client queues and send them to the dispatcher, which in case will send them to the scheduler.
 * It also listens to if the message was successfully send to the worker.
 */
class TaskConsumer implements MessageReceivedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TaskConsumer.class);

  private final ExecutorService executorService;
  private final String taskQueueName;
  private final ForwardTaskHandler forwardTaskHandler;
  private final TaskMessageHandler<MessageMetaData, Message<MessageMetaData>> taskMessageHandler;

  private boolean running = true;

  @SuppressWarnings("unchecked")
  public TaskConsumer(final ExecutorService executorService, final String taskQueueName, final boolean durable,
      final ForwardTaskHandler forwardTaskHandler, final AdaptorFactory factory) throws IOException {
    this.executorService = executorService;
    this.taskQueueName = taskQueueName;
    this.forwardTaskHandler = forwardTaskHandler;
    this.taskMessageHandler = factory.createTaskMessageHandler(taskQueueName, durable);
    taskMessageHandler.addMessageReceivedHandler(this);
  }

  public String getQueueName() {
    return taskQueueName;
  }

  /**
   * @return true if is running
   */
  public boolean isRunning() {
    return running;
  }

  @Override
  public void onMessageReceived(final Message<?> message) {
    if (running) {
      final Task task = new Task(this);
      task.setData(message);
      LOG.trace("Task received from {} for worker send to scheduler ({}).", taskQueueName, task.getId());
      forwardTaskHandler.forwardTask(task);
    }
  }

  @Override
  public void handleShutdownSignal() {
    forwardTaskHandler.killTasks();
    start();
  }

  /**
   * Inform the consumer the message delivery was successful.
   *
   * @param messageMetaData Meta data of the message that that was successful
   * @throws IOException
   */
  public void messageDelivered(final MessageMetaData messageMetaData) throws IOException {
    taskMessageHandler.messageDeliveredToWorker(messageMetaData);
  }

  /**
   * Inform the consumer the message delivery failed.
   *
   * @param messageMetaData Meta data of the message that failed
   * @throws IOException
   */
  public void messageDeliveryFailed(final MessageMetaData messageMetaData) throws IOException {
    taskMessageHandler.messageDeliveryToWorkerFailed(messageMetaData);
  }

  /**
   * Inform the consumer the message delivery failed.
   * @param message message that failed
   * @param exception the exception with which the message failed
   * @throws IOException
   */
  public void messageDeliveryAborted(final Message<MessageMetaData> message, final RuntimeException exception) throws IOException {
    taskMessageHandler.messageDeliveryAborted(message, exception);
  }

  public void start() {
    executorService.submit(() -> {
      try {
        taskMessageHandler.start();
      } catch (final IOException e) {
        LOG.error("TaskConsumer for {} got IO problems.", taskQueueName, e);
      }
    });
  }

  /**
   * Shutdown the task consumer.
   */
  public void shutdown() {
    running = false;
    try {
      taskMessageHandler.shutDown();
    } catch (final IOException e) {
      // eat error on shutdown
      LOG.trace("Exception while shutting down", e);
    }
  }

  @Override
  public String toString() {
    return "TaskConsumer [taskQueueName=" + taskQueueName + ", running=" + running + "]";
  }
}
