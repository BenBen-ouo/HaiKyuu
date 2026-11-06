/*
UDP 遊戲資料定義。
ControlInput 會壓成 bitmask；即時快照傳球、球員與觸球狀態，裁決階段只在可靠事件中傳送。
*/
package network;

import java.util.List;

import model.GameModel;
import model.rally.RallyContactSnapshot;
import model.ControlInput;
import model.ball.Ball;
import model.player.Player;
import model.player.BackPlayer;
import model.player.PlayerAction;
import model.player.QuickAttacker;
import model.player.Team;
import model.serve.ServeState;
import model.serve.ServeType;

public final class Packet {
    public static final int INPUT_BACK_LEFT = 1 << 0;
    public static final int INPUT_BACK_RIGHT = 1 << 1;
    public static final int INPUT_BACK_JUMP = 1 << 2;
    public static final int INPUT_BACK_DIVE = 1 << 3;
    public static final int INPUT_SETTER_JUMP = 1 << 4;
    public static final int INPUT_MB_ACTION = 1 << 5;
    public static final int INPUT_AIR_SET_MODIFIER = 1 << 6;
    public static final int INPUT_WS_ACTION = 1 << 7;
    public static final int INPUT_SPIKE_FLAT = 1 << 8;
    public static final int INPUT_SPIKE_SHORT = 1 << 9;
    public static final int INPUT_SPIKE_LOB = 1 << 10;
    public static final int INPUT_SERVE = 1 << 11;
    private static final int INPUT_SERVE_TYPE_SHIFT = 12;
    private static final int INPUT_SERVE_TYPE_MASK = 0x3 << INPUT_SERVE_TYPE_SHIFT;
    private static final PlayerAction[] PLAYER_ACTIONS = PlayerAction.values();
    private static final ServeType[] SERVE_TYPES = ServeType.values();
    private static final ServeState[] SERVE_STATES = ServeState.values();

    private Packet() {
    }

    /*
     * 可靠 COLLISION_EVENT 的事件類型。
     * 落地煙霧與碰網閃爍由權威事件確認。
     */
    public enum EventType {
        SERVE,
        SETTER_CONTACT,
        RECEPTION,
        LANDING,
        SCORE,
        RULE,
        RESET,
        FLOW,
        AIR_SET_CONTACT,
        JUMP_TOSS,
        NET_CONTACT
    }

    public static int encodeInput(ControlInput input) {
        int mask = 0;
        if (input.backLeft) mask |= INPUT_BACK_LEFT;
        if (input.backRight) mask |= INPUT_BACK_RIGHT;
        if (input.backJump) mask |= INPUT_BACK_JUMP;
        if (input.backDive) mask |= INPUT_BACK_DIVE;
        if (input.setterJump) mask |= INPUT_SETTER_JUMP;
        if (input.mbActionPressed) mask |= INPUT_MB_ACTION;
        if (input.airSetModifier) mask |= INPUT_AIR_SET_MODIFIER;
        if (input.wingActionPressed) mask |= INPUT_WS_ACTION;
        if (input.spikeFlat) mask |= INPUT_SPIKE_FLAT;
        if (input.spikeShort) mask |= INPUT_SPIKE_SHORT;
        if (input.spikeLob) mask |= INPUT_SPIKE_LOB;
        if (input.servePressed) mask |= INPUT_SERVE;

        int serveType = input.serveType == null ? ServeType.NORMAL.ordinal() : input.serveType.ordinal();
        return mask | ((serveType & 0x3) << INPUT_SERVE_TYPE_SHIFT);
    }

    public static ControlInput decodeInput(int mask) {
        ControlInput input = new ControlInput();
        input.backLeft = (mask & INPUT_BACK_LEFT) != 0;
        input.backRight = (mask & INPUT_BACK_RIGHT) != 0;
        input.backJump = (mask & INPUT_BACK_JUMP) != 0;
        input.backDive = (mask & INPUT_BACK_DIVE) != 0;
        input.setterJump = (mask & INPUT_SETTER_JUMP) != 0;
        input.mbActionPressed = (mask & INPUT_MB_ACTION) != 0;
        input.airSetModifier = (mask & INPUT_AIR_SET_MODIFIER) != 0;
        input.wingActionPressed = (mask & INPUT_WS_ACTION) != 0;
        input.spikeFlat = (mask & INPUT_SPIKE_FLAT) != 0;
        input.spikeShort = (mask & INPUT_SPIKE_SHORT) != 0;
        input.spikeLob = (mask & INPUT_SPIKE_LOB) != 0;
        input.servePressed = (mask & INPUT_SERVE) != 0;

        int typeOrdinal = (mask & INPUT_SERVE_TYPE_MASK) >>> INPUT_SERVE_TYPE_SHIFT;
        ServeType[] types = SERVE_TYPES;
        input.serveType = typeOrdinal < types.length ? types[typeOrdinal] : ServeType.NORMAL;
        return input;
    }

