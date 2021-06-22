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
package nl.aerius.taskmanager;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;

/**
 * Handler to read and write Priority Task Scheduler configuration files.
 */
class PriorityTaskSchedulerFileHandler implements SchedulerFileConfigurationHandler<PriorityTaskQueue, PriorityTaskSchedule> {

  private static final String PREFIX = "priority-task-scheduler.";

  private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

  @Override
  public PriorityTaskSchedule read(final File file) throws IOException {
    try (final Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return gson.fromJson(reader, PriorityTaskSchedule.class);
    }
  }

  @Override
  public void write(final File path, final PriorityTaskSchedule priorityTaskSchedule) throws IOException {
    final File targetFile = new File(path, PREFIX + priorityTaskSchedule.getWorkerQueueName() + ".json");
    try (final Writer writer = Files.newBufferedWriter(targetFile.toPath(), StandardCharsets.UTF_8)) {
      writer.write(gson.toJson(priorityTaskSchedule));
    }
  }
}
