/*
畫面渲染總入口，負責依序呼叫球場、角色、球、特效與分數繪製。
本身只做繪圖流程調度；扣球特效與碰撞箱繪製分別交由專責 Renderer 處理。
*/
package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import model.GameConfig;
import model.GameModel;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import network.NetworkView;
import network.TimingDiagnostics;

public class GameRenderer {
    private static final Color NET_HITBOX_FILL = new Color(40, 210, 90, 70);
    private static final Color NET_HITBOX_STROKE = new Color(0, 135, 55);

    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);
    private final SpikeEffectRenderer spikeEffectRenderer = new SpikeEffectRenderer();
    private final MatchDisplay matchDisplay = new MatchDisplay();

    public void render(Graphics2D g, GameModel model) {
        render(g, model, false);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld) {
        render(g, model, mirrorWorld, null);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld, NetworkView networkView) {
        render(g, RenderFrame.capture(model, networkView), mirrorWorld);
    }

    public void render(Graphics2D g, RenderFrame frame) {
        render(g, frame, frame.mirrorWorld());
    }

    private void render(Graphics2D g, RenderFrame frame, boolean mirrorWorld) {
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        if (mirrorWorld) {
            drawMirroredWorld(g, frame);
        } else {
            drawWorld(g, frame, true);
        }

        long hudStarted = System.nanoTime();
        for (RenderFrame.PlayerImage player : frame.players()) playerRenderer.drawBlockTouch(g, player, mirrorWorld);
        // 比分、規則提示與比賽結束畫面永遠保持正常方向。
        matchDisplay.draw(g, frame.match(), mirrorWorld);
        TimingDiagnostics.recordIfSlow("paint HUD", hudStarted, 5);
    }

    private void drawMirroredWorld(Graphics2D g, RenderFrame frame) {
        Graphics2D worldGraphics = (Graphics2D) g.create();
        try {
            worldGraphics.translate(GameConfig.SCREEN_WIDTH, 0);
            worldGraphics.scale(-1, 1);
            drawWorld(worldGraphics, frame, false);
        } finally {
            worldGraphics.dispose();
        }

        // 世界座標已鏡像，但除錯文字維持可讀。
        courtRenderer.drawWorldBoundaryGuide(g);
    }

    private void drawWorld(Graphics2D g, RenderFrame frame, boolean drawWorldBoundaryGuide) {
        long started = System.nanoTime();
        courtRenderer.draw(g, drawWorldBoundaryGuide, frame.netFlashFrames());
        drawNetHitBox(g, frame.net());
        TimingDiagnostics.recordIfSlow("paint court", started, 5);

        started = System.nanoTime();
        for (RenderFrame.PlayerImage player : frame.players()) playerRenderer.draw(g, player);
        TimingDiagnostics.recordIfSlow("paint players", started, 5);

        started = System.nanoTime();
        if (frame.ball().visible()) {
            ballRenderer.draw(g, frame.ball());
        }
        TimingDiagnostics.recordIfSlow("paint ball", started, 5);
        started = System.nanoTime();
        spikeEffectRenderer.draw(g, frame);
        TimingDiagnostics.recordIfSlow("paint effects", started, 5);
    }

    private void drawNetHitBox(Graphics2D g, RenderFrame.Net box) {
        if (!DebugSettings.areHitBoxesVisible()) {
            return;
        }

        Graphics2D debugGraphics = (Graphics2D) g.create();
        try {
            double radius = box.topRadius();
            Area shape = new Area(new Rectangle2D.Double(box.x(), box.y() + radius,
                    box.width(), box.height() - radius));
            shape.add(new Area(new Ellipse2D.Double(box.x() + box.width() / 2 - radius,
                    box.y(), radius * 2, radius * 2)));
            debugGraphics.setColor(NET_HITBOX_FILL);
            debugGraphics.fill(shape);

            debugGraphics.setColor(NET_HITBOX_STROKE);
            debugGraphics.setStroke(new BasicStroke(2));
            debugGraphics.draw(shape);
        } finally {
            debugGraphics.dispose();
        }
    }
}
