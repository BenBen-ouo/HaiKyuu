package network;

import java.util.IdentityHashMap;
import java.util.Map;
import model.player.Player;

/** 只平滑畫面位置；角色的物理位置仍立即接受 Server 快照。 */
final class PlayerRenderCorrection {
    private static final double DIRECT_APPLY_DISTANCE = 80.0;
    private static final int CORRECTION_FRAMES = 4;

    private final Map<Player, Offset> offsets = new IdentityHashMap<>();

    synchronized double renderedX(Player player) {
        Offset offset = offsets.get(player);
        return player.x + (offset == null ? 0 : offset.x);
    }

    synchronized double renderedY(Player player) {
        Offset offset = offsets.get(player);
        return player.y + (offset == null ? 0 : offset.y);
    }

    synchronized String serverAsset(Player player) {
        Offset offset = offsets.get(player);
        return offset == null ? player.assetName : offset.assetName;
    }

    synchronized void schedule(Player player, double visibleX, double visibleY, String assetName) {
        Offset offset = offsets.computeIfAbsent(player, ignored -> new Offset());
        double dx = visibleX - player.x;
        double dy = visibleY - player.y;
        if (Math.hypot(dx, dy) > DIRECT_APPLY_DISTANCE) {
            dx = 0;
            dy = 0;
        }
        offset.x = dx;
        offset.y = dy;
        offset.framesRemaining = CORRECTION_FRAMES;
        offset.assetName = assetName;
    }

    synchronized void advance() {
        for (Offset offset : offsets.values()) {
            if (offset.framesRemaining <= 0) {
                continue;
            }
            offset.x -= offset.x / offset.framesRemaining;
            offset.y -= offset.y / offset.framesRemaining;
            offset.framesRemaining--;
        }
    }

    synchronized void reset() {
        offsets.clear();
    }

    private static final class Offset {
        double x;
        double y;
        int framesRemaining;
        String assetName;
    }
}
