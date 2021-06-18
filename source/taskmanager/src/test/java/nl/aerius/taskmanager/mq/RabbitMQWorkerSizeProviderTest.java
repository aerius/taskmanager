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
package nl.aerius.taskmanager.mq;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;

/**
 * Test class for {@link RabbitMQWorkerSizeProvider}
 */
public class RabbitMQWorkerSizeProviderTest extends AbstractRabbitMQTest {

  private RabbitMQWorkerSizeProvider provider;

  @Override
  @BeforeEach
  void setUp() throws Exception {
    super.setUp();
    provider = new RabbitMQWorkerSizeProvider(executor, factory);
  }

  @Test
  void testStartShutdown() throws IOException {
    final WorkerSizeObserver dummyObserver = Mockito.mock(WorkerSizeObserver.class);
    provider.addObserver("test", dummyObserver);
    provider.start();
    provider.shutdown();
    assertFalse(provider.removeObserver("test"), "Observer should already have been removed");
  }
}
