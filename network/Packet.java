/*
UDP 遊戲資料定義。
TeamInput 會壓成 bitmask；完整狀態只在指定同步事件以 UdpCodec 二進位傳送。
*/
package network;

import model.Ball;
import model.GameModel;
import model.Player;
import model.PlayerAction;
import model.ServeState;
import model.ServeType;
import model.Team;
import model.TeamInput;

public final class Packet {
    public static final int INPUT_BACK_LEFT = 1 << 0;
    public static final int INPUT_BACK_RIGHT = 1 << 1;
    public static final int INPUT_BACK_JUMP = 1 << 2;
    public static final int INPUT_BACK_DIVE = 1 << 3;
    public static final int INPUT_SETTER_JUMP = 1 << 4;
    public static final int INPUT_QUICK_ATTACK = 1 << 5;
    public static final int INPUT_QUICK_BLOCK = 1 << 6;
    public static final int INPUT_WING_ATTACK = 1 << 7;
    public static final int INPUT_SPIKE_FLAT = 1 << 8;
    public static final int INPUT_SPIKE_SHORT = 1 << 9;
    public static final int INPUT_SPIKE_LOB = 1 << 10;
    public static final int INPUT_SERVE = 1 << 11;
    private static final int INPUT_SERVE_TYPE_SHIFT = 12;
    private static final int INPUT_SERVE_TYPE_MASK = 0x3 << INPUT_SERVE_TYPE_SHIFT;

    private Packet() {
    }

    /*
     * 可靠 COLLISION_EVENT 的事件類型。
     * 只保留目前指定的事件；攔網、撞網等額外事件暫不加入。
     */
    public enum EventType {
        SERVE,
        SETTER_CONTACT,
        LANDING,
        SCORE,
        RULE,
        RESET
    }

    public static int encodeInput(TeamInput input) {
        int mask = 0;
        if (input.backLeft) mask |= INPUT_BACK_LEFT;
        if (input.backRight) mask |= INPUT_BACK_RIGHT;
        if (input.backJump) mask |= INPUT_BACK_JUMP;
        if (input.backDive) mask |= INPUT_BACK_DIVE;
        if (input.setterJump) mask |= INPUT_SETTER_JUMP;
        if (input.quickAttack) mask |= INPUT_QUICK_ATTACK;
        if (input.quickBlock) mask |= INPUT_QUICK_BLOCK;
        if (input.wingAttack) mask |= INPUT_WING_ATTACK;
        if (input.spikeFlat) mask |= INPUT_SPIKE_FLAT;
        if (input.spikeShort) mask |= INPUT_SPIKE_SHORT;
        if (input.spikeLob) mask |= INPUT_SPIKE_LOB;
        if (input.servePressed) mask |= INPUT_SERVE;

        int serveType = input.serveType == null ? ServeType.NORMAL.ordinal() : input.serveType.ordinal();
        return mask | ((serveType & 0x3) << INPUT_SERVE_TYPE_SHIFT);
    }

    public static TeamInput decodeInput(int mask) {
        TeamInput input = new TeamInput();
        input.backLeft = (mask & INPUT_BACK_LEFT) != 0;
        input.backRight = (mask & INPUT_BACK_RIGHT) != 0;
        input.backJump = (mask & INPUT_BACK_JUMP) != 0;
        input.backDive = (mask & INPUT_BACK_DIVE) != 0;
        input.setterJump = (mask & INPUT_SETTER_JUMP) != 0;
        input.quickAttack = (mask & INPUT_QUICK_ATTACK) != 0;
        input.quickBlock = (mask & INPUT_QUICK_BLOCK) != 0;
        input.wingAttack = (mask & INPUT_WING_ATTACK) != 0;
        input.spikeFlat = (mask & INPUT_SPIKE_FLAT) != 0;
        input.spikeShort = (mask & INPUT_SPIKE_SHORT) != 0;
        input.spikeLob = (mask & INPUT_SPIKE_LOB) != 0;
        input.servePressed = (mask & INPUT_SERVE) != 0;

        int typeOrdinal = (mask & INPUT_SERVE_TYPE_MASK) >>> INPUT_SERVE_TYPE_SHIFT;
        ServeType[] types = ServeType.values();
        input.serveType = typeOrdinal < types.length ? types[typeOrdinal] : ServeType.NORMAL;
        return input;
    }

