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

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.Basic.RecoverOk;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Channel.FlowOk;
import com.rabbitmq.client.AMQP.Exchange.BindOk;
import com.rabbitmq.client.AMQP.Exchange.DeclareOk;
import com.rabbitmq.client.AMQP.Exchange.DeleteOk;
import com.rabbitmq.client.AMQP.Exchange.UnbindOk;
import com.rabbitmq.client.AMQP.Queue.PurgeOk;
import com.rabbitmq.client.AMQP.Tx.CommitOk;
import com.rabbitmq.client.AMQP.Tx.RollbackOk;
import com.rabbitmq.client.AMQP.Tx.SelectOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.FlowListener;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import nl.aerius.taskmanager.client.util.QueueHelper;

/**
 * Mock implementation simulating {@link Channel}.
 */
public class MockChannel implements Channel {

  private static final Logger LOG = LoggerFactory.getLogger(MockChannel.class);

  private static final Map<String, PriorityBlockingQueue<Body>> QUEUES = new ConcurrentHashMap<>();
  private static final Map<Long, Body> QUEUED = new ConcurrentHashMap<>();
  private byte[] received;
  private final ExecutorService executors = Executors.newCachedThreadPool();

  private boolean closed;

  public byte[] getReceived() {
    return received.clone();
  }

  @Override
  public final void basicPublish(final String exchange, final String routingKey, final BasicProperties props, final byte[] body) throws IOException {
    basicPublish(exchange, routingKey, false, false, props, body);
  }

  @Override
  public final void basicPublish(final String exchange, final String routingKey, final boolean mandatory, final BasicProperties props, final byte[] body)
      throws IOException {
    basicPublish(exchange, routingKey, mandatory, false, props, body);
  }

  @Override
  public void basicPublish(final String exchange, final String routingKey, final boolean mandatory, final boolean immediate,
      final BasicProperties props, final byte[] body) throws IOException {
    received = body == null ? new byte[0] : body.clone();
    if (routingKey != null) {
      getQueue(routingKey).add(new Body(routingKey, received, props));
    }
    if (props.getReplyTo() != null) {
      getQueue(props.getReplyTo()).add(new Body(routingKey, received, props));
    }
  }

  @Override
  public void addShutdownListener(final ShutdownListener listener) {
    // Mock method.
  }

  @Override
  public void removeShutdownListener(final ShutdownListener listener) {
    // Mock method.
  }

  @Override
  public ShutdownSignalException getCloseReason() {
    // Mock method.
    return null;
  }

