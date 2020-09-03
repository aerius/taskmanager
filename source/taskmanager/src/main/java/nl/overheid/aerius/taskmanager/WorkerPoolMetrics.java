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
package nl.overheid.aerius.taskmanager;

import java.util.Locale;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

/**
 * Set up metric collection for this worker pool with the given type name.
 */
public final class WorkerPoolMetrics {
  private WorkerPoolMetrics() {}

  public static void setupMetrics(final MetricRegistry metrics, final WorkerPool workerPool, final String workerQueueName) {
    metrics.register(metricName(workerQueueName, "workerSize"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return workerPool.getWorkerSize();
      }
    });
    metrics.register(metricName(workerQueueName, "currentWorkerSize"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return workerPool.getCurrentWorkerSize();
      }
    });
    metrics.register(metricName(workerQueueName, "runningWorkerSize"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return workerPool.getRunningWorkerSize();
      }
    });
  }

  public static void removeMetrics(final MetricRegistry metrics, final String workerQueueName) {
    metrics.remove(metricName(workerQueueName, "workerSize"));
    metrics.remove(metricName(workerQueueName, "currentWorkerSize"));
    metrics.remove(metricName(workerQueueName, "runningWorkerSize"));
  }

  /**
   * Constructs the metric name which consists of WorkerType in upper case dot metric type. For example: OPS.workerSize
   *
   * @param workerQueueName full name of the worker queue
   * @param metricName name of the metric
   * @return metric name as used to register metrics
   */
  private static String metricName(final String workerQueueName, final String metricName) {
    final int workerTypeIndex = workerQueueName.lastIndexOf('.');

    return MetricRegistry.name((workerTypeIndex > 0 ? workerQueueName.substring(workerTypeIndex) : workerQueueName).toUpperCase(Locale.ENGLISH),
        metricName);
  }
}
