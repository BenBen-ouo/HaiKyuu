/*
畫面渲染總入口，負責依序呼叫球場、角色、球、特效與分數繪製。
本身只做繪圖流程調度，不直接處理細節繪圖。
*/
package view;

import java.awt.*;
import model.*;

public class GameRenderer {
    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);
    private final EffectRenderer effectRenderer = new EffectRenderer(assets);

    public void render(Graphics2D g, GameModel model) {
        courtRenderer.draw(g);
        playerRenderer.drawTeam(g, model.redTeam, true);
        playerRenderer.drawTeam(g, model.blueTeam, false);
        ballRenderer.draw(g, model.ball);
        effectRenderer.draw(g, model.effects);
        drawSpikeEffects(g, model.spikeEffect);
        drawScore(g, model);
    }

    private void drawSpikeEffects(Graphics2D g, SpikeEffect spikeEffect) {
        // 1. 繪製扣球軌跡 (Spike Trail) - 單一層帶羽化線段的光束
        java.util.List<SpikeEffect.TrailPoint> points = spikeEffect.getTrailPoints();
        if (points.size() >= 2) {
            Stroke origStroke = g.getStroke();
            Object origAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (int i = 0; i < points.size() - 1; i++) {
                SpikeEffect.TrailPoint p1 = points.get(i);
                SpikeEffect.TrailPoint p2 = points.get(i + 1);

                // 確保是同一隊的軌跡線段
                if (p1.isRedTeam != p2.isRedTeam) {
                    continue;
                }

                double lifeRatio = ((double) p1.remainingFrames / p1.maxFrames + (double) p2.remainingFrames / p2.maxFrames) / 2.0;
                if (lifeRatio <= 0) continue;

                float hue = p1.isRedTeam ? SpikeEffect.RED_HUE : SpikeEffect.BLUE_HUE;
                Color baseColor = Color.getHSBColor(hue, SpikeEffect.TRAIL_SATURATION, SpikeEffect.TRAIL_BRIGHTNESS);

                // 1.1 外層羽化發光邊界
                int alphaOuter = (int) (100 * lifeRatio);
                g.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaOuter));
                g.setStroke(new BasicStroke((float) (12.0 * lifeRatio), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);

                // 1.2 內層實心核心 (形成單一條帶羽化邊緣的光條)
                int alphaCore = (int) (225 * lifeRatio);
                g.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaCore));
                g.setStroke(new BasicStroke((float) (5.0 * lifeRatio), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
            }

            g.setStroke(origStroke);
            if (origAntialias != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAntialias);
            }
        }

        // 2. 繪製落地煙霧 (Landing Smoke) - 圖片效果（左半場使用 smoke_effect2.jpg，右半場使用 smoke_effect.jpg）
        java.util.List<SpikeEffect.SmokeParticle> smokeParticles = spikeEffect.getSmokeParticles();
        if (!smokeParticles.isEmpty()) {
            String smokeImgName = spikeEffect.shouldUseSmokeEffect2() ? "smoke2.png" : "smoke.png";
            Image smokeImg = assets.get(smokeImgName);
            if (smokeImg != null) {
                Composite origComposite = g.getComposite();

                for (SpikeEffect.SmokeParticle p : smokeParticles) {
                    double lifeRatio = (double) p.remainingFrames / p.maxFrames;
                    if (lifeRatio <= 0) continue;

                    // 顏色淡雅，最大透明度約 0.32
                    float alpha = (float) (0.32 * lifeRatio);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                    int size = (int) (p.currentRadius * 2);

                    // 直接在粒子中心以座標繪製（不改變 transform）
                    g.drawImage(smokeImg, (int) (p.x - size / 2), (int) (p.y - size / 2), size, size, null);
                }
                g.setComposite(origComposite);
            }
        }
    }

    private void drawScore(Graphics2D g, GameModel model) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(model.redScore + " : " + model.blueScore, GameConfig.SCREEN_WIDTH / 2 - 28, 44);
    }
}