    /** 每 tick 的即時物理與觸球快照；得分及階段裁決仍只由可靠事件同步。 */
    public static final class WorldSnapshot {
        public final BallState ball;
        public final TeamState redTeam;
        public final TeamState blueTeam;
        public final RallyContactState rallyContacts;
        public final int collisionRevision;
        public final boolean spikeTrailActive;
        public final boolean spikeTrailRedSide;
        public final int netFlashFrames;

        public WorldSnapshot(BallState ball, TeamState redTeam, TeamState blueTeam,
                             RallyContactState rallyContacts,
                             int collisionRevision, boolean spikeTrailActive, boolean spikeTrailRedSide,
                             int netFlashFrames) {
            this.ball = ball;
            this.redTeam = redTeam;
            this.blueTeam = blueTeam;
            this.rallyContacts = rallyContacts;
            this.collisionRevision = collisionRevision;
            this.spikeTrailActive = spikeTrailActive;
            this.spikeTrailRedSide = spikeTrailRedSide;
            this.netFlashFrames = netFlashFrames;
        }

        public static WorldSnapshot from(GameModel model, int collisionRevision) {
            return new WorldSnapshot(
                    BallState.from(model.ball),
                    TeamState.from(model.redTeam),
                    TeamState.from(model.blueTeam),
                    RallyContactState.from(model),
                    collisionRevision,
                    model.spikeEffect.isSpikeTrailActive(),
                    model.spikeEffect.getCurrentSpikeIsRed(), model.netFlashFrames
            );
        }
    }

    /** 觸球相關狀態供每 tick 校正，也供可靠事件完整還原。 */
    public static final class RallyContactState {
        public final int redHitCount;
        public final int blueHitCount;
        public final int redLastHitterIndex;
        public final int blueLastHitterIndex;
        public final int lastHitTeamCode;
        public final boolean lastTouchWasBlock;
        public final boolean serveReceptionComplete;
        public final boolean redSetterTouched;
        public final boolean blueSetterTouched;
        public final boolean redSetterTouchedFirst;
        public final boolean blueSetterTouchedFirst;
        public final boolean redBlockUsed;
        public final boolean blueBlockUsed;
        public final boolean redAttackLocked;
        public final boolean blueAttackLocked;

        public RallyContactState(RallyContactSnapshot snapshot) {
            redHitCount = snapshot.red().hitCount();
            blueHitCount = snapshot.blue().hitCount();
            redLastHitterIndex = snapshot.red().lastHitterIndex();
            blueLastHitterIndex = snapshot.blue().lastHitterIndex();
            lastHitTeamCode = encodeNullableBoolean(snapshot.lastHitTeam());
            lastTouchWasBlock = snapshot.lastTouchWasBlock();
            serveReceptionComplete = snapshot.serveReceptionComplete();
            redSetterTouched = snapshot.red().setterTouched();
            blueSetterTouched = snapshot.blue().setterTouched();
            redSetterTouchedFirst = snapshot.red().setterTouchedFirst();
            blueSetterTouchedFirst = snapshot.blue().setterTouchedFirst();
            redBlockUsed = snapshot.red().blockUsed();
            blueBlockUsed = snapshot.blue().blockUsed();
            redAttackLocked = snapshot.red().attackLocked();
            blueAttackLocked = snapshot.blue().attackLocked();
        }

        public static RallyContactState from(GameModel model) {
            return new RallyContactState(model.captureRallyContacts());
        }

        public void applyTo(GameModel model) {
            model.applyNetworkRallyContactState(new RallyContactSnapshot(
                    new RallyContactSnapshot.TeamContacts(redHitCount, redLastHitterIndex, redSetterTouched,
                            redSetterTouchedFirst, redBlockUsed, redAttackLocked),
                    new RallyContactSnapshot.TeamContacts(blueHitCount, blueLastHitterIndex, blueSetterTouched,
                            blueSetterTouchedFirst, blueBlockUsed, blueAttackLocked),
                    decodeNullableBoolean(lastHitTeamCode), lastTouchWasBlock, serveReceptionComplete));
        }
    }

    public static final class CompactState {
        public final BallState ball;
        public final TeamState redTeam;
        public final TeamState blueTeam;

        public final int redScore;
        public final int blueScore;
        public final RallyContactState rallyContacts;

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
        public final int netFlashFrames;
        public final boolean landingSmoke;

        public CompactState(
                BallState ball,
                TeamState redTeam,
                TeamState blueTeam,
                int redScore,
                int blueScore,
                RallyContactState rallyContacts,
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
                int netFlashFrames, boolean landingSmoke
        ) {
            this.ball = ball;
            this.redTeam = redTeam;
            this.blueTeam = blueTeam;
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.rallyContacts = rallyContacts;
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
            this.netFlashFrames = netFlashFrames;
            this.landingSmoke = landingSmoke;
        }

        public static CompactState from(GameModel model) {
            return new CompactState(
                    BallState.from(model.ball),
                    TeamState.from(model.redTeam),
                    TeamState.from(model.blueTeam),
                    model.redScore,
                    model.blueScore,
                    RallyContactState.from(model),
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
                    model.netFlashFrames, model.landingSmokeThisFrame
            );
        }

