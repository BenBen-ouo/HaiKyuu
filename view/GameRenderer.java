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

    public void render(Graphics2D g, GameModel model) {
        courtRenderer.draw(g);

        // 畫在角色前方、球的下方，方便觀察網子實際碰撞範圍。
        drawNetHitBox(g);

        playerRenderer.drawTeam(g, model.redTeam, true);
        playerRenderer.drawTeam(g, model.blueTeam, false);
        ballRenderer.draw(g, model.ball);
        effectRenderer.draw(g, model.effects);

        drawScore(g, model);
    }

    private void drawNetHitBox(Graphics2D g) {
        int x = (int) Math.round(
                GameConfig.NET_HITBOX_CENTER_X
                        - GameConfig.NET_HITBOX_WIDTH / 2.0
        );
        int y = (int) Math.round(GameConfig.NET_HITBOX_TOP_Y);
        int width = (int) Math.round(GameConfig.NET_HITBOX_WIDTH);
        int height = (int) Math.round(GameConfig.NET_HITBOX_HEIGHT);

        // 使用複製後的 Graphics2D，避免透明度與線條粗細影響後續角色、球與分數繪製。
        Graphics2D debugGraphics = (Graphics2D) g.create();

        //測試用看有顏色的攻擊 hitBox，最後後刪除
        debugGraphics.setColor(new Color(40, 210, 90, 70));
        debugGraphics.fillRect(x, y, width, height);
        debugGraphics.setColor(new Color(0, 135, 55));
        debugGraphics.setStroke(new BasicStroke(2));
        debugGraphics.drawRect(x, y, width, height);

        debugGraphics.dispose();
    }

    private void drawScore(Graphics2D g, GameModel model) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(
                model.redScore + " : " + model.blueScore,
                GameConfig.SCREEN_WIDTH / 2 - 28,
                44
        );
    }
}