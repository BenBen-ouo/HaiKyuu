/*
集中控制球員動作動畫的開始、播放與結束條件。
包含接球、舉球、撲球、攻擊、攔網與跑步動畫的狀態切換。
*/
package model;

public class PlayerActionAnimator {
    private final Player player;
    private final PlayerAnimation animation;

    public PlayerActionAnimator(Player player, PlayerAnimation animation) {
        this.player = player;
        this.animation = animation;
    }

    public void playReceive() {
        if (isBusyForReceive()) {
            return;
        }

        startGroundAction(PlayerAction.RECEIVING);
        player.vx = 0;
        animation.play(AnimationSequences.frames(player, "receive"), AnimationSequences.durations(30));
    }

    public void playSetting() {
        startGroundAction(PlayerAction.SETTING);
        animation.play(
                AnimationSequences.frames(player, "setting1", "setting2", "setting1"),
                AnimationSequences.durations(5, 30, 5)
        );
    }

    public void playDive() {
        player.action = PlayerAction.DIVE;
        player.attacking = false;
        player.blocking = false;
        animation.play(
                AnimationSequences.frames(player, "dive1", "dive2", "dive3"),
                AnimationSequences.durations(5, 10, AnimationSequences.HOLD)
        );
    }

    public void startAttackReady(double horizontalSpeed) {
        player.mirrorImage = false;
        player.action = PlayerAction.ATTACK_READY;
        player.attacking = true;
        player.blocking = false;
        player.diving = false;
        player.vx = horizontalSpeed;
        player.vy = GameConfig.PLAYER_JUMP_SPEED;
        player.jumping = true;
        animation.show(player.teamAsset("attack1"));
    }

    public void startAttackSwing() {
        player.mirrorImage = false;
        player.action = PlayerAction.ATTACK_SWING;
        player.attacking = true;
        player.blocking = false;
        animation.play(
                AnimationSequences.frames(player, "attack2", "attack3"),
                AnimationSequences.durations(5, AnimationSequences.HOLD)
        );
    }

    public void startBlock() {
        player.mirrorImage = false;
        player.action = PlayerAction.BLOCK;
        player.blocking = true;
        player.attacking = false;
        player.diving = false;

        if (!player.jumping) {
            player.vy = GameConfig.PLAYER_JUMP_SPEED;
            player.jumping = true;
        }

        animation.play(
                AnimationSequences.frames(player, "block1", "block2", "block1"),
                AnimationSequences.durations(20, 20, AnimationSequences.HOLD)
        );
    }

    public void startRunApproach(int cycles) {
        player.mirrorImage = false;
        player.action = PlayerAction.RUN_APPROACH;
        player.attacking = false;
        player.blocking = false;
        player.diving = false;
        AnimationSequences.playRunCycles(animation, player, cycles);
    }

    public void startRunLoop() {
        if (animation.isLooping() && isRunLoopAction()) {
            return;
        }

        AnimationSequences.playRunLoop(animation, player);
    }

    public void updateActionState() {
        animation.update();

        if (shouldFinishAirAction() || shouldFinishHeldDive() || shouldFinishTimedGroundAction()) {
            finishAction();
        }
    }

    public void finishAction() {
        player.mirrorImage = false;
        player.action = PlayerAction.IDLE;
        player.attacking = false;
        player.blocking = false;
        animation.stopToIdle();
    }

    private boolean isBusyForReceive() {
        return player.action == PlayerAction.DIVE
                || player.action == PlayerAction.ATTACK_READY
                || player.action == PlayerAction.ATTACK_SWING
                || player.action == PlayerAction.BLOCK;
    }

    private void startGroundAction(PlayerAction action) {
        player.mirrorImage = false;
        player.action = action;
        player.attacking = false;
        player.blocking = false;
    }

    private boolean isRunLoopAction() {
        return player.action == PlayerAction.RUN_LOOP || player.action == PlayerAction.RUN_RETURN;
    }

    private boolean shouldFinishAirAction() {
        boolean airAction = player.action == PlayerAction.ATTACK_READY
                || player.action == PlayerAction.ATTACK_SWING
                || player.action == PlayerAction.BLOCK;

        return airAction && !player.jumping
                && (player.action == PlayerAction.ATTACK_READY || animation.isHoldingFrame());
    }

    private boolean shouldFinishHeldDive() {
        return player.action == PlayerAction.DIVE && !player.diving && animation.isHoldingFrame();
    }

    private boolean shouldFinishTimedGroundAction() {
        boolean timedGroundAction = player.action == PlayerAction.RECEIVING || player.action == PlayerAction.SETTING;
        return timedGroundAction && !animation.isPlaying();
    }
}