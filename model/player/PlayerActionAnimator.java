/*
集中控制球員動作動畫的開始、播放與結束條件。
包含接球、舉球、撲球、攻擊、攔網與跑步動畫的狀態切換。
*/
package model.player;

import model.GameConfig;

public class PlayerActionAnimator {
    private static final int RECEIVE_DURATION_FRAMES = 30;
    private static final int SETTING_TRANSITION_FRAMES = 5;
    private static final int SETTING_HOLD_FRAMES = 30;
    private static final int DIVE_TRANSITION_FRAMES = 5;
    private static final int SWING_TRANSITION_FRAMES = 5;
    private static final int BLOCK_RAISING_FRAMES = 15;
    private static final int BLOCK_EXTENDED_FRAMES = 30;
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

        player.attackHitBox.disable();
        startGroundAction(PlayerAction.RECEIVING);
        player.vx = 0;
        animation.play(AnimationSequences.frames(player, "receive"), AnimationSequences.durations(RECEIVE_DURATION_FRAMES));
    }

    public void playSetting() {
        startGroundAction(PlayerAction.SETTING);
        animation.play(
                AnimationSequences.frames(player, "setting1", "setting2", "setting1"),
                AnimationSequences.durations(SETTING_TRANSITION_FRAMES, SETTING_HOLD_FRAMES, SETTING_TRANSITION_FRAMES)
        );
    }

    public void playAirSetting() {
        player.action = PlayerAction.AIR_SETTING;
        player.attackHitBox.disable();
        animation.play(
                AnimationSequences.frames(player, "setting1", "setting2"),
                AnimationSequences.durations(SETTING_TRANSITION_FRAMES, AnimationSequences.HOLD)
        );
    }

    public void playDive() {
        player.attackHitBox.disable();
        player.action = PlayerAction.DIVE;
        animation.play(
                AnimationSequences.frames(player, "dive1", "dive2", "dive3"),
                AnimationSequences.durations(DIVE_TRANSITION_FRAMES, DIVE_TRANSITION_FRAMES, AnimationSequences.HOLD)
        );
    }

    public void startAttackReady(double velocityX) {
        player.mirrorImage = false;
        player.action = PlayerAction.ATTACK_READY;
        player.diving = false;
        player.attackHitBox.enable();
        player.vx = velocityX;
        player.vy = GameConfig.BACK_JUMP_VELOCITY_Y;
        // 紀錄起跳時的 X 座標以便後續三米線判定（以圖片中心為基準）
        player.jumpStartCenterX = player.x + player.imageWidth / 2.0;
        player.jumping = true;
        animation.show(player.teamAsset("attack1"));
    }

    public void startAttackSwing() {
        player.mirrorImage = false;
        player.action = PlayerAction.ATTACK_SWING;
        player.attackHitBox.enable();
        animation.play(
                AnimationSequences.frames(player, "attack2", "attack3"),
                AnimationSequences.durations(SWING_TRANSITION_FRAMES, AnimationSequences.HOLD)
        );
    }

    public void startBlock() {
        player.mirrorImage = false;
        player.action = PlayerAction.BLOCK;
        player.diving = false;
        player.attackHitBox.disable();

        if (!player.jumping) {
            player.vy = GameConfig.BACK_JUMP_VELOCITY_Y;
            player.jumping = true;
        }

        animation.play(
                AnimationSequences.frames(player, "block1", "block2", "block1"),
                AnimationSequences.durations(BLOCK_RAISING_FRAMES, BLOCK_EXTENDED_FRAMES, AnimationSequences.HOLD)
        );
    }

    public void startRunApproach(int cycles) {
        player.mirrorImage = false;
        player.action = PlayerAction.RUN_APPROACH;
        player.diving = false;
        player.attackHitBox.disable();
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
        disableAttackHitBoxAfterSwingMotion();

        if (shouldFinishAirAction() || shouldFinishHeldDive() || shouldFinishTimedGroundAction()) {
            finishAction();
        }
    }

    public void finishAction() {
        player.mirrorImage = false;
        player.action = PlayerAction.IDLE;
        player.attackHitBox.disable();
        animation.stopToIdle();
    }

    private boolean isBusyForReceive() {
        return player.action == PlayerAction.DIVE
                || player.action == PlayerAction.ATTACK_READY
                || player.action == PlayerAction.ATTACK_SWING
                || player.action == PlayerAction.BLOCK
                || player.action == PlayerAction.AIR_SETTING;
    }

    private void startGroundAction(PlayerAction action) {
        player.mirrorImage = false;
        player.action = action;
        player.attackHitBox.disable();
    }

    private boolean isRunLoopAction() {
        return player.action == PlayerAction.RUN_LOOP || player.action == PlayerAction.RUN_RETURN;
    }
    
    private void disableAttackHitBoxAfterSwingMotion() {
        if (player.action == PlayerAction.ATTACK_SWING && animation.isHoldingFrame()) {
            player.attackHitBox.disable();
        }
    }

    private boolean shouldFinishAirAction() {
        boolean airAction = player.action == PlayerAction.ATTACK_READY
                || player.action == PlayerAction.ATTACK_SWING
                || player.action == PlayerAction.BLOCK
                || player.action == PlayerAction.AIR_SETTING;

        return airAction && !player.jumping
                && (player.action == PlayerAction.ATTACK_READY
                    || player.action == PlayerAction.AIR_SETTING
                    || animation.isHoldingFrame());
    }

    private boolean shouldFinishHeldDive() {
        return player.action == PlayerAction.DIVE && !player.diving && animation.isHoldingFrame();
    }

    private boolean shouldFinishTimedGroundAction() {
        boolean timedGroundAction = player.action == PlayerAction.RECEIVING || player.action == PlayerAction.SETTING;
        return timedGroundAction && !animation.isPlaying();
    }
}
