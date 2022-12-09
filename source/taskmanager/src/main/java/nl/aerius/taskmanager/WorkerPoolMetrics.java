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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;

/**
 * Set up metric collection for this worker pool with the given type name.
 */
public final class WorkerPoolMetrics {

  private static final Meter METER = GlobalOpenTelemetry.getMeter("nl.aerius.taskmanager");

  private static final Map<String, ObservableDoubleGauge> REGISTERED_METRICS = new HashMap<>();

  private enum WorkerPoolMetricType {
    // @formatter:off
    WORKER_SIZE(WorkerPool::getWorkerSize, "Configured number of workers according to taskmanager"),
    CURRENT_WORKER_SIZE(WorkerPool::getCurrentWorkerSize, "Current number of workers according to taskmanager"),
    RUNNING_WORKER_SIZE(WorkerPool::getRunningWorkerSize, "Running (or occupied) number of workers according to taskmanager");
    // @formatter:on

    private final Function<WorkerPool, Integer> function;
    private final String description;

    private WorkerPoolMetricType(final Function<WorkerPool, Integer> function, final String description) {
      this.function = function;
      this.description = description;
    }

    int getValue(final WorkerPool workerPool) {
      return function.apply(workerPool);
    }

    String getGaugeName() {
      return "aer.taskmanager." + name().toLowerCase();
    }

    String getDescription() {
      return description;
    }

  }

  private WorkerPoolMetrics() {
  }

  public static void setupMetrics(final WorkerPool workerPool, final String workerQueueName) {
    final Attributes attributes = Attributes.of(AttributeKey.stringKey("worker_type"), workerIdentifier(workerQueueName));
    for (final WorkerPoolMetricType metricType : WorkerPoolMetricType.values()) {
      REGISTERED_METRICS.put(gaugeIdentifier(workerQueueName, metricType),
          METER.gaugeBuilder(metricType.getGaugeName())
              .setDescription(metricType.getDescription())
              .buildWithCallback(
                  result -> result.record(metricType.getValue(workerPool), attributes)));
    }
  }

  public static void removeMetrics(final String workerQueueName) {
    for (final WorkerPoolMetricType metricType : WorkerPoolMetricType.values()) {
      final String gaugeId = gaugeIdentifier(workerQueueName, metricType);
      if (REGISTERED_METRICS.containsKey(gaugeId)) {
        REGISTERED_METRICS.remove(gaugeId).close();
      }
    }
  }

  private static String workerIdentifier(final String workerQueueName) {
    final int workerTypeIndex = workerQueueName.lastIndexOf('.');

    return (workerTypeIndex > 0 ? workerQueueName.substring(workerTypeIndex) : workerQueueName).toUpperCase(Locale.ROOT);
  }

  private static String gaugeIdentifier(final String workerQueueName, final WorkerPoolMetricType gaugeType) {
    return workerQueueName + "_" + gaugeType.name();
  }

}
