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

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.PickledGraphite;

public final class MetricFactory {
  private static final Logger LOG = LoggerFactory.getLogger(MetricFactory.class);

  public static final String GRAPHITE_HOST_PROP = "metrics.graphite.host";
  public static final String GRAPHITE_PORT_PROP = "metrics.graphite.port";
  public static final String GRAPHITE_REPORT_INTERVAL = "metrics.graphite.interval";

  public static final String GRAPHITE_REPORT_KEY_PREFIX = "metrics.graphite.report.key.prefix";
  public static final String GRAPHITE_JVM_INSTRUMENTATION = "metrics.instrumentation.jvm";

  private static final String GRAPHITE_REPORT_INTERVAL_DEFAULT = "5";
  private static final String GRAPHITE_PORT_DEFAULT = "2004";
  public static final String GRAPHITE_REPORT_KEY_PREFIX_DEFAULT = "generic";
  private static final String GRAPHITE_JVM_INSTRUMENTATION_DEFAULT = "true";

  private static MetricRegistry metrics;

  private MetricFactory() {
  }

  public static void init(final Properties properties, final String application) {
    synchronized (MetricFactory.class) {
      if (metrics != null) {
        return;
      }

      metrics = new MetricRegistry();
    }

    final String host = getHost(properties);

    if (host == null || host.isBlank()) {
      LOG.info("No graphite host specified. Metrics will not be reported (but they will be collected regardless;"
          + " if not reporting metrics is intended it is advised to make this an option in MetricFactory.)");
      return;
    }
    final int port = getPort(properties);

    final String prefix = properties.getProperty(GRAPHITE_REPORT_KEY_PREFIX, GRAPHITE_REPORT_KEY_PREFIX_DEFAULT);
    final String fullPrefix = MetricRegistry.name(prefix, application);

    final GraphiteSender graphite = new PickledGraphite(new InetSocketAddress(host, port));
    final GraphiteReporter graphiteReporter = GraphiteReporter.forRegistry(metrics).prefixedWith(fullPrefix).build(graphite);

    // Find out whether to report JVM metrics, default to true, then report
    final boolean reportJvm = Boolean.parseBoolean(properties.getProperty(GRAPHITE_JVM_INSTRUMENTATION, GRAPHITE_JVM_INSTRUMENTATION_DEFAULT));
    if (reportJvm) {
      JVMMetricInstrumentation.registerJVMMetrics(metrics);

      LOG.info("Initialised JVM metric instrumentation.");
    }

    final int interval = Integer.parseInt(properties.getProperty(GRAPHITE_REPORT_INTERVAL, GRAPHITE_REPORT_INTERVAL_DEFAULT));
    graphiteReporter.start(interval, TimeUnit.SECONDS);

    LOG.info("Metric reporting initialized on prefix [{}] toward Graphite instance {}:{}", fullPrefix, host, port);
  }

  private static String getHost(final Properties properties) {
    return properties.getProperty(GRAPHITE_HOST_PROP);
  }

  private static int getPort(final Properties properties) {
    final String portStr = properties.getProperty(GRAPHITE_PORT_PROP, GRAPHITE_PORT_DEFAULT);
    int port = 0;
    try {
      port = Integer.parseInt(portStr);
    } catch (final NumberFormatException e) {
      LOG.error("Could not parse graphite port: {}, crashing.", portStr);
      throw new IllegalStateException("Invalid configuration.", e);
    }

    return port;
  }

  public static MetricRegistry getMetrics() {
    return metrics;
  }
}
