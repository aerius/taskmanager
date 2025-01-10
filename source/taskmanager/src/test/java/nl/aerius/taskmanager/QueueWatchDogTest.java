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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link QueueWatchDog}.
 */
class QueueWatchDogTest {

  @Test
  void testIsItDead() {
    final AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.now());
    final QueueWatchDog qwd = new QueueWatchDog() {
      @Override
      protected LocalDateTime now() {
        return now.get();
      }
    };
    assertFalse(qwd.isItDead(false, 0), "No running workers, with no messages, no problem");
    assertFalse(qwd.isItDead(false, 10), "No running workers, no problem");
    assertFalse(qwd.isItDead(true, 10), "Running workers, with messages, no problem");
    assertFalse(qwd.isItDead(true, 0), "Running workers, with no messages, possible problem, we just wait");
    now.set(now.get().plusMinutes(20)); //fast forward 20 minutes.
    assertTrue(qwd.isItDead(true, 0), "Running workers, with no messages, after specified time; yes reset");
  }
}
