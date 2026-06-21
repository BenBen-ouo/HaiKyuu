/*
排球物件，保存球的位置、速度、半徑與畫面旋轉狀態。
負責球的重力移動、世界邊界反彈、落地反彈與撞網處理。
*/
package model;

public class Ball {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double radius = GameConfig.BALL_RADIUS;

    // 正值為畫面上的順時針，負值為逆時針；單位為每一幀的角度。
    public double rotationDegrees;
    public double rotationSpeed;

    // 由最近一次球員觸球決定，供之後落地時套用不同強度的摩擦旋轉。
    private boolean highSpeedFloorBounceSpin;

    public Ball(double x, double y) {
        this.x = x;
        this.y = y;
        this.vx = 3.2;
        this.vy = -8.0;
    }

    public void update() {
        updateRotation();
        vy += GameConfig.GRAVITY;
        x += vx;
        y += vy;

        handleWorldBoundaries();
    }

    public void setRotationSpeed(double rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }

    public void stopRotation() {
        rotationSpeed = 0;
    }

    public void useFastFloorBounceSpin() {
        highSpeedFloorBounceSpin = true;
    }

    public void useSlowFloorBounceSpin() {
        highSpeedFloorBounceSpin = false;
    }

    private void updateRotation() {
        rotationDegrees = (rotationDegrees + rotationSpeed) % 360.0;
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
            applyFloorBounceSpin();
        }
    }

    private void applyFloorBounceSpin() {
        double horizontalDirection = Math.signum(vx);

        if (horizontalDirection == 0) {
            stopRotation();
            return;
        }

        double spinSpeed = highSpeedFloorBounceSpin
                ? GameConfig.FLOOR_BOUNCE_FAST_SPIN_SPEED
                : GameConfig.FLOOR_BOUNCE_SLOW_SPIN_SPEED;

        setRotationSpeed(horizontalDirection * spinSpeed);
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