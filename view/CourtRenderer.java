/*
負責繪製背景、地板、球場線、世界邊界提示與網子。
球場外觀相關的繪圖集中在這裡，避免 GameRenderer 過長。
*/
package view;

import java.awt.*;
import model.GameConfig;
import model.ball.NetHitBox;

public class CourtRenderer {
    private static final Color BACKGROUND_COLOR = new Color(225, 238, 248);
    private static final Color FLOOR_COLOR = new Color(255,178,101);
    private static final Color OUTSIDE_COURT_COLOR = new Color(106, 200, 243);
    private static final Color NET_COLOR = new Color(70, 70, 80);
    private static final Color NET_FLASH_COLOR = new Color(145, 145, 155);
    private static final Color POLE_COLOR = new Color(120, 20, 30);
    private static final int NET_VISIBLE_HEIGHT = 55;
    private static final int POLE_WIDTH = 6;
    private static final Color GUIDE_COLOR = new Color(180, 180, 180);
    private static final Shape NET_SHAPE = new NetHitBox().getShape();

    public void draw(Graphics2D g) {
        draw(g, true);
    }

    public void draw(Graphics2D g, boolean drawWorldBoundaryGuide) {
        draw(g, drawWorldBoundaryGuide, 0);
    }

    public void draw(Graphics2D g, boolean drawWorldBoundaryGuide, int netFlashFrames) {
        drawBackground(g);
        if (drawWorldBoundaryGuide) {
            drawWorldBoundaryGuide(g);
        }
        drawCourt(g, netFlashFrames);
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

    private void drawCourt(Graphics2D g, int netFlashFrames) {
        int floorY = GameConfig.FLOOR_Y_PX;
        int floorHeight = GameConfig.SCREEN_HEIGHT - floorY;
        int courtLeftX = (int) Math.round(GameConfig.COURT_LEFT_X);
        int courtRightX = (int) Math.round(GameConfig.COURT_RIGHT_X);

        drawFloor(g, floorY, floorHeight, courtLeftX, courtRightX);
        drawCourtLines(g, floorY, courtLeftX, courtRightX);
        drawNet(g, netFlashFrames);
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
        g.setStroke(new BasicStroke(GameConfig.COURT_LINE_WIDTH));
        g.drawLine(0, floorY, GameConfig.SCREEN_WIDTH, floorY);
        g.drawLine(centerX, floorY, centerX, GameConfig.SCREEN_HEIGHT);
        int threeMeter = (int) GameConfig.THREE_METER_PX;
        g.drawLine(centerX - threeMeter, floorY, centerX - threeMeter, GameConfig.SCREEN_HEIGHT);
        g.drawLine(centerX + threeMeter, floorY, centerX + threeMeter, GameConfig.SCREEN_HEIGHT);
        g.drawLine(courtLeftX, floorY, courtLeftX, GameConfig.SCREEN_HEIGHT);
        g.drawLine(courtRightX, floorY, courtRightX, GameConfig.SCREEN_HEIGHT);
    }

    private void drawNet(Graphics2D g, int netFlashFrames) {
        int top = (int) Math.round(GameConfig.FLOOR_Y - GameConfig.NET_HEIGHT);
        int split = top + NET_VISIBLE_HEIGHT;
        g.setColor(POLE_COLOR);
        g.fillRect((int) GameConfig.NET_X - POLE_WIDTH / 2, split,
                POLE_WIDTH, GameConfig.FLOOR_Y_PX - split);
        Graphics2D netGraphics = (Graphics2D) g.create();
        try {
            netGraphics.clipRect((int) GameConfig.NET_X - 4, top, 8, NET_VISIBLE_HEIGHT);
            int flashPeriod = GameConfig.NET_FLASH_FRAMES / GameConfig.NET_FLASH_COUNT;
            netGraphics.setColor(netFlashFrames > 0
                    && (GameConfig.NET_FLASH_FRAMES - netFlashFrames) % flashPeriod < flashPeriod / 2
                    ? NET_FLASH_COLOR : NET_COLOR);
            netGraphics.fill(NET_SHAPE);
        } finally {
            netGraphics.dispose();
        }
    }
}
