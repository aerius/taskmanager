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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;

import nl.aerius.taskmanager.adaptor.WorkerProducer.WorkerMetrics;
import nl.aerius.taskmanager.client.TaskMetrics;

/**
 * Test class for {@link PerformanceMetricsReporter}.
 *
 */
@ExtendWith(MockitoExtension.class)
class PerformanceMetricsReporterTest {

  private static final String QUEUE_GROUP_NAME = "ops";
  private static final String QUEUE_1 = "queue 1";
  private static final String QUEUE_2 = "queue 2";

  private final Map<String, DoubleHistogram> mockedHistograms = new HashMap<>();

  @Mock Meter mockedMeter;
  @Mock WorkerMetrics workMetrics;
  @Mock ScheduledExecutorService scheduledExecutorService;
  @Mock DoubleGauge mockGauge;
  @Captor ArgumentCaptor<Runnable> methodCaptor;
  @Captor ArgumentCaptor<Double> durationCaptor;

  private PerformanceMetricsReporter reporter;

  @BeforeEach
  void beforeEach() {
    doAnswer(inv -> {
      final DoubleHistogramBuilder mockBuilder = mock(DoubleHistogramBuilder.class);
      final DoubleHistogram histogram = mock(DoubleHistogram.class);
      doReturn(mockBuilder).when(mockBuilder).setDescription(any());
      doReturn(histogram).when(mockBuilder).build();
      mockedHistograms.put(inv.getArgument(0, String.class), histogram);
      return mockBuilder;
    }).when(mockedMeter).histogramBuilder(any());
    final DoubleGaugeBuilder mockGaugeBuilder = mock(DoubleGaugeBuilder.class);
    doReturn(mockGaugeBuilder).when(mockedMeter).gaugeBuilder(any());
    doReturn(mockGaugeBuilder).when(mockGaugeBuilder).setDescription(any());
    doReturn(mockGauge).when(mockGaugeBuilder).build();
    reporter = new PerformanceMetricsReporter(scheduledExecutorService, QUEUE_GROUP_NAME, mockedMeter, workMetrics);
    verify(scheduledExecutorService).scheduleWithFixedDelay(methodCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class));
  }

  @Test
  void testOnWorkDispatched() {
    doReturn(10).when(workMetrics).getCurrentWorkerSize();
    reporter.onWorkDispatched("1", createMap(QUEUE_1, 100L));
    reporter.onWorkDispatched("2", createMap(QUEUE_2, 200L));
    methodCaptor.getValue().run();
    verify(mockedHistograms.get("aer.taskmanager.dispatched")).record(eq(2.0), any());
    verify(mockedHistograms.get("aer.taskmanager.dispatched.wait")).record(durationCaptor.capture(), any());
    verify(mockedHistograms.get("aer.taskmanager.dispatched.queue")).record(eq(2.0), any());
    verify(mockedHistograms.get("aer.taskmanager.dispatched.queue.wait")).record(durationCaptor.capture(), any());
    durationCaptor.getAllValues()
        .forEach(v -> assertTrue(v > 99.0, "Duration should report at least 100.0 as it is the offset of the start time, but was " + v));
  }

  @Test
  void testOnWorkerFinished() {
    doReturn(10).when(workMetrics).getCurrentWorkerSize();
    reporter.onWorkerFinished("1", createMap(QUEUE_1, 100L));
    reporter.onWorkerFinished("2", createMap(QUEUE_2, 200L));
    methodCaptor.getValue().run();
    verify(mockedHistograms.get("aer.taskmanager.work")).record(eq(2.0), any());
    verify(mockedHistograms.get("aer.taskmanager.work.duration")).record(durationCaptor.capture(), any());
    verify(mockedHistograms.get("aer.taskmanager.work.queue")).record(eq(2.0), any());
    verify(mockedHistograms.get("aer.taskmanager.work.queue.duration")).record(durationCaptor.capture(), any());
    durationCaptor.getAllValues()
        .forEach(v -> assertTrue(v > 99.0, "Duration should report at least 100.0 as it is the offset of the start time, but was " + v));
  }

  @Test
  void testWorkLoad() throws InterruptedException {
    doReturn(4).when(workMetrics).getCurrentWorkerSize();
    reporter.onWorkDispatched("1", createMap(QUEUE_1, 100L));
    reporter.onWorkDispatched("2", createMap(QUEUE_2, 200L));
    methodCaptor.getValue().run();
    Thread.sleep(10); // Add a bit of delay to get sime time frame between these 2 run calls.
    methodCaptor.getValue().run();
    verify(mockGauge, times(2)).set(durationCaptor.capture(), any());
    assertEquals(50.0, durationCaptor.getAllValues().get(1));
  }

  private Map<String, Object> createMap(final String queueName, final long duration) {
    return new TaskMetrics().duration(duration).queueName(queueName).start(new Date().getTime() - 100).build();
  }
}
