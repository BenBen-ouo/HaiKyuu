/*
負責繪製所有球員、圖片翻轉與碰撞箱。
一般、攻擊與攔網使用的碰撞箱只在 DebugSettings 啟用時繪製，判定本身仍由 model 層執行。
*/
package view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import model.player.AttackHitBox;
import model.player.BlockHitBox;
import model.player.HitBox;
import model.player.Player;
import model.player.QuickAttacker;
import model.player.Team;

public class PlayerRenderer {
    private static final Color RED_HITBOX_FILL = new Color(255, 40, 40, 70);
    private static final Color BLUE_HITBOX_FILL = new Color(0, 90, 255, 70);
    private static final Color RED_HITBOX_STROKE = new Color(220, 0, 0);
    private static final Color BLUE_HITBOX_STROKE = new Color(0, 60, 220);
    private static final Color ATTACK_HITBOX_FILL = new Color(255, 190, 0, 80);
    private static final Color ATTACK_HITBOX_STROKE = new Color(255, 140, 0);

    private final AssetLoader assets;

    public PlayerRenderer(AssetLoader assets) {
        this.assets = assets;
    }

    public void drawTeam(Graphics2D g, Team team, boolean redTeam) {
        drawPlayer(g, team.wingSpiker, redTeam);
        drawPlayer(g, team.backPlayer, redTeam);
        drawPlayer(g, team.setter, redTeam);
        drawPlayer(g, team.quickAttacker, redTeam);
    }

    private void drawPlayer(Graphics2D g, Player player, boolean redTeam) {
        int imageX = (int) player.x;
        int imageY = (int) player.y;
        Image image = assets.get(player.assetName);

        if (image != null) {
            drawPlayerImage(g, image, player, imageX, imageY);
        } else {
            drawFallbackBody(g, player, redTeam, imageX, imageY);
        }

        if (DebugSettings.areHitBoxesVisible()) {
            drawDefaultHitBox(g, player, redTeam);
            drawBlockHitBox(g, player, redTeam);
            drawAttackHitBox(g, player);
        }
    }

    private void drawPlayerImage(Graphics2D g, Image image, Player player, int x, int y) {
        if (player.mirrorImage) {
            g.drawImage(image, x + player.imageWidth, y, -player.imageWidth, player.imageHeight, null);
            return;
        }

        g.drawImage(image, x, y, player.imageWidth, player.imageHeight, null);
    }

    private void drawFallbackBody(Graphics2D g, Player player, boolean redTeam, int x, int y) {
        g.setColor(redTeam ? new Color(220, 90, 90) : new Color(80, 125, 220));
        g.fillRoundRect(x, y, player.imageWidth, player.imageHeight, 14, 14);
    }

    private void drawDefaultHitBox(Graphics2D g, Player player, boolean redTeam) {
        if (!player.isDefaultHitBoxActive()) {
            return;
        }

        HitBox box = player.hitBox;
        Graphics2D hitBoxGraphics = (Graphics2D) g.create();
        try {
            hitBoxGraphics.rotate(Math.toRadians(box.rotationDegrees), box.getCenterX(), box.getCenterY());
            hitBoxGraphics.setColor(hitBoxFillColor(redTeam));
            hitBoxGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            hitBoxGraphics.fillRoundRect(
                    hitX(box),
                    hitY(box),
                    hitWidth(box),
                    hitHeight(box),
                    box.arcWidth,
                    box.arcHeight
            );

            hitBoxGraphics.setComposite(AlphaComposite.SrcOver);
            hitBoxGraphics.setColor(hitBoxStrokeColor(redTeam));
            hitBoxGraphics.setStroke(new BasicStroke(2));
            hitBoxGraphics.drawRoundRect(
                    hitX(box),
                    hitY(box),
                    hitWidth(box),
                    hitHeight(box),
                    box.arcWidth,
                    box.arcHeight
            );
        } finally {
            hitBoxGraphics.dispose();
        }
    }

    private void drawAttackHitBox(Graphics2D g, Player player) {
        AttackHitBox box = player.attackHitBox;
        if (!box.enabled) {
            return;
        }

        Graphics2D hitBoxGraphics = (Graphics2D) g.create();
        try {
            hitBoxGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            hitBoxGraphics.setColor(ATTACK_HITBOX_FILL);
            hitBoxGraphics.fillRect(attackX(box), attackY(box), attackWidth(box), attackHeight(box));

            hitBoxGraphics.setComposite(AlphaComposite.SrcOver);
            hitBoxGraphics.setColor(ATTACK_HITBOX_STROKE);
            hitBoxGraphics.setStroke(new BasicStroke(2));
            hitBoxGraphics.drawRect(attackX(box), attackY(box), attackWidth(box), attackHeight(box));
        } finally {
            hitBoxGraphics.dispose();
        }
    }

    private void drawBlockHitBox(Graphics2D g, Player player, boolean redTeam) {
        if (!(player instanceof QuickAttacker blocker) || !blocker.isBlockHitBoxActive()) {
            return;
        }

        BlockHitBox box = blocker.blockHitBox;
        Graphics2D hitBoxGraphics = (Graphics2D) g.create();
        try {
            hitBoxGraphics.rotate(Math.toRadians(box.rotationDegrees), box.getCenterX(), box.getCenterY());
            hitBoxGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            hitBoxGraphics.setColor(hitBoxFillColor(redTeam));
            hitBoxGraphics.fillRoundRect(
                    hitX(box),
                    hitY(box),
                    hitWidth(box),
                    hitHeight(box),
                    box.arcWidth,
                    box.arcHeight
            );

            hitBoxGraphics.setComposite(AlphaComposite.SrcOver);
            hitBoxGraphics.setColor(Color.BLACK);
            hitBoxGraphics.setStroke(new BasicStroke(2));
            hitBoxGraphics.drawRoundRect(
                    hitX(box),
                    hitY(box),
                    hitWidth(box),
                    hitHeight(box),
                    box.arcWidth,
                    box.arcHeight
            );
        } finally {
            hitBoxGraphics.dispose();
        }
    }

    private Color hitBoxFillColor(boolean redTeam) {
        return redTeam ? RED_HITBOX_FILL : BLUE_HITBOX_FILL;
    }

    private Color hitBoxStrokeColor(boolean redTeam) {
        return redTeam ? RED_HITBOX_STROKE : BLUE_HITBOX_STROKE;
    }

    private int hitX(HitBox box) {
        return (int) Math.round(box.getX());
    }

    private int hitY(HitBox box) {
        return (int) Math.round(box.getY());
    }

    private int hitWidth(HitBox box) {
        return (int) Math.round(box.width);
    }

    private int hitHeight(HitBox box) {
        return (int) Math.round(box.height);
    }

    private int attackX(AttackHitBox box) {
        return (int) Math.round(box.getX());
    }

    private int attackY(AttackHitBox box) {
        return (int) Math.round(box.getY());
    }

    private int attackWidth(AttackHitBox box) {
        return (int) Math.round(box.width);
    }

    private int attackHeight(AttackHitBox box) {
        return (int) Math.round(box.height);
    }
}
