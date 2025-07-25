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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import nl.aerius.taskmanager.domain.TaskManagerConfiguration;

/**
 * Test for {@link ConfigurationManager}.
 */
class ConfigurationManagerTest {

  private Properties properties;

  @BeforeEach
  void before() throws IOException {
    final String propFile = getClass().getClassLoader().getResource("taskmanager_test.properties").getPath();
    properties = ConfigurationManager.getPropertiesFromFile(propFile);
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS)
  void testLoadConfiguration() {
    final TaskManagerConfiguration tmc = ConfigurationManager.loadConfiguration(properties);

    assertNotNull(tmc.getBrokerConfiguration().getBrokerUsername(), "No username could be read from configuration file");
  }
}
