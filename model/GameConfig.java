/*
遊戲全域設定檔，集中管理畫面大小、重力、球速、球場位置與角色站位。
調整遊戲手感、發球參數、網子與角色初始位置時優先修改這裡。
*/
package model;

public final class GameConfig {
    private GameConfig() {}

    public static final int SCREEN_WIDTH = 1200;
    public static final int SCREEN_HEIGHT = 675;

    // 所有本機與網路模式共用的遊戲更新頻率。
    public static final int TICKS_PER_SECOND = 60;

    // 視窗外可活動區域：球與玩家可以超出畫面，但不可超出這些世界邊界。
    public static final double WORLD_LEFT = -240;
    public static final double WORLD_RIGHT = SCREEN_WIDTH + 240;
    public static final double WORLD_TOP = -1000;

    public static final int FLOOR_Y_PX = SCREEN_HEIGHT - 50;
    public static final double FLOOR_Y = FLOOR_Y_PX;
    public static final double GRAVITY = 0.25;

    // Setter 未選快攻時，舉球球心固定到達的最高世界座標。
    public static final double SETTER_SET_APEX_Y = 300;

    // Setter 第二球按住 S／↓ 時，快攻舉球的球心最高點世界座標。
    public static final double RED_QUICK_SET_APEX_X = 591;
    public static final double BLUE_QUICK_SET_APEX_X = 609;
    public static final double QUICK_SET_APEX_Y = 420;

    // 後排地面同方向連續移動：0.1／0.3／0.5／0.7 秒後升階；速度單位為像素／tick。
    public static final double BACK_RUN_START_SPEED = 4.0;
    public static final double BACK_RUN_SECOND_STAGE_SPEED = 4.5;
    public static final double BACK_RUN_THIRD_STAGE_SPEED = 5.0;
    public static final double BACK_RUN_FOURTH_STAGE_SPEED = 5.5;
    public static final double BACK_RUN_MAX_SPEED = 6.0;
    public static final int BACK_RUN_FIRST_STAGE_END_FRAMES = TICKS_PER_SECOND / 10;
    public static final int BACK_RUN_SECOND_STAGE_END_FRAMES = TICKS_PER_SECOND * 3 / 10;
    public static final int BACK_RUN_THIRD_STAGE_END_FRAMES = TICKS_PER_SECOND * 5 / 10;
    public static final int BACK_RUN_FOURTH_STAGE_END_FRAMES = TICKS_PER_SECOND * 7 / 10;
    // 起跳初始 vy；負值代表向上。各角色可獨立調整。
    // 世界 Y 向下：以下起跳速度分量均為負值，單位像素／tick。
    public static final double BACK_JUMP_VELOCITY_Y = -9.0;
    public static final double SETTER_JUMP_VELOCITY_Y = -6.3;
    public static final double MB_JUMP_VELOCITY_Y = -7.8;
    public static final double MB_REPEAT_BLOCK_JUMP_SPEED_MULTIPLIER = 0.8;
    public static final double WS_JUMP_VELOCITY_Y = -7.8;
    public static final double DIVE_SPEED = 5.0;

    public static final double BALL_RADIUS = 12;
    public static final double BALL_BOUNCE = 0.8;
    // 球撞到世界邊界（非網子）時的反彈係數。
    public static final double WORLD_BOUNDARY_BOUNCE = 0.4;

    // 球旋轉：正值為畫面上的順時針，負值為逆時針；單位為每幀角度。
    public static final double RECEIVE_SPIN_SPEED = 15.0;
    public static final double DIVE_RECEIVE_SPIN_SPEED = 25.0;
    public static final double SPIKE_SPIN_SPEED = 40.0;
    public static final double LOB_SPIKE_SPIN_SPEED = 5.0;

    // 球落地時，依上一個觸球類型設定的旋轉速度。
    public static final double FLOOR_BOUNCE_FAST_SPIN_SPEED = 30.0;
    public static final double FLOOR_BOUNCE_SLOW_SPIN_SPEED = 5.0;

    public static final double NET_WIDTH = 4;
    public static final int NET_FLASH_FRAMES = 60;
    public static final int NET_FLASH_COUNT = 6;
    public static final double NET_HEIGHT = 135; //因為我螢幕小 所以我照比例換算 243 * 5/9
    public static final double NET_X = SCREEN_WIDTH / 2;
    public static final double NET_TOP_Y = FLOOR_Y - NET_HEIGHT;

    // 實際網子判定維持寬 4；下段寬 6 的深紅球柱僅為外觀。
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
    public static final double BLOCK_HITBOX_BOUNCE = 0.6;
    // 頭部圓框的反彈係數；兩種攔網框皆不強制最低球速。
    public static final double BLOCK_HEAD_BOUNCE = 0.7;

