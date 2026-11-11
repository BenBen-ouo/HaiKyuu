package network;

import java.util.Locale;
import java.util.function.Consumer;

/** 每個 Client 自己的診斷計數；不控制快照接受、排序或模型更新。 */
final class SnapshotTiming {
    private static final long GAP_NANOS = 50_000_000L;
    private static final long QUEUE_NANOS = 25_000_000L;
    private static final long SUMMARY_NANOS = 5_000_000_000L;
    private final Consumer<String> report;
    private long lastReceive, lastApply, summaryAt;
    private long received, applied;
    private boolean hasReceive, hasApply;
    private int lastSequence = -1;

    SnapshotTiming(Consumer<String> report) { this.report = report; }

    synchronized void received(int sequence, int serverTick, long now) {
        if (hasReceive && now - lastReceive >= GAP_NANOS) {
            duration("snapshot receive gap", now - lastReceive, sequence, serverTick);
        }
        if (!hasReceive) summaryAt = now;
        hasReceive = true;
        lastReceive = now;
        received++;
    }

    synchronized void applied(int sequence, int serverTick, long arrivedAt, long now) {
        // 可靠事件後重套同一份位置不算新快照，不能掩蓋真正的套用間隔。
        if (sequence <= lastSequence) return;
        if (hasApply && now - lastApply >= GAP_NANOS) {
            duration("snapshot apply gap", now - lastApply, sequence, serverTick);
        }
        if (now - arrivedAt >= QUEUE_NANOS) {
            duration("snapshot receive-to-apply", now - arrivedAt, sequence, serverTick);
        }
        hasApply = true;
        lastApply = now;
        lastSequence = sequence;
        applied++;
        if (hasReceive && now - summaryAt >= SUMMARY_NANOS) {
            report.accept("snapshot counts received=" + received + " applied=" + applied
                    + " sequence=" + sequence + " serverTick=" + serverTick);
            summaryAt = now;
        }
    }

    private void duration(String operation, long nanos, int sequence, int tick) {
        report.accept(operation + " " + String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0)
                + " ms, sequence=" + sequence + " serverTick=" + tick);
    }
}
