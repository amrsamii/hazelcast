package com.hazelcast.internal.query.exec;

import com.hazelcast.internal.query.expression.Predicate;
import com.hazelcast.internal.query.io.Row;
import com.hazelcast.internal.query.io.RowBatch;

/**
 * Filter executor.
 */
public class FilterExec extends AbstractExec {
    /** Upstream executor. */
    private final Exec upstream;

    /** Filter. */
    private final Predicate filter;

    /** Last upstream batch. */
    private RowBatch curBatch;

    /** Current position in the last upstream batch. */
    private int curBatchPos = -1;

    /** Maximum position in the last upstream batch. */
    private int curBatchRowCnt = -1;

    /** Current row. */
    private Row curRow;

    /** Whether upstream operator is finished. */
    private boolean upstreamDone;

    public FilterExec(Exec upstream, Predicate filter) {
        this.upstream = upstream;
        this.filter = filter;
    }

    @Override
    public IterationResult advance() {
        while (true) {
            // No batch -> need to fetch one.
            if (curBatch == null) {
                // Already fetched everything -> return.
                if (upstreamDone)
                    return IterationResult.FETCHED_DONE;

                switch (upstream.advance()) {
                    case FETCHED_DONE:
                        upstreamDone = true;

                        // Fall-through.

                    case FETCHED:
                        RowBatch batch = upstream.currentBatch();
                        int batchRowCnt = batch.getRowCount();

                        if (batchRowCnt > 0) {
                            curBatch = batch;
                            curBatchPos = -1;
                            curBatchRowCnt = batchRowCnt;
                        }

                    case WAIT:
                        return IterationResult.WAIT;

                    default:
                        // TODO: Implement error handling.
                        throw new UnsupportedOperationException("Implement me");
                }
            }

            if (curBatch != null) {
                IterationResult res = advanceCurrentBatch();

                if (res != null)
                    return res;
            }
        }
    }

    /**
     * Advance position in the current batch
     *
     * @return Iteration result is succeeded, {@code null} if failed.
     */
    private IterationResult advanceCurrentBatch() {
        // Loop until the first matching row is found.
        assert curBatch != null;

        RowBatch curBatch0 = curBatch;
        int curBatchPos0 = curBatchPos;

        while (true) {
            curBatchPos0++;

            if (curBatchPos0 == curBatchRowCnt) {
                // Shifted behind -> nullify and return null.
                curBatch = null;
                curBatchPos = -1;
                curBatchRowCnt = -1;

                curRow = null;

                return upstreamDone ? IterationResult.FETCHED_DONE : null;
            }
            else {
                // Shifted successfully -> check filter match.
                Row candidateRow = curBatch0.getRow(curBatchPos0);

                if (filter.eval(ctx, candidateRow)) {
                    curBatchPos = curBatchPos0;
                    curRow = candidateRow;

                    if (curBatchPos0 + 1 == curBatchRowCnt && upstreamDone)
                        return IterationResult.FETCHED_DONE;
                    else
                        return IterationResult.FETCHED;
                }
            }
        }
    }

    @Override
    public RowBatch currentBatch() {
        return curRow;
    }
}