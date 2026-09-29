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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;

import nl.aerius.taskmanager.domain.RabbitMQQueueStatus;

/**
 * Test class for {@link RabbitMQClientQueueReporter}.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQClientQueueReporterTest {

  private @Mock Meter meter;
  private Consumer<ObservableDoubleMeasurement> recordMetrics;

  private @Captor ArgumentCaptor<Double> metricCaptor;
  private @Captor ArgumentCaptor<Attributes> attributesCaptor;

  private RabbitMQClientQueueReporter reporter;

  @BeforeEach
  void beforeEach() {
    final DoubleGaugeBuilder builder = mock(DoubleGaugeBuilder.class);
    final ObservableDoubleGauge gauge = mock(ObservableDoubleGauge.class);
    doReturn(builder).when(meter).gaugeBuilder(any());
    doReturn(builder).when(builder).setDescription(any());
    doAnswer(a -> {
      recordMetrics = a.getArgument(0);
      return gauge;
    }).when(builder).buildWithCallback(any());
    reporter = new RabbitMQClientQueueReporter(meter, "worker.test");
  }

  @Test
  void testOnClientQueueUpdate() {
    reporter.onClientQueueUpdate("aerius.test.some_client_queue", new RabbitMQQueueStatus(12, 20, 3));
    final ObservableDoubleMeasurement measurement = mock(ObservableDoubleMeasurement.class);

    recordMetrics.accept(measurement);
    verify(measurement).record(metricCaptor.capture(), attributesCaptor.capture());

    assertEquals(20, metricCaptor.getValue().intValue(), "Expected to report the number of messages");
    assertEquals("some_client_queue", attributesCaptor.getValue().get(AttributeKey.stringKey("queue_name")),
        "Expected the client queue are attribute.");
  }

  @Test
  void testFitler() {
    assertTrue(reporter.filter("aerius.test.some_client_queue"), "Should return true if queue name contains worker 'test' name");
    assertFalse(reporter.filter("aerius.other.some_client_queue"), "Should return false if queue name doesn't contain worker 'test' name");
  }
}
