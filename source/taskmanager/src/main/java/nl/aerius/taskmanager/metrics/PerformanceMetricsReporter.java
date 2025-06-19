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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;

import nl.aerius.taskmanager.adaptor.WorkerProducer.WorkerFinishedHandler;
import nl.aerius.taskmanager.adaptor.WorkerProducer.WorkerMetrics;
import nl.aerius.taskmanager.client.TaskMetrics;
import nl.aerius.taskmanager.metrics.DurationMetric.DurationMetricValue;

/**
 * Reports the following open telemetry metrics:
 *
 * - Number of tasks dispatched to a worker
 * - Number of tasks dispatched to a worker by queue it came from
 * - Average wait time of dispatched tasks to a worker
 * - Average wait time of dispatched tasks to a worker by queue it came from
 *
 * - Number of tasks run on a worker
 * - Number of tasks run on a worker by queue it came from
 * - Average duration of the run time of tasks run on a worker
 * - Average duration of the run time of tasks run on a worker by queue it came from
 *
 * - Average load (in percentage) of all workers (of a certain type) together.
 */
public class PerformanceMetricsReporter implements WorkerFinishedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(PerformanceMetricsReporter.class);

  private static final String DISPATCH = "Avg dispatch wait time ";
  private static final String WORK = "Avg work duration ";

  private final DoubleHistogram dispatchedWorkerCountHistorgram;
  private final DoubleHistogram dispatchedWorkerWaitHistorgram;
  private final DoubleHistogram dispatchedQueueCountHistorgram;
  private final DoubleHistogram dispatchedQueueWaitHistogram;

  private final DoubleHistogram workWorkerCountHistogram;
  private final DoubleHistogram workWorkerDurationHistogram;
  private final DoubleHistogram workQueueCountHistogram;
  private final DoubleHistogram workQueueDurationHistogram;

  private static final int UPDATE_TIME_SECONDS = 60;

  private final Map<String, DurationMetric> dispatchedQueueMetrics = new HashMap<>();
  private final DurationMetric dispatchedWorkerMetrics;
  private final Map<String, DurationMetric> workQueueMetrics = new HashMap<>();
  private final DurationMetric workWorkerMetrics;
  private final LoadMetric loadMetrics = new LoadMetric();

  private final Meter meter;
  private final String queueGroupName;
  private final WorkerMetrics workerMetrics;
  private final DoubleGauge loadGauge;

  private final Attributes workerAttributes;

  public PerformanceMetricsReporter(final ScheduledExecutorService newScheduledThreadPool, final String queueGroupName, final Meter meter,
      final WorkerMetrics workerMetrics) {
    this.queueGroupName = queueGroupName;
    this.meter = meter;
    this.workerMetrics = workerMetrics;

    // Histograms for measuring number of tasks, and average duration time it took before a task was send to to the worker.
    // Measures by worker and per queue to the worker
    dispatchedWorkerCountHistorgram = createHistorgram("aer.taskmanager.dispatched", "Count the number of tasks that are dispatched to a worker.");
    dispatchedWorkerWaitHistorgram = createHistorgram("aer.taskmanager.dispatched.wait",
        "Average wait time before a task is dispatched to a worker.");
    dispatchedQueueCountHistorgram = createHistorgram("aer.taskmanager.dispatched.queue",
        "Count the number of tasks from a queue that are dispatched to a worker.");
    dispatchedQueueWaitHistogram = createHistorgram("aer.taskmanager.dispatched.queue.wait",
        "Average wait time before a task from a queue is dispatched to a worker.");

    // Histograms for measuring number of tasks, and average duration time a task run took on a worker.
    // Measures by worker and per queue to the worker
    workWorkerCountHistogram = createHistorgram("aer.taskmanager.work", "Count the number task processed on a worker.");
    workWorkerDurationHistogram = createHistorgram("aer.taskmanager.work.duration",
        "Average duration time a task took to process on a worker, including wait time.");
    workQueueCountHistogram = createHistorgram("aer.taskmanager.work.queue",
        "Count the number task from a queue processed on a worker.");
    workQueueDurationHistogram = createHistorgram("aer.taskmanager.work.queue.duration",
        "Average duration time a task from a queue took to process on a worker, including wait time.");

    // Average load time (in percentage) of the work load on all workers together.
    loadGauge = meter.gaugeBuilder("aer.taskmanager.work.load").setDescription("Percentage of workers used in the timeframe.").build();

    workerAttributes = OpenTelemetryMetrics.workerAttributes(queueGroupName);
    dispatchedWorkerMetrics = new DurationMetric(workerAttributes);
    workWorkerMetrics = new DurationMetric(workerAttributes);
    newScheduledThreadPool.scheduleWithFixedDelay(this::update, 1, UPDATE_TIME_SECONDS, TimeUnit.SECONDS);
  }

  private DoubleHistogram createHistorgram(final String name, final String description) {
    return meter
        .histogramBuilder(name)
        .setDescription(description)
        .build();
  }

  @Override
  public void onWorkDispatched(final String messageId, final Map<String, Object> messageMetaData) {
    final TaskMetrics taskMetrics = new TaskMetrics(messageMetaData);
    taskMetrics.determineDuration();
    dispatchedQueueMetrics.computeIfAbsent(taskMetrics.queueName(), k -> createQueueDurationMetric(taskMetrics)).register(taskMetrics);
    dispatchedWorkerMetrics.register(taskMetrics);
    loadMetrics.register(1, workerMetrics.getCurrentWorkerSize());
  }

  @Override
  public synchronized void onWorkerFinished(final String messageId, final Map<String, Object> messageMetaData) {
    final TaskMetrics taskMetrics = new TaskMetrics(messageMetaData);
    taskMetrics.determineDuration();
    workQueueMetrics.computeIfAbsent(taskMetrics.queueName(), k -> createQueueDurationMetric(taskMetrics)).register(taskMetrics);
    workWorkerMetrics.register(taskMetrics);
    loadMetrics.register(-1, workerMetrics.getCurrentWorkerSize());
  }

  private DurationMetric createQueueDurationMetric(final TaskMetrics taskMetrics) {
    return new DurationMetric(OpenTelemetryMetrics.queueAttributes(queueGroupName, taskMetrics.queueName()));
  }

  private synchronized void update() {
    try {
      metrics(DISPATCH, dispatchedQueueMetrics, dispatchedWorkerCountHistorgram, dispatchedWorkerWaitHistorgram);
      metrics(DISPATCH, dispatchedQueueCountHistorgram, dispatchedQueueWaitHistogram, queueGroupName, dispatchedWorkerMetrics);
      metrics(WORK, workQueueMetrics, workWorkerCountHistogram, workWorkerDurationHistogram);
      metrics(WORK, workQueueCountHistogram, workQueueDurationHistogram, queueGroupName, workWorkerMetrics);
      workLoad();
    } catch (final RuntimeException e) {
      LOG.error("Update metrics failed.", e);
    }
  }

  private void metrics(final String prefixText, final Map<String, DurationMetric> metrics, final DoubleHistogram histogram,
      final DoubleHistogram waitHistorgram) {
    for (final Entry<String, DurationMetric> entry : metrics.entrySet()) {
      metrics(prefixText, histogram, waitHistorgram, entry.getKey(), entry.getValue());
    }
  }

  private void metrics(final String prefixText, final DoubleHistogram histogram, final DoubleHistogram waitHistorgram, final String name,
      final DurationMetric metrics) {
    final DurationMetricValue metric = metrics.process();
    final int count = metric.count();

    if (count > 0) {
      histogram.record(count, metrics.getAttributes());
      waitHistorgram.record(metric.avgDuration(), metrics.getAttributes());
      LOG.debug("{} for {}: {} ms/task (#tasks: {})", prefixText, name, metric.avgDuration(), count);
    }
  }

  private void workLoad() {
    final double load = loadMetrics.process();

    loadGauge.set(load, workerAttributes);
    LOG.debug("Workload for '{}' is: {}%", queueGroupName, Math.round(load));
  }
}
