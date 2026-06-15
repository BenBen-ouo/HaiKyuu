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
        double netHalfWidth = GameConfig.NET_WIDTH / 2.0;

        if (redSide) {
            double baseX = GameConfig.NET_X;

            backPlayer = new BackPlayer("player 1 back.png", baseX + GameConfig.RED_BACK_OFFSET_X, baseY, true);
            setter = new Setter("player 1 S.png", baseX + GameConfig.RED_SETTER_OFFSET_X, baseY, true);
            quickAttacker = new QuickAttacker("player 1 MB.png", baseX + GameConfig.RED_QUICK_OFFSET_X, baseY, true);
            wingSpiker = new WingSpiker("player 1 WS.png", baseX + GameConfig.RED_WING_OFFSET_X, baseY, true);

            setTeamBoundaries(GameConfig.WORLD_LEFT, netX - netHalfWidth);
            setupRedHitBoxes();
        } else {
            double baseX = GameConfig.NET_X;

            backPlayer = new BackPlayer("player 2 back.png", baseX + GameConfig.BLUE_BACK_OFFSET_X, baseY, false);
            setter = new Setter("player 2 S.png", baseX + GameConfig.BLUE_SETTER_OFFSET_X, baseY, false);
            quickAttacker = new QuickAttacker("player 2 MB.png", baseX + GameConfig.BLUE_QUICK_OFFSET_X, baseY, false);
            wingSpiker = new WingSpiker("player 2 WS.png", baseX + GameConfig.BLUE_WING_OFFSET_X, baseY, false);

            setTeamBoundaries(netX + netHalfWidth, GameConfig.WORLD_RIGHT);
            setupBlueHitBoxes();
        }
    }

    private void setTeamBoundaries(double min, double max) {
        for (Player p : getPlayers()) {
            p.minX = min;
            p.maxX = max;
        }
    }

    private void setupRedHitBoxes() {
        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(21, 50, 28, 10, 8, 8, 20);
        setter.hitBox.set(21, 14, 28, 20, 8, 8, 0);
        quickAttacker.hitBox.set(40, 0, 10, 30, 8, 8, 20);
        wingSpiker.hitBox.set(21, 50, 28, 10, 8, 8, 20);
    }

    private void setupBlueHitBoxes() {
        // set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees)
        backPlayer.hitBox.set(21, 50, 28, 10, 8, 8, -20);
        setter.hitBox.set(21, 14, 28, 20, 8, 8, 0);
        quickAttacker.hitBox.set(20, 0, 10, 30, 8, 8, -20);
        wingSpiker.hitBox.set(21, 50, 28, 10, 8, 8, -20);
    }

    public Player[] getPlayers() {
        return new Player[]{backPlayer, setter, quickAttacker, wingSpiker};
    }

    public void update(TeamInput input) {
        for (Player p : getPlayers()) {
            p.update(input);
        }
    }
}
