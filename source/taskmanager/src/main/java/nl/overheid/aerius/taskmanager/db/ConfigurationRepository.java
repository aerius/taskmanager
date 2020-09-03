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
package nl.overheid.aerius.taskmanager.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import nl.overheid.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Task manager database configuration repository.
 *
 * @deprecated Database configuration is replaced with file configuration and will be removed in a future version.
 */
@Deprecated
public final class ConfigurationRepository {

  private static final String GET_TASK_SCHEDULER_CONFIGURATIONS =
      "SELECT task_scheduler_id, name, description"
          + " FROM system.task_schedulers";

  private static final String GET_TASK_CONFIGURATIONS_FOR_SCHEDULER =
      "SELECT name, description, priority, max_capacity_use "
          + " FROM system.task_scheduler_queues "
          + " WHERE task_scheduler_id = ? ";

  private ConfigurationRepository() {
    //don't allow to instantiate
  }

  /**
   * Returns list of schedulers from the database that are assigned to a taskmanager.
   *
   * @param connection The connection to use for any required queries (will not be closed by the method)
   * @return Returns the list of schedules read from the database
   * @throws SQLException When an exception occurred querying the database.
   */
  public static List<PriorityTaskSchedule> getTaskSchedulerConfigurations(final Connection connection) throws SQLException {
    final List<PriorityTaskSchedule> schedules = new ArrayList<>();

    try (final PreparedStatement taskSchedulerPS = connection
        .prepareStatement(GET_TASK_SCHEDULER_CONFIGURATIONS)) {
      final ResultSet rs = taskSchedulerPS.executeQuery();

      while (rs.next()) {
        final PriorityTaskSchedule schedulerConfig = new PriorityTaskSchedule(
            rs.getInt("task_scheduler_id"),
            rs.getString("name"));

        schedules.add(schedulerConfig);
      }
    }
    for (final PriorityTaskSchedule tsc : schedules) {
      tsc.setTaskConfigurations(getTaskConfigurationsForScheduler(connection, tsc));
    }
    return schedules;
  }

  /**
   * Get a list of taskconfigurations from the database that are assigned to a scheduler.
   * @param connection The connection to use for any required queries (will not be closed by the method)
   * @param schedulerConfig The taskSchedulerConfiguration to retrieve the task configurations for.
   * @return A list of taskconfigurations configured for the scheduler.
   * @throws SQLException When an exception occurred querying the database.
   */
  public static List<PriorityTaskQueue> getTaskConfigurationsForScheduler(final Connection connection,
      final PriorityTaskSchedule schedulerConfig) throws SQLException {
    final List<PriorityTaskQueue> taskConfigs = new ArrayList<>();
    try (final PreparedStatement taskSchedulerPS = connection
        .prepareStatement(GET_TASK_CONFIGURATIONS_FOR_SCHEDULER)) {
      taskSchedulerPS.setInt(1, schedulerConfig.getId());
      final ResultSet rs = taskSchedulerPS.executeQuery();
      while (rs.next()) {
        final PriorityTaskQueue taskConfig = new PriorityTaskQueue(
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("priority"),
            Double.parseDouble(Float.toString(rs.getFloat("max_capacity_use"))));
        taskConfigs.add(taskConfig);
      }
    }
    return taskConfigs;
  }
}
