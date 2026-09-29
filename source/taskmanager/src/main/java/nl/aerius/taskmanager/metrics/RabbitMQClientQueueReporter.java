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
package nl.aerius.taskmanager.metrics;

import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.api.metrics.Meter;

import nl.aerius.taskmanager.adaptor.ClientQueueObserver;
import nl.aerius.taskmanager.domain.RabbitMQQueueStatus;

/**
 * Reports RabbitMQ client queue number of messages on the queue.
 *
 * The report registers the metrics using the last part of the worker queue name. Typical worker queue name is 'aerius.worker.some_name'.
 * The last part of the worker queue is used as attribute when reporting the metric.
 * All client queues related to this worker queue will be reported on.
 * The specific client metric is reported with the last part of the client queue name as attribute value.
 * The metrics are reported as gauge metric to the metric 'aer.rabbitmq.client_queue'.
 */
public class RabbitMQClientQueueReporter implements ClientQueueObserver {

  private static final String CLIENT_QUEUE_METRIC = "aer.rabbitmq.client.queue";
  private final String workerQueue;
  private final UsageMetricsReporter clientQueueReporter;
  private final Map<String, Double> queueCounters = new HashMap<>();

  public RabbitMQClientQueueReporter(final Meter meter, final String workerQueueName) {
    this.workerQueue = OpenTelemetryMetrics.onlyLastPart(workerQueueName);
    clientQueueReporter = new UsageMetricsReporter(meter, CLIENT_QUEUE_METRIC, "Number of messages on the RabbitMQ client queues");
  }

  @Override
  public void onClientQueueUpdate(final String clientQueueName, final RabbitMQQueueStatus value) {
    if (!queueCounters.containsKey(clientQueueName)) {
      clientQueueReporter.addMetrics(workerQueue, () -> queueCounters.get(clientQueueName),
          OpenTelemetryMetrics.queueAttributes(workerQueue, clientQueueName));
    }
    queueCounters.put(clientQueueName, Double.valueOf(value.messages()));
  }

  @Override
  public boolean filter(final String queueName) {
    return queueName.contains(workerQueue) && !queueName.contains("worker");
  }
}
