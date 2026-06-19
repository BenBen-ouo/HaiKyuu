/*
負責繪製排球圖片。
依照 Ball 的中心點與半徑計算圖片位置與大小。
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

        g.drawImage(image, x, y, diameter, diameter, null);
    }
}