    /**
     * 不可靠 BALL_SNAPSHOT 使用的小型球狀態。
     * 不包含球員、比分或回合資料，避免高頻同步覆寫整個 GameModel。
     */
    public static final class BallSnapshot {
        public final BallState ball;
        public final int collisionRevision;

        public BallSnapshot(BallState ball, int collisionRevision) {
            this.ball = ball;
            this.collisionRevision = collisionRevision;
        }

        public static BallSnapshot from(GameModel model, int collisionRevision) {
            return new BallSnapshot(BallState.from(model.ball), collisionRevision);
        }
    }

    public static final class CompactState {
        public final BallState ball;
        public final TeamState redTeam;
        public final TeamState blueTeam;

        public final int redScore;
        public final int blueScore;
        public final int redHitCount;
        public final int blueHitCount;
        public final int redLastHitterIndex;
        public final int blueLastHitterIndex;
        public final int lastHitTeamCode;
        public final boolean lastTouchWasBlock;

        public final int serveStateOrdinal;
        public final boolean redServing;
        public final boolean rallyOver;
        public final int deadBallTimer;

        public final boolean matchOver;
        public final int matchWinnerCode;
        public final String transientMessage;
        public final int transientMessageTimer;
        public final int transientMessageColorCode;
        public final boolean pendingTouchOut;
        public final int pendingTouchOutWinnerCode;
        public final int matchOverCountdownFrames;

        public CompactState(
                BallState ball,
                TeamState redTeam,
                TeamState blueTeam,
                int redScore,
                int blueScore,
                int redHitCount,
                int blueHitCount,
                int redLastHitterIndex,
                int blueLastHitterIndex,
                int lastHitTeamCode,
                boolean lastTouchWasBlock,
                int serveStateOrdinal,
                boolean redServing,
                boolean rallyOver,
                int deadBallTimer,
                boolean matchOver,
                int matchWinnerCode,
                String transientMessage,
                int transientMessageTimer,
                int transientMessageColorCode,
                boolean pendingTouchOut,
                int pendingTouchOutWinnerCode,
                int matchOverCountdownFrames
        ) {
            this.ball = ball;
            this.redTeam = redTeam;
            this.blueTeam = blueTeam;
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.redHitCount = redHitCount;
            this.blueHitCount = blueHitCount;
            this.redLastHitterIndex = redLastHitterIndex;
            this.blueLastHitterIndex = blueLastHitterIndex;
            this.lastHitTeamCode = lastHitTeamCode;
            this.lastTouchWasBlock = lastTouchWasBlock;
            this.serveStateOrdinal = serveStateOrdinal;
            this.redServing = redServing;
            this.rallyOver = rallyOver;
            this.deadBallTimer = deadBallTimer;
            this.matchOver = matchOver;
            this.matchWinnerCode = matchWinnerCode;
            this.transientMessage = transientMessage;
            this.transientMessageTimer = transientMessageTimer;
            this.transientMessageColorCode = transientMessageColorCode;
            this.pendingTouchOut = pendingTouchOut;
            this.pendingTouchOutWinnerCode = pendingTouchOutWinnerCode;
            this.matchOverCountdownFrames = matchOverCountdownFrames;
        }

