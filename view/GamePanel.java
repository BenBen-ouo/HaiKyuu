/*
Swing 遊戲畫面面板，負責啟動 60 FPS 更新與重繪。
連線模式每幀交由 GameClient 執行本地預測與 UDP 封包處理，並顯示網路狀態。
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
            final long frameTime = 1000L / 60L;
            while (!Thread.currentThread().isInterrupted()) {
                long start = System.currentTimeMillis();
                controller.update();
                repaint();
                long used = System.currentTimeMillis() - start;
                long sleep = Math.max(2L, frameTime - used);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "haikyuu-game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        renderer.render(g, model);
        networkStatusRenderer.draw(g, networkView);
        g.dispose();
    }
}
