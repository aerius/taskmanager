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

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

/**
 * Base class for a single schedule configuration.
 * @param <T> specific task queue configuration class
 */
public class TaskSchedule<T extends TaskQueue> {

  @Expose
  private String workerQueueName;

  @Expose
  private List<T> queues = new ArrayList<>();

  public String getWorkerQueueName() {
    return workerQueueName;
  }

  public void setWorkerQueueName(final String workerQueueName) {
    this.workerQueueName = workerQueueName;
  }

  public List<T> getTaskQueues() {
    return queues;
  }

  public void setTaskConfigurations(final List<T> taskConfigurations) {
    this.queues = taskConfigurations;
  }
}
