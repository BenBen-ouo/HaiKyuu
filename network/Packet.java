/*
區域網路模式的序列化封包與遊戲狀態快照。
Client 只傳 Input；主機送回完整可繪製的 State，避免兩端各自模擬物理而失去同步。
*/
package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import model.AttackHitBox;
import model.Ball;
import model.EffectManager;
import model.GameModel;
import model.Player;
import model.PlayerAction;
import model.ServeType;
import model.SpikeEffect;
import model.Team;
import model.TeamInput;
import model.VisualEffect;

public final class Packet {
    public static final int PROTOCOL_VERSION = 1;

    private Packet() {
    }

    public interface Message extends Serializable {
    }

    public static final class Hello implements Message {
        private static final long serialVersionUID = 1L;
        public final int protocolVersion;

        public Hello(int protocolVersion) {
            this.protocolVersion = protocolVersion;
        }
    }

    public static final class Welcome implements Message {
        private static final long serialVersionUID = 1L;
        public final boolean assignedBluePlayer;
        public final String message;

        public Welcome(boolean assignedBluePlayer, String message) {
            this.assignedBluePlayer = assignedBluePlayer;
            this.message = message;
        }
    }

    public static final class Error implements Message {
        private static final long serialVersionUID = 1L;
        public final String message;

        public Error(String message) {
            this.message = message;
        }
    }

    public static final class Input implements Message {
        private static final long serialVersionUID = 1L;
        public final TeamInputState teamInput;
        public final boolean restartDown;
        public final boolean cancelResetDown;

        public Input(TeamInputState teamInput, boolean restartDown, boolean cancelResetDown) {
            this.teamInput = teamInput;
            this.restartDown = restartDown;
            this.cancelResetDown = cancelResetDown;
        }
    }

    public static final class State implements Message {
        private static final long serialVersionUID = 1L;
        public final GameState gameState;
        public final boolean redResetConfirmed;
        public final boolean blueResetConfirmed;

        public State(GameState gameState, boolean redResetConfirmed, boolean blueResetConfirmed) {
            this.gameState = gameState;
            this.redResetConfirmed = redResetConfirmed;
            this.blueResetConfirmed = blueResetConfirmed;
        }
    }

    public static final class TeamInputState implements Serializable {
        private static final long serialVersionUID = 1L;

        public boolean backLeft;
        public boolean backRight;
        public boolean backJump;
        public boolean backDive;
        public boolean setterJump;
        public boolean quickAttack;
        public boolean quickBlock;
        public boolean wingAttack;
        public boolean spikeFlat;
        public boolean spikeShort;
        public boolean spikeLob;
        public boolean servePressed;
        public ServeType serveType;

        public static TeamInputState from(TeamInput input) {
            TeamInputState state = new TeamInputState();
            state.backLeft = input.backLeft;
            state.backRight = input.backRight;
            state.backJump = input.backJump;
            state.backDive = input.backDive;
            state.setterJump = input.setterJump;
            state.quickAttack = input.quickAttack;
            state.quickBlock = input.quickBlock;
            state.wingAttack = input.wingAttack;
            state.spikeFlat = input.spikeFlat;
            state.spikeShort = input.spikeShort;
            state.spikeLob = input.spikeLob;
            state.servePressed = input.servePressed;
            state.serveType = input.serveType;
            return state;
        }

        public static TeamInputState empty() {
            return from(new TeamInput());
        }

        public TeamInput toTeamInput() {
            TeamInput input = new TeamInput();
            input.backLeft = backLeft;
            input.backRight = backRight;
            input.backJump = backJump;
            input.backDive = backDive;
            input.setterJump = setterJump;
            input.quickAttack = quickAttack;
            input.quickBlock = quickBlock;
            input.wingAttack = wingAttack;
            input.spikeFlat = spikeFlat;
            input.spikeShort = spikeShort;
            input.spikeLob = spikeLob;
            input.servePressed = servePressed;
            input.serveType = serveType == null ? ServeType.NORMAL : serveType;
            return input;
        }
    }

