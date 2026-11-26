package model.rally;

/** 已由模型確認的觸球，不含輸入意圖；僅供本地觀察，不是網路封包。 */
public record ContactEvent(long id, boolean redSide, int role, int touch, Kind kind, Route route,
        double x, double y, double vx, double vy, boolean jumping, int preparedMask) {
    public enum Kind { REGULAR, AIR_SET, ATTACK, BLOCK, SERVE }
    public enum Route {
        NORMAL, FLAT, SHORT, LOB, LONG_LOB, FREE_BALL,
        SERVE_NORMAL, SERVE_LOW, SERVE_SHORT, SERVE_CEILING,
        JUMP_NORMAL, JUMP_SHORT, JUMP_SLOW, JUMP_LONG;

        public static Route spike(int mode) {
            return switch (mode) {
                case 0 -> NORMAL; case 1 -> FLAT; case 2 -> SHORT; case 3 -> LOB; case 4 -> LONG_LOB;
                default -> throw new IllegalArgumentException("Unknown spike route: " + mode);
            };
        }
    }
}
