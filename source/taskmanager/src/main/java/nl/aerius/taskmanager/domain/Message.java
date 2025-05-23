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
package nl.aerius.taskmanager.domain;

/**
 * Abstract data class containing the message send from clients to workers.
 */
public abstract class Message {

  /**
   * @return The ID indicating messages that are correlated.
   */
  public abstract String getCorrelationId();

  /**
   * @return The unique ID for this message.
   */
  public abstract String getMessageId();

  @Override
  public String toString() {
    return this.getMessageId();
  }
}