  @Override
  public void notifyListeners() {
    // Mock method.
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

  @Override
  public int getChannelNumber() {
    // Mock method.
    return 0;
  }

  @Override
  public Connection getConnection() {
    // Mock method.
    return null;
  }

  @Override
  public void close() throws IOException {
    closed = true;
  }

  @Override
  public void close(final int closeCode, final String closeMessage) throws IOException {
    close();
  }

  @Override
  public FlowOk flow(final boolean active) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public FlowOk getFlow() {
    // Mock method.
    return null;
  }

  @Override
  public void abort() throws IOException {
    // Mock method.
  }

  @Override
  public void abort(final int closeCode, final String closeMessage) throws IOException {
    // Mock method.
  }

  @Override
  public void addReturnListener(final ReturnListener listener) {
    // Mock method.
  }

  @Override
  public boolean removeReturnListener(final ReturnListener listener) {
    // Mock method.
    return false;
  }

  @Override
  public void clearReturnListeners() {
    // Mock method.
  }

  @Override
  public void addFlowListener(final FlowListener listener) {
    // Mock method.
  }

  @Override
  public boolean removeFlowListener(final FlowListener listener) {
    // Mock method.
    return false;
  }

  @Override
  public void clearFlowListeners() {
    // Mock method.
  }

  @Override
  public void addConfirmListener(final ConfirmListener listener) {
    // Mock method.
  }

  @Override
  public boolean removeConfirmListener(final ConfirmListener listener) {
    // Mock method.
    return false;
  }

  @Override
  public void clearConfirmListeners() {
    // Mock method.
  }

  @Override
  public Consumer getDefaultConsumer() {
    // Mock method.
    return null;
  }

  @Override
  public void setDefaultConsumer(final Consumer consumer) {
    // Mock method.

  }

  @Override
  public void basicQos(final int prefetchSize, final int prefetchCount, final boolean global) throws IOException {
    // Mock method.

  }

  @Override
  public void basicQos(final int prefetchCount) throws IOException {
    // Mock method.

  }

  @Override
  public DeclareOk exchangeDeclare(final String exchange, final String type) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeclareOk exchangeDeclare(final String exchange, final String type, final boolean durable) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeclareOk exchangeDeclare(final String exchange, final String type, final boolean durable, final boolean autoDelete,
      final Map<String, Object> arguments) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeclareOk exchangeDeclare(final String exchange, final String type, final boolean durable, final boolean autoDelete, final boolean internal,
      final Map<String, Object> arguments) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeclareOk exchangeDeclarePassive(final String name) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeleteOk exchangeDelete(final String exchange, final boolean ifUnused) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public DeleteOk exchangeDelete(final String exchange) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public BindOk exchangeBind(final String destination, final String source, final String routingKey) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public BindOk exchangeBind(final String destination, final String source, final String routingKey, final Map<String, Object> arguments)
      throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public UnbindOk exchangeUnbind(final String destination, final String source, final String routingKey) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public UnbindOk exchangeUnbind(final String destination, final String source, final String routingKey, final Map<String, Object> arguments)
      throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare() throws IOException {
    return new com.rabbitmq.client.AMQP.Queue.DeclareOk() {

      @Override
      public int protocolClassId() {
        return 0;
      }

      @Override
      public int protocolMethodId() {
        return 0;
      }

      @Override
      public String protocolMethodName() {
        return null;
      }

      @Override
      public String getQueue() {
        return UUID.randomUUID().toString();
      }

      @Override
      public int getMessageCount() {
        return 0;
      }

      @Override
      public int getConsumerCount() {
        return 0;
      }
    };
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare(final String queue, final boolean durable, final boolean exclusive,
      final boolean autoDelete, final Map<String, Object> arguments) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclarePassive(final String queue) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(final String queue) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(final String queue, final boolean ifUnused, final boolean ifEmpty)
      throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(final String queue, final String exchange, final String routingKey)
      throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(final String queue, final String exchange, final String routingKey,
      final Map<String, Object> arguments) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(final String queue, final String exchange, final String routingKey)
      throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(final String queue, final String exchange, final String routingKey,
      final Map<String, Object> arguments) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public PurgeOk queuePurge(final String queue) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public GetResponse basicGet(final String queue, final boolean autoAck) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public void basicAck(final long deliveryTag, final boolean multiple) throws IOException {
    if (QUEUED.containsKey(deliveryTag)) {
      QUEUED.remove(deliveryTag);
    }
  }

  @Override
  public void basicNack(final long deliveryTag, final boolean multiple, final boolean requeue) throws IOException {
    if (QUEUED.containsKey(deliveryTag)) {
      final Body body = QUEUED.remove(deliveryTag);
      body.setPriority(1);
      getQueue(body.getQueueName()).add(body);
    }

  }

  @Override
  public void basicReject(final long deliveryTag, final boolean requeue) throws IOException {
    // Mock method.

  }

  @Override
  public String basicConsume(final String queue, final Consumer callback) throws IOException {
    return basicConsume(queue, true, callback);
  }

  @Override
  public String basicConsume(final String queue, final boolean autoAck, final Consumer callback) throws IOException {
    return basicConsume(queue, true, "", callback);
  }

  @Override
  public String basicConsume(final String queue, final boolean autoAck, final String consumerTag, final Consumer callback) throws IOException {
    return basicConsume(queue, true, "", false, false, null, callback);
  }

  @Override
  public String basicConsume(final String queueName, final boolean autoAck, final String consumerTag, final boolean noLocal, final boolean exclusive,
      final Map<String, Object> arguments, final Consumer callback) throws IOException {
    scheduleCallback(queueName, callback);
    return null;
  }

  @Override
  public void basicCancel(final String consumerTag) throws IOException {
    // Mock method.

  }

  @Override
  public RecoverOk basicRecover() throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public RecoverOk basicRecover(final boolean requeue) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  @Deprecated
  public void basicRecoverAsync(final boolean requeue) throws IOException {
    // Mock method.
  }

  @Override
  public SelectOk txSelect() throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public CommitOk txCommit() throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public RollbackOk txRollback() throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public com.rabbitmq.client.AMQP.Confirm.SelectOk confirmSelect() throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public long getNextPublishSeqNo() {
    // Mock method.
    return 0;
  }

  @Override
  public boolean waitForConfirms() throws InterruptedException {
    // Mock method.
    return false;
  }

  @Override
  public void waitForConfirmsOrDie() throws IOException, InterruptedException {
    // Mock method.

  }

  @Override
  public void asyncRpc(final Method method) throws IOException {
    // Mock method.

  }

  @Override
  public Command rpc(final Method method) throws IOException {
    // Mock method.
    return null;
  }

  @Override
  public boolean waitForConfirms(final long arg0) throws InterruptedException, TimeoutException {
    // Mock method.
    return false;
  }

  @Override
  public void waitForConfirmsOrDie(final long arg0) throws IOException, InterruptedException, TimeoutException {
    // Mock method.
  }

  protected byte[] mockResults(final BasicProperties properties, final byte[] body) throws Exception {
    return body;
  }

  private PriorityBlockingQueue<Body> getQueue(final String key) {
    synchronized (QUEUES) {
      if (!QUEUES.containsKey(key)) {
        QUEUES.put(key, new PriorityBlockingQueue<>(10, new Comparator<Body>() {
          @Override
          public int compare(final Body o1, final Body o2) {
            return Integer.compare(o1.getPriority(), o2.getPriority());
          }
        }));
      }
      return QUEUES.get(key);
    }
  }

  private void scheduleCallback(final String queueName, final Consumer callback) {
    executors.execute(new Runnable() {

      @Override
      public void run() {
        final PriorityBlockingQueue<Body> queue = getQueue(queueName);
        try {
          final Body value = queue.take();
          final long id = new Random().nextLong();
          QUEUED.put(id, value);
          final Envelope envelope = new Envelope(id, false, "", "");
          callback.handleDelivery(null, envelope, value.getProperties(), mockResults(value.getProperties(), value.getBody()));
        } catch (final Exception e) {
          LOG.error("handle Delivery in MockChannel failed", e);
          try {
            callback.handleDelivery(null, null, null, QueueHelper.objectToBytes(e));
          } catch (final IOException e1) {
            LOG.error("handle Delivery of error in MockChannel failed", e1);
          }
          return;
        }
      }
    });
  }

  private class Body {
    private int priority;
    private final byte[] content;
    private final String queueName;
    private final BasicProperties properties;

    public Body(final String queueName, final byte[] content, final BasicProperties properties) {
      this.queueName = queueName;
      this.content = content;
      this.properties = properties;
      priority = 1;
    }

    public byte[] getBody() {
      return content;
    }

    public String getQueueName() {
      return queueName;
    }

    public int getPriority() {
      return priority;
    }

    public BasicProperties getProperties() {
      return properties;
    }

    public void setPriority(final int priority) {
      this.priority = priority;
    }
  }
}
