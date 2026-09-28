package network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import model.GameConfig;
import model.player.Player;

/** 對手只讀 Server 圖片與座標；畫面延後兩 tick 插值，漏包最多外推六 tick。 */
final class RemotePlayerInterpolator {
    private static final int DISPLAY_DELAY_TICKS = 2;
    private static final int MAX_EXTRAPOLATE_TICKS = 6;
    private static final long TICK_NANOS = 1_000_000_000L / GameConfig.TICKS_PER_SECOND;
    private static final int HISTORY_SIZE = 8;

    private final Map<Player, Deque<Sample>> histories = new IdentityHashMap<>();

    synchronized void observe(Player player, Packet.PlayerState state, int tick) {
        observeAt(player, state, tick, System.nanoTime());
    }

    synchronized void observeAt(Player player, Packet.PlayerState state, int tick, long receivedNanos) {
        Deque<Sample> history = histories.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        if (!history.isEmpty() && tick <= history.peekLast().tick) {
            return;
        }
        history.addLast(new Sample(tick, state.x, state.y, state.assetName, state.mirrorImage, receivedNanos));
        while (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    synchronized void snap(Player player, Packet.PlayerState state, int tick) {
        histories.remove(player);
        observe(player, state, tick);
    }

    synchronized double renderedX(Player player) {
        return renderedXAt(player, System.nanoTime());
    }

    synchronized double renderedY(Player player) {
        return renderedYAt(player, System.nanoTime());
    }

    synchronized double renderedXAt(Player player, long nowNanos) {
        return position(player, nowNanos, true);
    }

    synchronized double renderedYAt(Player player, long nowNanos) {
        return position(player, nowNanos, false);
    }

    synchronized String renderedAsset(Player player) {
        Sample sample = displayedSample(player, System.nanoTime());
        return sample == null ? player.assetName : sample.assetName;
    }

    synchronized boolean renderedMirror(Player player) {
        Sample sample = displayedSample(player, System.nanoTime());
        return sample == null ? player.mirrorImage : sample.mirrorImage;
    }

    synchronized void reset() {
        histories.clear();
    }

    private double position(Player player, long nowNanos, boolean horizontal) {
        Deque<Sample> history = histories.get(player);
        if (history == null || history.isEmpty()) {
            return horizontal ? player.x : player.y;
        }
        Sample latest = history.peekLast();
        double target = targetTick(latest, nowNanos);
        Sample previous = null;
        for (Sample sample : history) {
            if (sample.tick >= target) {
                if (previous == null) {
                    return coordinate(sample, horizontal);
                }
                double fraction = (target - previous.tick) / (sample.tick - previous.tick);
                return coordinate(previous, horizontal)
                        + (coordinate(sample, horizontal) - coordinate(previous, horizontal)) * fraction;
            }
            previous = sample;
        }

        if (history.size() < 2) {
            return coordinate(latest, horizontal);
        }
        Sample beforeLatest = null;
        for (Sample sample : history) {
            if (sample != latest) beforeLatest = sample;
        }
        double velocity = (coordinate(latest, horizontal) - coordinate(beforeLatest, horizontal))
                / (latest.tick - beforeLatest.tick);
        double extrapolated = coordinate(latest, horizontal) + velocity * (target - latest.tick);
        return horizontal
                ? Math.max(player.minX, Math.min(player.maxX - player.imageWidth, extrapolated))
                : Math.min(GameConfig.FLOOR_Y - player.imageHeight, extrapolated);
    }

    private Sample displayedSample(Player player, long nowNanos) {
        Deque<Sample> history = histories.get(player);
        if (history == null || history.isEmpty()) return null;
        double target = targetTick(history.peekLast(), nowNanos);
        Sample selected = history.peekFirst();
        for (Sample sample : history) {
            if (sample.tick > target) break;
            selected = sample;
        }
        return selected;
    }

    private static double targetTick(Sample latest, long nowNanos) {
        double elapsedTicks = Math.max(0, nowNanos - latest.receivedNanos) / (double) TICK_NANOS;
        return Math.min(latest.tick + MAX_EXTRAPOLATE_TICKS,
                latest.tick - DISPLAY_DELAY_TICKS + elapsedTicks);
    }

    private static double coordinate(Sample sample, boolean horizontal) {
        return horizontal ? sample.x : sample.y;
    }

    private static final class Sample {
        final int tick;
        final double x;
        final double y;
        final String assetName;
        final boolean mirrorImage;
        final long receivedNanos;

        Sample(int tick, double x, double y, String assetName, boolean mirrorImage, long receivedNanos) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.assetName = assetName;
            this.mirrorImage = mirrorImage;
            this.receivedNanos = receivedNanos;
        }
    }
}