    public static final class GameState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final BallState ball;
        public final TeamState redTeam;
        public final TeamState blueTeam;
        public final int redScore;
        public final int blueScore;
        public final int redHitCount;
        public final int blueHitCount;
        public final boolean matchOver;
        public final Boolean matchWinnerRed;
        public final String transientMessage;
        public final int transientMessageTimer;
        public final Boolean transientMessageIsRed;
        public final List<VisualEffectState> effects;
        public final SpikeEffectState spikeEffect;

        private GameState(
                BallState ball,
                TeamState redTeam,
                TeamState blueTeam,
                int redScore,
                int blueScore,
                int redHitCount,
                int blueHitCount,
                boolean matchOver,
                Boolean matchWinnerRed,
                String transientMessage,
                int transientMessageTimer,
                Boolean transientMessageIsRed,
                List<VisualEffectState> effects,
                SpikeEffectState spikeEffect
        ) {
            this.ball = ball;
            this.redTeam = redTeam;
            this.blueTeam = blueTeam;
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.redHitCount = redHitCount;
            this.blueHitCount = blueHitCount;
            this.matchOver = matchOver;
            this.matchWinnerRed = matchWinnerRed;
            this.transientMessage = transientMessage;
            this.transientMessageTimer = transientMessageTimer;
            this.transientMessageIsRed = transientMessageIsRed;
            this.effects = effects;
            this.spikeEffect = spikeEffect;
        }

        public static GameState from(GameModel model) {
            List<VisualEffectState> effectStates = new ArrayList<>();
            for (VisualEffect effect : model.effects.getEffects()) {
                effectStates.add(VisualEffectState.from(effect));
            }

            return new GameState(
                    BallState.from(model.ball),
                    TeamState.from(model.redTeam),
                    TeamState.from(model.blueTeam),
                    model.redScore,
                    model.blueScore,
                    model.redHitCount,
                    model.blueHitCount,
                    model.matchOver,
                    model.matchWinnerRed,
                    model.transientMessage,
                    model.transientMessageTimer,
                    model.transientMessageIsRed,
                    effectStates,
                    SpikeEffectState.from(model.spikeEffect)
            );
        }

        public void applyTo(GameModel model) {
            ball.applyTo(model.ball);
            redTeam.applyTo(model.redTeam);
            blueTeam.applyTo(model.blueTeam);

            model.redScore = redScore;
            model.blueScore = blueScore;
            model.redHitCount = redHitCount;
            model.blueHitCount = blueHitCount;
            model.matchOver = matchOver;
            model.matchWinnerRed = matchWinnerRed;
            model.transientMessage = transientMessage;
            model.transientMessageTimer = transientMessageTimer;
            model.transientMessageIsRed = transientMessageIsRed;

            restoreEffects(model.effects, effects);
            spikeEffect.applyTo(model.spikeEffect);
        }

