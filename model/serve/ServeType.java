/*
發球類型列舉，保存每種發球的基礎水平與垂直速度。
目前包含一般發球、高拋發球、低網發球與短球發球。
*/
package model.serve;

import model.GameConfig;

public enum ServeType {
    NORMAL(GameConfig.SERVE_NORMAL_HORIZONTAL_SPEED, GameConfig.SERVE_NORMAL_VELOCITY_Y),
    CEILING(GameConfig.SERVE_CEILING_HORIZONTAL_SPEED, GameConfig.SERVE_CEILING_VELOCITY_Y),
    LOW_NET(GameConfig.SERVE_LOW_NET_HORIZONTAL_SPEED, GameConfig.SERVE_LOW_NET_VELOCITY_Y),
    SHORT(GameConfig.SERVE_SHORT_HORIZONTAL_SPEED, GameConfig.SERVE_SHORT_VELOCITY_Y);

    public final double horizontalSpeed;
    public final double verticalVelocity;

    ServeType(double horizontalSpeed, double verticalVelocity) {
        this.horizontalSpeed = horizontalSpeed;
        this.verticalVelocity = verticalVelocity;
    }
}
