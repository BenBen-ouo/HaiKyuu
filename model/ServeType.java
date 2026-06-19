package model;

public enum ServeType {
    NORMAL(GameConfig.SERVE_NORMAL_VX, GameConfig.SERVE_NORMAL_VY),
    CEILING(GameConfig.SERVE_CEILING_VX, GameConfig.SERVE_CEILING_VY),
    LOW_NET(GameConfig.SERVE_LOW_NET_VX, GameConfig.SERVE_LOW_NET_VY),
    SHORT(GameConfig.SERVE_SHORT_VX, GameConfig.SERVE_SHORT_VY);

    public final double baseVx;
    public final double baseVy;

    ServeType(double baseVx, double baseVy) {
        this.baseVx = baseVx;
        this.baseVy = baseVy;
    }
}