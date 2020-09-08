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
package nl.aerius.taskmanager.client;

/**
 * Interface for acting on data retrieved from the message queue, where we are expecting multiple results.
 */
public interface TaskMultipleResultCallback extends TaskResultCallback {

  /**
   * Check if the received object is actually the indication that the task is finished.
   *
   * @param value The value to check for. Should have a way to determine if this is the final result (either by type or some property)
   * @return True if it's the final result, false if it isn't.
   */
  boolean isFinalResult(Object value);

  /**
   * @param listener The listener that wants to know about cancellations.
   */
  void setTaskCancelListener(TaskCancelListener listener);

}
