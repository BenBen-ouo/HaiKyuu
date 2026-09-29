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
import network.NetworkView;
import network.TimingDiagnostics;

public class GamePanel extends JPanel {
    private final GameModel model;
    private final GameController controller;
    private final NetworkView networkView;
    private final GameRenderer renderer = new GameRenderer();
    private final NetworkStatusRenderer networkStatusRenderer = new NetworkStatusRenderer();

    public GamePanel(GameModel model, GameController controller) {
        this(model, controller, null);
    }

    public GamePanel(GameModel model, GameController controller, NetworkView networkView) {
        this.model = model;
        this.controller = controller;
        this.networkView = networkView;
        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setFocusable(true);
    }

    public void startGameLoop() {
        Thread loop = new Thread(() -> {
            long tickNanos = 1_000_000_000L / GameConfig.TICKS_PER_SECOND;
            long nextTickNanos = System.nanoTime();

            while (!Thread.currentThread().isInterrupted()) {
                long lateNanos = System.nanoTime() - nextTickNanos;
                if (networkView != null && lateNanos >= 25_000_000L) {
                    TimingDiagnostics.record("client game loop late "
                            + String.format("%.2f", lateNanos / 1_000_000.0) + " ms");
                }
                long updateStarted = System.nanoTime();
                controller.update();
                if (networkView != null) {
                    TimingDiagnostics.recordIfSlow("client update", updateStarted, 25);
                }
                repaint();

                nextTickNanos += tickNanos;
                sleepUntil(nextTickNanos);
            }
        }, "haikyuu-game-loop");
        loop.setDaemon(true);
        loop.start();
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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean mirrorWorld = networkView != null && networkView.isBluePerspective();
        renderer.render(g, model, mirrorWorld, networkView);
        networkStatusRenderer.draw(g, networkView);
        g.dispose();
        if (networkView != null) {
            TimingDiagnostics.recordIfSlow("client paint", paintStarted, 25);
        }
    }
}
