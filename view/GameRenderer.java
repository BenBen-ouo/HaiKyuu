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
        playerRenderer.drawTeam(g, model.redTeam, true);
        playerRenderer.drawTeam(g, model.blueTeam, false);
        ballRenderer.draw(g, model.ball);
        effectRenderer.draw(g, model.effects);
        drawScore(g, model);
    }

    private void drawScore(Graphics2D g, GameModel model) {
        if (model.matchOver != null && model.matchOver) {
            String msg = (model.matchWinnerRed != null && model.matchWinnerRed) ? "RED WINS" : "BLUE WINS";
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(msg);
            int x = GameConfig.SCREEN_WIDTH / 2 - w / 2;
            int y = GameConfig.SCREEN_HEIGHT / 2;
            // 背景半透明方塊增強可讀性
            Composite orig = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g.setColor(Color.WHITE);
            g.fillRect(x - 16, y - fm.getAscent(), w + 32, fm.getAscent() + 36);
            g.setComposite(orig);

            g.setColor(Color.BLACK);
            g.drawString(msg, x, y + fm.getAscent() / 2);

            // 顯示重開提示
            String hint = "Press R to restart";
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            FontMetrics hf = g.getFontMetrics();
            int hw = hf.stringWidth(hint);
            int hx = GameConfig.SCREEN_WIDTH / 2 - hw / 2;
            int hy = y + fm.getAscent() + 20;
            g.drawString(hint, hx, hy);
            return;
        }

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(model.redScore + " : " + model.blueScore, GameConfig.SCREEN_WIDTH / 2 - 28, 44);
    }
}
