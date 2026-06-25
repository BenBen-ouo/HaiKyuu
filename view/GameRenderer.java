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
import model.NetHitBox;
import network.NetworkView;

public class GameRenderer {
    private static final Color NET_HITBOX_FILL = new Color(40, 210, 90, 70);
    private static final Color NET_HITBOX_STROKE = new Color(0, 135, 55);

    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);
    private final EffectRenderer effectRenderer = new EffectRenderer(assets);
    private final SpikeEffectRenderer spikeEffectRenderer = new SpikeEffectRenderer();
    private final MatchDisplay matchDisplay = new MatchDisplay();

    public void render(Graphics2D g, GameModel model) {
        render(g, model, false);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld) {
        render(g, model, mirrorWorld, null);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld, NetworkView networkView) {
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        if (mirrorWorld) {
            drawMirroredWorld(g, model, networkView);
        } else {
            drawWorld(g, model, true, networkView);
        }

        // 比分、規則提示與比賽結束畫面永遠保持正常方向。
        matchDisplay.draw(g, model, mirrorWorld);
    }

    private void drawMirroredWorld(Graphics2D g, GameModel model, NetworkView networkView) {
        Graphics2D worldGraphics = (Graphics2D) g.create();
        try {
            worldGraphics.translate(GameConfig.SCREEN_WIDTH, 0);
            worldGraphics.scale(-1, 1);
            drawWorld(worldGraphics, model, false, networkView);
        } finally {
            worldGraphics.dispose();
        }

        // 世界座標已鏡像，但除錯文字維持可讀。
        courtRenderer.drawWorldBoundaryGuide(g);
        playerRenderer.drawMirroredStateLabels(g, model.redTeam);
        playerRenderer.drawMirroredStateLabels(g, model.blueTeam);
    }

    private void drawWorld(Graphics2D g, GameModel model, boolean drawStateLabels, NetworkView networkView) {
        courtRenderer.draw(g, drawStateLabels);
        drawNetHitBox(g, model.netHitBox);

        playerRenderer.drawTeam(g, model.redTeam, true, drawStateLabels);
        playerRenderer.drawTeam(g, model.blueTeam, false, drawStateLabels);

        double ballX = networkView == null ? model.ball.x : networkView.getRenderedBallX(model.ball.x);
        double ballY = networkView == null ? model.ball.y : networkView.getRenderedBallY(model.ball.y);
        double ballRotation = networkView == null
                ? model.ball.rotationDegrees
                : networkView.getRenderedBallRotation(model.ball.rotationDegrees);
        ballRenderer.draw(g, model.ball, ballX, ballY, ballRotation);
        effectRenderer.draw(g, model.effects);
        spikeEffectRenderer.draw(g, model.spikeEffect);
    }

    private void drawNetHitBox(Graphics2D g, NetHitBox box) {
        if (!DebugSettings.areHitBoxesVisible()) {
            return;
        }

        int x = (int) Math.round(box.getLeft());
        int y = (int) Math.round(box.getTop());
        int width = (int) Math.round(box.getRight() - box.getLeft());
        int height = (int) Math.round(box.getBottom() - box.getTop());

        Graphics2D debugGraphics = (Graphics2D) g.create();
        try {
            debugGraphics.setColor(NET_HITBOX_FILL);
            debugGraphics.fillRect(x, y, width, height);

            debugGraphics.setColor(NET_HITBOX_STROKE);
            debugGraphics.setStroke(new BasicStroke(2));
            debugGraphics.drawRect(x, y, width, height);
        } finally {
            debugGraphics.dispose();
        }
    }
}
