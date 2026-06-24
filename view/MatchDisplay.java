/*
集中管理比賽相關的中間畫面顯示：
- 頂部分數
- 中央暫時訊息（IN / OUT / TOUCH OUT / 後排三米線）
- 比賽結束的勝利畫面

將顯示邏輯從 GameRenderer 抽出，方便維護與重用。
*/
package view;

import java.awt.*;
import model.*;

public class MatchDisplay {
    public void draw(Graphics2D g, GameModel model, boolean bluePerspective) {
        // 畫面頂端分數依觀看隊伍排序；P2 畫面先顯示藍隊分數。
        String scoreText = bluePerspective
                ? model.blueScore + " : " + model.redScore
                : model.redScore + " : " + model.blueScore;
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics scoreMetrics = g.getFontMetrics();
        g.drawString(scoreText, (GameConfig.SCREEN_WIDTH - scoreMetrics.stringWidth(scoreText)) / 2, 44);

        // 若比賽結束，顯示勝利框 (圓角、以贏家顏色)
        if (model.matchOver) {
            String msg = (model.matchWinnerRed != null && model.matchWinnerRed) ? "RED WINS" : "BLUE WINS";
            g.setFont(new Font("Arial", Font.BOLD, 48));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(msg);
            int x = GameConfig.SCREEN_WIDTH / 2 - w / 2;
            int y = GameConfig.SCREEN_HEIGHT / 2;

            // 顏色以勝隊為主
            Color borderColor = Color.BLACK;
            Color fillColor = Color.WHITE;
            if (model.matchWinnerRed != null) {
                if (model.matchWinnerRed) {
                    borderColor = new Color(200, 30, 30);
                    fillColor = new Color(255, 220, 220);
                } else {
                    borderColor = new Color(30, 80, 200);
                    fillColor = new Color(220, 230, 255);
                }
            }

            Composite orig = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g.setColor(fillColor);
            int rx = x - 16;
            int ry = y - fm.getAscent();
            int rw = w + 32;
            int rh = fm.getAscent() + 36;
            int arc = 28;
            g.fillRoundRect(rx, ry, rw, rh, arc, arc);
            g.setComposite(orig);

            Stroke origSt = g.getStroke();
            g.setStroke(new BasicStroke(4f));
            g.setColor(borderColor);
            g.drawRoundRect(rx, ry, rw, rh, arc, arc);
            g.setStroke(origSt);

            g.setColor(Color.BLACK);
            g.drawString(msg, x, y + fm.getAscent() / 2);

            // 顯示重開提示
            String hint = "Press R to restart";
            g.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 20));
            FontMetrics hf = g.getFontMetrics();
            int hw = hf.stringWidth(hint);
            int hx = GameConfig.SCREEN_WIDTH / 2 - hw / 2;
            int hy = y + fm.getAscent() + 20;
            g.drawString(hint, hx, hy);

            // 在 matchOver 時，不顯示其他 transient 訊息
            return;
        }

        // 顯示暫時訊息（IN/OUT/TOUCH OUT/後排三米線）
        if (model.transientMessage != null && model.transientMessageTimer > 0) {
            String msg = model.transientMessage;
            // 固定框大小，基於勝利字型寬度
            Font winnerFont = new Font("Microsoft JhengHei", Font.BOLD, 48);
            FontMetrics wfm = g.getFontMetrics(winnerFont);
            int winnerTextW = wfm.stringWidth("RED WINS");
            int winnerBoxW = winnerTextW + 32;
            int winnerBoxH = wfm.getAscent() + 36;

            int rx = GameConfig.SCREEN_WIDTH / 2 - winnerBoxW / 2;
            int ry = GameConfig.SCREEN_HEIGHT / 2 - winnerBoxH / 2;
            int rw = winnerBoxW;
            int rh = winnerBoxH;
            int arc = 28;

            // 顏色決定
            Color borderColor = Color.BLACK;
            Color fillColor = Color.WHITE;
            if (model.transientMessageIsRed != null) {
                if (model.transientMessageIsRed) {
                    borderColor = new Color(200, 30, 30);
                    fillColor = new Color(255, 220, 220);
                } else {
                    borderColor = new Color(30, 80, 200);
                    fillColor = new Color(220, 230, 255);
                }
            }

            Composite orig = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g.setColor(fillColor);
            g.fillRoundRect(rx, ry, rw, rh, arc, arc);
            g.setComposite(orig);

            Stroke origSt = g.getStroke();
            g.setStroke(new BasicStroke(4f));
            g.setColor(borderColor);
            g.drawRoundRect(rx, ry, rw, rh, arc, arc);
            g.setStroke(origSt);

            // 中央置中文字
            g.setFont(new Font("Microsoft JhengHei", Font.BOLD, 36));
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(msg);
            int tx = rx + (rw - textW) / 2;
            int baseline = ry + (rh - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();

            g.setColor(Color.BLACK);
            g.drawString(msg, tx, baseline);
        }
    }
}
