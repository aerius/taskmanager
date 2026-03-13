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

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link StartupGuard}
 */
class StartupGuardTest {

  private boolean open;
  @Test
  void testOpen() {
    open = false;
    final StartupGuard guard = new StartupGuard();
    new Thread(this::trigger).run();
    assertFalse(guard.isOpen(), "Guard should not be open.");
    guard.onNumberOfWorkersUpdate(0, 1);
    assertFalse(guard.isOpen(), "Guard should still not be open when number of message > 0.");
    guard.onNumberOfWorkersUpdate(0, 0);
    assertTrue(guard.isOpen(), "Guard should be open when number of message has become 0.");
    guard.onNumberOfWorkersUpdate(0, 1);
    assertTrue(guard.isOpen(), "Guard should still be be open once the number of messages has been zero once.");
  }

  private void trigger() {
    open = true;
  }
}
