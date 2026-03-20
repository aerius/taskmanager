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
package nl.aerius.taskmanager.metrics;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.StartupGuard;
import nl.aerius.taskmanager.adaptor.WorkerProducer.WorkerProducerHandler;
import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.domain.QueueWatchDogListener;

/**
 *
 */
public class TaskManagerMetricsRegister implements WorkerProducerHandler, WorkerSizeObserver, QueueWatchDogListener {

  private static final Logger LOG = LoggerFactory.getLogger(TaskManagerMetricsRegister.class);

  private final TaskManagerUsageMetricsProvider taskManagerUsageMetricsProvider;
  private final StartupGuard startupGuard;

  private int numberOfWorkers;

  public TaskManagerMetricsRegister(final TaskManagerUsageMetricsProvider taskManagerUsageMetricsProvider, final StartupGuard startupGuard) {
    this.taskManagerUsageMetricsProvider = taskManagerUsageMetricsProvider;
    this.startupGuard = startupGuard;
  }

  @Override
  public void onWorkDispatched(final String messageId, final Map<String, Object> messageMetaData) {
    taskManagerUsageMetricsProvider.register(1, numberOfWorkers);
  }

  @Override
  public void onWorkerFinished(final String messageId, final Map<String, Object> messageMetaData) {
    taskManagerUsageMetricsProvider.register(-1, numberOfWorkers);
  }

  @Override
  public synchronized void onNumberOfWorkersUpdate(final int numberOfWorkers, final int numberOfMessages, final int numberOfMessagesInProgress) {
    this.numberOfWorkers = numberOfWorkers;
    if (!startupGuard.isOpen() && numberOfMessages > 0) {
      LOG.info("Queue {} will be started with {} messages already on the queue.", taskManagerUsageMetricsProvider.getWorkerQueueName(),
          numberOfMessages);
      taskManagerUsageMetricsProvider.register(numberOfMessages, numberOfWorkers);
    }
  }

  @Override
  public void reset() {
    taskManagerUsageMetricsProvider.reset();
  }
}
