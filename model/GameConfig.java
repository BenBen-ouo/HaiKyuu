/*
遊戲全域設定檔，集中管理畫面大小、重力、球速、球場位置與角色站位。
調整遊戲手感、發球參數、網子與角色初始位置時優先修改這裡。
*/
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

    // 球旋轉：正值為畫面上的順時針，負值為逆時針；單位為每幀角度。
    public static final double RECEIVE_SPIN_SPEED = 4.0;
    public static final double DIVE_RECEIVE_SPIN_SPEED = 20.0;
    public static final double SPIKE_SPIN_SPEED = 25.0;
    public static final double LOB_SPIKE_SPIN_SPEED = 5.0;

    // 球落地時，依上一個觸球類型設定的旋轉速度。
    public static final double FLOOR_BOUNCE_FAST_SPIN_SPEED = 10.0;
    public static final double FLOOR_BOUNCE_SLOW_SPIN_SPEED = 6.0;

    public static final double NET_WIDTH = 4;
    public static final double NET_HEIGHT = 135; //因為我螢幕小 所以我照比例換算 243 * 5/9
    public static final double NET_X = SCREEN_WIDTH / 2;
    public static final double NET_TOP_Y = FLOOR_Y - NET_HEIGHT;

    // 網子碰撞箱：預設和畫面上的網子完全重合，可獨立調整碰撞區域。
    public static final double NET_HITBOX_CENTER_X = NET_X;
    public static final double NET_HITBOX_TOP_Y = NET_TOP_Y;
    public static final double NET_HITBOX_WIDTH = NET_WIDTH;
    public static final double NET_HITBOX_HEIGHT = NET_HEIGHT;

    // 網子左右側與上方的反彈係數；數值越小，碰撞後減速越明顯。
    public static final double NET_SIDE_BOUNCE = 0.4;
    public static final double NET_TOP_BOUNCE = 0.4;

    // 避免球以過低速度卡在網子邊緣或頂部。
    public static final double NET_MIN_REBOUND_SPEED = 1.5;

    // MB 攔網 hitBox 反彈參數，只在 block2 圖片幀啟用。
    public static final double BLOCK_HITBOX_BOUNCE = 0.72;
    public static final double BLOCK_HITBOX_MIN_SPEED = 6.0;

    // 球場範圍 (以網子中心點左右各 500)
    public static final double COURT_WIDTH = 1000;
    public static final double COURT_LEFT_X = NET_X - (COURT_WIDTH / 2.0);
    public static final double COURT_RIGHT_X = NET_X + (COURT_WIDTH / 2.0);

    public static final int PLAYER_IMAGE_WIDTH = 100;
    public static final int PLAYER_IMAGE_HEIGHT = 100;
    public static final double PLAYER_NET_OVERLAP_X = PLAYER_IMAGE_WIDTH / 3.0;

    public static final double PLAYER_BASE_Y = FLOOR_Y - PLAYER_IMAGE_HEIGHT;

    // 調整這些數值即可自訂扣球判定區域。
    public static final double ATTACK_HITBOX_WIDTH = 10;
    public static final double ATTACK_HITBOX_HEIGHT = 25;

    // 攻擊 hitBox，左隊放在角色右上角，右隊放在角色左上角。
    public static final double RED_ATTACK_HITBOX_OFFSET_X = 55;
    public static final double RED_ATTACK_HITBOX_OFFSET_Y = 35;
    public static final double BLUE_ATTACK_HITBOX_OFFSET_X = 35;
    public static final double BLUE_ATTACK_HITBOX_OFFSET_Y = 35;

    // 扣球球路。vy 為正時往下，為負時往上。
    public static final double SPIKE_SPEED_X = 17.0;
    public static final double SPIKE_SPEED_Y = 7.0;
    
    // D / →：平打，橫向速度更大。
    public static final double FLAT_SPIKE_SPEED_X = 20.0;
    public static final double FLAT_SPIKE_SPEED_Y = 4.5;

    // S / ↓：短球，向下速度更大。
    public static final double SHORT_SPIKE_SPEED_X = 15.0;
    public static final double SHORT_SPIKE_SPEED_Y = 10.0;

    // S + D / ↓ + →：比普通球再更多力量。
    public static final double LONG_SPIKE_SPEED_X = 10.0;
    public static final double LONG_SPIKE_SPEED_Y = 10.0;

    // W / ↑：吊球，慢速上拋後越過攔網。
    public static final double LOB_SPIKE_SPEED_X = 3.0;
    public static final double LOB_SPIKE_SPEED_Y = -3.0;

    // W + D / ↑ + →：吊長球。
    public static final double LONG_LOB_SPIKE_SPEED_X = 8.0;
    public static final double LONG_LOB_SPIKE_SPEED_Y = -4.5;

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
    public static final double SERVE_NORMAL_VX = 9;
    public static final double SERVE_NORMAL_VY = -12;
    public static final double SERVE_CEILING_VX = 4.5;
    public static final double SERVE_CEILING_VY = -25;
    public static final double SERVE_LOW_NET_VX = 14.8;
    public static final double SERVE_LOW_NET_VY = -7;
    public static final double SERVE_SHORT_VX = 6.5;
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
    public static final double SERVE_RANDOM_VX_RANGE = 0.7;
    public static final double SERVE_RANDOM_VY_RANGE = 0.5;
}