        public static CompactState from(GameModel model) {
            return new CompactState(
                    BallState.from(model.ball),
                    TeamState.from(model.redTeam),
                    TeamState.from(model.blueTeam),
                    model.redScore,
                    model.blueScore,
                    model.redHitCount,
                    model.blueHitCount,
                    model.getLastHitterIndexForNetwork(true),
                    model.getLastHitterIndexForNetwork(false),
                    encodeNullableBoolean(model.getLastHitTeam()),
                    model.wasLastTouchBlockForNetwork(),
                    model.getServeHandler().getState().ordinal(),
                    model.getServeHandler().isRedServing(),
                    model.isRallyOverForNetwork(),
                    model.getDeadBallTimerForNetwork(),
                    model.matchOver,
                    encodeNullableBoolean(model.matchWinnerRed),
                    model.transientMessage,
                    model.transientMessageTimer,
                    encodeNullableBoolean(model.transientMessageIsRed),
                    model.pendingTouchOut,
                    encodeNullableBoolean(model.pendingTouchOutWinner),
                    model.matchOverCountdownFrames
            );
        }

        public void applyTo(GameModel model) {
            ball.applyTo(model.ball);
            redTeam.applyTo(model.redTeam);
            blueTeam.applyTo(model.blueTeam);

            model.redScore = redScore;
            model.blueScore = blueScore;
            model.matchOver = matchOver;
            model.matchWinnerRed = decodeNullableBoolean(matchWinnerCode);
            model.transientMessage = transientMessage;
            model.transientMessageTimer = transientMessageTimer;
            model.transientMessageIsRed = decodeNullableBoolean(transientMessageColorCode);
            model.pendingTouchOut = pendingTouchOut;
            model.pendingTouchOutWinner = decodeNullableBoolean(pendingTouchOutWinnerCode);
            model.matchOverCountdownFrames = matchOverCountdownFrames;

            ServeState[] states = ServeState.values();
            ServeState serveState = serveStateOrdinal >= 0 && serveStateOrdinal < states.length
                    ? states[serveStateOrdinal]
                    : ServeState.READY;
            model.getServeHandler().applyNetworkState(serveState, redServing);
            model.applyNetworkRallyState(
                    redHitCount,
                    blueHitCount,
                    decodeNullableBoolean(lastHitTeamCode),
                    lastTouchWasBlock,
                    redLastHitterIndex,
                    blueLastHitterIndex,
                    rallyOver,
                    deadBallTimer
            );
            model.resumeNetworkPrediction();
        }
    }

    public static final class BallState {
        public final double x;
        public final double y;
        public final double vx;
        public final double vy;
        public final double radius;
        public final double rotationDegrees;
        public final double rotationSpeed;
        public final boolean fastFloorBounceSpin;

        public BallState(
                double x,
                double y,
                double vx,
                double vy,
                double radius,
                double rotationDegrees,
                double rotationSpeed,
                boolean fastFloorBounceSpin
        ) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.rotationDegrees = rotationDegrees;
            this.rotationSpeed = rotationSpeed;
            this.fastFloorBounceSpin = fastFloorBounceSpin;
        }

        public static BallState from(Ball ball) {
            return new BallState(
                    ball.x,
                    ball.y,
                    ball.vx,
                    ball.vy,
                    ball.radius,
                    ball.rotationDegrees,
                    ball.rotationSpeed,
                    ball.usesFastFloorBounceSpin()
            );
        }