        public void applyTo(GameModel model) {
            applyTo(model, false);
        }

        /** Client 回合事件同步裁決，但不停止仍在本地預測的角色動畫。 */
        public void applyToForClient(GameModel model) {
            applyTo(model, true);
        }

        private void applyTo(GameModel model, boolean preserveAnimations) {
            model.netFlashFrames = netFlashFrames;
            ball.applyTo(model.ball);
            if (preserveAnimations) {
                redTeam.applyMotionTo(model.redTeam);
                blueTeam.applyMotionTo(model.blueTeam);
            } else {
                redTeam.applyTo(model.redTeam);
                blueTeam.applyTo(model.blueTeam);
            }

            model.redScore = redScore;
            model.blueScore = blueScore;
            model.matchOver = matchOver;
            model.matchWinnerRed = decodeNullableBoolean(matchWinnerCode);
            model.transientMessage = transientMessage;
            model.transientMessageTimer = transientMessageTimer;
            model.transientMessageIsRed = decodeNullableBoolean(transientMessageColorCode);
            model.pendingTouchOut = pendingTouchOut;
            model.pendingTouchOutWinner = decodeNullableBoolean(pendingTouchOutWinnerCode);

            ServeState[] states = SERVE_STATES;
            ServeState serveState = states[serveStateOrdinal];
            model.getServeHandler().applyNetworkState(serveState, redServing);
            rallyContacts.applyTo(model);
            model.applyNetworkRallyPhase(rallyOver, deadBallTimer);
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
        public final List<PlayerState> players;

        public TeamState(PlayerState[] players) {
            this.players = List.of(players);
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
            for (int i = 0; i < targets.length && i < players.size(); i++) {
                players.get(i).applyTo(targets[i]);
            }
        }

        public void applyMotionTo(Team team) {
            Player[] targets = team.getPlayers();
            for (int i = 0; i < targets.length && i < players.size(); i++) {
                players.get(i).applyMotionTo(targets[i]);
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
        public final boolean diving;
        public final boolean mirrorImage;
        public final double jumpStartCenterX;
        public final int actionOrdinal;
        public final int animationFrameIndex;
        public final boolean attackHitBoxEnabled;
        public final int countedBlockJumps;
        public final boolean opponentFirstTouchSeen;

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
                boolean diving,
                boolean mirrorImage,
                double jumpStartCenterX,
                int actionOrdinal,
                int animationFrameIndex,
                boolean attackHitBoxEnabled,
                int countedBlockJumps,
                boolean opponentFirstTouchSeen,
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
            this.diving = diving;
            this.mirrorImage = mirrorImage;
            this.jumpStartCenterX = jumpStartCenterX;
            this.actionOrdinal = actionOrdinal;
            this.animationFrameIndex = animationFrameIndex;
            this.attackHitBoxEnabled = attackHitBoxEnabled;
            this.countedBlockJumps = countedBlockJumps;
            this.opponentFirstTouchSeen = opponentFirstTouchSeen;
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
                    player.diving,
                    player.mirrorImage,
                    player.jumpStartCenterX,
                    player.getAction().ordinal(),
                    player.getAnimationFrameIndex(),
                    player.attackHitBox.enabled,
                    player instanceof QuickAttacker mb ? mb.getCountedBlockJumps() : 0,
                    player instanceof QuickAttacker mb ? mb.hasSeenOpponentFirstTouch() : false,
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
            applyMotionTo(player);

            if (player instanceof QuickAttacker) {
                return;
            }

            PlayerAction[] actions = PLAYER_ACTIONS;
            player.applyNetworkAction(
                    actions[actionOrdinal],
                    assetName, animationFrameIndex
            );
        }

        /** 一般角色保留 Client 動畫；MB 的動作、圖片與攻擊框須採同一份 Server 狀態。 */
        public void applyMotionTo(Player player) {
            player.x = x;
            player.y = y;
            player.vx = vx;
            player.vy = vy;
            player.jumping = jumping;
            player.diving = diving;
            player.mirrorImage = mirrorImage;
            player.jumpStartCenterX = jumpStartCenterX;
            if (player instanceof QuickAttacker mb) {
                mb.applyBlockJumpState(countedBlockJumps, opponentFirstTouchSeen);
            }
            if (player instanceof BackPlayer backPlayer && jumping) {
                backPlayer.applyJumpVelocityX(vx);
            }
            player.hitBox.set(
                    hitBoxOffsetX,
                    hitBoxOffsetY,
                    hitBoxWidth,
                    hitBoxHeight,
                    hitBoxArcWidth,
                    hitBoxArcHeight,
                    hitBoxRotationDegrees
            );
            PlayerAction[] actions = PLAYER_ACTIONS;
            PlayerAction serverAction = actions[actionOrdinal];
            if (player instanceof QuickAttacker || player.getAction() != serverAction
                    || !player.isAnimationPlaying()) {
                player.applyNetworkAction(serverAction, assetName, animationFrameIndex);
            }

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
