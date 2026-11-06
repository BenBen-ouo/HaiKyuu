package model;

import model.ball.Ball;
import model.player.Player;

/** 單隊當幀操作及模型推導的角色判斷上下文；不作為網路解碼的資料型別。 */
public final class TeamInput extends ControlInput {
    public boolean hasFirstRegularTouch;
    public boolean opponentHasFirstRegularTouch;
    public Ball ball;
    public boolean canBackAirSet;
    public boolean canWingAirSet;
    public Player lastHitter;
    public boolean canResolveAttack = true;
    public boolean attackLocked;

    public TeamInput() {}

    public TeamInput(ControlInput controls) {
        controls.copyControlsTo(this);
    }

    /** 模型單一更新執行緒重用的工作副本；每 tick 清除上一幀上下文。 */
    void resetFrom(ControlInput controls) {
        controls.copyControlsTo(this);
        hasFirstRegularTouch = false;
        opponentHasFirstRegularTouch = false;
        ball = null;
        canBackAirSet = false;
        canWingAirSet = false;
        lastHitter = null;
        canResolveAttack = true;
        attackLocked = false;
    }

    @Override
    public TeamInput copy() {
        TeamInput copy = new TeamInput(this);
        copy.hasFirstRegularTouch = hasFirstRegularTouch;
        copy.opponentHasFirstRegularTouch = opponentHasFirstRegularTouch;
        copy.ball = ball;
        copy.canBackAirSet = canBackAirSet;
        copy.canWingAirSet = canWingAirSet;
        copy.lastHitter = lastHitter;
        copy.canResolveAttack = canResolveAttack;
        copy.attackLocked = attackLocked;
        return copy;
    }

    @Override
    public TeamInput mirroredHorizontally() {
        return (TeamInput) super.mirroredHorizontally();
    }
}
