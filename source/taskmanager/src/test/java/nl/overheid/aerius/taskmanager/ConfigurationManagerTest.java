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

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import nl.overheid.aerius.taskmanager.domain.TaskManagerConfiguration;

/**
 * Test for {@link ConfigurationManager}.
 */
public class ConfigurationManagerTest {

  private Properties properties;

  @Before
  public void before() throws IOException {
    final String propFile = getClass().getClassLoader().getResource("taskmanager_test.properties").getPath();
    properties = ConfigurationManager.getPropertiesFromFile(propFile);
  }

  @Test(timeout = 2000)
  public void testLoadConfiguration() throws IOException, SQLException {
    final TaskManagerConfiguration tmc = ConfigurationManager.loadConfiguration(properties);

    assertNotNull("No username could be read from configuration file", tmc.getBrokerConfiguration().getBrokerUsername());
  }
}
