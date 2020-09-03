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
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.overheid.aerius.metrics.MetricFactory;
import nl.overheid.aerius.taskmanager.PriorityTaskScheduler.PriorityTaskSchedulerFactory;
import nl.overheid.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.overheid.aerius.taskmanager.client.BrokerConnectionFactory;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskSchedule;
import nl.overheid.aerius.taskmanager.domain.TaskManagerConfiguration;
import nl.overheid.aerius.taskmanager.mq.RabbitMQAdaptorFactory;

/**
 * The main class, used to start the task manager.
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  /**
   * When this main method is used, the task manager will be started.
   *
   * @param args no arguments needed, but if supplied, they should fit the description given by using -help.
   * @throws IOException When an error occurred reading a file during configuration.
   * @throws SQLException When an error occurred trying to contact the database during configuration.
   * @throws ParseException When command line option parsing failed
   * @throws InterruptedException When taskmanager interrupted
   */
  public static void main(final String[] args) throws IOException, SQLException, ParseException, InterruptedException {
    final CmdOptions cmdOptions = new CmdOptions(args);
    if (cmdOptions.printIfInfoOption()) {
      return;
    }

    LOG.info("--------------------------------TASKMANAGER STARTED------------------------------------");
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        LOG.info("--------------------------------TASKMANAGER STOPPED-----------------------------------");
      }
    });
    final ExecutorService executorService = Executors.newCachedThreadPool();

    try {
      startupFromConfiguration(executorService, cmdOptions.getConfigFile());
    } finally {
      if (!executorService.isTerminated()) {
        executorService.shutdown();
      }
    }
  }

  /**
   * Starts the task manager.
   *
   * @param executorService execution service
   * @param configurationFile configuration properties file
   * @throws SQLException sql errors
   * @throws IOException io errors
   * @throws InterruptedException interrupted errors
   */
  static void startupFromConfiguration(final ExecutorService executorService, final String configurationFile)
      throws IOException, SQLException, InterruptedException {
    final Properties props = ConfigurationManager.getPropertiesFromFile(configurationFile);
    MetricFactory.init(props, "taskmanager");
    final TaskManagerConfiguration tmConfig = ConfigurationManager.loadConfiguration(props);
    final BrokerConnectionFactory bcFactory = new BrokerConnectionFactory(executorService, tmConfig.getBrokerConfiguration());
    final AdaptorFactory aFactory = new RabbitMQAdaptorFactory(bcFactory);
    final PriorityTaskSchedulerFactory schedulerFactory = new PriorityTaskSchedulerFactory();
    final TaskManager<PriorityTaskQueue, PriorityTaskSchedule> manager = new TaskManager<>(executorService, aFactory, schedulerFactory);
    final TaskSchedulerWatcher<PriorityTaskQueue, PriorityTaskSchedule> watcher =
        new TaskSchedulerWatcher<>(manager, schedulerFactory, tmConfig.getConfigurationDirectory());

    try {
      watcher.run(); // This will wait until shutdown.
    } finally {
      manager.shutdown();
    }
  }
}
