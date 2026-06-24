/*
負責繪製背景、地板、球場線、世界邊界提示與網子。
球場外觀相關的繪圖集中在這裡，避免 GameRenderer 過長。
*/
package view;

import java.awt.*;
import model.GameConfig;

public class CourtRenderer {
    private static final Color BACKGROUND_COLOR = new Color(225, 238, 248);
    private static final Color FLOOR_COLOR = new Color(243, 146, 10);
    private static final Color OUTSIDE_COURT_COLOR = new Color(106, 200, 243);
    private static final Color NET_COLOR = new Color(70, 70, 80);
    private static final Color GUIDE_COLOR = new Color(180, 180, 180);

    public void draw(Graphics2D g) {
        draw(g, true);
    }

    public void draw(Graphics2D g, boolean drawWorldBoundaryGuide) {
        drawBackground(g);
        if (drawWorldBoundaryGuide) {
            drawWorldBoundaryGuide(g);
        }
        drawCourt(g);
    }

    public void drawWorldBoundaryGuide(Graphics2D g) {
        g.setColor(GUIDE_COLOR);
        g.drawString("World boundary: left=" + (int) GameConfig.WORLD_LEFT
                + ", right=" + (int) GameConfig.WORLD_RIGHT
                + ", top=" + (int) GameConfig.WORLD_TOP, 16, 22);
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(BACKGROUND_COLOR);
        g.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
    }

    private void drawCourt(Graphics2D g) {
        int floorY = (int) GameConfig.FLOOR_Y;
        int floorHeight = GameConfig.SCREEN_HEIGHT - floorY;
        int courtLeftX = GameConfig.SCREEN_WIDTH / 2 - 500;
        int courtRightX = GameConfig.SCREEN_WIDTH / 2 + 500;

        drawFloor(g, floorY, floorHeight, courtLeftX, courtRightX);
        drawCourtLines(g, floorY, courtLeftX, courtRightX);
        drawNet(g);
    }

    private void drawFloor(Graphics2D g, int floorY, int floorHeight, int courtLeftX, int courtRightX) {
        g.setColor(FLOOR_COLOR);
        g.fillRect(0, floorY, GameConfig.SCREEN_WIDTH, floorHeight);

        g.setColor(OUTSIDE_COURT_COLOR);
        g.fillRect(0, floorY, courtLeftX, floorHeight);
        g.fillRect(courtRightX, floorY, GameConfig.SCREEN_WIDTH - courtRightX, floorHeight);
    }

    private void drawCourtLines(Graphics2D g, int floorY, int courtLeftX, int courtRightX) {
        int centerX = GameConfig.SCREEN_WIDTH / 2;

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(0, floorY, GameConfig.SCREEN_WIDTH, floorY);
        g.drawLine(centerX, floorY, centerX, GameConfig.SCREEN_HEIGHT);
        int threeMeter = (int) GameConfig.THREE_METER_PX;
        g.drawLine(centerX - threeMeter, floorY, centerX - threeMeter, GameConfig.SCREEN_HEIGHT);
        g.drawLine(centerX + threeMeter, floorY, centerX + threeMeter, GameConfig.SCREEN_HEIGHT);
        g.drawLine(courtLeftX, floorY, courtLeftX, GameConfig.SCREEN_HEIGHT);
        g.drawLine(courtRightX, floorY, courtRightX, GameConfig.SCREEN_HEIGHT);
    }

    private void drawNet(Graphics2D g) {
        int netX = (int) (GameConfig.NET_X - GameConfig.NET_WIDTH / 2.0);

        g.setColor(NET_COLOR);
        g.fillRect(netX, (int) GameConfig.NET_TOP_Y, (int) GameConfig.NET_WIDTH, (int) GameConfig.NET_HEIGHT);
    }
}
