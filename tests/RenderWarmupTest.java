package view;

import controller.GameController;
import controller.KeyboardController;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import model.GameConfig;
import model.GameModel;

/** 在新 JVM 執行，量測首次判決並驗證預熱不改畫面、模型或特效。 */
public final class RenderWarmupTest {
    public static void main(String[] args) throws Exception {
        var model = new GameModel();
        var before = RenderFrame.capture(model, null);
        long started = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> new GamePanel(model,
                new GameController(model, new KeyboardController())));
        System.out.printf("render preparation %.3f ms%n", elapsed(started));
        check(before.equals(RenderFrame.capture(model, null)), "面板預熱不得觸球、給分或產生模型特效");

        var image = image();
        var g = image.createGraphics();
        try {
            hints(g);
            var display = new MatchDisplay();
            for (String text : new String[]{"IN", "OUT", "TOUCH OUT", "發球犯規", "後排三米線", "四觸違規"}) {
                started = System.nanoTime();
                display.draw(g, new RenderFrame.Match(1, 0, false, null, text, 90, true), false);
                // 時間只供比較，不把 OS 排程或機器效能寫成脆弱的測試門檻。
                System.out.printf("prepared judgment %s %.3f ms%n", text, elapsed(started));
            }
        } finally { g.dispose(); }

        var prepared = new GameRenderer();
        prepared.prepareForFirstFrame();
        prepared.prepareForFirstFrame();
        var raw = new GameRenderer();
        for (boolean mirror : new boolean[]{false, true}) {
            check(Arrays.equals(pixels(render(raw, model, mirror)), pixels(render(prepared, model, mirror))),
                    "離屏樣本不得殘留在正式畫面");
            model.transientMessage = "IN"; model.transientMessageTimer = 90; model.transientMessageIsRed = true;
            model.spikeEffect.spawnSmoke(580, GameConfig.FLOOR_Y);
            var snapshot = RenderFrame.capture(model, null);
            check(Arrays.equals(pixels(render(raw, model, mirror)), pixels(render(prepared, model, mirror))),
                    "預熱後判決、煙霧與鏡像維持原有像素");
            check(snapshot.equals(RenderFrame.capture(model, null)), "繪圖不得推進或修改模型");
            model.spikeEffect.clear(); model.transientMessageTimer = 0;
        }
        System.out.println("RenderWarmupTest passed");
    }
    private static double elapsed(long started) { return (System.nanoTime() - started) / 1e6; }
    private static BufferedImage image() {
        return new BufferedImage(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    }
    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    private static BufferedImage render(GameRenderer renderer, GameModel model, boolean mirror) {
        var result = image(); var g = result.createGraphics();
        try { hints(g); renderer.render(g, model, mirror); } finally { g.dispose(); }
        return result;
    }
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
