package view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import model.AppVersion;
import model.GameConfig;

/** 讀取畫面快照，繪製版本、比分、得分原因與賽末結果。 */
public final class MatchDisplay {
    private static final Font VERSION_FONT = new Font("Arial", Font.PLAIN, 14);
    private static final Font SCORE_FONT = new Font("Arial", Font.BOLD, 24);
    private static final Font VICTORY_FONT = new Font("Arial", Font.BOLD, 48);
    private static final Font JUDGMENT_MEASURE_FONT = new Font("Microsoft JhengHei", Font.BOLD, 48);
    private static final Font JUDGMENT_FONT = new Font("Microsoft JhengHei", Font.BOLD, 36);
    private static final Font RESTART_HINT_FONT = new Font("Microsoft JhengHei", Font.PLAIN, 20);
    private static final Color RED_BORDER = new Color(200, 30, 30);
    private static final Color RED_FILL = new Color(255, 220, 220);
    private static final Color BLUE_BORDER = new Color(30, 80, 200);
    private static final Color BLUE_FILL = new Color(220, 230, 255);
    private static final AlphaComposite PANEL_COMPOSITE =
            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .9f);
    private static final BasicStroke PANEL_STROKE = new BasicStroke(4f);
    private static final int VERSION_RIGHT_MARGIN_PX = 12;
    private static final int VERSION_BASELINE_Y = 40;
    private static final int SCORE_BASELINE_Y = 44;
    private static final int PANEL_PADDING_X_PX = 16;
    private static final int PANEL_EXTRA_HEIGHT_PX = 36;
    private static final int PANEL_ARC_PX = 28;
    private static final int RESTART_HINT_GAP_PX = 20;
    private static final String VICTORY_MEASURE_TEXT = "RED WINS";
    private static final String RESTART_HINT = "Press R to restart";

    public void draw(Graphics2D g, RenderFrame.Match match, boolean bluePerspective) {
        g.setColor(Color.GRAY);
        g.setFont(VERSION_FONT);
        g.drawString(AppVersion.LABEL,
                GameConfig.SCREEN_WIDTH - g.getFontMetrics().stringWidth(AppVersion.LABEL)
                        - VERSION_RIGHT_MARGIN_PX, VERSION_BASELINE_Y);

        String score = bluePerspective ? match.blueScore() + " : " + match.redScore()
                        : match.redScore() + " : " + match.blueScore();
        g.setColor(Color.BLACK);
        g.setFont(SCORE_FONT);
        g.drawString(score, (GameConfig.SCREEN_WIDTH - g.getFontMetrics().stringWidth(score)) / 2,
                SCORE_BASELINE_Y);

        if (match.matchOver() && match.transientMessageTimer() <= 0) {
            drawVictory(g, match.matchWinnerRed());
        } else if (match.transientMessage() != null && match.transientMessageTimer() > 0) {
            drawJudgment(g, match.transientMessage(), match.transientMessageIsRed());
        }
    }

    private void drawVictory(Graphics2D g, Boolean redWinner) {
        String text = Boolean.TRUE.equals(redWinner) ? "RED WINS" : "BLUE WINS";
        g.setFont(VICTORY_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = GameConfig.SCREEN_WIDTH / 2 - width / 2;
        int y = GameConfig.SCREEN_HEIGHT / 2;
        drawPanel(g, new Rectangle(x - PANEL_PADDING_X_PX, y - metrics.getAscent(),
                width + PANEL_PADDING_X_PX * 2, metrics.getAscent() + PANEL_EXTRA_HEIGHT_PX), redWinner);
        g.setColor(Color.BLACK);
        g.drawString(text, x, y + metrics.getAscent() / 2);

        g.setFont(RESTART_HINT_FONT);
        int hintX = GameConfig.SCREEN_WIDTH / 2 - g.getFontMetrics().stringWidth(RESTART_HINT) / 2;
        g.drawString(RESTART_HINT, hintX, y + metrics.getAscent() + RESTART_HINT_GAP_PX);
    }

    private void drawJudgment(Graphics2D g, String text, Boolean redSide) {
        FontMetrics measure = g.getFontMetrics(JUDGMENT_MEASURE_FONT);
        int width = measure.stringWidth(VICTORY_MEASURE_TEXT) + PANEL_PADDING_X_PX * 2;
        int height = measure.getAscent() + PANEL_EXTRA_HEIGHT_PX;
        Rectangle bounds = new Rectangle(GameConfig.SCREEN_WIDTH / 2 - width / 2,
                GameConfig.SCREEN_HEIGHT / 2 - height / 2, width, height);
        drawPanel(g, bounds, redSide);
        g.setFont(JUDGMENT_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
        int baseline = bounds.y + (bounds.height - metrics.getAscent() - metrics.getDescent()) / 2
                + metrics.getAscent();
        g.setColor(Color.BLACK);
        g.drawString(text, x, baseline);
    }

    private void drawPanel(Graphics2D g, Rectangle bounds, Boolean redSide) {
        Color fill = redSide == null ? Color.WHITE : redSide ? RED_FILL : BLUE_FILL;
        Color border = redSide == null ? Color.BLACK : redSide ? RED_BORDER : BLUE_BORDER;
        Composite originalComposite = g.getComposite();
        g.setComposite(PANEL_COMPOSITE);
        g.setColor(fill);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, PANEL_ARC_PX, PANEL_ARC_PX);
        g.setComposite(originalComposite);
        Stroke originalStroke = g.getStroke();
        g.setStroke(PANEL_STROKE);
        g.setColor(border);
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, PANEL_ARC_PX, PANEL_ARC_PX);
        g.setStroke(originalStroke);
    }
}
