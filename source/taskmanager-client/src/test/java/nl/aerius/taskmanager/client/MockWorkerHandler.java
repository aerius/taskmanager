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
package nl.aerius.taskmanager.client;

import java.io.Serializable;

/**
 *
 */
class MockWorkerHandler implements WorkerHandler {

  private boolean closed;
  private Object lastReturnObject;

  @Override
  public Serializable handleWorkLoad(final Serializable input, final WorkerIntermediateResultSender resultSender, final String correlationId) {
    Serializable returnObject = null;
    if (input instanceof TestIntegerDoubleTaskInput) {
      final TestIntegerDoubleTaskInput inputDoubleTask = (TestIntegerDoubleTaskInput) input;
      final TestIntegerDoubleTaskOutput outputDoubleTask = new TestIntegerDoubleTaskOutput();
      outputDoubleTask.setDoubled(inputDoubleTask.getTobeDoubled() * 2);
      returnObject = outputDoubleTask;
      try {
        Thread.sleep(200);
      } catch (final InterruptedException e) {
        //
      }
    } else if (input instanceof TestThrowExceptionTaskInput) {
      //throw a runtime exception to test what happens.
      returnObject = new IllegalArgumentException();
    } else if (input instanceof TestTaskInput) {
      returnObject = new TestTaskOutput();
    } else {
      returnObject = new UnsupportedOperationException();
    }
    lastReturnObject = returnObject;
    return returnObject;
  }

  public boolean isClosed() {
    return closed;
  }

  public Object getLastReturnObject() {
    return lastReturnObject;
  }

}

class TestTaskInput implements Serializable {

  private static final long serialVersionUID = -3219757676475152784L;

}

class TestThrowExceptionTaskInput extends TestTaskInput {

  private static final long serialVersionUID = -7964350615206270007L;

}

class TestIntegerDoubleTaskInput extends TestTaskInput {

  private static final long serialVersionUID = -4152840760999866348L;

  private int tobeDoubled;

  public int getTobeDoubled() {
    return tobeDoubled;
  }

  public void setTobeDoubled(final int tobeDoubled) {
    this.tobeDoubled = tobeDoubled;
  }

}

class TestTaskOutput implements Serializable {

  private static final long serialVersionUID = -606039928235937483L;

}

class TestIntegerDoubleTaskOutput extends TestTaskOutput {

  private static final long serialVersionUID = -7124955912712297100L;

  private int doubled;

  public int getDoubled() {
    return doubled;
  }

  public void setDoubled(final int doubled) {
    this.doubled = doubled;
  }

}