    // 球場範圍 (以網子中心點左右各 500)
    public static final double COURT_WIDTH = 1000;
    public static final double COURT_LEFT_X = NET_X - (COURT_WIDTH / 2.0);
    public static final double COURT_RIGHT_X = NET_X + (COURT_WIDTH / 2.0);
    // 底線與其他白色球場線共用線寬；落點判定包含球碰到白線外緣。
    public static final float COURT_LINE_WIDTH = 3.0f;

    public static final int PLAYER_IMAGE_WIDTH = 100;
    public static final int PLAYER_IMAGE_HEIGHT = 100;
    public static final double PLAYER_NET_OVERLAP_X = PLAYER_IMAGE_WIDTH / 3.0;

    public static final double PLAYER_BASE_Y = FLOOR_Y - PLAYER_IMAGE_HEIGHT;

    // 扣球球路。vy 為正時往下，為負時往上。
    public static final double SPIKE_HORIZONTAL_SPEED = 17.0;
    public static final double SPIKE_VELOCITY_Y = 7.0;
    
    // 紅隊 D／藍隊 ←：平打，橫向速度更大。
    public static final double FLAT_SPIKE_HORIZONTAL_SPEED = 18.0;
    public static final double FLAT_SPIKE_VELOCITY_Y = 3.5;

    // S / ↓：短球，向下速度更大。
    public static final double SHORT_SPIKE_HORIZONTAL_SPEED = 13.5;
    public static final double SHORT_SPIKE_VELOCITY_Y = 11.0;

    // W / ↑：吊球，慢速上拋後越過攔網。
    public static final double LOB_SPIKE_HORIZONTAL_SPEED = 3.0;
    public static final double LOB_SPIKE_VELOCITY_Y = -3.0;

    // 紅隊 W + D／藍隊 ↑ + ←：吊長球。
    public static final double LONG_LOB_SPIKE_HORIZONTAL_SPEED = 8.0;
    public static final double LONG_LOB_SPIKE_VELOCITY_Y = -4.5;

    // red 隊各角色相對基準點的位置
    public static final double RED_BACK_OFFSET_X = -300 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_SETTER_OFFSET_X = -30 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_QUICK_OFFSET_X = -5 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    public static final double RED_WING_OFFSET_X = -163.5 - PLAYER_IMAGE_WIDTH + PLAYER_NET_OVERLAP_X;
    // 待發球時圖片中心在畫面左緣外 10 像素。
    public static final double RED_BACK_SERVE_X = -10 - PLAYER_IMAGE_WIDTH / 2.0;
    public static final double RED_BACK_SERVE_Y = PLAYER_BASE_Y;

    // 待發球的球心維持原本位置，不隨後排發球員的站位往場內移動。
    public static final double RED_SERVE_BALL_X = BALL_RADIUS;
    public static final double RED_SERVE_BALL_OFFSET_Y = -5;

    // blue 隊各角色相對基準點的位置
    public static final double BLUE_BACK_OFFSET_X = 300 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_SETTER_OFFSET_X = 30 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_QUICK_OFFSET_X = 5 - PLAYER_NET_OVERLAP_X;
    public static final double BLUE_WING_OFFSET_X = 163.5 - PLAYER_NET_OVERLAP_X;
    // 待發球時圖片中心在畫面右緣外 10 像素。
    public static final double BLUE_BACK_SERVE_X = SCREEN_WIDTH + 10 - PLAYER_IMAGE_WIDTH / 2.0;
    public static final double BLUE_BACK_SERVE_Y = PLAYER_BASE_Y;

    public static final double BLUE_SERVE_BALL_X = SCREEN_WIDTH - BALL_RADIUS;
    public static final double BLUE_SERVE_BALL_OFFSET_Y = -5;

