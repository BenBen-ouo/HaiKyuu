/*
集中管理比賽相關的中間畫面顯示：頂部分數、規則提示與比賽結束畫面。
分數可依 Player 2 的鏡像視角調整為藍隊在左、紅隊在右，所有文字均維持正常方向。
*/
package view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import model.GameConfig;
import model.GameModel;

public class MatchDisplay {
    public void draw(Graphics2D g, GameModel model) {
        draw(g, model, false);
    }

    public void draw(Graphics2D g, GameModel model, boolean bluePerspective) {
        drawScore(g, model, bluePerspective);

        if (model.matchOver) {
            drawWinner(g, model);
            return;
        }

        drawTransientMessage(g, model);
    }

    private void drawScore(Graphics2D g, GameModel model, boolean bluePerspective) {
        String score = bluePerspective
                ? model.blueScore + " : " + model.redScore
                : model.redScore + " : " + model.blueScore;

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(score, GameConfig.SCREEN_WIDTH / 2 - metrics.stringWidth(score) / 2, 44);
    }

    private void drawWinner(Graphics2D g, GameModel model) {
        String message = model.matchWinnerRed != null && model.matchWinnerRed
                ? "RED WINS"
                : "BLUE WINS";

        g.setFont(new Font("Arial", Font.BOLD, 48));
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(message);
        int x = GameConfig.SCREEN_WIDTH / 2 - textWidth / 2;
        int y = GameConfig.SCREEN_HEIGHT / 2;

        Color borderColor = model.matchWinnerRed != null && model.matchWinnerRed
                ? new Color(200, 30, 30)
                : new Color(30, 80, 200);
        Color fillColor = model.matchWinnerRed != null && model.matchWinnerRed
                ? new Color(255, 220, 220)
                : new Color(220, 230, 255);

        drawMessageBox(g, message, x, y, borderColor, fillColor, 48);

        String hint = "雙方按 R 重設，按 N 取消確認";
        g.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 20));
        FontMetrics hintMetrics = g.getFontMetrics();
        g.setColor(Color.BLACK);
        g.drawString(
                hint,
                GameConfig.SCREEN_WIDTH / 2 - hintMetrics.stringWidth(hint) / 2,
                y + metrics.getAscent() + 20
        );
    }

    private void drawTransientMessage(Graphics2D g, GameModel model) {
        if (model.transientMessage == null || model.transientMessageTimer <= 0) {
            return;
        }

        Color borderColor = model.transientMessageIsRed != null && model.transientMessageIsRed
                ? new Color(200, 30, 30)
                : new Color(30, 80, 200);
        Color fillColor = model.transientMessageIsRed != null && model.transientMessageIsRed
                ? new Color(255, 220, 220)
                : new Color(220, 230, 255);

        g.setFont(new Font("Microsoft JhengHei", Font.BOLD, 36));
        FontMetrics metrics = g.getFontMetrics();
        int x = GameConfig.SCREEN_WIDTH / 2 - metrics.stringWidth(model.transientMessage) / 2;
        int y = GameConfig.SCREEN_HEIGHT / 2;

        drawMessageBox(g, model.transientMessage, x, y, borderColor, fillColor, 36);
    }

    private void drawMessageBox(
            Graphics2D g,
            String message,
            int textX,
            int baselineY,
            Color borderColor,
            Color fillColor,
            int fontSize
    ) {
        g.setFont(new Font("Microsoft JhengHei", Font.BOLD, fontSize));
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(message);
        int x = textX - 16;
        int y = baselineY - metrics.getAscent();
        int boxWidth = width + 32;
        int boxHeight = metrics.getAscent() + 36;
        int arc = 28;

        Composite originalComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g.setColor(fillColor);
        g.fillRoundRect(x, y, boxWidth, boxHeight, arc, arc);
        g.setComposite(originalComposite);

        Stroke originalStroke = g.getStroke();
        g.setStroke(new BasicStroke(4f));
        g.setColor(borderColor);
        g.drawRoundRect(x, y, boxWidth, boxHeight, arc, arc);
        g.setStroke(originalStroke);

        g.setColor(Color.BLACK);
        g.drawString(message, textX, baselineY + metrics.getAscent() / 2);
    }
}
