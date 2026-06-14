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

        if (redSide) {
            double baseX = GameConfig.NET_X;

            backPlayer = new BackPlayer(
                    "player 1 back.png",
                    baseX + GameConfig.RED_BACK_OFFSET_X,
                    baseY
            );

            setter = new Setter(
                    "player 1 S.png",
                    baseX + GameConfig.RED_SETTER_OFFSET_X,
                    baseY
            );

            quickAttacker = new QuickAttacker(
                    "player 1 MB.png",
                    baseX + GameConfig.RED_QUICK_OFFSET_X,
                    baseY
            );

            wingSpiker = new WingSpiker(
                    "player 1 WS.png",
                    baseX + GameConfig.RED_WING_OFFSET_X,
                    baseY
            );

        } else {
            double baseX = GameConfig.NET_X;

            backPlayer = new BackPlayer(
                    "player 2 back.png",
                    baseX + GameConfig.BLUE_BACK_OFFSET_X,
                    baseY
            );

            setter = new Setter(
                    "player 2 S.png",
                    baseX + GameConfig.BLUE_SETTER_OFFSET_X,
                    baseY
            );

            quickAttacker = new QuickAttacker(
                    "player 2 MB.png",
                    baseX + GameConfig.BLUE_QUICK_OFFSET_X,
                    baseY
            );

            wingSpiker = new WingSpiker(
                    "player 2 WS.png",
                    baseX + GameConfig.BLUE_WING_OFFSET_X,
                    baseY
            );
        }
    }

    public void update(TeamInput input) {
        backPlayer.update(input, redSide);
        setter.update(input);
        quickAttacker.update(input);
        wingSpiker.update(input);
    }
}