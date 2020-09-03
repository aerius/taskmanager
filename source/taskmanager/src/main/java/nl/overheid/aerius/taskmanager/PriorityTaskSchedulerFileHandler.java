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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import nl.overheid.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Handler to read and write Priority Task Scheduler configuration files.
 */
class PriorityTaskSchedulerFileHandler implements SchedulerFileConfigurationHandler<PriorityTaskQueue, PriorityTaskSchedule> {

  private static final String PREFIX = "priority-task-scheduler.";

  private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

  @Override
  public PriorityTaskSchedule read(final File file) throws FileNotFoundException, IOException {
    try (final Reader reader = new FileReader(file)) {
      return gson.fromJson(reader, PriorityTaskSchedule.class);
    }
  }

  @Override
  public void write(final File path, final PriorityTaskSchedule priorityTaskSchedule) throws IOException {
    try (final Writer writer = new FileWriter(new File(path, PREFIX + priorityTaskSchedule.getWorkerQueueName() + ".json"))) {
      writer.write(gson.toJson(priorityTaskSchedule));
    }
   }
}
