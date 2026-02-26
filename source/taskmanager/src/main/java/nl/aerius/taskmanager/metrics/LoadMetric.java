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
package nl.aerius.taskmanager.metrics;

/**
 * Class to keep track of work load per worker type.
 * Each time a new task is added (dispatched) or removed (work finished) the number of running workers is counted and calculated how long
 * these workers were running from the last time measured point. The time multiplied by the factor of number of running workers relative to the
 * number of workers available is added to a counter.
 * When {@link #process()} is called the average work load is calculated by dividing the counted running workers time by the total measured time.
 */
class LoadMetric {

  public record AverageLoad(double averageLoad, int averageFree) {}

  /**
   * Last time {@link #register(int, int)} was called.
   */
  private long last = System.currentTimeMillis();
  /**
   * Total measured time since the last time {@link #process()} was called.
   */
  private int totalMeasureTime;
  /**
   * Measured load time as the sum of load for specific time moments. Sum of (load * time frame).
   * Dividing this number by the total time of the time frame will give an average load time.
   */
  private double totalLoad;
  /**
   * Measured free workers as the sum of number of free workers for specific time moments. Sum of (free workers * time frame).
   * Dividing this number by the total time of the time frame will give an average number of free workers.
   */
  private long totalFree;
  /**
   * Number of workers running at a time.
   */
  private int runningWorkers;
  /**
   * Total number of available workers.
   */
  private int numberOfWorkers;

  /**
   * Register change in number of running workers.
   *
   * @param deltaActiveWorkers number of jobs on the workers being added or subtracted.
   * @param numberOfWorkers Number of available workers
   */
  public synchronized void register(final int deltaActiveWorkers, final int numberOfWorkers) {
    this.numberOfWorkers = numberOfWorkers;
    final long newLast = System.currentTimeMillis();
    final long delta = newLast - last;

    totalLoad += delta * (numberOfWorkers > 0 ? (runningWorkers / (double) numberOfWorkers) : 0);
    totalFree += delta * Math.max(0, numberOfWorkers - runningWorkers);
    totalMeasureTime += delta;
    last = newLast;
    runningWorkers += deltaActiveWorkers;
  }

  /**
   * Resets the metric state. Sets running workers to 0, and resets the average load time by calling process.
   */
  public synchronized void reset() {
    runningWorkers = 0;
    process();
  }

  /**
   * Calculates average duration over the last time frame since this method was called. Internals are reset in this method to a new measure point.
   *
   * @return Average load of the workers since the last time this method was called
   */
  public synchronized AverageLoad process() {
    // Call register here to set the end time this moment. This will calculate workers running up till now as being active.
    register(0, numberOfWorkers);
    final double averageLoad = totalMeasureTime > 0 ? (totalLoad * 100.0) / totalMeasureTime : 0;
    final int averageFree = totalMeasureTime > 0 ? (int) Math.floor(totalFree / totalMeasureTime) : 0;

    totalMeasureTime = 0;
    totalLoad = 0;
    totalFree = 0;
    return new AverageLoad(averageLoad, averageFree);
  }
}
