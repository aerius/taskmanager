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
package nl.aerius.taskmanager.scheduler.priorityqueue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import nl.aerius.taskmanager.domain.PriorityTaskQueue;

/**
 * Map to keep track of queue configurations and number of tasks running on workers.
 */
class PriorityQueueMap {
  /**
   * Map to keep track of queue configuration per queue.
   */
  private final Map<String, PriorityTaskQueue> taskQueueConfigurations = new ConcurrentHashMap<>();
  /**
   * Map to keep track of the number of tasks running on workers per queue.
   */
  private final Map<String, AtomicInteger> tasksOnWorkersPerQueue = new ConcurrentHashMap<>();
  private final Function<String, String> keyMapper;

  public PriorityQueueMap() {
    this(Function.identity());
  }

  public PriorityQueueMap(final Function<String, String> keyMapper) {
    this.keyMapper = keyMapper;
  }

  public PriorityTaskQueue get(final String queueName) {
    return taskQueueConfigurations.get(key(queueName));
  }

  public boolean containsKey(final String queueName) {
    return taskQueueConfigurations.containsKey(key(queueName));
  }

  public PriorityTaskQueue put(final String queueName, final PriorityTaskQueue queue) {
    final String keyQueueName = key(queueName);

    tasksOnWorkersPerQueue.computeIfAbsent(keyQueueName, k -> new AtomicInteger());
    return taskQueueConfigurations.put(keyQueueName, queue);
  }

  public void decrementOnWorker(final String queueName) {
    tasksOnWorkersPerQueue.get(key(queueName)).decrementAndGet();
  }

  public void incrementOnWorker(final String queueName) {
    tasksOnWorkersPerQueue.get(key(queueName)).incrementAndGet();
  }

  public int onWorker(final String queueName) {
    return Optional.ofNullable(tasksOnWorkersPerQueue.get(key(queueName))).map(AtomicInteger::intValue).orElse(0);
  }

  public void remove(final String queueName) {
    final String keyQueueName = key(queueName);

    taskQueueConfigurations.remove(keyQueueName);
    tasksOnWorkersPerQueue.remove(keyQueueName);
  }

  private String key(final String queueName) {
    return keyMapper.apply(queueName);
  }
}
