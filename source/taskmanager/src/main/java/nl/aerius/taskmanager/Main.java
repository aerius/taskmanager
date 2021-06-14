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

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.metrics.MetricFactory;
import nl.aerius.taskmanager.PriorityTaskScheduler.PriorityTaskSchedulerFactory;
import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.adaptor.WorkerSizeProviderProxy;
import nl.aerius.taskmanager.client.BrokerConnectionFactory;
import nl.aerius.taskmanager.domain.PriorityTaskQueue;
import nl.aerius.taskmanager.domain.PriorityTaskSchedule;
import nl.aerius.taskmanager.domain.TaskManagerConfiguration;
import nl.aerius.taskmanager.mq.RabbitMQAdaptorFactory;

/**
 * The main class, used to start the task manager.
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);
  /**
   * Number of threads for the scheduled threads.
   */
  private static final int THREAD_POOL_SIZE = 30;

  private Main() {
    // main class
  }

  /**
   * When this main method is used, the task manager will be started.
   *
   * @param args no arguments needed, but if supplied, they should fit the description given by using -help.
   * @throws IOException When an error occurred reading a file during configuration.
   * @throws ParseException When command line option parsing failed
   */
  public static void main(final String[] args) throws IOException, ParseException {
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
    final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);

    try {
      startupFromConfiguration(executorService, scheduledExecutorService, cmdOptions.getConfigFile());
    } finally {
      if (!executorService.isTerminated()) {
        executorService.shutdown();
      }
      if (!scheduledExecutorService.isTerminated()) {
        scheduledExecutorService.shutdown();
      }
    }
  }

  /**
   * Starts the task manager.
   * @param executorService
   *
   * @param executorService dynamic execution service
   * @param scheduledExecutorService scheduled execution service for scheduled tasks
   * @param configurationFile configuration properties file
   * @throws IOException io errors
   */
  static void startupFromConfiguration(final ExecutorService executorService, final ScheduledExecutorService scheduledExecutorService,
      final String configurationFile) throws IOException {
    final Properties props = ConfigurationManager.getPropertiesFromFile(configurationFile);
    MetricFactory.init(props, "taskmanager");
    final TaskManagerConfiguration tmConfig = ConfigurationManager.loadConfiguration(props);
    final BrokerConnectionFactory bcFactory = new BrokerConnectionFactory(executorService, tmConfig.getBrokerConfiguration());
    final AdaptorFactory aFactory = new RabbitMQAdaptorFactory(scheduledExecutorService, bcFactory);
    final WorkerSizeProviderProxy workerSizeObserver = aFactory.createWorkerSizeProvider();
    final PriorityTaskSchedulerFactory schedulerFactory = new PriorityTaskSchedulerFactory();
    final TaskManager<PriorityTaskQueue, PriorityTaskSchedule> manager = new TaskManager<>(executorService, aFactory, schedulerFactory, workerSizeObserver);
    final TaskSchedulerWatcher<PriorityTaskQueue, PriorityTaskSchedule> watcher = new TaskSchedulerWatcher<>(manager, schedulerFactory,
        tmConfig.getConfigurationDirectory());

    try {
      workerSizeObserver.start();
      watcher.run(); // This will wait until shutdown.
    } finally {
      manager.shutdown();
      workerSizeObserver.shutdown();
    }
  }
}
