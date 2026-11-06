/*
Swing 遊戲畫面面板，負責固定 60 tick/s 更新與重繪。
連線模式每個 tick 交由 GameClient 執行本地預測與 UDP 封包處理，並顯示網路狀態。
*/
package view;

import controller.GameController;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import model.GameConfig;
import model.GameModel;
import model.FixedTickSchedule;
import network.NetworkView;
import network.TimingDiagnostics;

public class GamePanel extends JPanel {
    private final GameModel model;
    private final GameController controller;
    private final NetworkView networkView;
    private final GameRenderer renderer = new GameRenderer();
    private final NetworkStatusRenderer networkStatusRenderer = new NetworkStatusRenderer();
    private Thread gameLoop;
    private volatile RenderFrame renderFrame;

    public GamePanel(GameModel model, GameController controller) {
        this(model, controller, null);
    }

    public GamePanel(GameModel model, GameController controller, NetworkView networkView) {
        this.model = model;
        this.controller = controller;
        this.networkView = networkView;
        this.renderFrame = RenderFrame.capture(model, networkView);
        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setFocusable(true);
    }

    public synchronized void startGameLoop() {
        if (gameLoop != null && gameLoop.isAlive()) return;
        gameLoop = new Thread(this::runGameLoop, "haikyuu-game-loop");
        gameLoop.setDaemon(true);
        gameLoop.start();
    }

    private void runGameLoop() {
        FixedTickSchedule schedule = new FixedTickSchedule(System.nanoTime(), GameConfig.TICKS_PER_SECOND);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                long lateNanos = System.nanoTime() - schedule.nextDeadlineNanos();
                if (networkView != null && lateNanos >= 25_000_000L) {
                    TimingDiagnostics.record("client game loop late "
                            + String.format("%.2f", lateNanos / 1_000_000.0) + " ms");
                }
                long updateStarted = System.nanoTime();
                controller.update();
                renderFrame = RenderFrame.capture(model, networkView);
                if (networkView != null) {
                    TimingDiagnostics.recordIfSlow("client update", updateStarted, 25);
                }
                repaint();

                if (schedule.completeTick(System.nanoTime())) {
                    TimingDiagnostics.record("client catch-up limit reached");
                }
                sleepUntil(schedule.nextDeadlineNanos());
            }
        } catch (RuntimeException exception) {
            TimingDiagnostics.record("game update failed: " + exception.getClass().getSimpleName());
            exception.printStackTrace();
            if (networkView != null) networkView.close();
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                    this, "遊戲更新失敗，請重新開啟對局。", "遊戲中止", javax.swing.JOptionPane.ERROR_MESSAGE));
        }
    }

    public synchronized void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.interrupt();
        }
    }

    private void sleepUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return;
        }
        try {
            Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        long paintStarted = System.nanoTime();
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RenderFrame frame = renderFrame;
            renderer.render(g, frame);
            networkStatusRenderer.draw(g, frame.connection());
        } finally {
            g.dispose();
        }
        if (networkView != null) {
            TimingDiagnostics.recordIfSlow("client paint", paintStarted, 25);
        }
    }
}
