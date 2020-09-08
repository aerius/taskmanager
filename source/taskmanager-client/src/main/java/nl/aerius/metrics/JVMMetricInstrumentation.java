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
package nl.aerius.metrics;

import java.lang.management.ManagementFactory;
import java.util.Map;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;

public final class JVMMetricInstrumentation {
  private JVMMetricInstrumentation() {}

  public static void registerJVMMetrics(final MetricRegistry metrics) {
    registerAll(metrics, "BufferPool", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
    registerAll(metrics, "ClassLoading", new ClassLoadingGaugeSet());
    registerAll(metrics, "GarbageCollector", new GarbageCollectorMetricSet());
    registerAll(metrics, "MemoryUsage", new MemoryUsageGaugeSet());
    registerAll(metrics, "ThreadState", new ThreadStatesGaugeSet());
    registerAll(metrics, "OperatingSystem", new OperatingSystemGaugeSet());
  }

  /**
   * Equivalent of the _private_ method MetricRegistry.registerAll(String, MetricSet), which should not be private.
   */
  private static void registerAll(final MetricRegistry registry, final String prefix, final MetricSet metrics) {
    for (final Map.Entry<String, Metric> entry : metrics.getMetrics().entrySet()) {
      if (entry.getValue() instanceof MetricSet) {
        registerAll(registry, MetricRegistry.name(prefix, entry.getKey()), (MetricSet) entry.getValue());
      } else {
        registry.register(MetricRegistry.name(prefix, entry.getKey()), entry.getValue());
      }
    }
  }
}
