/*
所有球員的共同基底類別，保存位置、速度、狀態、碰撞箱與動畫控制器。
角色共用的接球、舉球、攻擊、攔網、撲球動畫入口也集中在這裡。
*/
package model.player;

import model.GameConfig;
import model.SideRules;
import model.TeamInput;
import model.ball.Ball;

public abstract class Player {
    public String assetName;
    public double x;
    public double y;

    protected double initialX;
    protected double initialY;

    public double vx;
    public double vy;

    public int imageWidth = GameConfig.PLAYER_IMAGE_WIDTH;
    public int imageHeight = GameConfig.PLAYER_IMAGE_HEIGHT;
    public HitBox hitBox;

    // 預留給之後扣球判斷使用；目前不參與球的碰撞。
    public AttackHitBox attackHitBox;

    public boolean jumping;
    public boolean attacking;
    public boolean blocking;
    public boolean diving;
    public boolean redSide;
    public boolean mirrorImage = false;

    // 當次跳起的起跳 X 座標（用於判定三米線違規）
    public double jumpStartX = Double.NaN;
    protected final PlayerAnimation animation;
    protected final PlayerActionAnimator actionAnimator;
    protected PlayerAction action = PlayerAction.IDLE;
    private boolean attackAttemptThisFrame;
    private boolean ballInAttackHitBoxWhenSwingStarted;
    private boolean attackAttemptStartedInAir;
    private boolean heldAttackQueued;
    private boolean airSetAttemptThisFrame;

    public double minX = GameConfig.WORLD_LEFT;
    public double maxX = GameConfig.WORLD_RIGHT;

