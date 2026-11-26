package model.ball;

import model.GameConfig;

/** 最低速只等比補足反射向量；高速、頭框與鏡像不因下限改變。 */
public final class BlockReflectionTest {
    public static void main(String[] args) {
        for (double direction : new double[]{-1, 1}) {
            for (double incoming : new double[]{0, .1, 2, 5, 20}) {
                var original = BlockReflection.reflect(-direction * incoming, incoming / 2,
                        direction, -.4, direction, GameConfig.BLOCK_HITBOX_BOUNCE, 0);
                var limited = BlockReflection.reflect(-direction * incoming, incoming / 2,
                        direction, -.4, direction, GameConfig.BLOCK_HITBOX_BOUNCE,
                        GameConfig.BLOCK_HITBOX_MIN_REBOUND_SPEED);
                double oldSpeed = Math.hypot(original.x(), original.y());
                double speed = Math.hypot(limited.x(), limited.y());
                check(Math.abs(speed - Math.max(oldSpeed, GameConfig.BLOCK_HITBOX_MIN_REBOUND_SPEED)) < 1e-9,
                        "只補足低於下限的總速度");
                if (oldSpeed > 0) {
                    check(Math.abs(original.x() * limited.y() - original.y() * limited.x()) < 1e-9
                            && original.x() * limited.x() + original.y() * limited.y() > 0,
                            "慢球反射方向不變");
                } else check(limited.x() * direction > 0 && limited.y() < 0, "靜止球沿法線彈開");
                var mirror = BlockReflection.reflect(direction * incoming, incoming / 2,
                        -direction, -.4, -direction, GameConfig.BLOCK_HITBOX_BOUNCE,
                        GameConfig.BLOCK_HITBOX_MIN_REBOUND_SPEED);
                check(Math.abs(limited.x() + mirror.x()) < 1e-9 && Math.abs(limited.y() - mirror.y()) < 1e-9,
                        "左右反射完全鏡像");
            }
        }
        var head = BlockReflection.reflect(-.1, 0, 1, 0, 1, GameConfig.BLOCK_HEAD_BOUNCE, 0);
        check(Math.abs(Math.hypot(head.x(), head.y()) - .1 * GameConfig.BLOCK_HEAD_BOUNCE) < 1e-9,
                "頭框不套用手部速度下限");
        var center = BlockReflection.reflect(0, 0, 0, 0, -1, GameConfig.BLOCK_HITBOX_BOUNCE,
                GameConfig.BLOCK_HITBOX_MIN_REBOUND_SPEED);
        check(Double.isFinite(center.x()) && center.x() <= 0 && Double.isFinite(center.y()),
                "靜止同心碰撞仍有有限且正確朝向的結果");
        System.out.println("BlockReflectionTest passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
