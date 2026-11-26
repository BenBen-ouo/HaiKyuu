/*
快攻手 MB 的角色邏輯；正常來回的貼網第一球可起跳攻擊，其餘依本隊第一球狀態決定動作。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;
import model.ball.Ball;

public final class QuickAttacker extends Player {
    private static final int FIRST_ATTACK_MIN_CONTACT_FRAMES = 2;
    private boolean previousMbActionPressed = false;
    private int countedBlockJumps;
    private boolean opponentFirstTouchSeen;
    public final BlockHitBox blockHitBox;
    public final CircleHitBox headBlockHitBox;

    public QuickAttacker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        blockHitBox = new BlockHitBox(this);
        headBlockHitBox = new CircleHitBox(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        previousMbActionPressed = false;
        resetBlockJumpCount();
    }

    public void resetBlockJumpCount() {
        countedBlockJumps = 0;
        opponentFirstTouchSeen = false;
    }

    /** 對手在新球權接起第一顆一般球，攔網起跳從第一次重新計算。 */
    public void beginOpponentReceiveCycle() {
        countedBlockJumps = 0;
        opponentFirstTouchSeen = true;
    }

    public int getCountedBlockJumps() {
        return countedBlockJumps;
    }

    public boolean hasSeenOpponentFirstTouch() {
        return opponentFirstTouchSeen;
    }

    public void applyBlockJumpState(int count, boolean firstTouchSeen) {
        countedBlockJumps = Math.max(0, count);
        opponentFirstTouchSeen = firstTouchSeen;
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        boolean justPressedQuick = input.mbActionPressed && !previousMbActionPressed;
        if (input.opponentHasFirstRegularTouch) opponentFirstTouchSeen = true;

        vx = 0;

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            if (isHeldAttackReady(input.mbActionPressed, justPressedQuick)
                    && input.canResolveAttack && !input.attackLocked && input.lastHitter != this
                    && (input.hasFirstRegularTouch || input.canMbAttackFirstBall && isBallOnOwnSide(input.ball))
                    && isBallInAttackBox(input)) {
                queueAttackAttempt();
            }

            applyGravity();
            updateActionAnimation();
            previousMbActionPressed = input.mbActionPressed;
            return;
        }

        if (action == PlayerAction.BLOCK) {
            applyGravity();
            updateActionAnimation();
            previousMbActionPressed = input.mbActionPressed;
            return;
        }

        if (justPressedQuick) {
            if (input.hasFirstRegularTouch || input.canMbAttackFirstBall && firstBallAttackContactFrames(input.ball) >= 0) {
                startAttackReady(0);
                vy = GameConfig.MB_JUMP_VELOCITY_Y;
            } else {
                startBlockAnimation();
                vy = GameConfig.MB_JUMP_VELOCITY_Y
                        * (opponentFirstTouchSeen && countedBlockJumps > 0
                                ? GameConfig.MB_REPEAT_BLOCK_JUMP_SPEED_MULTIPLIER : 1.0);
                if (opponentFirstTouchSeen) countedBlockJumps++;
            }
        }

        applyGravity();
        updateActionAnimation();
        if (!jumping && action == PlayerAction.IDLE) {
            animation.show(teamAsset(input.hasFirstRegularTouch ? "MB ready attack" : "MB"));
        }
        previousMbActionPressed = input.mbActionPressed;
    }

    /** 回傳本場來球與完整起跳的首次交會幀；-1 表示不可達，不預知後續碰撞。 */
    public int firstBallAttackContactFrames(Ball ball) {
        if (!PlayerPhysics.isOnGround(this) || action != PlayerAction.IDLE || !isBallOnOwnSide(ball)) return -1;
        int duration = (int) Math.ceil(-2 * GameConfig.MB_JUMP_VELOCITY_Y / GameConfig.GRAVITY);
        for (int frame = FIRST_ATTACK_MIN_CONTACT_FRAMES; frame <= duration; frame++) {
            double gravityTravel = GameConfig.GRAVITY * frame * (frame + 1) / 2;
            double bx = ball.x + ball.vx * frame;
            double by = ball.y + ball.vy * frame + gravityTravel;
            double py = y + GameConfig.MB_JUMP_VELOCITY_Y * frame + gravityTravel;
            if (py > GameConfig.FLOOR_Y - imageHeight || by + ball.radius >= GameConfig.FLOOR_Y) break;
            if (redSide ? bx >= GameConfig.NET_X : bx <= GameConfig.NET_X) continue;
            double left = x + attackHitBox.offsetX;
            double top = py + attackHitBox.offsetY;
            double dx = bx - Math.max(left, Math.min(bx, left + attackHitBox.width));
            double dy = by - Math.max(top, Math.min(by, top + attackHitBox.height));
            if (dx * dx + dy * dy <= ball.radius * ball.radius) return frame;
        }
        return -1;
    }

    private boolean isBallOnOwnSide(Ball ball) {
        return ball != null && (redSide ? ball.x < GameConfig.NET_X : ball.x > GameConfig.NET_X);
    }

    public boolean isHeadBlockHitBoxActive() {
        return jumping && (getBlockPhase() == BlockPhase.RAISING || getBlockPhase() == BlockPhase.RETRACTING);
    }

    public HitBox activeBlockHitBox() {
        return isBlockHitBoxActive() ? blockHitBox
                : isHeadBlockHitBoxActive() ? headBlockHitBox : null;
    }
    @Override
    public boolean isDefaultHitBoxActive() {
        // MB 目前只有攻擊與攔網判定；攔網改由獨立的 blockHitBox 負責。
        return false;
    }
}
