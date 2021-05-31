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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

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
    return new MockChannel();
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
    try {
      close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
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
}
