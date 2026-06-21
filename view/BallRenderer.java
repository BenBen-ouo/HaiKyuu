/*
負責繪製排球圖片與畫面上的旋轉效果。
依照 Ball 的中心點、半徑與 rotationDegrees 計算圖片的位置與旋轉角度。
*/
package view;

import java.awt.*;
import model.Ball;

public class BallRenderer {
    private final AssetLoader assets;

    public BallRenderer(AssetLoader assets) {
        this.assets = assets;
    }

    public void draw(Graphics2D g, Ball ball) {
        Image image = assets.get("mikasa.png");

        if (image == null) {
            return;
        }

        int diameter = (int) (ball.radius * 2);
        int x = (int) (ball.x - ball.radius);
        int y = (int) (ball.y - ball.radius);

        Graphics2D rotatedGraphics = (Graphics2D) g.create();
        rotatedGraphics.rotate(Math.toRadians(ball.rotationDegrees), ball.x, ball.y);
        rotatedGraphics.drawImage(image, x, y, diameter, diameter, null);
        rotatedGraphics.dispose();
    }
}