        private static void restoreEffects(EffectManager target, List<VisualEffectState> states) {
            target.clear();
            for (VisualEffectState state : states) {
                target.getEffects().add(state.toVisualEffect());
            }
        }
    }

    public static final class BallState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final double x;
        public final double y;
        public final double vx;
        public final double vy;
        public final double radius;
        public final double rotationDegrees;
        public final double rotationSpeed;

        private BallState(
                double x,
                double y,
                double vx,
                double vy,
                double radius,
                double rotationDegrees,
                double rotationSpeed
        ) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.rotationDegrees = rotationDegrees;
            this.rotationSpeed = rotationSpeed;
        }

        public static BallState from(Ball ball) {
            return new BallState(
                    ball.x,
                    ball.y,
                    ball.vx,
                    ball.vy,
                    ball.radius,
                    ball.rotationDegrees,
                    ball.rotationSpeed
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
        }
    }

    public static final class TeamState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final PlayerState backPlayer;
        public final PlayerState setter;
        public final PlayerState quickAttacker;
        public final PlayerState wingSpiker;

        private TeamState(
                PlayerState backPlayer,
                PlayerState setter,
                PlayerState quickAttacker,
                PlayerState wingSpiker
        ) {
            this.backPlayer = backPlayer;
            this.setter = setter;
            this.quickAttacker = quickAttacker;
            this.wingSpiker = wingSpiker;
        }

        public static TeamState from(Team team) {
            return new TeamState(
                    PlayerState.from(team.backPlayer),
                    PlayerState.from(team.setter),
                    PlayerState.from(team.quickAttacker),
                    PlayerState.from(team.wingSpiker)
            );
        }

        public void applyTo(Team team) {
            backPlayer.applyTo(team.backPlayer);
            setter.applyTo(team.setter);
            quickAttacker.applyTo(team.quickAttacker);
            wingSpiker.applyTo(team.wingSpiker);
        }
    }

    public static final class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;

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
        public final PlayerAction action;
        public final HitBoxState hitBox;
        public final AttackHitBoxState attackHitBox;

        private PlayerState(
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
                PlayerAction action,
                HitBoxState hitBox,
                AttackHitBoxState attackHitBox
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
            this.action = action;
            this.hitBox = hitBox;
            this.attackHitBox = attackHitBox;
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
                    player.getAction(),
                    HitBoxState.from(player),
                    AttackHitBoxState.from(player.attackHitBox)
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
            player.setActionForNetwork(action);
            hitBox.applyTo(player);
            attackHitBox.applyTo(player.attackHitBox);
        }
    }

    public static final class HitBoxState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final double offsetX;
        public final double offsetY;
        public final double width;
        public final double height;
        public final int arcWidth;
        public final int arcHeight;
        public final double rotationDegrees;

        private HitBoxState(
                double offsetX,
                double offsetY,
                double width,
                double height,
                int arcWidth,
                int arcHeight,
                double rotationDegrees
        ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
            this.arcWidth = arcWidth;
            this.arcHeight = arcHeight;
            this.rotationDegrees = rotationDegrees;
        }

        public static HitBoxState from(Player player) {
            return new HitBoxState(
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
            player.hitBox.set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees);
        }
    }

    public static final class AttackHitBoxState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final boolean enabled;
        public final double offsetX;
        public final double offsetY;
        public final double width;
        public final double height;

        private AttackHitBoxState(boolean enabled, double offsetX, double offsetY, double width, double height) {
            this.enabled = enabled;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
        }

        public static AttackHitBoxState from(AttackHitBox attackHitBox) {
            return new AttackHitBoxState(
                    attackHitBox.enabled,
                    attackHitBox.offsetX,
                    attackHitBox.offsetY,
                    attackHitBox.width,
                    attackHitBox.height
            );
        }

        public void applyTo(AttackHitBox attackHitBox) {
            attackHitBox.set(offsetX, offsetY, width, height);
            if (enabled) {
                attackHitBox.enable();
            } else {
                attackHitBox.disable();
            }
        }
    }

    public static final class VisualEffectState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String assetName;
        public final double x;
        public final double y;
        public final int remainingFrames;

        private VisualEffectState(String assetName, double x, double y, int remainingFrames) {
            this.assetName = assetName;
            this.x = x;
            this.y = y;
            this.remainingFrames = remainingFrames;
        }

        public static VisualEffectState from(VisualEffect effect) {
            return new VisualEffectState(effect.assetName, effect.x, effect.y, effect.remainingFrames);
        }

        public VisualEffect toVisualEffect() {
            return new VisualEffect(assetName, x, y, remainingFrames);
        }
    }

    public static final class SpikeEffectState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final boolean trailActive;
        public final boolean currentSpikeRed;
        public final List<TrailPointState> trailPoints;
        public final List<SmokeParticleState> smokeParticles;

        private SpikeEffectState(
                boolean trailActive,
                boolean currentSpikeRed,
                List<TrailPointState> trailPoints,
                List<SmokeParticleState> smokeParticles
        ) {
            this.trailActive = trailActive;
            this.currentSpikeRed = currentSpikeRed;
            this.trailPoints = trailPoints;
            this.smokeParticles = smokeParticles;
        }

        public static SpikeEffectState from(SpikeEffect spikeEffect) {
            List<TrailPointState> trails = new ArrayList<>();
            for (SpikeEffect.TrailPoint point : spikeEffect.getTrailPoints()) {
                trails.add(TrailPointState.from(point));
            }

            List<SmokeParticleState> smoke = new ArrayList<>();
            for (SpikeEffect.SmokeParticle particle : spikeEffect.getSmokeParticles()) {
                smoke.add(SmokeParticleState.from(particle));
            }

            return new SpikeEffectState(
                    spikeEffect.isSpikeTrailActive(),
                    spikeEffect.getCurrentSpikeIsRed(),
                    trails,
                    smoke
            );
        }

        public void applyTo(SpikeEffect spikeEffect) {
            spikeEffect.clear();
            if (trailActive) {
                spikeEffect.startSpikeTrail(currentSpikeRed);
            }

            for (TrailPointState point : trailPoints) {
                spikeEffect.getTrailPoints().add(point.toTrailPoint());
            }

            for (SmokeParticleState particle : smokeParticles) {
                spikeEffect.getSmokeParticles().add(particle.toSmokeParticle());
            }
        }
    }

    public static final class TrailPointState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final double x;
        public final double y;
        public final int remainingFrames;
        public final int maxFrames;
        public final boolean redTeam;

        private TrailPointState(double x, double y, int remainingFrames, int maxFrames, boolean redTeam) {
            this.x = x;
            this.y = y;
            this.remainingFrames = remainingFrames;
            this.maxFrames = maxFrames;
            this.redTeam = redTeam;
        }

        public static TrailPointState from(SpikeEffect.TrailPoint point) {
            return new TrailPointState(
                    point.x,
                    point.y,
                    point.remainingFrames,
                    point.maxFrames,
                    point.isRedTeam
            );
        }

        public SpikeEffect.TrailPoint toTrailPoint() {
            SpikeEffect.TrailPoint point = new SpikeEffect.TrailPoint(x, y, maxFrames, redTeam);
            point.remainingFrames = remainingFrames;
            return point;
        }
    }

    public static final class SmokeParticleState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final double x;
        public final double y;
        public final double vx;
        public final double vy;
        public final double startRadius;
        public final double currentRadius;
        public final double maxRadius;
        public final int remainingFrames;
        public final int maxFrames;
        public final int blobCount;
        public final float[] ox;
        public final float[] oy;
        public final float[] br;
        public final float[] ba;
        public final float[] rot;
        public final float[][] shapeOffset;

        private SmokeParticleState(SpikeEffect.SmokeParticle particle) {
            this.x = particle.x;
            this.y = particle.y;
            this.vx = particle.vx;
            this.vy = particle.vy;
            this.startRadius = particle.startRadius;
            this.currentRadius = particle.currentRadius;
            this.maxRadius = particle.maxRadius;
            this.remainingFrames = particle.remainingFrames;
            this.maxFrames = particle.maxFrames;
            this.blobCount = particle.blobCount;
            this.ox = particle.ox.clone();
            this.oy = particle.oy.clone();
            this.br = particle.br.clone();
            this.ba = particle.ba.clone();
            this.rot = particle.rot.clone();
            this.shapeOffset = cloneShapeOffset(particle.shapeOffset);
        }

        public static SmokeParticleState from(SpikeEffect.SmokeParticle particle) {
            return new SmokeParticleState(particle);
        }

        public SpikeEffect.SmokeParticle toSmokeParticle() {
            SpikeEffect.SmokeParticle particle = new SpikeEffect.SmokeParticle(
                    x,
                    y,
                    vx,
                    vy,
                    startRadius,
                    maxRadius,
                    maxFrames
            );
            particle.currentRadius = currentRadius;
            particle.remainingFrames = remainingFrames;
            particle.blobCount = blobCount;
            particle.ox = ox.clone();
            particle.oy = oy.clone();
            particle.br = br.clone();
            particle.ba = ba.clone();
            particle.rot = rot.clone();
            particle.shapeOffset = cloneShapeOffset(shapeOffset);
            return particle;
        }

        private static float[][] cloneShapeOffset(float[][] source) {
            float[][] copy = new float[source.length][];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i].clone();
            }
            return copy;
        }
    }
}
