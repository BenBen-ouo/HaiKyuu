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
        draw(g, ball, ball.x, ball.y, ball.rotationDegrees);
    }

    /** 球物理仍使用 Ball；畫面座標可由網路校正層暫時覆蓋。 */
    public void draw(Graphics2D g, Ball ball, double renderX, double renderY, double renderRotationDegrees) {
        Image image = assets.get("mikasa.png");

        if (image == null) {
            return;
        }

        int diameter = (int) (ball.radius * 2);
        int x = (int) (renderX - ball.radius);
        int y = (int) (renderY - ball.radius);

        Graphics2D rotatedGraphics = (Graphics2D) g.create();
        rotatedGraphics.rotate(Math.toRadians(renderRotationDegrees), renderX, renderY);
        rotatedGraphics.drawImage(image, x, y, diameter, diameter, null);
        rotatedGraphics.dispose();
    }
}