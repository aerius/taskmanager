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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.overheid.aerius.taskmanager.client.configuration.BrokerConfiguration;
import nl.overheid.aerius.taskmanager.db.ConfigurationRepository;
import nl.overheid.aerius.taskmanager.domain.PriorityTaskSchedule;
import nl.overheid.aerius.taskmanager.domain.TaskManagerConfiguration;

/**
 * Class to help with loading and saving configuration.
 */
final class ConfigurationManager {

  private static final Logger LOG = LoggerFactory.getLogger(TaskManagerConfiguration.class);
  private static final int DEFAULT_RETRY_SECONDS = 60;

  private ConfigurationManager() {
  }

  /**
   * Load the configuration from default properties and a database configured in a property file
   * (either from a "taskmanager.properties" or from classpath).
   * @param props properties
   * @return The configuration file to be used by the taskmanager.
   * @throws IOException When an exception occurred trying to
   * @throws SQLException When an exception occurred retrieving configuration from the database.
   */
  public static TaskManagerConfiguration loadConfiguration(final Properties props) throws IOException, SQLException {
    final boolean hadDatabaseConf = getDatabaseUrl(props) != null;

    final File configDirectory;

    if (hadDatabaseConf) {
      configDirectory = Files.createTempDirectory("taskmanager").toFile();

      writeConfigurationFromLegacyDatabase(props, configDirectory);
      LOG.error("LEGACY: Database configuration not supported anymore. "
          + "The configuration from the database is written in directory: {}. "
          + "Remove the database configuration and put the configuration files in the given directory.", configDirectory);
    } else {
      configDirectory = null;
    }
    return loadConfigurationFromFile(new TaskManagerProperties(props), configDirectory);
  }

  private static void writeConfigurationFromLegacyDatabase(final Properties props, final File configDirectoryFile) throws IOException, SQLException {
    final List<PriorityTaskSchedule> schedules = loadConfigurationFromDatabase(props);
    final PriorityTaskSchedulerFileHandler handler = new PriorityTaskSchedulerFileHandler();

    schedules.forEach(s -> {
      try {
        handler.write(configDirectoryFile, s);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /**
   *
   * @param properties taskmanager configuration options
   * @param temporaryConfigurationDirectory directory to use when configuration is read from the database.
   * @return
   * @throws FileNotFoundException
   */
  private static TaskManagerConfiguration loadConfigurationFromFile(final TaskManagerProperties properties,
      final File temporaryConfigurationDirectory) throws FileNotFoundException {
    final boolean hasNoTemporaryConfigurationDirectory = temporaryConfigurationDirectory == null;
    final List<String> validationErrors = properties.getValidationErrors(hasNoTemporaryConfigurationDirectory);

    if (!validationErrors.isEmpty()) {
      throw new IllegalArgumentException(validationErrors.stream().collect(Collectors.joining(", ")));
    }
    final File configurationDirectory = hasNoTemporaryConfigurationDirectory ? properties.getConfigDirectory() : temporaryConfigurationDirectory;
    final TaskManagerConfiguration tmc = new TaskManagerConfiguration(properties.buildConnectionConfiguration(), configurationDirectory);
    LOG.info("Read configuration from {}", configurationDirectory);
    return tmc;
  }

  static List<PriorityTaskSchedule> loadConfigurationFromDatabase(final Properties properties) throws IOException, SQLException {
    try (final Connection connection = getConnection(properties)) {
      return ConfigurationRepository.getTaskSchedulerConfigurations(connection);
    } catch (final SQLException e) {
      LOG.error("Error retrieving configuration from database.", e);
      throw e;
    }
  }

  public static Properties getPropertiesFromFile(final String propertiesFileName) throws IOException {
    final File localFile = new File(propertiesFileName);
    final Properties properties = new Properties();

    try (final InputStream inputStream = new FileInputStream(localFile)) {
      properties.load(inputStream);
    }
    return properties;
  }

  private static Connection getConnection(final Properties properties) throws IOException, SQLException {
    return getConnection((String) getDatabaseUrl(properties),
        (String) properties.get("database.username"),
        (String) properties.get("database.password"),
        retryTime(properties.get("database.retry")));
  }

  private static Object getDatabaseUrl(final Properties properties) {
    return properties.get("database.url");
  }

  private static int retryTime(final Object retry) {
    return retry == null ? DEFAULT_RETRY_SECONDS : Integer.valueOf((String) retry);
  }

  /**
   * Returns a new connection to the database. If connecting fails it keep retrying forever with the given retry time.
   *
   * @param jdbcURL The (JDBC) url to the database.
   * @param dbUsername The username to use to connect to the database.
   * @param dbPassword The password to use to connect to the database.
   * @param retry time in seconds to wait to retry if connecting to the database fails.
   * @return a database connection
   * @throws SQLException
   */
  private static Connection getConnection(final String jdbcURL, final String dbUsername, final String dbPassword, final int retry)
      throws SQLException {
    try {
      return DriverManager.getConnection(jdbcURL, dbUsername, dbPassword);
    } catch (final SQLException e) {
      LOG.warn("Connection to the database failed. Retrying. Message: {}", e.getMessage());
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(retry));
        return getConnection(jdbcURL, dbUsername, dbPassword, retry);
      } catch (final InterruptedException e1) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  /**
   * TaskManager property configurations.
   */
  private static class TaskManagerProperties extends BrokerConfiguration {

    private static final String PROPERTY_CONFIG_DIRECTORY = "taskmanager.configuration.directory";

    /**
     * @param properties Properties containing the predefined properties.
     */
    public TaskManagerProperties(final Properties properties) {
      super(properties);
    }

    public List<String> getValidationErrors(final boolean validateConfDirectory) {
      final List<String> reasons = super.getValidationErrors();

      if (validateConfDirectory) {
        final File configurationDirectory = getConfigDirectory();

        if (configurationDirectory == null) {
          reasons.add("Missing configuration directory '" + PROPERTY_CONFIG_DIRECTORY + "'");
        } else if (!(configurationDirectory.exists() && configurationDirectory.isDirectory() && configurationDirectory.canRead())) {
          reasons.add("Can't read taskmanager queue configuration directory: " + configurationDirectory);
        }
      }
      return reasons;
    }

    public File getConfigDirectory() {
      final String configDirectory = getProperty(PROPERTY_CONFIG_DIRECTORY);

      return configDirectory == null ? null : new File(configDirectory);
    }
  }

}