    // 發球 X 是正值速率，由隊伍方向轉成 vx；Y 是帶符號世界速度，負值往上。
    public static final double SERVE_NORMAL_HORIZONTAL_SPEED = 9;
    public static final double SERVE_NORMAL_VELOCITY_Y = -12;
    public static final double SERVE_CEILING_HORIZONTAL_SPEED = 5.1;
    public static final double SERVE_CEILING_VELOCITY_Y = -20;
    public static final double SERVE_LOW_NET_HORIZONTAL_SPEED = 14.8;
    public static final double SERVE_LOW_NET_VELOCITY_Y = -7;
    public static final double SERVE_SHORT_HORIZONTAL_SPEED = 6.4;
    public static final double SERVE_SHORT_VELOCITY_Y = -14.1;
    // 跳發出手速度；S／↓ 與 A／藍隊 → 兩種變化各自設定水平與垂直分量。
    public static final double SERVE_JUMP_HORIZONTAL_SPEED = 20.0;
    public static final double SERVE_JUMP_VELOCITY_Y = -1.0;
    public static final double SERVE_JUMP_SHORT_HORIZONTAL_SPEED = 17.2;
    public static final double SERVE_JUMP_SHORT_VELOCITY_Y = -1.0;
    public static final double SERVE_JUMP_LONG_VELOCITY_Y = -2.0;
    public static final double SERVE_JUMP_SLOW_HORIZONTAL_SPEED = 12.5;
    public static final double SERVE_JUMP_SLOW_VELOCITY_Y = -3.0;

    // 三米線距離（像素）；後排起跳扣球以起跳中心判定，踩線也違規。
    public static final double THREE_METER_PX = 167.0;

    // 跳發拋球的水平落點由後排起跳最高點的攻擊框中心計算；垂直初速由固定最高點計算。
    public static final double JUMP_SERVE_TOSS_LANDING_Y = FLOOR_Y - BALL_RADIUS;

    // 發球隨機誤差範圍
    public static final double SERVE_RANDOM_VX_RANGE = 0.7;
    public static final double SERVE_RANDOM_VY_RANGE = 0.5;

    /** 啟動時拒絕會讓物理或動畫計算失效的設定；不限制合法調參手感。 */
    public static void validate() {
        requirePositive("jump serve variant horizontal speeds", SERVE_JUMP_SHORT_HORIZONTAL_SPEED,
                SERVE_JUMP_SLOW_HORIZONTAL_SPEED);
        requirePositive("screen and physics", SCREEN_WIDTH, SCREEN_HEIGHT, TICKS_PER_SECOND,
                GRAVITY, BALL_RADIUS, PLAYER_IMAGE_WIDTH, PLAYER_IMAGE_HEIGHT, NET_WIDTH, NET_HEIGHT);
        requirePositive("court dimensions", WORLD_RIGHT - WORLD_LEFT, FLOOR_Y - WORLD_TOP,
                COURT_RIGHT_X - COURT_LEFT_X, COURT_LINE_WIDTH, THREE_METER_PX);
        requirePositive("jump velocity magnitude", -BACK_JUMP_VELOCITY_Y, -SETTER_JUMP_VELOCITY_Y,
                -MB_JUMP_VELOCITY_Y, -WS_JUMP_VELOCITY_Y);
        requirePositive("block multiplier", MB_REPEAT_BLOCK_JUMP_SPEED_MULTIPLIER);
        requireNonNegative("movement and bounce", BACK_RUN_START_SPEED, BACK_RUN_SECOND_STAGE_SPEED,
                BACK_RUN_THIRD_STAGE_SPEED, BACK_RUN_FOURTH_STAGE_SPEED, BACK_RUN_MAX_SPEED,
                BALL_BOUNCE, NET_SIDE_BOUNCE, NET_TOP_BOUNCE, BLOCK_HEAD_BOUNCE, BLOCK_HITBOX_BOUNCE);
        if (BACK_RUN_FIRST_STAGE_END_FRAMES <= 0
                || BACK_RUN_FIRST_STAGE_END_FRAMES >= BACK_RUN_SECOND_STAGE_END_FRAMES
                || BACK_RUN_SECOND_STAGE_END_FRAMES >= BACK_RUN_THIRD_STAGE_END_FRAMES
                || BACK_RUN_THIRD_STAGE_END_FRAMES >= BACK_RUN_FOURTH_STAGE_END_FRAMES
                || BACK_RUN_START_SPEED > BACK_RUN_SECOND_STAGE_SPEED
                || BACK_RUN_SECOND_STAGE_SPEED > BACK_RUN_THIRD_STAGE_SPEED
                || BACK_RUN_THIRD_STAGE_SPEED > BACK_RUN_FOURTH_STAGE_SPEED
                || BACK_RUN_FOURTH_STAGE_SPEED > BACK_RUN_MAX_SPEED) {
            throw new IllegalArgumentException("Invalid back-row acceleration stages");
        }
        if (NET_FLASH_COUNT <= 0
                || NET_FLASH_FRAMES < 2 * NET_FLASH_COUNT) {
            throw new IllegalArgumentException("Invalid action or flash duration");
        }
    }

    private static void requirePositive(String group, double... values) {
        for (double value : values) {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid " + group);
        }
    }

    private static void requireNonNegative(String group, double... values) {
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid " + group);
        }
    }
}
