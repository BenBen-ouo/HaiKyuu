package view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;

/** 只繪製已擷取的角色圖片及權威碰撞框，不讀取可變角色。 */
public final class PlayerRenderer {
    private static final Color RED_HITBOX_FILL = new Color(255, 40, 40, 70);
    private static final Color BLUE_HITBOX_FILL = new Color(0, 90, 255, 70);
    private static final Color RED_HITBOX_STROKE = new Color(220, 0, 0);
    private static final Color BLUE_HITBOX_STROKE = new Color(0, 60, 220);
    private static final Color ATTACK_HITBOX_FILL = new Color(255, 190, 0, 80);
    private static final Color ATTACK_HITBOX_STROKE = new Color(255, 140, 0);
    private static final float REGULAR_ALPHA = .45f;
    private static final float BLOCK_ALPHA = .55f;
    private static final int HITBOX_STROKE_WIDTH = 2;
    private static final int FALLBACK_BODY_ARC = 14;

    private final AssetLoader assets;

    public PlayerRenderer(AssetLoader assets) {
        this.assets = assets;
    }

    public void draw(Graphics2D g, RenderFrame.PlayerImage player) {
        int x = (int) Math.round(player.x());
        int y = (int) Math.round(player.y());
        Image image = assets.get(player.assetName());
        if (image != null) {
            g.drawImage(image, player.mirrorImage() ? x + player.width() : x, y,
                    player.mirrorImage() ? -player.width() : player.width(), player.height(), null);
        } else {
            g.setColor(player.redSide() ? new Color(220, 90, 90) : new Color(80, 125, 220));
            g.fillRoundRect(x, y, player.width(), player.height(), FALLBACK_BODY_ARC, FALLBACK_BODY_ARC);
        }
        if (!DebugSettings.areHitBoxesVisible()) return;
        Color fill = player.redSide() ? RED_HITBOX_FILL : BLUE_HITBOX_FILL;
        Color stroke = player.redSide() ? RED_HITBOX_STROKE : BLUE_HITBOX_STROKE;
        drawBox(g, player.regular(), fill, stroke, REGULAR_ALPHA, false);
        drawBox(g, player.block(), fill, Color.BLACK, BLOCK_ALPHA, false);
        drawBox(g, player.head(), fill, Color.BLACK, BLOCK_ALPHA, true);
        drawBox(g, player.attack(), ATTACK_HITBOX_FILL, ATTACK_HITBOX_STROKE, REGULAR_ALPHA, false);
    }

    private void drawBox(Graphics2D g, RenderFrame.Box box, Color fill, Color stroke,
            float alpha, boolean ellipse) {
        if (box == null) return;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            layer.rotate(Math.toRadians(box.rotationDegrees()),
                    box.x() + box.width() / 2, box.y() + box.height() / 2);
            int x = (int) Math.round(box.x());
            int y = (int) Math.round(box.y());
            int width = (int) Math.round(box.width());
            int height = (int) Math.round(box.height());
            layer.setColor(fill);
            layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (ellipse) layer.fillOval(x, y, width, height);
            else layer.fillRoundRect(x, y, width, height, box.arcWidth(), box.arcHeight());
            layer.setComposite(AlphaComposite.SrcOver);
            layer.setColor(stroke);
            layer.setStroke(new BasicStroke(HITBOX_STROKE_WIDTH));
            if (ellipse) layer.drawOval(x, y, width, height);
            else layer.drawRoundRect(x, y, width, height, box.arcWidth(), box.arcHeight());
        } finally {
            layer.dispose();
        }
    }
}
