import controller.GameController;
import controller.KeyboardController;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import model.GameModel;
import model.ai.AiDifficulty;
import model.ai.RuleBasedAi;
import view.GamePanel;
import view.RenderFrame;

public final class LocalPauseTest {
    public static void main(String[] args) throws Exception {
        testFrozenModelAndInputRelease();
        testFrozenAi();
        SwingUtilities.invokeAndWait(LocalPauseTest::testPauseButtons);
        System.out.println("LocalPauseTest passed");
    }

    private static void testFrozenModelAndInputRelease() {
        GameModel game = new GameModel();
        KeyboardController keyboard = new KeyboardController();
        GameController controller = new GameController(game, keyboard);
        GamePanel panel = new GamePanel(game, controller);
        game.getServeHandler().setWaitingForServe(false);
        game.ball.x = 300; game.ball.y = 200; game.ball.vx = 4; game.ball.vy = 1;
        game.netFlashFrames = 30;
        game.transientMessage = "IN";
        game.transientMessageTimer = 50;
        game.spikeEffect.startSpikeTrail(true);
        game.spikeEffect.addTrailPoint(300, 200);
        game.spikeEffect.spawnSmoke(250, 625);
        var before = RenderFrame.capture(game, null);
        controller.setPaused(true);
        press(keyboard, panel, KeyEvent.VK_R);
        press(keyboard, panel, KeyEvent.VK_SPACE);
        press(keyboard, panel, KeyEvent.VK_D);
        for (int tick = 0; tick < 100; tick++) controller.update();
        check(before.equals(RenderFrame.capture(game, null)), "暫停不更新球、角色、特效或比分");
        controller.setPaused(false);
        press(keyboard, panel, KeyEvent.VK_SPACE); // 模擬仍按住時的系統重複按鍵。
        check(!keyboard.getRedInput().servePressed && !keyboard.getRedInput().backRight
                && !keyboard.isRestartPressed(), "暫停期間按住的操作不帶到繼續遊戲");
        controller.update();
        check(game.ball.x != before.ball().x(), "恢復後接續原本物理，而非重開對局");
        release(keyboard, panel, KeyEvent.VK_SPACE);
        press(keyboard, panel, KeyEvent.VK_SPACE);
        check(keyboard.getRedInput().servePressed, "放開再按後恢復接受操作");
    }

    private static void testFrozenAi() {
        for (boolean red : new boolean[]{true, false}) {
            var game = new GameModel();
            var ai = new RuleBasedAi(red, AiDifficulty.DEFAULT, new java.util.Random(3));
            var controller = new GameController(game, new KeyboardController(), null, ai);
            controller.setPaused(true);
            for (int tick = 0; tick < 200; tick++) controller.update();
            check(ai.state() == RuleBasedAi.State.WAITING, "暫停期間 AI 不決策或推進等待時間");
            controller.setPaused(false);
            controller.update();
            check(ai.state() != RuleBasedAi.State.WAITING, "繼續後 AI 恢復決策");
            check(controller.isAiMatch() && controller.isAiRedSide() == red, "玩家與機器人左右身分對應");
        }
    }

    private static void testPauseButtons() {
        var game = new GameModel();
        var controller = new GameController(game, new KeyboardController());
        var panel = new GamePanel(game, controller);
        JButton pause = button(panel, "Pause"), resume = button(panel, "繼續遊戲");
        check(pause != null && resume != null, "本地提供暫停與確認繼續按鈕");
        pause.doClick();
        check(controller.isPaused() && !pause.isEnabled() && resume.getParent().isVisible(), "按下暫停立即凍結並顯示繼續面板");
        resume.doClick();
        check(!controller.isPaused() && pause.isEnabled() && !resume.getParent().isVisible(), "按下繼續才恢復");
        var network = new network.NetworkView() {
            public boolean isBluePerspective() { return false; }
            public String getConnectionMessage() { return "test"; }
            public String getResetMessage() { return ""; }
            public boolean isConnected() { return true; }
            public boolean isSessionEnded() { return false; }
            public double getRenderedPlayerX(model.player.Player p) { return p.x; }
            public double getRenderedPlayerY(model.player.Player p) { return p.y; }
            public String getRenderedPlayerAsset(model.player.Player p) { return p.assetName; }
            public boolean getRenderedPlayerMirror(model.player.Player p) { return p.mirrorImage; }
            public void close() {}
        };
        check(button(new GamePanel(game, controller, network), "Pause") == null, "連線畫面不提供暫停");
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton b && b.getText().equals(text)) return b;
            if (child instanceof Container c) {
                JButton found = button(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void press(KeyboardController k, Component c, int key) {
        k.keyPressed(new KeyEvent(c, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }
    private static void release(KeyboardController k, Component c, int key) {
        k.keyReleased(new KeyEvent(c, KeyEvent.KEY_RELEASED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
