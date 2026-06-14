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
}
