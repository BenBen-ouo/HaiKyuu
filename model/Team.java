/*
代表一整隊球員，負責建立 back、setter、MB、WS 四名角色。
同時設定各角色初始位置、移動邊界與一般觸球碰撞箱。
*/
package model;

public class Team {
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
            setupRedHitBoxes();
            setupRedAttackHitBoxes();
            setupRedBlockHitBoxes();
        } else {
            backPlayer = new BackPlayer("player 2 back.png", baseX + GameConfig.BLUE_BACK_OFFSET_X, baseY, false);
            setter = new Setter("player 2 S.png", baseX + GameConfig.BLUE_SETTER_OFFSET_X, baseY, false);
            quickAttacker = new QuickAttacker("player 2 MB.png", baseX + GameConfig.BLUE_QUICK_OFFSET_X, baseY, false);
            wingSpiker = new WingSpiker("player 2 WS.png", baseX + GameConfig.BLUE_WING_OFFSET_X, baseY, false);

            double blueMinX = netX - playerNetOverlap;
            double blueMaxX = GameConfig.WORLD_RIGHT;

            setTeamBoundaries(blueMinX, blueMaxX);
            setupBlueHitBoxes();
            setupBlueAttackHitBoxes();
            setupBlueBlockHitBoxes();
        }
    }

    private void setTeamBoundaries(double min, double max) {
        for (Player player : getPlayers()) {
            player.minX = min;
            player.maxX = max;
        }
    }

    private void setupRedHitBoxes() {
        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(45, 60, 20, 10, 10, 10, 20);
        setter.hitBox.set(40, 50, 20, 10, 5, 5, 0);
        quickAttacker.hitBox.set(50, 25, 15, 40, 10, 10, 40);
        wingSpiker.hitBox.set(45, 60, 20, 10, 10, 10, 20);
    }

    private void setupBlueHitBoxes() {
        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(35, 60, 20, 10, 10, 10, -20);
        setter.hitBox.set(40, 50, 20, 10, 5, 5, 0);
        quickAttacker.hitBox.set(35, 25, 15, 40, 10, 10, -40);
        wingSpiker.hitBox.set(35, 60, 20, 10, 10, 10, -20);
    }

    private void setupRedAttackHitBoxes() {
        // set(offsetX, offsetY, width, height)
        // 左隊攻擊 hitBox 預設在角色圖片右上角。
        backPlayer.attackHitBox.set(
                GameConfig.RED_ATTACK_HITBOX_OFFSET_X,
                GameConfig.RED_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
        quickAttacker.attackHitBox.set(
                GameConfig.RED_ATTACK_HITBOX_OFFSET_X,
                GameConfig.RED_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
        wingSpiker.attackHitBox.set(
                GameConfig.RED_ATTACK_HITBOX_OFFSET_X,
                GameConfig.RED_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
    }

    private void setupBlueAttackHitBoxes() {
        // set(offsetX, offsetY, width, height)
        // 右隊攻擊 hitBox 預設在角色圖片左上角。
        backPlayer.attackHitBox.set(
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_X,
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
        quickAttacker.attackHitBox.set(
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_X,
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
        wingSpiker.attackHitBox.set(
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_X,
                GameConfig.BLUE_ATTACK_HITBOX_OFFSET_Y,
                GameConfig.ATTACK_HITBOX_WIDTH,
                GameConfig.ATTACK_HITBOX_HEIGHT
        );
    }

    private void setupRedBlockHitBoxes() {
        // Reuse attack offsets for block hitbox positioning by default.
        backPlayer.blockHitBox.set(
                GameConfig.RED_BLOCK_HITBOX_OFFSET_X,
                GameConfig.RED_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
        quickAttacker.blockHitBox.set(
                GameConfig.RED_BLOCK_HITBOX_OFFSET_X,
                GameConfig.RED_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
        wingSpiker.blockHitBox.set(
                GameConfig.RED_BLOCK_HITBOX_OFFSET_X,
                GameConfig.RED_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
    }

    private void setupBlueBlockHitBoxes() {
        backPlayer.blockHitBox.set(
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_X,
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
        quickAttacker.blockHitBox.set(
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_X,
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
        wingSpiker.blockHitBox.set(
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_X,
                GameConfig.BLUE_BLOCK_HITBOX_OFFSET_Y,
                GameConfig.BLOCK_HITBOX_WIDTH,
                GameConfig.BLOCK_HITBOX_HEIGHT
        );
    }

    public Player[] getPlayers() {
        return new Player[]{backPlayer, setter, quickAttacker, wingSpiker};
    }

    public void resetAllPlayers() {
        for (Player p : getPlayers()) {
            p.resetToInitial();
        }
    }

    public void update(TeamInput input) {
        for (Player player : getPlayers()) {
            player.update(input);
        }
    }
}