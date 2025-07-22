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
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.aerius.taskmanager.adaptor.AdaptorFactory;
import nl.aerius.taskmanager.adaptor.WorkerProducer;
import nl.aerius.taskmanager.adaptor.WorkerSizeObserver;
import nl.aerius.taskmanager.adaptor.WorkerSizeProviderProxy;
import nl.aerius.taskmanager.domain.QueueConfig;
import nl.aerius.taskmanager.domain.TaskConsumer;
import nl.aerius.taskmanager.domain.TaskQueue;
import nl.aerius.taskmanager.domain.TaskSchedule;
import nl.aerius.taskmanager.metrics.OpenTelemetryMetrics;
import nl.aerius.taskmanager.metrics.PerformanceMetricsReporter;
import nl.aerius.taskmanager.scheduler.TaskScheduler;
import nl.aerius.taskmanager.scheduler.TaskScheduler.TaskSchedulerFactory;

/**
 * Main task manager class, manages all schedulers.
 */
class TaskManager<T extends TaskQueue, S extends TaskSchedule<T>> {

  private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final AdaptorFactory factory;
  private final TaskSchedulerFactory<T, S> schedulerFactory;
  private final WorkerSizeProviderProxy workerSizeObserverProxy;
  private final Map<String, TaskScheduleBucket> buckets = new HashMap<>();

  public TaskManager(final ExecutorService executorService, final ScheduledExecutorService scheduledExecutorService, final AdaptorFactory factory,
      final TaskSchedulerFactory<T, S> schedulerFactory, final WorkerSizeProviderProxy workerSizeObserverProxy) {
    this.executorService = executorService;
    this.scheduledExecutorService = scheduledExecutorService;
    this.factory = factory;
    this.schedulerFactory = schedulerFactory;
    this.workerSizeObserverProxy = workerSizeObserverProxy;
  }

  /**
   * Add or Update a new task scheduler.
   *
   * @param schedule scheduler configuration
   * @throws InterruptedException
   */
  public boolean updateTaskScheduler(final TaskSchedule<T> schedule) throws InterruptedException {
    // Set up scheduler with worker pool
    final String workerQueueName = schedule.getWorkerQueueName();
    final QueueConfig workerQueueConfig = new QueueConfig(workerQueueName, schedule.isDurable(), schedule.isEagerFetch(), schedule.getQueueType());
    if (!buckets.containsKey(workerQueueName)) {
      LOG.info("Added scheduler for worker queue {}", workerQueueName);
      buckets.put(workerQueueName, new TaskScheduleBucket(workerQueueConfig));
    }
    final TaskScheduleBucket taskScheduleBucket = buckets.get(workerQueueName);

    taskScheduleBucket.updateQueues(schedule.getQueues(), workerQueueConfig);
    return taskScheduleBucket.isRunning();
  }

  /**
   * Removed the scheduler for the given worker type.
   *
   * @param workerQueueName queueName
   */
  public void removeTaskScheduler(final String workerQueueName) {
    LOG.info("Removed schedule for worker queue {}", workerQueueName);
    final TaskScheduleBucket bucket = buckets.get(workerQueueName);

    if (bucket != null) {
      bucket.shutdown();
      buckets.remove(workerQueueName);
    }
  }

  /**
   * Shuts down all schedulers and consumers.
   */
  public void shutdown() {
    buckets.forEach((k, v) -> v.shutdown());
    buckets.clear();
  }

  private class TaskScheduleBucket {
    private final TaskDispatcher dispatcher;
    private final WorkerProducer workerProducer;
    private final Map<String, TaskConsumer> taskConsumers = new HashMap<>();
    private final TaskScheduler<T> taskScheduler;
    private final String workerQueueName;

    public TaskScheduleBucket(final QueueConfig queueConfig) throws InterruptedException {
      this.workerQueueName = queueConfig.queueName();
      taskScheduler = schedulerFactory.createScheduler(queueConfig);
      workerProducer = factory.createWorkerProducer(queueConfig);
      final WorkerPool workerPool = new WorkerPool(workerQueueName, workerProducer, taskScheduler);
      final PerformanceMetricsReporter reporter = new PerformanceMetricsReporter(scheduledExecutorService, queueConfig.queueName(),
          OpenTelemetryMetrics.METER, workerPool);
      workerProducer.addWorkerFinishedHandler(reporter);
      workerSizeObserverProxy.addObserver(workerQueueName, workerPool);
      if (taskScheduler instanceof final WorkerSizeObserver wzo) {
        workerSizeObserverProxy.addObserver(workerQueueName, wzo);
      }
      workerProducer.start();
      // Set up metrics
      WorkerPoolMetrics.setupMetrics(workerPool, workerQueueName);
      // Set up dispatcher
      dispatcher = new TaskDispatcher(workerQueueName, taskScheduler, workerPool);
      executorService.execute(dispatcher);
      Thread.sleep(TimeUnit.SECONDS.toMillis(1)); // just wait a little second to make sure the process is actually running.
      LOG.info("Started taskscheduler {}: {}", taskScheduler.getClass().getSimpleName(), queueConfig);
    }

    /**
     * @return
     */
    public boolean isRunning() {
      return dispatcher.isRunning();
    }

    private void updateQueues(final List<T> newTaskQueues, final QueueConfig workerQueueConfig) {
      final Map<String, ? extends TaskQueue> newTaskQueuesMap = newTaskQueues.stream().filter(Objects::nonNull)
          .collect(Collectors.toMap(TaskQueue::getQueueName, Function.identity()));
      // Remove queues that are not in the new list
      final List<Entry<String, TaskConsumer>> removedQueues = taskConsumers.entrySet().stream().filter(e -> !newTaskQueuesMap.containsKey(e.getKey()))
          .toList();
      removedQueues.forEach(e -> removeTaskConsumer(e.getKey()));
      // Add and Update existing queues
      newTaskQueues.stream().filter(Objects::nonNull).forEach(tc -> addOrUpdateTaskQueue(tc, workerQueueConfig));
    }

    private void addOrUpdateTaskQueue(final T taskQueueConfiguration, final QueueConfig workerQueueConfig) {
      addTaskConsumerIfAbsent(new QueueConfig(taskQueueConfiguration.getQueueName(), workerQueueConfig.durable(), workerQueueConfig.eagerFetch(),
          workerQueueConfig.queueType()));
      taskScheduler.updateQueue(taskQueueConfiguration);
    }

    /**
     * Adds a task consumer.
     *
     * @param queueConfig Configuration parameters for the queue
     */
    public void addTaskConsumerIfAbsent(final QueueConfig queueConfig) {
      taskConsumers.computeIfAbsent(queueConfig.queueName(), tqn -> {
        try {
          final TaskConsumer taskConsumer = new TaskConsumerImpl(executorService, queueConfig, dispatcher, factory);
          taskConsumer.start();
          LOG.info("Started task queue: {}", queueConfig);
          return taskConsumer;
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    /**
     * Removes a task consumer with the given queue name.
     *
     * @param workerQueueName queue name of the task consumer
     */
    private void removeTaskConsumer(final String taskQueueName) {
      LOG.info("Removed task queue {}", taskQueueName);
      taskScheduler.removeQueue(taskQueueName);
      taskConsumers.remove(taskQueueName).shutdown();
    }

    public void shutdown() {
      dispatcher.shutdown();
      workerProducer.shutdown();
      WorkerPoolMetrics.removeMetrics(workerQueueName);
      taskConsumers.forEach((k, v) -> v.shutdown());
    }
  }
}
