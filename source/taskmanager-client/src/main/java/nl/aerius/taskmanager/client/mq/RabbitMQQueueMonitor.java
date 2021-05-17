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
package nl.aerius.taskmanager.client.mq;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import nl.aerius.taskmanager.client.configuration.ConnectionConfiguration;

/**
 * RabbitMQ implementation to manage implementation specific part of the worker pool. This covers managing the total size of available workers and
 * informing the worker pool when a worker is finished.
 * <p>When the connection shuts down this pool manager will shutdown.
 */
public class RabbitMQQueueMonitor implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQQueueMonitor.class);
  private static final int TIMEOUT = (int) TimeUnit.SECONDS.toMillis(3);

  private final long refreshDelay;
  private final Map<String, List<QueueUpdateHandler>> handlersMap = new HashMap<>();
  private final ConnectionConfiguration configuration;
  private final CloseableHttpClient httpClient;
  private final HttpHost targetHost;
  private final HttpClientContext context;
  private boolean running;

  public RabbitMQQueueMonitor(final ConnectionConfiguration configuration) {
    this.configuration = configuration;
    refreshDelay = TimeUnit.SECONDS.toMillis(configuration.getBrokerManagementRefreshRate());
    httpClient = HttpClientBuilder.create().setDefaultRequestConfig(getDefaultRequestConfig()).build();
    if (refreshDelay == 0) {
      // if refreshDelay == 0 then querying to a server is disabled. This is used in unit tests.
      targetHost = null;
      context = null;
    } else {
      targetHost = new HttpHost(configuration.getBrokerHost(), configuration.getBrokerManagementPort(), "http");
      final CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(
          new AuthScope(configuration.getBrokerHost(), configuration.getBrokerManagementPort()),
          new UsernamePasswordCredentials(configuration.getBrokerUsername(), configuration.getBrokerPassword()));
      // Create AuthCache instance
      final AuthCache authCache = new BasicAuthCache();
      // Generate BASIC scheme object and add it to the local auth cache
      final BasicScheme basicAuth = new BasicScheme();
      authCache.put(targetHost, basicAuth);

      context = HttpClientContext.create();
      context.setCredentialsProvider(credsProvider);
      context.setAuthCache(authCache);
    }
  }

  public void addQueueUpdateHandler(final String queueName, final QueueUpdateHandler handler) {
    if (!handlersMap.containsKey(queueName)) {
      handlersMap.put(queueName, new ArrayList<>());
    }
    handlersMap.get(queueName).add(handler);
  }

  public void removeQueueUpdateHandler(final String queueName, final QueueUpdateHandler handler) {
    handlersMap.get(queueName).remove(handler);
  }

  @Override
  public void run() {
    running = refreshDelay > 0;
    try {
      while (running) {
        handlersMap.forEach(this::updateWorkerQueueState);
        try {
          Thread.sleep(refreshDelay);
        } catch (final InterruptedException e) {
          running = false;
          LOG.error("RabbitMQQueueMonitor died in sleep.", e);
          Thread.currentThread().interrupt();
        }
      }
    } finally {
      try {
        httpClient.close();
      } catch (final IOException e) {
        LOG.trace("IOException on close httpclient", e);
      }
    }
  }

  /**
   * Stops the worker pool.
   */
  public void shutdown() {
    running = false;
  }

  private void updateWorkerQueueState(final String queueName, final List<QueueUpdateHandler> handlers) {
    // Use RabbitMQ HTTP-API.
    // URL: [host]:[port]/api/queues/[virtualHost]/[QueueName]
    final String virtualHost = configuration.getBrokerVirtualHost().replace("/", "%2f");
    final String apiPath = "/api/queues/" + virtualHost + "/" + queueName;

    try {
      final JsonElement je = getJsonResultFromApi(apiPath);
      if (je == null) {
        LOG.error("Queue configuration from RabbitMQ admin json get call returned null.");
      } else {
        final JsonObject jsonObject = je.getAsJsonObject();
        final int numberOfWorkers = getJsonIntPrimitive(jsonObject, "consumers");
        final int numberOfMessages = getJsonIntPrimitive(jsonObject, "messages");
        final int numberOfMessagesReady = getJsonIntPrimitive(jsonObject, "messages_ready");
        handlers.forEach(h -> h.onQueueUpdate(queueName, numberOfWorkers, numberOfMessages, numberOfMessagesReady));
        LOG.trace("[{}] active workers:{}, messages:{}", queueName, numberOfWorkers, numberOfMessages);
      }
    } catch (URISyntaxException | UnsupportedEncodingException e) {
      LOG.error("RabbitMQQueueMonitor", e);
    }
  }

  private int getJsonIntPrimitive(final JsonObject jsonObject, final String key) {
    final int value;
    if (jsonObject == null || jsonObject.getAsJsonPrimitive(key) == null) {
      value = 0;
    } else {
      value = jsonObject.getAsJsonPrimitive(key).getAsInt();
    }
    return value;
  }

  protected JsonElement getJsonResultFromApi(final String apiPath) throws URISyntaxException, UnsupportedEncodingException {
    JsonElement returnElement = null;
    final URI uri = new URI("http://" + configuration.getBrokerHost() + ":" + configuration.getBrokerManagementPort() + apiPath);
    try (final CloseableHttpResponse response = httpClient.execute(targetHost, new HttpGet(uri), context)) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        try (final InputStreamReader is = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final JsonReader jr = new JsonReader(is)) {
          returnElement = JsonParser.parseReader(jr);
        }
      } else {
        LOG.error("Status code wasn't 200 when retrieving json result. Status was: {}, {}",
            response.getStatusLine().getStatusCode(), response.getStatusLine().toString());
      }
    } catch (final IOException e) {
      LOG.error("Exception while trying to retrieve json result.", e);
    }
    return returnElement;
  }

  private RequestConfig getDefaultRequestConfig() {
    return RequestConfig.custom()
        .setConnectTimeout(TIMEOUT)
        .setConnectionRequestTimeout(TIMEOUT)
        .setSocketTimeout(TIMEOUT)
        .build();
  }
}
