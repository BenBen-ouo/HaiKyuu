package model;

/** 固定步長的排程時基；長停頓後只補有限 tick，不改變單步物理時間。 */
public final class FixedTickSchedule {
    public static final int MAX_CATCH_UP_TICKS = 5;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long tickNanos;
    private long nextDeadlineNanos;
    private int consecutiveLateTicks;

    public FixedTickSchedule(long startedAtNanos, int ticksPerSecond) {
        if (ticksPerSecond <= 0 || ticksPerSecond > NANOS_PER_SECOND) {
            throw new IllegalArgumentException("Invalid tick rate");
        }
        tickNanos = NANOS_PER_SECOND / ticksPerSecond;
        nextDeadlineNanos = startedAtNanos;
    }

    /** 完成一個 tick；回傳是否已捨棄過多的壁鐘積壓。 */
    public boolean completeTick(long nowNanos) {
        nextDeadlineNanos += tickNanos;
        if (nowNanos - nextDeadlineNanos >= 0) {
            if (++consecutiveLateTicks >= MAX_CATCH_UP_TICKS) {
                nextDeadlineNanos = nowNanos + tickNanos;
                consecutiveLateTicks = 0;
                return true;
            }
        } else {
            consecutiveLateTicks = 0;
        }
        return false;
    }

    public long nextDeadlineNanos() {
        return nextDeadlineNanos;
    }
}