        public void applyTo(Ball ball) {
            ball.x = x;
            ball.y = y;
            ball.vx = vx;
            ball.vy = vy;
            ball.radius = radius;
            ball.rotationDegrees = rotationDegrees;
            ball.rotationSpeed = rotationSpeed;
            ball.setFastFloorBounceSpin(fastFloorBounceSpin);
            ball.syncPreviousPosition();
        }
    }

    public static final class TeamState {
        public final PlayerState[] players;

        public TeamState(PlayerState[] players) {
            this.players = players;
        }

        public static TeamState from(Team team) {
            Player[] players = team.getPlayers();
            PlayerState[] states = new PlayerState[players.length];
            for (int i = 0; i < players.length; i++) {
                states[i] = PlayerState.from(players[i]);
            }
            return new TeamState(states);
        }

        public void applyTo(Team team) {
            Player[] targets = team.getPlayers();
            for (int i = 0; i < targets.length && i < players.length; i++) {
                players[i].applyTo(targets[i]);
            }
        }
    }

    public static final class PlayerState {
        public final String assetName;
        public final double x;
        public final double y;
        public final double vx;
        public final double vy;
        public final boolean jumping;
        public final boolean attacking;
        public final boolean blocking;
        public final boolean diving;
        public final boolean mirrorImage;
        public final double jumpStartX;
        public final int actionOrdinal;
        public final boolean attackHitBoxEnabled;

        // 一般觸球 hitBox 必須與角色動作一併由 Server 快照還原。
        public final double hitBoxOffsetX;
        public final double hitBoxOffsetY;
        public final double hitBoxWidth;
        public final double hitBoxHeight;
        public final int hitBoxArcWidth;
        public final int hitBoxArcHeight;
        public final double hitBoxRotationDegrees;

        public PlayerState(
                String assetName,
                double x,
                double y,
                double vx,
                double vy,
                boolean jumping,
                boolean attacking,
                boolean blocking,
                boolean diving,
                boolean mirrorImage,
                double jumpStartX,
                int actionOrdinal,
                boolean attackHitBoxEnabled,
                double hitBoxOffsetX,
                double hitBoxOffsetY,
                double hitBoxWidth,
                double hitBoxHeight,
                int hitBoxArcWidth,
                int hitBoxArcHeight,
                double hitBoxRotationDegrees
        ) {
            this.assetName = assetName;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.jumping = jumping;
            this.attacking = attacking;
            this.blocking = blocking;
            this.diving = diving;
            this.mirrorImage = mirrorImage;
            this.jumpStartX = jumpStartX;
            this.actionOrdinal = actionOrdinal;
            this.attackHitBoxEnabled = attackHitBoxEnabled;
            this.hitBoxOffsetX = hitBoxOffsetX;
            this.hitBoxOffsetY = hitBoxOffsetY;
            this.hitBoxWidth = hitBoxWidth;
            this.hitBoxHeight = hitBoxHeight;
            this.hitBoxArcWidth = hitBoxArcWidth;
            this.hitBoxArcHeight = hitBoxArcHeight;
            this.hitBoxRotationDegrees = hitBoxRotationDegrees;
        }

        public static PlayerState from(Player player) {
            return new PlayerState(
                    player.assetName,
                    player.x,
                    player.y,
                    player.vx,
                    player.vy,
                    player.jumping,
                    player.attacking,
                    player.blocking,
                    player.diving,
                    player.mirrorImage,
                    player.jumpStartX,
                    player.getAction().ordinal(),
                    player.attackHitBox.enabled,
                    player.hitBox.offsetX,
                    player.hitBox.offsetY,
                    player.hitBox.width,
                    player.hitBox.height,
                    player.hitBox.arcWidth,
                    player.hitBox.arcHeight,
                    player.hitBox.rotationDegrees
            );
        }

        public void applyTo(Player player) {
            player.x = x;
            player.y = y;
            player.vx = vx;
            player.vy = vy;
            player.jumping = jumping;
            player.attacking = attacking;
            player.blocking = blocking;
            player.diving = diving;
            player.mirrorImage = mirrorImage;
            player.jumpStartX = jumpStartX;
            player.hitBox.set(
                    hitBoxOffsetX,
                    hitBoxOffsetY,
                    hitBoxWidth,
                    hitBoxHeight,
                    hitBoxArcWidth,
                    hitBoxArcHeight,
                    hitBoxRotationDegrees
            );

            PlayerAction[] actions = PlayerAction.values();
            player.applyNetworkAction(
                    actionOrdinal >= 0 && actionOrdinal < actions.length
                            ? actions[actionOrdinal]
                            : PlayerAction.IDLE,
                    assetName
            );

            if (attackHitBoxEnabled) {
                player.attackHitBox.enable();
            } else {
                player.attackHitBox.disable();
            }
        }
    }

    public static int encodeNullableBoolean(Boolean value) {
        if (value == null) return 0;
        return value ? 1 : 2;
    }

    public static Boolean decodeNullableBoolean(int code) {
        if (code == 1) return Boolean.TRUE;
        if (code == 2) return Boolean.FALSE;
        return null;
    }
}
