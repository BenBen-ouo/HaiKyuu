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
        double baseX = GameConfig.NET_X;
        double netX = GameConfig.NET_X;

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
        } else {
            backPlayer = new BackPlayer("player 2 back.png", baseX + GameConfig.BLUE_BACK_OFFSET_X, baseY, false);
            setter = new Setter("player 2 S.png", baseX + GameConfig.BLUE_SETTER_OFFSET_X, baseY, false);
            quickAttacker = new QuickAttacker("player 2 MB.png", baseX + GameConfig.BLUE_QUICK_OFFSET_X, baseY, false);
            wingSpiker = new WingSpiker("player 2 WS.png", baseX + GameConfig.BLUE_WING_OFFSET_X, baseY, false);

            double blueMinX = netX - playerNetOverlap;
            double blueMaxX = GameConfig.WORLD_RIGHT;

            setTeamBoundaries(blueMinX, blueMaxX);
            setupBlueHitBoxes();
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
        backPlayer.hitBox.set(40, 60, 25, 10, 10, 10, 20);
        setter.hitBox.set(40, 50, 20, 10, 5, 5, 0);
        quickAttacker.hitBox.set(50, 30, 10, 30, 10, 10, 30);
        wingSpiker.hitBox.set(45, 60, 20, 10, 10, 10, 20);
    }

    private void setupBlueHitBoxes() {
        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(35, 60, 25, 10, 10, 10, -20);
        setter.hitBox.set(40, 50, 20, 10, 5, 5, 0);
        quickAttacker.hitBox.set(40, 30, 10, 30, 10, 10, -30);
        wingSpiker.hitBox.set(35, 60, 20, 10, 10, 10, -20);
    }

    public Player[] getPlayers() {
        return new Player[]{backPlayer, setter, quickAttacker, wingSpiker};
    }

    public void update(TeamInput input) {
        for (Player player : getPlayers()) {
            player.update(input);
        }
    }
}