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
package nl.overheid.aerius.taskmanager;

import java.io.IOException;

import nl.overheid.aerius.taskmanager.adaptor.TaskMessageHandler;
import nl.overheid.aerius.taskmanager.domain.Message;
import nl.overheid.aerius.taskmanager.domain.MessageMetaData;

/**
 * Mock implementation of {@link TaskMessageHandler}.
 */
public class MockTaskMessageHandler implements TaskMessageHandler {

  private MessageMetaData failedMessage;
  private MessageMetaData message;
  private Message abortedMessage;

  @Override
  public void addMessageReceivedHandler(final MessageReceivedHandler messageReceivedHandler) {
    //no-op
  }

  @Override
  public void start() throws IOException {
    //no-op
  }

  @Override
  public void shutDown() throws IOException {
    //no-op
  }

  @Override
  public void messageDeliveredToWorker(final MessageMetaData message) throws IOException {
    this.message = message;
  }

  public MessageMetaData getMessage() {
    return message;
  }

  @Override
  public void messageDeliveryToWorkerFailed(final MessageMetaData message) throws IOException {
    failedMessage = message;
  }

  @Override
  public void messageDeliveryAborted(final Message message, final RuntimeException exception) throws IOException {
    abortedMessage = message;
  }

  public Message getAbortedMessage() {
    return abortedMessage;
  }

  public MessageMetaData getFailedMessage() {
    return failedMessage;
  }

}
