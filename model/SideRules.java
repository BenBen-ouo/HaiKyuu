package model;

public final class SideRules {
    private SideRules() {}

    public static boolean isBallOnOwnSide(boolean redSide, double ballX) {
        return redSide ? ballX <= GameConfig.NET_X : ballX >= GameConfig.NET_X;
    }

    public static double directionTowardOpponent(boolean redSide) {
        return redSide ? 1.0 : -1.0;
    }

    public static int defaultDiveDirection(boolean redSide) {
        return redSide ? 1 : -1;
    }

    public static int horizontalDirectionFromBackInput(TeamInput input, boolean redSide) {
        if (input.backLeft && !input.backRight) {
            return -1;
        }

        if (input.backRight && !input.backLeft) {
            return 1;
        }

        return defaultDiveDirection(redSide);
    }

    public static boolean shouldMirrorImage(boolean redSide, int horizontalDirection) {
        // 預設素材方向：player 1 面右，player 2 面左。
        return redSide ? horizontalDirection < 0 : horizontalDirection > 0;
    }

    public static double thirdTouchTargetX(boolean redSide) {
        return redSide ? GameConfig.SCREEN_WIDTH * 0.8 : GameConfig.SCREEN_WIDTH * 0.2;
    }
}