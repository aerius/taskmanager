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
package nl.aerius.taskmanager.exception;

import nl.aerius.taskmanager.domain.Message;

/**
 * Failed to send the message to the worker.
 */
public class MessageDeliveryFailedException extends Exception {

  private static final long serialVersionUID = -5304526617667892468L;

  private final Message message;

  /**
   * Message delivery failed exception.
   * @param message message that failed
   * @param e exception throw
   */
  public MessageDeliveryFailedException(final Message message, final Exception e) {
    super(e);
    this.message = message;
  }

  public Message getDeliveredMessage() {
    return message;
  }
}
