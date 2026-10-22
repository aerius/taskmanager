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
package nl.aerius.taskmanager.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Connection;

/**
 * Test for class {@link BrokerConnectionFactory}.
 */
class BrokerConnectionFactoryTest {

  private final ExecutorService executor = mock(ExecutorService.class);
  private final Connection mockConnection = mock(Connection.class);
  private BrokerConnectionFactory factory;

  @BeforeEach
  void beforeEach() {
    factory = spy(new BrokerConnectionFactory(executor) {
      @Override
      protected Connection createNewConnection() throws IOException, TimeoutException {
        return mockConnection;
      }
    });
  }

  @Test
  void testRegisteringAndClose() {
    final TaskManagerClientSender client1 = new TaskManagerClientSender(factory);
    final TaskManagerClientSender client2 = new TaskManagerClientSender(factory);

    factory.close();
    factory.deRegisterClient(client1);
    factory.deRegisterClient(client2);
    verify(factory, times(2)).registerClient(any());
    verify(factory, times(4)).deRegisterClient(any());
  }

  @Test
  void testOpenConnection() {
    assertSame(mockConnection, factory.getConnection(), "Should return the mocked connection");

    doReturn(true).when(mockConnection).isOpen();
    assertTrue(factory.isOpen(), "Expected an open connection");
  }

}
