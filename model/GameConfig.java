package model;

public final class GameConfig {
    private GameConfig() {}

    public static final int SCREEN_WIDTH = 1200;
    public static final int SCREEN_HEIGHT = 675;

    // 視窗外可活動區域：球與玩家可以超出畫面，但不可超出這些世界邊界。
    public static final double WORLD_LEFT = -240;
    public static final double WORLD_RIGHT = SCREEN_WIDTH + 240;
    public static final double WORLD_TOP = -1000;

    public static final int FLOOR_Y = SCREEN_HEIGHT - 50;
    public static final double GRAVITY = 0.25;
    public static final double PLAYER_SPEED = 4.3;
    public static final double PLAYER_JUMP_SPEED = -8;
    public static final double DIVE_SPEED = 5.0;

    public static final double BALL_RADIUS = 12;
    public static final double BALL_BOUNCE = 0.72;
    public static final double NET_BOUNCE = 0.4;

    public static final double NET_WIDTH = 6;
    public static final double NET_HEIGHT = 135; //因為我螢幕小 所以我照比例換算 243 * 5/9
    public static final double NET_X = SCREEN_WIDTH / 2;
    public static final double NET_TOP_Y = FLOOR_Y - NET_HEIGHT;

    // 球場範圍 (以網子中心點左右各 500)
    public static final double COURT_WIDTH = 1000;
    public static final double COURT_LEFT_X = NET_X - (COURT_WIDTH / 2.0);
    public static final double COURT_RIGHT_X = NET_X + (COURT_WIDTH / 2.0);

    public static final int PLAYER_IMAGE_WIDTH = 100;
    public static final int PLAYER_IMAGE_HEIGHT = 100;
    public static final double PLAYER_NET_OVERLAP_X = PLAYER_IMAGE_WIDTH / 3.0;

    public static final double PLAYER_BASE_Y = FLOOR_Y - PLAYER_IMAGE_HEIGHT;

    // red 隊各角色相對基準點的位置
    public static final double RED_BACK_OFFSET_X = -300 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_SETTER_OFFSET_X = -30 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_QUICK_OFFSET_X = -5 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_WING_OFFSET_X = -140 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_BACK_SERVE_X = -100;
    public static final double RED_BACK_SERVE_Y = PLAYER_BASE_Y;
    // 發球時，球相對於 red backPlayer 圖片左上角的位置
    public static final double RED_SERVE_BALL_OFFSET_X = PLAYER_IMAGE_WIDTH + 12;
    public static final double RED_SERVE_BALL_OFFSET_Y = -5;

    // blue 隊各角色相對基準點的位置
    public static final double BLUE_BACK_OFFSET_X = 300 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_SETTER_OFFSET_X = 30 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_QUICK_OFFSET_X = 5 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_WING_OFFSET_X = 140 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_BACK_SERVE_X = SCREEN_WIDTH + 100 - PLAYER_IMAGE_WIDTH;
    public static final double BLUE_BACK_SERVE_Y = PLAYER_BASE_Y;
    // blue 隊發球時，球相對於 blue backPlayer 圖片左上角的位置
    public static final double BLUE_SERVE_BALL_OFFSET_X = PLAYER_IMAGE_WIDTH + 12;
    public static final double BLUE_SERVE_BALL_OFFSET_Y = -5;

    // 發球球速，之後可以再依照手感慢慢調
    public static final double SERVE_NORMAL_VX = 10;
    public static final double SERVE_NORMAL_VY = -12;
    public static final double SERVE_CEILING_VX = 5;
    public static final double SERVE_CEILING_VY = -25;
    public static final double SERVE_LOW_NET_VX = 14.8;
    public static final double SERVE_LOW_NET_VY = -7;
    public static final double SERVE_SHORT_VX = 7.0;
    public static final double SERVE_SHORT_VY = -14.0;
    public static final double SERVE_JUMP_VX = 11.5;
    public static final double SERVE_JUMP_VY = -9.5;

    // 跳發第一段拋球的預計落地點。
    // 之後你要調整跳發拋球位置，主要改這三個數值。
    public static final double RED_JUMP_SERVE_TOSS_LANDING_X = 75;
    public static final double BLUE_JUMP_SERVE_TOSS_LANDING_X = SCREEN_WIDTH - 75;
    public static final double JUMP_SERVE_TOSS_LANDING_Y = FLOOR_Y - BALL_RADIUS;
    public static final double JUMP_SERVE_TOSS_POWER = 14.0;

    // 發球隨機誤差範圍
    public static final double SERVE_RANDOM_VX_RANGE = 0.6;
    public static final double SERVE_RANDOM_VY_RANGE = 0.8;
}