import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import network.Packet;
import network.UdpCodec;
import view.GameRenderer;

/** 同機比較用的小型基準；不是 GUI／雙機實測或絕對效能保證。 */
public final class RefactorBenchmark {
    private static final int WARMUP_FRAMES = 120;
    private static final int SAMPLE_FRAMES = 360;
    private static final int RUN_COUNT = 3;
    private static final int CORE_WARMUP_TICKS = 20_000;

    public static void main(String[] args) throws Exception {
        warmUpCore();
        for (boolean effects : new boolean[]{false, true}) {
            for (int run = 0; run < RUN_COUNT; run++) measure(effects, run);
        }
    }

    private static void warmUpCore() throws Exception {
        GameModel model = new GameModel();
        GameModel client = new GameModel();
        TeamInput idle = new TeamInput();
        for (int tick = 0; tick < CORE_WARMUP_TICKS; tick++) {
            model.update(idle, idle);
            byte[] data = UdpCodec.worldSnapshot(1, tick, tick, Packet.WorldSnapshot.from(model, 0));
            var frame = (UdpCodec.WorldSnapshotFrame) UdpCodec.decode(data, data.length);
            frame.snapshot.ball.applyTo(client.ball);
            frame.snapshot.redTeam.applyMotionTo(client.redTeam);
            frame.snapshot.blueTeam.applyMotionTo(client.blueTeam);
            frame.snapshot.rallyContacts.applyTo(client);
        }
    }

    private static void measure(boolean effects, int run) throws Exception {
        GameModel model = new GameModel();
        GameModel client = new GameModel();
        GameRenderer renderer = new GameRenderer();
        BufferedImage image = new BufferedImage(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        long[] updates = new long[SAMPLE_FRAMES];
        long[] paints = new long[SAMPLE_FRAMES];
        long[] codecs = new long[SAMPLE_FRAMES];
        int bytes = 0;
        try {
            for (int frame = -WARMUP_FRAMES; frame < SAMPLE_FRAMES; frame++) {
                if (effects) {
                    model.spikeEffect.startSpikeTrail(true);
                    model.spikeEffect.addTrailPoint(300 + Math.floorMod(frame, 120), 400);
                    if (Math.floorMod(frame, 40) == 0) model.spikeEffect.spawnSmoke(300, GameConfig.FLOOR_Y);
                }
                TeamInput input = new TeamInput();
                input.backRight = Math.floorMod(frame, 80) < 40;
                input.backLeft = !input.backRight;
                long start = System.nanoTime();
                model.update(input, new TeamInput());
                if (effects) {
                    // 固定形狀與可見位置，避免煙霧亂數影響前後比較。
                    for (var smoke : model.spikeEffect.getSmokeParticles()) {
                        smoke.x = 300;
                        smoke.y = GameConfig.FLOOR_Y;
                        for (int i = 0; i < smoke.blobCount; i++) {
                            smoke.ox[i] = (i - 1) * 8;
                            smoke.oy[i] = 0;
                            smoke.br[i] = 1;
                            smoke.ba[i] = 1;
                            smoke.rot[i] = 0;
                            Arrays.fill(smoke.shapeOffset[i], 1);
                        }
                    }
                }
                long updated = System.nanoTime();
                renderer.render(graphics, model);
                long painted = System.nanoTime();
                byte[] packet = UdpCodec.worldSnapshot(1, 1, 1, Packet.WorldSnapshot.from(model, 0));
                var snapshot = (UdpCodec.WorldSnapshotFrame) UdpCodec.decode(packet, packet.length);
                if (snapshot == null) throw new AssertionError("codec");
                snapshot.snapshot.ball.applyTo(client.ball);
                snapshot.snapshot.redTeam.applyMotionTo(client.redTeam);
                snapshot.snapshot.blueTeam.applyMotionTo(client.blueTeam);
                snapshot.snapshot.rallyContacts.applyTo(client);
                long decoded = System.nanoTime();
                if (frame >= 0) {
                    updates[frame] = updated - start;
                    paints[frame] = painted - updated;
                    codecs[frame] = decoded - painted;
                    bytes = packet.length;
                }
            }
        } finally {
            graphics.dispose();
        }
        report(effects, run, "update", updates, bytes);
        report(effects, run, "render", paints, bytes);
        report(effects, run, "codec", codecs, bytes);
    }

    private static void report(boolean effects, int run, String operation, long[] samples, int bytes) {
        double mean = Arrays.stream(samples).average().orElseThrow() / 1_000.0;
        long late = Arrays.stream(samples).filter(value -> value > 1_000_000_000L / GameConfig.TICKS_PER_SECOND).count();
        Arrays.sort(samples);
        System.out.printf(java.util.Locale.ROOT, "%s,%d,%s,%.3f,%.3f,%d,%d%n",
                effects ? "effects" : "idle", run, operation, mean,
                samples[(int) Math.ceil(samples.length * .95) - 1] / 1_000.0, late, bytes);
    }
}
