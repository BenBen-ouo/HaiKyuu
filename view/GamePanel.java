/*
Swing 遊戲畫面面板，負責啟動 60 FPS 遊戲迴圈。
每幀呼叫 controller 更新模型，再呼叫 renderer 重繪畫面。
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

public class GamePanel extends JPanel {
    private final GameModel model;
    private final GameController controller;
    private final GameRenderer renderer = new GameRenderer();

    public GamePanel(GameModel model, GameController controller) {
        this.model = model;
        this.controller = controller;
        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setFocusable(true);
    }

    public void startGameLoop() {
        Thread loop = new Thread(() -> {
            final int fps = 60;
            final long frameTime = 1000L / fps;

            while (true) {
                long start = System.currentTimeMillis();
                controller.update();
                repaint();

                long used = System.currentTimeMillis() - start;
                long sleep = Math.max(2, frameTime - used);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        loop.setDaemon(true);
        loop.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        renderer.render(g2, model);
    }
}
