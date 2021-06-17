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

import java.io.IOException;

import org.mockito.Mockito;

import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.aerius.taskmanager.adaptor.WorkerProducer;
import nl.aerius.taskmanager.adaptor.WorkerSizeProviderProxy;

/**
 * Mock implementation of {@link AdaptorFactory}.
 */
public class MockAdaptorFactory implements AdaptorFactory {

  private final MockWorkerProducer mockWorkerProducer = new MockWorkerProducer();
  private final MockTaskMessageHandler mockTaskMessageHandler = new MockTaskMessageHandler();

  @Override
  public WorkerSizeProviderProxy createWorkerSizeProvider() {
    return Mockito.mock(WorkerSizeProviderProxy.class);
  }

  @Override
  public WorkerProducer createWorkerProducer(final String workerQueueName) {
    return mockWorkerProducer;
  }

  @Override
  public TaskMessageHandler createTaskMessageHandler(final String taskQueueName) throws IOException {
    return mockTaskMessageHandler;
  }

  public MockTaskMessageHandler getMockTaskMessageHandler() {
    return mockTaskMessageHandler;
  }
}
