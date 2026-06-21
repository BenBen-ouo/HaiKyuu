/*
畫面渲染總入口，負責依序呼叫球場、角色、球、特效與分數繪製。
本身只做繪圖流程調度，不直接處理細節繪圖。
*/
package view;

import java.awt.*;
import model.*;

public class GameRenderer {
    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);
    private final EffectRenderer effectRenderer = new EffectRenderer(assets);
    private final MatchDisplay matchDisplay = new MatchDisplay();

    public void render(Graphics2D g, GameModel model) {
        // 啟用文字抗鋸齒，提升中文顯示品質。
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        courtRenderer.draw(g);

        // 畫在角色前方、球的下方，方便觀察網子實際碰撞範圍。
        drawNetHitBox(g);

        playerRenderer.drawTeam(g, model.redTeam, true);
        playerRenderer.drawTeam(g, model.blueTeam, false);

        ballRenderer.draw(g, model.ball);
        effectRenderer.draw(g, model.effects);

        // MatchDisplay 統一處理比分、規則提示與比賽結束畫面。
        matchDisplay.draw(g, model);
    }

    private void drawNetHitBox(Graphics2D g) {
        int x = (int) Math.round(
                GameConfig.NET_HITBOX_CENTER_X
                        - GameConfig.NET_HITBOX_WIDTH / 2.0
        );
        int y = (int) Math.round(GameConfig.NET_HITBOX_TOP_Y);
        int width = (int) Math.round(GameConfig.NET_HITBOX_WIDTH);
        int height = (int) Math.round(GameConfig.NET_HITBOX_HEIGHT);

        // 使用複製後的 Graphics2D，避免透明度與線條粗細影響後續繪製。
        Graphics2D debugGraphics = (Graphics2D) g.create();

        debugGraphics.setColor(new Color(40, 210, 90, 70));
        debugGraphics.fillRect(x, y, width, height);

        debugGraphics.setColor(new Color(0, 135, 55));
        debugGraphics.setStroke(new BasicStroke(2));
        debugGraphics.drawRect(x, y, width, height);

        debugGraphics.dispose();
    }
}