package com.hazelcast.internal.query.exec;

import com.hazelcast.internal.query.QueryContext;
import com.hazelcast.internal.query.io.Row;

/**
 * Partitioner which decides where to send specific batch.
 */
public interface SendPartitioner {
    /**
     * Map row to the outbox.
     *
     * @param ctx Query context.
     * @param row Row.
     * @return Outbox to send row to.
     */
    Outbox map(QueryContext ctx, Row row);
}
