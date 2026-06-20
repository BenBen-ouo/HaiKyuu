/*
排球物件，保存球的位置、速度與半徑。
負責球的重力移動、世界邊界反彈、落地反彈與撞網處理。
*/
package model;

public class Ball {
    private enum NetCollisionSide {
        LEFT,
        RIGHT,
        TOP
    }

    public double x;
    public double y;
    public double vx;
    public double vy;
    public double radius = GameConfig.BALL_RADIUS;

    private double previousX;
    private double previousY;

    public Ball(double x, double y) {
        this.x = x;
        this.y = y;
        this.previousX = x;
        this.previousY = y;
        this.vx = 3.2;
        this.vy = -8.0;
    }

    public void update() {
        previousX = x;
        previousY = y;

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

    public void collideWithNet(NetHitBox netHitBox) {
        if (!netHitBox.intersectsBall(this)) {
            return;
        }

        switch (findNetCollisionSide(netHitBox)) {
            case LEFT -> bounceFromNetLeft(netHitBox);
            case RIGHT -> bounceFromNetRight(netHitBox);
            case TOP -> bounceFromNetTop(netHitBox);
        }
    }

    private NetCollisionSide findNetCollisionSide(NetHitBox netHitBox) {
        NetCollisionSide entrySide = findEntrySide(netHitBox);
        return entrySide != null ? entrySide : findClosestCollisionSide(netHitBox);
    }

    private NetCollisionSide findEntrySide(NetHitBox netHitBox) {
        double earliestHitTime = Double.POSITIVE_INFINITY;
        NetCollisionSide hitSide = null;

        if (vx > 0) {
            double hitTime = crossingTime(previousX, x, netHitBox.getLeft() - radius);

            if (isValidHitTime(hitTime) && hitTime < earliestHitTime) {
                earliestHitTime = hitTime;
                hitSide = NetCollisionSide.LEFT;
            }
        }

        if (vx < 0) {
            double hitTime = crossingTime(previousX, x, netHitBox.getRight() + radius);

            if (isValidHitTime(hitTime) && hitTime < earliestHitTime) {
                earliestHitTime = hitTime;
                hitSide = NetCollisionSide.RIGHT;
            }
        }

        if (vy > 0) {
            double hitTime = crossingTime(previousY, y, netHitBox.getTop() - radius);

            if (isValidHitTime(hitTime) && hitTime < earliestHitTime) {
                hitSide = NetCollisionSide.TOP;
            }
        }

        return hitSide;
    }

    private double crossingTime(double previousPosition, double currentPosition, double boundary) {
        double movement = currentPosition - previousPosition;

        if (Math.abs(movement) < 0.0001) {
            return Double.NaN;
        }

        return (boundary - previousPosition) / movement;
    }

    private boolean isValidHitTime(double hitTime) {
        return hitTime >= 0.0 && hitTime <= 1.0;
    }

    private NetCollisionSide findClosestCollisionSide(NetHitBox netHitBox) {
        double leftOverlap = x + radius - netHitBox.getLeft();
        double rightOverlap = netHitBox.getRight() - (x - radius);
        double topOverlap = y + radius - netHitBox.getTop();

        if (topOverlap <= leftOverlap && topOverlap <= rightOverlap) {
            return NetCollisionSide.TOP;
        }

        double netCenterX = (netHitBox.getLeft() + netHitBox.getRight()) / 2.0;
        return x < netCenterX ? NetCollisionSide.LEFT : NetCollisionSide.RIGHT;
    }

    private void bounceFromNetLeft(NetHitBox netHitBox) {
        x = netHitBox.getLeft() - radius;
        vx = -reboundSpeed(vx, GameConfig.NET_SIDE_BOUNCE);
    }

    private void bounceFromNetRight(NetHitBox netHitBox) {
        x = netHitBox.getRight() + radius;
        vx = reboundSpeed(vx, GameConfig.NET_SIDE_BOUNCE);
    }

    private void bounceFromNetTop(NetHitBox netHitBox) {
        y = netHitBox.getTop() - radius;
        vy = -reboundSpeed(vy, GameConfig.NET_TOP_BOUNCE);
    }

    private double reboundSpeed(double incomingSpeed, double bounceFactor) {
        double reboundSpeed = Math.abs(incomingSpeed) * bounceFactor;
        return Math.max(reboundSpeed, GameConfig.NET_MIN_REBOUND_SPEED);
    }
}