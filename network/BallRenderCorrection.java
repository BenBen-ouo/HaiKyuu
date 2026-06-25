/*
Client 專用的球畫面校正器。
物理球立即採用 Server 狀態，這個類別只保留短暫的畫面偏移來平滑顯示。
*/
package network;

final class BallRenderCorrection {
    private static final double DIRECT_APPLY_DISTANCE = 80.0;
    private static final double SMALL_CORRECTION_DISTANCE = 20.0;
    private static final int SMALL_CORRECTION_FRAMES = 3;
    private static final int MEDIUM_CORRECTION_FRAMES = 5;

    private boolean initialized;
    private double offsetX;
    private double offsetY;
    private double rotationOffset;
    private int framesRemaining;

    /**
     * 將剛套用的權威球狀態與舊畫面狀態比較。
     *
     * @return true 表示誤差過大，呼叫端應清除舊扣球軌跡。
     */
    boolean schedule(
            double authoritativeX,
            double authoritativeY,
            double authoritativeRotation,
            double visibleX,
            double visibleY,
            double visibleRotation
    ) {
        initialized = true;
        double errorX = authoritativeX - visibleX;
        double errorY = authoritativeY - visibleY;
        double distance = Math.hypot(errorX, errorY);

        if (distance > DIRECT_APPLY_DISTANCE) {
            reset();
            return true;
        }

        offsetX = visibleX - authoritativeX;
        offsetY = visibleY - authoritativeY;
        rotationOffset = normalizeDegrees(visibleRotation - authoritativeRotation);
        framesRemaining = distance < SMALL_CORRECTION_DISTANCE
                ? SMALL_CORRECTION_FRAMES
                : MEDIUM_CORRECTION_FRAMES;
        return false;
    }

    void advance() {
        if (!initialized || framesRemaining <= 0) {
            return;
        }

        offsetX -= offsetX / framesRemaining;
        offsetY -= offsetY / framesRemaining;
        rotationOffset -= rotationOffset / framesRemaining;
        framesRemaining--;
    }

    void reset() {
        initialized = true;
        offsetX = 0;
        offsetY = 0;
        rotationOffset = 0;
        framesRemaining = 0;
    }

    double renderedX(double authoritativeX) {
        return initialized ? authoritativeX + offsetX : authoritativeX;
    }

    double renderedY(double authoritativeY) {
        return initialized ? authoritativeY + offsetY : authoritativeY;
    }

    double renderedRotation(double authoritativeRotation) {
        return initialized ? authoritativeRotation + rotationOffset : authoritativeRotation;
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        if (normalized > 180.0) {
            return normalized - 360.0;
        }
        if (normalized < -180.0) {
            return normalized + 360.0;
        }
        return normalized;
    }
}
