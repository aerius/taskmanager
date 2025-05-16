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
package nl.aerius.taskmanager.mq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.adaptor.WorkerProducer.WorkerMetrics;
import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.adaptor.WorkerSizeProviderProxy;
import nl.aerius.taskmanager.client.BrokerConnectionFactory;

/**
 * Provider to use different means to get information about the size and utilisation of the workers.
 * It both queries the RabbitMQ admin interface to get a total insight as it listens to changes in the number of consumers registering
 * with RabbitMQ.
 */
public class RabbitMQWorkerSizeProvider implements WorkerSizeProviderProxy {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQWorkerSizeProvider.class);

  /**
   * Delay first read from the RabittMQ admin to give the taskmanager some time to start up and register all observers.
   */
  private static final int INITIAL_DELAY_SECONDS = 10;

  private final ScheduledExecutorService executorService;
  private final BrokerConnectionFactory factory;
  private final RabbitMQChannelQueueEventsWatcher channelQueueEventsWatcher;
  private final RabbitMQWorkerEventProducer eventProducer;
  private final long refreshDelaySeconds;

  private final Map<String, WorkerSizeObserver> observers = new HashMap<>();
  private final Map<String, RabbitMQQueueMonitor> monitors = new HashMap<>();

  private boolean running;

  public RabbitMQWorkerSizeProvider(final ScheduledExecutorService executorService, final BrokerConnectionFactory factory) {
    this.executorService = executorService;
    this.factory = factory;
    channelQueueEventsWatcher = new RabbitMQChannelQueueEventsWatcher(factory, this);
    refreshDelaySeconds = factory.getConnectionConfiguration().getBrokerManagementRefreshRate();
    eventProducer = new RabbitMQWorkerEventProducer(executorService, factory);
  }

  @Override
  public void addObserver(final String workerQueueName, final WorkerSizeObserver observer) {
    observers.put(workerQueueName, observer);
    if (observer instanceof WorkerMetrics) {
      eventProducer.addMetrics(workerQueueName, (WorkerMetrics) observer);
    }
    if (refreshDelaySeconds > 0) {
      final RabbitMQQueueMonitor monitor = new RabbitMQQueueMonitor(factory.getConnectionConfiguration());
      monitors.put(workerQueueName, monitor);
    } else {
      LOG.info("Not monitoring RabbitMQ admin api because refresh delay was {} seconds", refreshDelaySeconds);
    }
  }

  @Override
  public boolean removeObserver(final String workerQueueName) {
    final RabbitMQQueueMonitor monitor = monitors.remove(workerQueueName);

    if (monitor != null) {
      monitor.shutdown();
    }
    eventProducer.removeMetrics(workerQueueName);
    return observers.remove(workerQueueName) != null;
  }

  @Override
  public void start() throws IOException {
    channelQueueEventsWatcher.start();
    eventProducer.start();
    if (refreshDelaySeconds > 0) {
      running = true;
      executorService.scheduleWithFixedDelay(this::updateWorkerQueueState, INITIAL_DELAY_SECONDS, refreshDelaySeconds, TimeUnit.SECONDS);
    }
  }

  @Override
  public void shutdown() {
    for (final String key : new ArrayList<>(observers.keySet())) {
      removeObserver(key);
    }
    eventProducer.shutdown();
    channelQueueEventsWatcher.shutdown();
  }

  @Override
  public WorkerSizeObserver getWorkerSizeObserver(final String workerQueueName) {
    return observers.get(workerQueueName);
  }

  private void updateWorkerQueueState() {
    if (running) {
      try {
        monitors.forEach((k, v) -> v.updateWorkerQueueState(k, getWorkerSizeObserver(k)));
      } catch (final RuntimeException e) {
        LOG.error("Runtime error during updateWorkerQueueState", e);
      }
    }
  }
}
