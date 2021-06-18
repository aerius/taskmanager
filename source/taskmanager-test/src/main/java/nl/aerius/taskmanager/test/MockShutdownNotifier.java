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
package nl.aerius.taskmanager.test;

import java.io.IOException;

import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Mock implementation for {@link ShutdownNotifier} interfaces.
 */
class MockShutdownNotifier implements ShutdownNotifier {

  private boolean closed;

  @Override
  public void addShutdownListener(final ShutdownListener listener) {
    // Mock method.
  }

  @Override
  public void removeShutdownListener(final ShutdownListener listener) {
    // Mock method.
  }

  @Override
  public ShutdownSignalException getCloseReason() {
    // Mock method.
    return null;
  }

  @Override
  public void notifyListeners() {
    // Mock method.
  }

  /**
   * Mockes closing the connection.
   *
   * @throws IOException
   */
  public void close() throws IOException {
    closed = true;
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

}
