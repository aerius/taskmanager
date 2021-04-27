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
package nl.aerius.taskmanager.client.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import nl.aerius.taskmanager.client.configuration.ConnectionConfiguration;

/**
 * Test class for {@link RabbitMQQueueMonitor}.
 */
public class RabbitMQQueueMonitorTest {

  private static final String DUMMY = "dummy";

  @Test
  public void testGetWorkerQueueState() throws InterruptedException {
    final ConnectionConfiguration configuration = ConnectionConfiguration.builder()
        .brokerHost(DUMMY).brokerPort(0).brokerUsername(DUMMY).brokerPassword(DUMMY).build();
    final AtomicInteger workerSize = new AtomicInteger();
    final AtomicInteger messagesSize = new AtomicInteger();
    final QueueUpdateHandler mwps = new QueueUpdateHandler() {

      @Override
      public void onQueueUpdate(final String queueName, final int numberOfWorkers, final int numberOfMessages, final int numberOfMessagesReady) {
        workerSize.set(numberOfWorkers);
        messagesSize.set(numberOfMessages);
      }
    };
    final RabbitMQQueueMonitor rpm = new RabbitMQQueueMonitor(configuration) {
      @Override
      protected JsonElement getJsonResultFromApi(final String apiPath) {
        final JsonParser parser = new JsonParser();
        try (final InputStream fr = getClass().getResourceAsStream("queue_aerius.worker.ops.txt");
            final InputStreamReader is = new InputStreamReader(fr);
            final JsonReader jr = new JsonReader(is)) {
          return parser.parse(jr);
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    rpm.addQueueUpdateHandler(DUMMY, mwps);
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor.execute(rpm);
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      rpm.shutdown();
      assertEquals(4, workerSize.get(), "Number of workers");
      assertEquals(3, messagesSize.get(), "Number of messages");
    } finally {
      executor.shutdown();
    }
  }

}
