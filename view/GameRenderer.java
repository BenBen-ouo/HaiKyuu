package view;

import java.awt.*;
import java.awt.geom.AffineTransform;
import model.*;

public class GameRenderer {
    private final AssetLoader assets = new AssetLoader();

    public void render(Graphics2D g, GameModel model) {
        drawBackground(g);
        drawWorldBoundaryGuide(g);
        drawCourt(g);
        drawTeam(g, model.redTeam, true);
        drawTeam(g, model.blueTeam, false);
        drawBall(g, model.ball);
        drawScore(g, model);
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(new Color(225, 238, 248));
        g.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
    }

    private void drawWorldBoundaryGuide(Graphics2D g) { ///之後可以改成debug模式才顯示
        g.setColor(new Color(180, 180, 180));
        g.drawString("World boundary: left=" + (int) GameConfig.WORLD_LEFT +
                ", right=" + (int) GameConfig.WORLD_RIGHT +
                ", top=" + (int) GameConfig.WORLD_TOP, 16, 22);
    }

    private void drawCourt(Graphics2D g) {
        g.setColor(new Color(238, 190, 115));
        g.fillRect(0, (int) GameConfig.FLOOR_Y, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT - (int) GameConfig.FLOOR_Y);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(0, (int) GameConfig.FLOOR_Y, GameConfig.SCREEN_WIDTH, (int) GameConfig.FLOOR_Y);
        g.drawLine(GameConfig.SCREEN_WIDTH / 2, (int) GameConfig.FLOOR_Y, GameConfig.SCREEN_WIDTH / 2, GameConfig.SCREEN_HEIGHT);
        g.drawLine(GameConfig.SCREEN_WIDTH / 2 - 167,(int) GameConfig.FLOOR_Y,GameConfig.SCREEN_WIDTH / 2 - 167,GameConfig.SCREEN_HEIGHT);
        g.drawLine(GameConfig.SCREEN_WIDTH / 2 + 167,(int) GameConfig.FLOOR_Y,GameConfig.SCREEN_WIDTH / 2 + 167,GameConfig.SCREEN_HEIGHT);
        g.drawLine(GameConfig.SCREEN_WIDTH / 2 - 500,(int) GameConfig.FLOOR_Y,GameConfig.SCREEN_WIDTH / 2 - 500,GameConfig.SCREEN_HEIGHT);
        g.drawLine(GameConfig.SCREEN_WIDTH / 2 + 500,(int) GameConfig.FLOOR_Y,GameConfig.SCREEN_WIDTH / 2 + 500,GameConfig.SCREEN_HEIGHT);

        int netX = (int) (GameConfig.NET_X - GameConfig.NET_WIDTH / 2.0);
        g.setColor(new Color(70, 70, 80));
        g.fillRect(netX, (int) GameConfig.NET_TOP_Y, (int) GameConfig.NET_WIDTH, (int) GameConfig.NET_HEIGHT);
    }

    private void drawTeam(Graphics2D g, Team team, boolean redTeam) {
        drawPlayer(g, team.wingSpiker, redTeam);
        drawPlayer(g, team.backPlayer, redTeam);
        drawPlayer(g, team.setter, redTeam);
        drawPlayer(g, team.quickAttacker, redTeam);
    }

    private void drawPlayer(Graphics2D g, Player p, boolean redTeam) {
        Image img = assets.get(p.assetName);

        int imageX = (int) p.x;
        int imageY = (int) p.y;

        if (img != null) {
            g.drawImage(img, imageX, imageY, p.imageWidth, p.imageHeight, null);
        } else {
            g.setColor(redTeam ? new Color(220, 90, 90) : new Color(80, 125, 220));
            g.fillRoundRect(imageX, imageY, p.imageWidth, p.imageHeight, 14, 14);
        }

        drawPlayerHitBox(g, p, redTeam);
        drawPlayerStateText(g, p, imageX, imageY);
    }

    private void drawPlayerHitBox(Graphics2D g, Player p, boolean redTeam) {
        HitBox box = p.hitBox;

        int hitX = (int) Math.round(box.getX());
        int hitY = (int) Math.round(box.getY());
        int hitW = (int) Math.round(box.width);
        int hitH = (int) Math.round(box.height);

        AffineTransform oldTransform = g.getTransform();
        Composite oldComposite = g.getComposite();
        Stroke oldStroke = g.getStroke();

        g.rotate(
                Math.toRadians(box.rotationDegrees),
                box.getCenterX(),
                box.getCenterY()
        );

        if (redTeam) {
            g.setColor(new Color(255, 40, 40, 70));
        } else {
            g.setColor(new Color(0, 90, 255, 70));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.fillRoundRect(
                hitX,
                hitY,
                hitW,
                hitH,
                box.arcWidth,
                box.arcHeight
        );

        g.setComposite(oldComposite);

        if (redTeam) {
            g.setColor(new Color(220, 0, 0));
        } else {
            g.setColor(new Color(0, 60, 220));
        }

        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(
                hitX,
                hitY,
                hitW,
                hitH,
                box.arcWidth,
                box.arcHeight
        );

        g.setStroke(oldStroke);
        g.setTransform(oldTransform);
        g.setComposite(oldComposite);
    }

    private void drawPlayerStateText(Graphics2D g, Player p, int x, int y) {
        if (p.attacking) {
            g.setColor(Color.RED);
            g.drawString("ATK", x + 8, y - 8);
        }

        if (p.blocking) {
            g.setColor(Color.BLUE);
            g.drawString("BLK", x + 8, y - 22);
        }

        if (p.diving) {
            g.setColor(Color.MAGENTA);
            g.drawString("DIVE", x + 4, y - 8);
        }
    }

    private void drawBall(Graphics2D g, Ball ball) {
        Image img = assets.get("mikasa.jpg");

        int d = (int) (ball.radius * 2);
        int x = (int) (ball.x - ball.radius);
        int y = (int) (ball.y - ball.radius);

        if (img != null) {
            g.drawImage(img, x, y, d, d, null);
        } else {
            g.setColor(new Color(245, 210, 60));
            g.fillOval(x, y, d, d);

            g.setColor(Color.BLACK);
            g.drawOval(x, y, d, d);
        }
    }

    private void drawScore(Graphics2D g, GameModel model) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(model.redScore + " : " + model.blueScore, GameConfig.SCREEN_WIDTH / 2 - 28, 44);
    }
}