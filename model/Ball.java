/*
排球物件，保存球的位置、速度與半徑。
負責球的重力移動、世界邊界反彈、落地反彈與撞網處理。
*/
package model;

public class Ball {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double radius = GameConfig.BALL_RADIUS;

    public Ball(double x, double y) {
        this.x = x;
        this.y = y;
        this.vx = 3.2;
        this.vy = -8.0;
    }

    public void update() {
        vy += GameConfig.GRAVITY;
        x += vx;
        y += vy;

        handleWorldBoundaries();
    }

    private void handleWorldBoundaries() {
        if (x - radius < GameConfig.WORLD_LEFT) {
            x = GameConfig.WORLD_LEFT + radius;
            vx = -vx * GameConfig.NET_BOUNCE;
        }
        if (x + radius > GameConfig.WORLD_RIGHT) {
            x = GameConfig.WORLD_RIGHT - radius;
            vx = -vx * GameConfig.NET_BOUNCE;
        }
        if (y - radius < GameConfig.WORLD_TOP) {
            y = GameConfig.WORLD_TOP + radius;
            vy = -vy * GameConfig.NET_BOUNCE;
        }
        if (y + radius > GameConfig.FLOOR_Y) {
            y = GameConfig.FLOOR_Y - radius;
            vy = -Math.abs(vy) * GameConfig.BALL_BOUNCE;
            vx *= 0.96;
        }
    }

    public void collideWithNet() {
        double netLeft = GameConfig.NET_X - GameConfig.NET_WIDTH / 2.0;
        double netRight = GameConfig.NET_X + GameConfig.NET_WIDTH / 2.0;

        boolean hitX = x + radius > netLeft && x - radius < netRight;
        boolean hitY = y + radius > GameConfig.NET_TOP_Y && y - radius < GameConfig.FLOOR_Y;

        if (hitX && hitY) {
            if (x < GameConfig.NET_X) {
                x = netLeft - radius;
                vx = -Math.abs(vx) * GameConfig.NET_BOUNCE;
            } else {
                x = netRight + radius;
                vx = Math.abs(vx) * GameConfig.NET_BOUNCE;
            }
        }
    }
}
