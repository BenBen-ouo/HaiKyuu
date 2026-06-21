/*
球員物理與位置工具，處理重力、落地、邊界限制與重置到初始位置。
讓 Player 本體不用直接保存太多移動細節。
*/
package model;

public final class PlayerPhysics {
    private PlayerPhysics() {}

    public static void resetToInitial(Player player) {
        player.x = player.initialX;
        player.y = player.initialY;
        clearMotionAndActions(player);
    }

    public static void clearMotionAndActions(Player player) {
        player.vx = 0;
        player.vy = 0;
        player.jumping = false;
        player.attacking = false;
        player.blocking = false;
        player.diving = false;
        player.mirrorImage = false;
    }

    public static void applyGravity(Player player) {
        player.vy += GameConfig.GRAVITY;
        player.x += player.vx;
        player.y += player.vy;

        if (isBelowFloor(player)) {
            landOnFloor(player);
        }

        clampToMovementBounds(player);
    }

    public static boolean isOnGround(Player player) {
        return !player.jumping && player.y + player.imageHeight >= GameConfig.FLOOR_Y - 0.5;
    }

    private static boolean isBelowFloor(Player player) {
        return player.y + player.imageHeight > GameConfig.FLOOR_Y;
    }

    private static void landOnFloor(Player player) {
        player.y = GameConfig.FLOOR_Y - player.imageHeight;
        player.vy = 0;
        player.jumping = false;
        player.diving = false;
        // 重置起跳座標，避免殘留影響三米線判定
        player.jumpStartX = Double.NaN;
    }

    private static void clampToMovementBounds(Player player) {
        if (player.x < player.minX) {
            player.x = player.minX;
        }

        if (player.x + player.imageWidth > player.maxX) {
            player.x = player.maxX - player.imageWidth;
        }
    }
}