    public Player(String assetName, double x, double y, boolean redSide) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
        this.initialX = x;
        this.initialY = y;
        this.redSide = redSide;
        this.hitBox = new HitBox(this);
        this.attackHitBox = new AttackHitBox(this);
        this.animation = new PlayerAnimation(this, assetName);
        this.actionAnimator = new PlayerActionAnimator(this, animation);
    }

    public abstract void update(TeamInput input);

    public void resetToInitial() {
        attackAttemptThisFrame = false;
        ballInAttackHitBoxWhenSwingStarted = false;
        attackAttemptStartedInAir = false;
        heldAttackQueued = false;
        airSetAttemptThisFrame = false;
        PlayerPhysics.resetToInitial(this);
        finishAction();
        attackHitBox.disable();
    }

    public void applyGravity() {
        PlayerPhysics.applyGravity(this);
    }

    public boolean intersectsBall(Ball ball) {
        return isDefaultHitBoxActive() && hitBox.intersectsBall(ball);
    }

    public boolean isDefaultHitBoxActive() {
        return true;
    }

    public boolean isBlockHitBoxActive() {
        return action == PlayerAction.BLOCK && isShowingActionFrame("block2");
    }

    protected boolean isShowingActionFrame(String actionName) {
        return assetName.equals(teamAsset(actionName));
    }

    public double getHitBoxCenterX() {
        return hitBox.getCenterX();
    }

    public double getHitBoxCenterY() {
        return hitBox.getCenterY();
    }

    public boolean isAttackReady() {
        return action == PlayerAction.ATTACK_READY;
    }

    public boolean isAttackSwinging() {
        return action == PlayerAction.ATTACK_SWING;
    }

    public boolean isReceiving() {
        return action == PlayerAction.RECEIVING;
    }

    public boolean isSetting() {
        return action == PlayerAction.SETTING;
    }

    public boolean isMovementLockedByAnimation() {
        return action == PlayerAction.RECEIVING;
    }

    public void playReceiveAnimation() {
        actionAnimator.playReceive();
    }

    public void playSettingAnimation() {
        actionAnimator.playSetting();
    }

    public void playDiveAnimation() {
        actionAnimator.playDive();
    }

    protected void startAttackReady(double horizontalSpeed) {
        heldAttackQueued = false;
        actionAnimator.startAttackReady(horizontalSpeed);
    }

    public void startAttackSwingAnimation() {
        heldAttackQueued = false;
        attackAttemptThisFrame = true;
        attackAttemptStartedInAir = jumping;
        actionAnimator.startAttackSwing();
    }

    /** 只提出本幀攻擊請求；球真的被碰撞流程擊中後才播放揮臂。 */
    protected void queueAttackAttempt() {
        attackAttemptThisFrame = true;
        attackAttemptStartedInAir = jumping;
    }

    public boolean hasAirSetAttemptThisFrame() {
        return airSetAttemptThisFrame;
    }

    protected void startAirSettingAnimation() {
        heldAttackQueued = false;
        airSetAttemptThisFrame = true;
        actionAnimator.playAirSetting();
    }

    protected boolean isBallInAttackBox(TeamInput input) {
        boolean overlaps = input.ball != null && attackHitBox.intersectsBall(input.ball);
        // 角色可能在同幀落地；保留落地前已成立的球與攻擊框重疊。
        if (overlaps) ballInAttackHitBoxWhenSwingStarted = true;
        return overlaps;
    }

    protected boolean canAirSetWith(TeamInput input) {
        return input.airSetModifier;
    }

    /** 起跳後第二次按攻擊鍵可先等球；放開按鍵就取消等待。 */
    protected boolean isHeldAttackReady(boolean attackPressed, boolean justPressed) {
        if (!attackPressed) {
            heldAttackQueued = false;
        } else if (justPressed && jumping && action == PlayerAction.ATTACK_READY) {
            heldAttackQueued = true;
        }
        return attackPressed && heldAttackQueued && jumping && action == PlayerAction.ATTACK_READY;
    }

    public boolean hasValidAttackAttemptThisFrame() {
        return attackAttemptThisFrame && attackAttemptStartedInAir
                && ballInAttackHitBoxWhenSwingStarted;
    }

    /** 球移動前確認本幀開始揮臂時已與攻擊框重疊，不以球移動後的位置補判。 */
    public void captureAttackAttemptBallOverlap(Ball ball) {
        ballInAttackHitBoxWhenSwingStarted = attackAttemptThisFrame
                && (ballInAttackHitBoxWhenSwingStarted || attackHitBox.intersectsBall(ball));
    }

    protected void clearAttackAttempt() {
        attackAttemptThisFrame = false;
        ballInAttackHitBoxWhenSwingStarted = false;
        attackAttemptStartedInAir = false;
        airSetAttemptThisFrame = false;
    }

    protected void startBlockAnimation() {
        actionAnimator.startBlock();
    }

    protected void startRunApproachAnimation(int cycles) {
        actionAnimator.startRunApproach(cycles);
    }

    protected void startRunLoopAnimation() {
        actionAnimator.startRunLoop();
    }

    protected void updateActionAnimation() {
        actionAnimator.updateActionState();
    }

    /**
     * Client 等待 Server 回合結果時，只延續既有角色動作的視覺與落地。
     * 不讀取新的玩家輸入，也不自行開始新的角色動作。
     */
    public void updateWhileAwaitingAuthority() {
        clearAttackAttempt();
        vx = 0;
        applyGravity();
        updateActionAnimation();
    }

    protected void finishAction() {
        actionAnimator.finishAction();
    }

    protected String teamAsset(String actionName) {
        return (redSide ? "player 1 " : "player 2 ") + actionName + ".png";
    }

    protected double directionTowardNet() {
        return SideRules.directionTowardOpponent(redSide);
    }

    /* 供網路快照還原角色動作與對應 hitBox 顯示。 */
    public PlayerAction getAction() {
        return action;
    }

    public void setActionForNetwork(PlayerAction action) {
        this.action = action == null ? PlayerAction.IDLE : action;
    }

    /**
     * 套用 Server 快照指定的動作與圖片，並停止本地尚未結束的動畫序列。
     * 避免舊動畫在下一幀覆蓋 Server 已同步的角色畫面。
     */
    public void applyNetworkAction(PlayerAction action, String assetName) {
        this.action = action == null ? PlayerAction.IDLE : action;
        if (assetName != null && !assetName.isBlank()) {
            animation.applyNetworkAsset(assetName);
        }
    }

    public boolean isAssetCompatibleWith(PlayerAction serverAction) {
        String name = assetName;
        return switch (serverAction) {
            case IDLE -> !name.contains(" run") && !name.contains(" dive")
                    && !name.contains(" attack") && !name.contains(" block")
                    && !name.contains(" setting") && !name.contains(" receive");
            case RUN_APPROACH, RUN_LOOP, RUN_RETURN -> name.contains(" run");
            case ATTACK_READY -> name.contains(" attack1");
            case ATTACK_SWING -> name.contains(" attack2") || name.contains(" attack3");
            case BLOCK -> name.contains(" block");
            case DIVE -> name.contains(" dive");
            case SETTING, AIR_SETTING -> name.contains(" setting");
            case RECEIVING -> name.contains(" receive");
        };
    }

    public boolean isAnimationPlaying() {
        return animation.isPlaying();
    }
}
