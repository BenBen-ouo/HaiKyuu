/*
代表一整隊球員，負責建立 back、setter、MB、WS 四名角色。
同時設定各角色初始位置、移動邊界與一般觸球碰撞箱。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class Team {
    private static final double RECEIVE_HITBOX_RED_X = 30;
    private static final double RECEIVE_HITBOX_Y = 60;
    private static final double RECEIVE_HITBOX_WIDTH = 25;
    private static final double RECEIVE_HITBOX_HEIGHT = 10;
    private static final int RECEIVE_HITBOX_ARC = 10;
    private static final double RECEIVE_HITBOX_ROTATION = 20;

    private static final double SETTER_HITBOX_RED_X = 40;
    private static final double SETTER_HITBOX_Y = 50;
    private static final double SETTER_HITBOX_WIDTH = 20;
    private static final double SETTER_HITBOX_HEIGHT = 10;
    private static final int SETTER_HITBOX_ARC = 5;
    private static final double SETTER_HITBOX_ROTATION = 0; ///之後再看有沒有需要讓貼網球早點接觸

    private static final double BLOCK_HITBOX_RED_X = 50;
    private static final double BLOCK_HITBOX_Y = 20;
    private static final double BLOCK_HITBOX_WIDTH = 10;
    private static final double BLOCK_HITBOX_HEIGHT = 45;
    private static final int BLOCK_HITBOX_ARC = 10;
    private static final double BLOCK_HITBOX_ROTATION = 35;

    private static final double ATTACK_HITBOX_RED_X = 40;
    private static final double ATTACK_HITBOX_Y = 10;
    private static final double ATTACK_HITBOX_WIDTH = 30;
    private static final double ATTACK_HITBOX_HEIGHT = 40;

    public BackPlayer backPlayer;
    public Setter setter;
    public QuickAttacker quickAttacker;
    public WingSpiker wingSpiker;
    public boolean redSide;

    public Team(boolean redSide) {
        this.redSide = redSide;

        double baseY = GameConfig.PLAYER_BASE_Y;
        double netX = GameConfig.NET_X;
        double baseX = GameConfig.NET_X;

        // 因為圖片旁邊有留白，所以允許圖片邊界跨過網子中心 1/3 圖片寬度
        // 真正是否碰到球，仍然由各角色自己的 hitBox 決定
        double playerNetOverlap = GameConfig.PLAYER_IMAGE_WIDTH / 3.0;

        if (redSide) {
            backPlayer = new BackPlayer("player 1 back.png", baseX + GameConfig.RED_BACK_OFFSET_X, baseY, true);
            setter = new Setter("player 1 S.png", baseX + GameConfig.RED_SETTER_OFFSET_X, baseY, true);
            quickAttacker = new QuickAttacker("player 1 MB.png", baseX + GameConfig.RED_QUICK_OFFSET_X, baseY, true);
            wingSpiker = new WingSpiker("player 1 WS.png", baseX + GameConfig.RED_WING_OFFSET_X, baseY, true);

            double redMinX = GameConfig.WORLD_LEFT;
            double redMaxX = netX + playerNetOverlap;

            setTeamBoundaries(redMinX, redMaxX);
        } else {
            backPlayer = new BackPlayer("player 2 back.png", baseX + GameConfig.BLUE_BACK_OFFSET_X, baseY, false);
            setter = new Setter("player 2 S.png", baseX + GameConfig.BLUE_SETTER_OFFSET_X, baseY, false);
            quickAttacker = new QuickAttacker("player 2 MB.png", baseX + GameConfig.BLUE_QUICK_OFFSET_X, baseY, false);
            wingSpiker = new WingSpiker("player 2 WS.png", baseX + GameConfig.BLUE_WING_OFFSET_X, baseY, false);

            double blueMinX = netX - playerNetOverlap;
            double blueMaxX = GameConfig.WORLD_RIGHT;

            setTeamBoundaries(blueMinX, blueMaxX);
        }

        setupHitBoxes();
        backPlayer.captureDefaultHitBox();
    }

    private void setTeamBoundaries(double min, double max) {
        for (Player player : getPlayers()) {
            player.minX = min;
            player.maxX = max;
        }
    }

    private void setupHitBoxes() {
        double receiveX = redSide
                ? RECEIVE_HITBOX_RED_X
                : mirroredOffsetX(RECEIVE_HITBOX_RED_X, RECEIVE_HITBOX_WIDTH);
        double setterX = redSide
                ? SETTER_HITBOX_RED_X
                : mirroredOffsetX(SETTER_HITBOX_RED_X, SETTER_HITBOX_WIDTH);
        double blockX = redSide
                ? BLOCK_HITBOX_RED_X
                : mirroredOffsetX(BLOCK_HITBOX_RED_X, BLOCK_HITBOX_WIDTH);
        double attackX = redSide
                ? ATTACK_HITBOX_RED_X
                : mirroredOffsetX(ATTACK_HITBOX_RED_X, ATTACK_HITBOX_WIDTH);
        double rotationDirection = redSide ? 1 : -1;

        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(receiveX, RECEIVE_HITBOX_Y,
                RECEIVE_HITBOX_WIDTH, RECEIVE_HITBOX_HEIGHT,
                RECEIVE_HITBOX_ARC, RECEIVE_HITBOX_ARC,
                rotationDirection * RECEIVE_HITBOX_ROTATION);
        setter.hitBox.set(setterX, SETTER_HITBOX_Y,
                SETTER_HITBOX_WIDTH, SETTER_HITBOX_HEIGHT,
                SETTER_HITBOX_ARC, SETTER_HITBOX_ARC, 0);
        quickAttacker.blockHitBox.set(blockX, BLOCK_HITBOX_Y,
                BLOCK_HITBOX_WIDTH, BLOCK_HITBOX_HEIGHT,
                BLOCK_HITBOX_ARC, BLOCK_HITBOX_ARC,
                rotationDirection * BLOCK_HITBOX_ROTATION);
        wingSpiker.hitBox.set(receiveX, RECEIVE_HITBOX_Y,
                RECEIVE_HITBOX_WIDTH, RECEIVE_HITBOX_HEIGHT,
                RECEIVE_HITBOX_ARC, RECEIVE_HITBOX_ARC,
                rotationDirection * RECEIVE_HITBOX_ROTATION);

        // 後排、MB、WS 共用攻擊框；藍隊 X 由紅隊鏡像位置算出。
        backPlayer.attackHitBox.set(attackX, ATTACK_HITBOX_Y,
                ATTACK_HITBOX_WIDTH, ATTACK_HITBOX_HEIGHT);
        quickAttacker.attackHitBox.set(attackX, ATTACK_HITBOX_Y,
                ATTACK_HITBOX_WIDTH, ATTACK_HITBOX_HEIGHT);
        wingSpiker.attackHitBox.set(attackX, ATTACK_HITBOX_Y,
                ATTACK_HITBOX_WIDTH, ATTACK_HITBOX_HEIGHT);
    }

    private static double mirroredOffsetX(double redOffsetX, double width) {
        return GameConfig.PLAYER_IMAGE_WIDTH - redOffsetX - width;
    }

    public Player[] getPlayers() {
        return new Player[]{backPlayer, setter, quickAttacker, wingSpiker};
    }

    public void resetAllPlayers() {
        for (Player p : getPlayers()) {
            p.resetToInitial();
        }
    }

    /** 下一球發球員不回後排原站位，其餘隊員照常歸位。 */
    public void resetPlayersExceptBack() {
        setter.resetToInitial();
        quickAttacker.resetToInitial();
        wingSpiker.resetToInitial();
    }

    public void update(TeamInput input) {
        for (Player player : getPlayers()) {
            player.update(input);
        }
    }

    /** 等待 Server 回合結果時，只延續角色既有動作與落地，不讀取新輸入。 */
    public void updateWhileAwaitingAuthority() {
        for (Player player : getPlayers()) {
            player.updateWhileAwaitingAuthority();
        }
    }
}
