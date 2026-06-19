/*
負責繪製 EffectManager 中保存的視覺特效。
之後球軌跡、落地煙霧等圖片效果會由這裡顯示。
*/
package view;

import java.awt.*;
import model.EffectManager;
import model.VisualEffect;

public class EffectRenderer {
    private static final int DEFAULT_SIZE = 40;
    private final AssetLoader assets;

    public EffectRenderer(AssetLoader assets) {
        this.assets = assets;
    }

    public void draw(Graphics2D g, EffectManager manager) {
        for (VisualEffect effect : manager.getEffects()) {
            drawEffect(g, effect);
        }
    }

    private void drawEffect(Graphics2D g, VisualEffect effect) {
        Image image = assets.get(effect.assetName);

        if (image != null) {
            g.drawImage(image, (int) effect.x, (int) effect.y, DEFAULT_SIZE, DEFAULT_SIZE, null);
        }
    }
}
