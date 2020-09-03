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
package nl.overheid.aerius.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

/**
 * A set of gauges for operating system settings.
 */
public class OperatingSystemGaugeSet implements MetricSet {
  private static final Logger LOG = LoggerFactory.getLogger(OperatingSystemGaugeSet.class);

  private final OperatingSystemMXBean mxBean;
  private final Optional<Method> committedVirtualMemorySize;
  private final Optional<Method> totalSwapSpaceSize;
  private final Optional<Method> freeSwapSpaceSize;
  private final Optional<Method> processCpuTime;
  private final Optional<Method> freePhysicalMemorySize;
  private final Optional<Method> totalPhysicalMemorySize;
  private final Optional<Method> openFileDescriptorCount;
  private final Optional<Method> maxFileDescriptorCount;
  private final Optional<Method> systemCpuLoad;
  private final Optional<Method> processCpuLoad;

  /**
   * Creates new gauges using the platform OS bean.
   */
  public OperatingSystemGaugeSet() {
    this(ManagementFactory.getOperatingSystemMXBean());
  }

  /**
   * Creates a new gauges using the given OS bean.
   *
   * @param mxBean an {@link OperatingSystemMXBean}
   */
  public OperatingSystemGaugeSet(final OperatingSystemMXBean mxBean) {
    this.mxBean = mxBean;

    committedVirtualMemorySize = getMethod("getCommittedVirtualMemorySize");
    totalSwapSpaceSize = getMethod("getTotalSwapSpaceSize");
    freeSwapSpaceSize = getMethod("getFreeSwapSpaceSize");
    processCpuTime = getMethod("getProcessCpuTime");
    freePhysicalMemorySize = getMethod("getFreePhysicalMemorySize");
    totalPhysicalMemorySize = getMethod("getTotalPhysicalMemorySize");
    openFileDescriptorCount = getMethod("getOpenFileDescriptorCount");
    maxFileDescriptorCount = getMethod("getMaxFileDescriptorCount");
    systemCpuLoad = getMethod("getSystemCpuLoad");
    processCpuLoad = getMethod("getProcessCpuLoad");
  }

  @Override
  public Map<String, Metric> getMetrics() {
    final Map<String, Metric> gauges = new HashMap<>();

    addMemoryGauges(gauges);
    addSwapGauges(gauges);
    addCpuGauges(gauges);
    addFileDescriptorGauges(gauges);

    return gauges;
  }

  private void addFileDescriptorGauges(final Map<String, Metric> gauges) {
    gauges.put("fileDescriptorRatio", new Gauge<Double>() {
      @Override
      public Double getValue() {
        return invokeRatio(openFileDescriptorCount, maxFileDescriptorCount);
      }
    });
  }

  private void addMemoryGauges(final Map<String, Metric> gauges) {
    gauges.put("committedVirtualMemorySize", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(committedVirtualMemorySize);
      }
    });
    gauges.put("freePhysicalMemorySize", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(freePhysicalMemorySize);
      }
    });
    gauges.put("totalPhysicalMemorySize", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(totalPhysicalMemorySize);
      }
    });
  }

  private void addCpuGauges(final Map<String, Metric> gauges) {
    gauges.put("processCpuTime", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(processCpuTime);
      }
    });
    gauges.put("systemCpuLoad", new Gauge<Double>() {
      @Override
      public Double getValue() {
        return invokeDouble(systemCpuLoad);
      }
    });
    gauges.put("processCpuLoad", new Gauge<Double>() {
      @Override
      public Double getValue() {
        return invokeDouble(processCpuLoad);
      }
    });
  }

  private void addSwapGauges(final Map<String, Metric> gauges) {
    gauges.put("totalSwapSpaceSize", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(totalSwapSpaceSize);
      }
    });
    gauges.put("freeSwapSpaceSize", new Gauge<Long>() {
      @Override
      public Long getValue() {
        return invokeLong(freeSwapSpaceSize);
      }
    });
  }

  private Optional<Method> getMethod(final String name) {
    try {
      final Method method = mxBean.getClass().getDeclaredMethod(name);
      method.setAccessible(true);
      return Optional.of(method);
    } catch (final NoSuchMethodException e) {
      LOG.error("Cannot find method. {}", name, e);
      return Optional.empty();
    }
  }

  private long invokeLong(final Optional<Method> method) {
    if (method.isPresent()) {
      try {
        return (long) method.get().invoke(mxBean);
      } catch (IllegalAccessException | InvocationTargetException ite) {
        LOG.error("Cannot invoke method. {}", method.toString(), ite);
        return 0L;
      }
    }
    return 0L;
  }

  private double invokeDouble(final Optional<Method> method) {
    if (method.isPresent()) {
      try {
        return (double) method.get().invoke(mxBean);
      } catch (IllegalAccessException | InvocationTargetException ite) {
        LOG.error("Cannot invoke method. {}", method.toString(), ite);
        return 0D;
      }
    }
    return 0D;
  }

  private double invokeRatio(final Optional<Method> numeratorMethod, final Optional<Method> denominatorMethod) {
    if (numeratorMethod.isPresent() && denominatorMethod.isPresent()) {
      try {
        // There's a fine line between a numerator and a denominator -- only a fraction of people understand that. \_(00)_/
        final long denominator = (long) denominatorMethod.get().invoke(mxBean);
        if (denominator != 0) {
          final long numerator = (long) numeratorMethod.get().invoke(mxBean);
          return 1D * numerator / denominator;
        }
      } catch (IllegalAccessException | InvocationTargetException ite) {
        LOG.error("Cannot invoke method. {} {}", numeratorMethod, denominatorMethod, ite);
        return Double.NaN;
      }
    }

    return Double.NaN;
  }
}
