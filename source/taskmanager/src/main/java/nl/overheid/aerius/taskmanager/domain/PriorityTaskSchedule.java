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
package nl.overheid.aerius.taskmanager.domain;

import java.io.Serializable;

import com.google.gson.annotations.Expose;

/**
 * The configuration of a TaskScheduler.
 */
public class PriorityTaskSchedule extends TaskSchedule<PriorityTaskQueue> implements Serializable {

  private static final long serialVersionUID = 1L;

  // configured with DB
  @Expose(serialize = false)
  private Integer id;

  /**
   * @param id The (database) ID.
   * @param workerQueueName worker type
   */
  public PriorityTaskSchedule(final Integer id, final String workerQueueName) {
    this.id = id;
    setWorkerQueueName(workerQueueName);
  }

  public Integer getId() {
    return id;
  }

  public void setId(final Integer id) {
    this.id = id;
  }

  public String getDescription() {
    return "";
  }
}
