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

import java.util.Locale;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;

/**
 * Class to help with opntelemetry metrics within the taskmanager.
 */
public final class OpenTelemetryMetrics {

  public static final Meter METER = GlobalOpenTelemetry.getMeter("nl.aerius.taskmanager");

  private OpenTelemetryMetrics() {
    // Util class
  }

  public static Attributes workerDefaultAttributes(final String workerQueueName) {
    return Attributes.builder()
        .put(AttributeKey.stringKey("worker_type"), workerIdentifier(workerQueueName))
        .build();
  }

  private static String workerIdentifier(final String workerQueueName) {
    final int workerTypeIndex = workerQueueName.lastIndexOf('.');

    return (workerTypeIndex > 0 ? workerQueueName.substring(workerTypeIndex) : workerQueueName).toUpperCase(Locale.ROOT);
  }
}
