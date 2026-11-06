package view;

import java.util.ArrayList;
import java.util.List;
import model.GameModel;
import model.effect.SpikeEffect;
import model.player.HitBox;
import model.player.Player;
import model.player.QuickAttacker;
import network.NetworkView;

/** 由更新執行緒擷取後發布給 EDT；內容不持有可變模型或網路物件。 */
public record RenderFrame(
        List<PlayerImage> players, BallImage ball, List<TrailPoint> trail,
        List<Smoke> smoke, Match match, Net net, int netFlashFrames,
        boolean mirrorWorld, Connection connection) {

    public RenderFrame {
        players = List.copyOf(players);
        trail = List.copyOf(trail);
        smoke = List.copyOf(smoke);
    }

    public static RenderFrame capture(GameModel model, NetworkView network) {
        List<PlayerImage> players = new ArrayList<>(8);
        // 後排永遠位於其他球員上方。
        for (var team : List.of(model.redTeam, model.blueTeam)) {
            players.add(capturePlayer(team.wingSpiker, network));
            players.add(capturePlayer(team.setter, network));
            players.add(capturePlayer(team.quickAttacker, network));
        }
        players.add(capturePlayer(model.redTeam.backPlayer, network));
        players.add(capturePlayer(model.blueTeam.backPlayer, network));
        List<TrailPoint> trail = model.spikeEffect.getTrailPoints().stream()
                .map(point -> new TrailPoint(point.x, point.y, point.remainingFrames, point.maxFrames, point.isRedTeam))
                .toList();
        List<Smoke> smoke = model.spikeEffect.getSmokeParticles().stream().map(RenderFrame::captureSmoke).toList();
        var net = model.netHitBox;
        return new RenderFrame(players,
                new BallImage(model.ball.x, model.ball.y, model.ball.radius, model.ball.rotationDegrees,
                        !model.isLockedScorePhase()), trail, smoke,
                new Match(model.isPracticeMode(), model.isPracticeRedServing(), model.redScore, model.blueScore,
                        model.matchOver, model.matchWinnerRed, model.transientMessage,
                        model.transientMessageTimer, model.transientMessageIsRed),
                new Net(net.getLeft(), net.getTop(), net.getRight() - net.getLeft(),
                        net.getBottom() - net.getTop(), net.getTopRadius()),
                model.netFlashFrames, network != null && network.isBluePerspective(),
                network == null ? null : new Connection(network.getConnectionMessage(),
                        network.getResetMessage(), network.isConnected()));
    }

    private static PlayerImage capturePlayer(Player player, NetworkView network) {
        Box regular = player.isDefaultHitBoxActive() ? Box.from(player.hitBox) : null;
        Box block = null;
        Box head = null;
        if (player instanceof QuickAttacker mb) {
            if (mb.isBlockHitBoxActive()) block = Box.from(mb.blockHitBox);
            if (mb.isHeadBlockHitBoxActive()) head = Box.from(mb.headBlockHitBox);
        }
        var attack = player.attackHitBox;
        Box attackBox = attack.enabled
                ? new Box(attack.getX(), attack.getY(), attack.width, attack.height, 0, 0, 0) : null;
        return new PlayerImage(network == null ? player.x : network.getRenderedPlayerX(player),
                network == null ? player.y : network.getRenderedPlayerY(player),
                player.imageWidth, player.imageHeight,
                network == null ? player.assetName : network.getRenderedPlayerAsset(player),
                network == null ? player.mirrorImage : network.getRenderedPlayerMirror(player),
                player.redSide, regular, block, head, attackBox);
    }

    private static Smoke captureSmoke(SpikeEffect.SmokeParticle particle) {
        List<SmokeBlob> blobs = new ArrayList<>(particle.blobCount);
        for (int i = 0; i < particle.blobCount; i++) {
            List<Float> shape = new ArrayList<>(particle.shapeOffset[i].length);
            for (float point : particle.shapeOffset[i]) shape.add(point);
            blobs.add(new SmokeBlob(particle.ox[i], particle.oy[i], particle.br[i],
                    particle.ba[i], particle.rot[i], shape));
        }
        return new Smoke(particle.x, particle.y, particle.currentRadius,
                particle.remainingFrames, particle.maxFrames, blobs);
    }

    public record PlayerImage(double x, double y, int width, int height, String assetName,
            boolean mirrorImage, boolean redSide, Box regular, Box block, Box head, Box attack) {}

    public record Box(double x, double y, double width, double height,
            int arcWidth, int arcHeight, double rotationDegrees) {
        static Box from(HitBox box) {
            return new Box(box.getX(), box.getY(), box.width, box.height,
                    box.arcWidth, box.arcHeight, box.rotationDegrees);
        }
    }

    public record BallImage(double x, double y, double radius, double rotationDegrees, boolean visible) {}
    public record Net(double x, double y, double width, double height, double topRadius) {}
    public record Connection(String message, String resetMessage, boolean connected) {}
    public record TrailPoint(double x, double y, int remainingFrames, int maxFrames, boolean isRedTeam) {}
    public record Smoke(double x, double y, double currentRadius, int remainingFrames,
            int maxFrames, List<SmokeBlob> blobs) {
        public Smoke { blobs = List.copyOf(blobs); }
    }
    public record SmokeBlob(float offsetX, float offsetY, float radiusMultiplier,
            float alphaMultiplier, float rotationRadians, List<Float> shape) {
        public SmokeBlob { shape = List.copyOf(shape); }
    }

    public record Match(boolean isPracticeMode, boolean isPracticeRedServing, int redScore,
            int blueScore, boolean matchOver, Boolean matchWinnerRed, String transientMessage,
            int transientMessageTimer, Boolean transientMessageIsRed) {}
}
