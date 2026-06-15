package model;

public final class GameConfig {
    private GameConfig() {}

    public static final int SCREEN_WIDTH = 1200;
    public static final int SCREEN_HEIGHT = 675;

    // 視窗外可活動區域：球與玩家可以超出畫面，但不可超出這些世界邊界。
    public static final double WORLD_LEFT = -240;
    public static final double WORLD_RIGHT = SCREEN_WIDTH + 240;
    public static final double WORLD_TOP = -260;

    public static final int FLOOR_Y = SCREEN_HEIGHT - 50;
    public static final double GRAVITY = 0.25;
    public static final double PLAYER_SPEED = 4.3;
    public static final double PLAYER_JUMP_SPEED = -8;
    public static final double DIVE_SPEED = 8.0; ///之後改成撲接時碰撞箱範圍變大，先不動

    public static final double BALL_RADIUS = 25;
    public static final double BALL_BOUNCE = 0.72;
    public static final double NET_BOUNCE = 0.4;

    public static final double NET_WIDTH = 6;
    public static final double NET_HEIGHT = 135; //因為我螢幕小 所以我照比例換算 243 * 5/9
    public static final double NET_X = SCREEN_WIDTH / 2;
    public static final double NET_TOP_Y = FLOOR_Y - NET_HEIGHT;

    public static final int PLAYER_IMAGE_WIDTH = 50;
    public static final int PLAYER_IMAGE_HEIGHT = 92;

    public static final double PLAYER_BASE_Y = FLOOR_Y - PLAYER_IMAGE_HEIGHT;

    // red 隊各角色相對基準點的位置
    public static final double RED_BACK_OFFSET_X = -300 - PLAYER_IMAGE_WIDTH;
    public static final double RED_SETTER_OFFSET_X = -50 - PLAYER_IMAGE_WIDTH;
    public static final double RED_QUICK_OFFSET_X = -5 - PLAYER_IMAGE_WIDTH;
    public static final double RED_WING_OFFSET_X = -167 - PLAYER_IMAGE_WIDTH; //照比例換算 300 * 5/9

    // blue 隊各角色相對基準點的位置
    public static final double BLUE_BACK_OFFSET_X = 300;
    public static final double BLUE_SETTER_OFFSET_X = 50;
    public static final double BLUE_QUICK_OFFSET_X = 5;
    public static final double BLUE_WING_OFFSET_X = 167; //照比例換算 300 * 5/9
}