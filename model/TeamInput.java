package model;

public class TeamInput {
    public boolean backLeft;
    public boolean backRight;
    public boolean backJump;
    public boolean backDive;

    public boolean setterJump;

    public boolean quickAttack;
    public boolean quickBlock;

    public boolean wingAttack;

    public boolean servePressed;
    public ServeType serveType = ServeType.NORMAL;

    // 由 GameModel 每一幀依照球的位置填入。
    // red 隊：球在網子左邊 = 本隊場。
    // blue 隊：球在網子右邊 = 本隊場。
    public boolean ballOnOwnSide;
    public boolean ballOnOpponentSide;
}