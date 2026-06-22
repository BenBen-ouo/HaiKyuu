/*
 * UDP 遊戲資料定義。
 * TeamInput 會壓成 bitmask；CompactState 僅在過網或強制事件時以 UdpCodec 二進位傳送。
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
    public static final int PROTOCOL_VERSION = 3;

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

    public enum EventType {
        NET_CROSS,
        SERVE,
        NET_BOUNCE,
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
        mask |= (serveType & 0x3) << INPUT_SERVE_TYPE_SHIFT;
        return mask;
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
        input.serveType = typeOrdinal >= 0 && typeOrdinal < types.length
                ? types[typeOrdinal]
                : ServeType.NORMAL;
        return input;
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

        public final boolean spikeTrailActive;
        public final boolean spikeTrailRed;

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
                int matchOverCountdownFrames,
                boolean spikeTrailActive,
                boolean spikeTrailRed
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
            this.spikeTrailActive = spikeTrailActive;
            this.spikeTrailRed = spikeTrailRed;
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
                    model.matchOverCountdownFrames,
                    model.spikeEffect.isSpikeTrailActive(),
                    model.spikeEffect.getCurrentSpikeIsRed()
            );
        }

        public void applyTo(GameModel model) {
            ball.applyTo(model.ball);
            redTeam.applyTo(model.redTeam);
            blueTeam.applyTo(model.blueTeam);
            applyMetadataTo(model);
        }

        /*
         * 小誤差校正時，先同步比分、發球與回合規則，
         * 座標則交由 GameClient 在數個畫面幀內平滑拉回。
         */
        public void applyMetadataTo(GameModel model) {
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

            if (spikeTrailActive) {
                if (!model.spikeEffect.isSpikeTrailActive()
                        || model.spikeEffect.getCurrentSpikeIsRed() != spikeTrailRed) {
                    model.spikeEffect.startSpikeTrail(spikeTrailRed);
                }
            } else {
                model.spikeEffect.stopSpikeTrail();
            }
        }

        public void blendPositionsInto(GameModel model, double amount) {
            blendBall(model.ball, amount);
            redTeam.blendPositionsInto(model.redTeam, amount);
            blueTeam.blendPositionsInto(model.blueTeam, amount);
        }

        private void blendBall(Ball target, double amount) {
            target.x += (ball.x - target.x) * amount;
            target.y += (ball.y - target.y) * amount;
            target.vx += (ball.vx - target.vx) * amount;
            target.vy += (ball.vy - target.vy) * amount;
            target.rotationDegrees += (ball.rotationDegrees - target.rotationDegrees) * amount;
            target.rotationSpeed += (ball.rotationSpeed - target.rotationSpeed) * amount;
            target.setFastFloorBounceSpin(ball.fastFloorBounceSpin);
            target.syncPreviousPosition();
        }

        public double maxPositionDifference(GameModel model) {
            double max = distance(ball.x, ball.y, model.ball.x, model.ball.y);
            max = Math.max(max, redTeam.maxPositionDifference(model.redTeam));
            return Math.max(max, blueTeam.maxPositionDifference(model.blueTeam));
        }

        private static double distance(double x1, double y1, double x2, double y2) {
            double dx = x1 - x2;
            double dy = y1 - y2;
            return Math.sqrt(dx * dx + dy * dy);
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

        public double maxPositionDifference(Team team) {
            double max = 0;
            Player[] targets = team.getPlayers();
            for (int i = 0; i < targets.length && i < players.length; i++) {
                max = Math.max(max, players[i].positionDifference(targets[i]));
            }
            return max;
        }

        public void blendPositionsInto(Team team, double amount) {
            Player[] targets = team.getPlayers();
            for (int i = 0; i < targets.length && i < players.length; i++) {
                players[i].blendPositionInto(targets[i], amount);
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
                boolean attackHitBoxEnabled
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
                    player.attackHitBox.enabled
            );
        }

        public void applyTo(Player player) {
            player.assetName = assetName;
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

            PlayerAction[] actions = PlayerAction.values();
            player.setActionForNetwork(
                    actionOrdinal >= 0 && actionOrdinal < actions.length
                            ? actions[actionOrdinal]
                            : PlayerAction.IDLE
            );

            if (attackHitBoxEnabled) {
                player.attackHitBox.enable();
            } else {
                player.attackHitBox.disable();
            }
        }

        public double positionDifference(Player player) {
            double dx = x - player.x;
            double dy = y - player.y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        public void blendPositionInto(Player player, double amount) {
            player.x += (x - player.x) * amount;
            player.y += (y - player.y) * amount;
            player.vx += (vx - player.vx) * amount;
            player.vy += (vy - player.vy) * amount;
        }
    }

    public static int encodeNullableBoolean(Boolean value) {
        if (value == null) {
            return 0;
        }
        return value ? 1 : 2;
    }

    public static Boolean decodeNullableBoolean(int code) {
        if (code == 1) {
            return Boolean.TRUE;
        }
        if (code == 2) {
            return Boolean.FALSE;
        }
        return null;
    }
}
