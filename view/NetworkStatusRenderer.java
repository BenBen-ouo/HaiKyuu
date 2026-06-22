/*
繪製區域網路模式的本機資訊、連線狀態與雙方重設確認提示。
此類別永遠使用未鏡像的座標，因此文字在 Player 2 畫面也維持正常可讀。
*/
package view;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import model.GameConfig;
import network.NetworkView;

public class NetworkStatusRenderer {
    public void draw(Graphics2D g, NetworkView networkView) {
        if (networkView == null) {
            return;
        }

        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        drawHeader(g, networkView);
        drawConnectionMessage(g, networkView);
        drawResetMessage(g, networkView);
    }

    private void drawHeader(Graphics2D g, NetworkView networkView) {
        String text = networkView.getHeaderText();
        g.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        FontMetrics metrics = g.getFontMetrics();

        int padding = 10;
        int width = metrics.stringWidth(text) + padding * 2;
        int height = metrics.getHeight() + padding;
        int x = GameConfig.SCREEN_WIDTH - width - 12;
        int y = 10;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(Color.DARK_GRAY);
        g.drawRoundRect(x, y, width, height, 12, 12);
        g.drawString(text, x + padding, y + padding / 2 + metrics.getAscent());
    }

    private void drawConnectionMessage(Graphics2D g, NetworkView networkView) {
        String text = networkView.getConnectionMessage();
        g.setFont(new Font("Microsoft JhengHei", Font.BOLD, 20));
        FontMetrics metrics = g.getFontMetrics();

        int width = metrics.stringWidth(text) + 28;
        int height = metrics.getHeight() + 18;
        int x = GameConfig.SCREEN_WIDTH / 2 - width / 2;
        int y = 55;

        Color border = networkView.isConnected()
                ? new Color(30, 130, 70)
                : new Color(200, 80, 30);
        Color fill = networkView.isConnected()
                ? new Color(225, 250, 232)
                : new Color(255, 236, 220);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g.setColor(fill);
        g.fillRoundRect(x, y, width, height, 16, 16);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(border);
        g.drawRoundRect(x, y, width, height, 16, 16);
        g.setColor(Color.BLACK);
        g.drawString(text, x + 14, y + 9 + metrics.getAscent());
    }

    private void drawResetMessage(Graphics2D g, NetworkView networkView) {
        String text = networkView.getResetMessage();
        if (text == null) {
            return;
        }

        g.setFont(new Font("Microsoft JhengHei", Font.BOLD, 24));
        FontMetrics metrics = g.getFontMetrics();

        int width = metrics.stringWidth(text) + 36;
        int height = metrics.getHeight() + 20;
        int x = GameConfig.SCREEN_WIDTH / 2 - width / 2;
        int y = GameConfig.SCREEN_HEIGHT / 2 - height / 2;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        g.setColor(new Color(255, 250, 205));
        g.fillRoundRect(x, y, width, height, 20, 20);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(new Color(170, 125, 0));
        g.drawRoundRect(x, y, width, height, 20, 20);
        g.setColor(Color.BLACK);
        g.drawString(text, x + 18, y + 10 + metrics.getAscent());
    }
}
