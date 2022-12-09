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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

import nl.aerius.taskmanager.metrics.OpenTelemetryMetrics;

/**
 * Track metrics for dispatching tasks.
 */
public final class TaskDispatcherMetrics {

  private static final LongCounter DISPATCHED_METER = OpenTelemetryMetrics.METER
      .counterBuilder("aer.taskmanager.dispatched")
      .setDescription("Counts number of times a task is dispatched to a worker.")
      .build();

  private TaskDispatcherMetrics() {
    // Util-like class
  }

  public static void dispatched(final String workerQueueName) {
    final Attributes attributes = OpenTelemetryMetrics.workerDefaultAttributes(workerQueueName);
    DISPATCHED_METER.add(1, attributes);
  }

}
