/*
畫面渲染總入口，負責依序呼叫球場、角色、球、特效與分數繪製。
本身只做繪圖流程調度，不直接處理細節繪圖。
*/
package view;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
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

        // 2. 繪製落地煙霧 (Landing Smoke) - 程式化粒子煙霧（取代原本的圖片效果）
        java.util.List<SpikeEffect.SmokeParticle> smokeParticles = spikeEffect.getSmokeParticles();
        if (!smokeParticles.isEmpty()) {
            Composite origComposite = g.getComposite();
            Paint origPaint = g.getPaint();

            // 離屏圖層（整張畫面大小，簡單但可靠）
            int w = GameConfig.SCREEN_WIDTH;
            int h = GameConfig.SCREEN_HEIGHT;
            BufferedImage smokeLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = smokeLayer.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (SpikeEffect.SmokeParticle p : smokeParticles) {
                double lifeRatio = (double) p.remainingFrames / p.maxFrames;
                if (lifeRatio <= 0) continue;

                // 更深的基礎 alpha，並利用多個重疊 blob 形成不規則形狀
                float baseAlpha = (float) (0.6 * lifeRatio);

                float radius = (float) p.currentRadius;
                float cx = (float) p.x;
                float cy = (float) p.y;

                int blobs = p.blobCount;
                for (int i = 0; i < blobs; i++) {
                    float ox = p.ox[i];
                    float oy = p.oy[i];
                    float br = p.br[i];
                    float ba = p.ba[i];

                    float rBlob = radius * br;
                    float cx_i = cx + ox;
                    float cy_i = cy + oy;

                    float[] dist = {0.0f, 0.6f, 1.0f};
                    Color[] colors = new Color[] {
                        new Color(0.18f, 0.18f, 0.18f, baseAlpha * ba),
                        new Color(0.12f, 0.12f, 0.12f, baseAlpha * ba * 0.5f),
                        new Color(0f, 0f, 0f, 0f)
                    };

                    RadialGradientPaint rgp = new RadialGradientPaint(new Point2D.Float(cx_i, cy_i), rBlob, dist, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);
                    sg.setPaint(rgp);

                    // 使用多邊形近似不規則 blob（採用事前產生的 radius offsets）
                    float[] offsets = p.shapeOffset[i];
                    int pts = offsets.length;
                    Path2D.Float poly = new Path2D.Float();
                    for (int pi = 0; pi < pts; pi++) {
                        double ang = 2.0 * Math.PI * pi / pts;
                        float mul = offsets[pi];
                        float sx = cx_i + (float) Math.cos(ang) * rBlob * mul;
                        float sy = cy_i + (float) Math.sin(ang) * rBlob * mul * 0.6f; // y 壓扁
                        if (pi == 0) {
                            poly.moveTo(sx, sy);
                        } else {
                            poly.lineTo(sx, sy);
                        }
                    }
                    poly.closePath();

                    AffineTransform at = AffineTransform.getRotateInstance(p.rot[i], cx_i, cy_i);
                    Shape transformed = at.createTransformedShape(poly);
                    sg.fill(transformed);
                }
            }

            sg.dispose();

            // 5x5 高斯近似核
            int[] gk = {1, 4, 6, 4, 1};
            float[] kernel = new float[25];
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    kernel[i * 5 + j] = (gk[i] * gk[j]) / 256f; // normalize by 16*16
                }
            }

            ConvolveOp cop = new ConvolveOp(new Kernel(5, 5, kernel), ConvolveOp.EDGE_NO_OP, null);
            BufferedImage blurred = cop.filter(smokeLayer, null);

            // 把模糊後的煙霧疊回主畫面
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(blurred, 0, 0, null);

            g.setPaint(origPaint);
            g.setComposite(origComposite);
        }
    }

    private void drawScore(Graphics2D g, GameModel model) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(model.redScore + " : " + model.blueScore, GameConfig.SCREEN_WIDTH / 2 - 28, 44);
    }
}
