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
import java.net.InetAddress;
import java.util.Map;

import com.rabbitmq.client.BlockedCallback;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.UnblockedCallback;

/**
 * Mock class for a {@link Connection}.
 */
public class MockConnection extends MockShutdownNotifier implements Connection {

  @Override
  public InetAddress getAddress() {
    return null;
  }

  @Override
  public int getPort() {
    return 0;
  }

  @Override
  public int getChannelMax() {
    return 0;
  }

  @Override
  public int getFrameMax() {
    return 0;
  }

  @Override
  public int getHeartbeat() {
    return 0;
  }

  @Override
  public Map<String, Object> getClientProperties() {
    return null;
  }

  @Override
  public Map<String, Object> getServerProperties() {
    return null;
  }

  @Override
  public Channel createChannel() throws IOException {
    return MockedChannelFactory.create();
  }

  @Override
  public Channel createChannel(final int channelNumber) throws IOException {
    return null;
  }

  @Override
  public void close(final int closeCode, final String closeMessage) throws IOException {
    close();
  }

  @Override
  public void close(final int timeout) throws IOException {
    close();
  }

  @Override
  public void close(final int closeCode, final String closeMessage, final int timeout) throws IOException {
    close();
  }

  @Override
  public void abort() {
    close();
  }

  @Override
  public void abort(final int closeCode, final String closeMessage) {
    abort();
  }

  @Override
  public void abort(final int timeout) {
    abort();
  }

  @Override
  public void abort(final int closeCode, final String closeMessage, final int timeout) {
    abort();
  }

  @Override
  public String getClientProvidedName() {
    return null;
  }

  @Override
  public void addBlockedListener(final BlockedListener listener) {}

  @Override
  public boolean removeBlockedListener(final BlockedListener listener) {
    return false;
  }

  @Override
  public void clearBlockedListeners() {}

  @Override
  public ExceptionHandler getExceptionHandler() {
    return null;
  }

  @Override
  public BlockedListener addBlockedListener(final BlockedCallback blockedCallback, final UnblockedCallback unblockedCallback) {
    return null;
  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  public void setId(final String id) {

  }
}
