/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.util;

import io.confluent.ksql.name.SourceName;
import io.confluent.ksql.query.BlockingRowQueue;
import io.confluent.ksql.query.LimitHandler;
import io.confluent.ksql.schema.ksql.LogicalSchema;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;

/**
 * Metadata of a transient query, e.g. {@code SELECT * FROM FOO;}.
 */
public class TransientQueryMetadata extends QueryMetadata {

  private final BlockingRowQueue rowQueue;
  private final AtomicBoolean isRunning = new AtomicBoolean(true);

  // CHECKSTYLE_RULES.OFF: ParameterNumberCheck
  public TransientQueryMetadata(
      final String statementString,
      final KafkaStreams kafkaStreams,
      final LogicalSchema logicalSchema,
      final Set<SourceName> sourceNames,
      final String executionPlan,
      final BlockingRowQueue rowQueue,
      final String queryApplicationId,
      final Topology topology,
      final Map<String, Object> streamsProperties,
      final Map<String, Object> overriddenProperties,
      final Consumer<QueryMetadata> closeCallback,
      final long closeTimeout) {
    // CHECKSTYLE_RULES.ON: ParameterNumberCheck
    super(
        statementString,
        kafkaStreams,
        logicalSchema,
        sourceNames,
        executionPlan,
        queryApplicationId,
        topology,
        streamsProperties,
        overriddenProperties,
        closeCallback,
        closeTimeout
    );
    this.rowQueue = Objects.requireNonNull(rowQueue, "rowQueue");

    if (!logicalSchema.metadata().isEmpty() || !logicalSchema.key().isEmpty()) {
      throw new IllegalArgumentException("Transient queries only support value columns");
    }
  }

  public boolean isRunning() {
    return isRunning.get();
  }

  public BlockingRowQueue getRowQueue() {
    return rowQueue;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof TransientQueryMetadata)) {
      return false;
    }

    final TransientQueryMetadata that = (TransientQueryMetadata) o;

    return Objects.equals(this.rowQueue, that.rowQueue) && super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rowQueue, super.hashCode());
  }

  public void setLimitHandler(final LimitHandler limitHandler) {
    rowQueue.setLimitHandler(limitHandler);
  }

  @Override
  public void stop() {
    close();
  }

  @Override
  protected void doClose(final boolean cleanUp) {
    // To avoid deadlock, close the queue first to ensure producer side isn't blocked trying to
    // write to the blocking queue, otherwise super.close call can deadlock:
    rowQueue.close();

    // Now safe to close:
    super.doClose(cleanUp);
    isRunning.set(false);
  }
}
