package model;

public class TeamInput {
    public static final int SERVE_NORMAL = 0;
    public static final int SERVE_CEILING = 1;
    public static final int SERVE_LOW_NET = 2;
    public static final int SERVE_SHORT = 3;
    public static final int SERVE_JUMP = 4;

    public boolean backLeft;
    public boolean backRight;
    public boolean backJump;
    public boolean backDive;

    public boolean setterJump;

    public boolean quickAttack;
    public boolean quickBlock;

    public boolean wingAttack;

    public boolean servePressed;
    public int serveType = SERVE_NORMAL;
}