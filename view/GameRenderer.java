/*
畫面渲染總入口，負責依序呼叫球場、角色、球、特效與分數繪製。
本身只做繪圖流程調度，不直接處理細節繪圖。
*/
package view;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import model.*;
import network.NetworkView;

public class GameRenderer {
    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);
    private final EffectRenderer effectRenderer = new EffectRenderer(assets);
    private final MatchDisplay matchDisplay = new MatchDisplay();

    public void render(Graphics2D g, GameModel model) {
        render(g, model, false);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld) {
        render(g, model, mirrorWorld, null);
    }

    public void render(Graphics2D g, GameModel model, boolean mirrorWorld, NetworkView networkView) {
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        if (mirrorWorld) {
            drawMirroredWorld(g, model, networkView);
        } else {
            drawWorld(g, model, true, networkView);
        }

        // 比分、規則提示與比賽結束畫面永遠保持正常方向。
        matchDisplay.draw(g, model, mirrorWorld);
    }

    private void drawMirroredWorld(Graphics2D g, GameModel model, NetworkView networkView) {
        Graphics2D worldGraphics = (Graphics2D) g.create();
        worldGraphics.translate(GameConfig.SCREEN_WIDTH, 0);
        worldGraphics.scale(-1, 1);
        drawWorld(worldGraphics, model, false, networkView);
        worldGraphics.dispose();

        // 世界座標已鏡像，但除錯文字維持可讀。
        courtRenderer.drawWorldBoundaryGuide(g);
        playerRenderer.drawMirroredStateLabels(g, model.redTeam);
        playerRenderer.drawMirroredStateLabels(g, model.blueTeam);
    }

    private void drawWorld(Graphics2D g, GameModel model, boolean drawStateLabels, NetworkView networkView) {
        courtRenderer.draw(g, drawStateLabels);
        drawNetHitBox(g);

        playerRenderer.drawTeam(g, model.redTeam, true, drawStateLabels);
        playerRenderer.drawTeam(g, model.blueTeam, false, drawStateLabels);

        double ballX = networkView == null ? model.ball.x : networkView.getRenderedBallX(model.ball.x);
        double ballY = networkView == null ? model.ball.y : networkView.getRenderedBallY(model.ball.y);
        double ballRotation = networkView == null
                ? model.ball.rotationDegrees
                : networkView.getRenderedBallRotation(model.ball.rotationDegrees);
        ballRenderer.draw(g, model.ball, ballX, ballY, ballRotation);
        effectRenderer.draw(g, model.effects);
        drawSpikeEffects(g, model.spikeEffect);
    }

    private void drawSpikeEffects(Graphics2D g, SpikeEffect spikeEffect) {
        // 1. 繪製扣球軌跡。
        java.util.List<SpikeEffect.TrailPoint> points = spikeEffect.getTrailPoints();
        if (points.size() >= 2) {
            Stroke originalStroke = g.getStroke();
            Object originalAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            for (int i = 0; i < points.size() - 1; i++) {
                SpikeEffect.TrailPoint p1 = points.get(i);
                SpikeEffect.TrailPoint p2 = points.get(i + 1);

                // 不把紅、藍兩隊不同扣球的軌跡接成一條線。
                if (p1.isRedTeam != p2.isRedTeam) {
                    continue;
                }

                double lifeRatio = (
                        (double) p1.remainingFrames / p1.maxFrames
                                + (double) p2.remainingFrames / p2.maxFrames
                ) / 2.0;

                if (lifeRatio <= 0) {
                    continue;
                }

                float hue = p1.isRedTeam
                        ? SpikeEffect.RED_HUE
                        : SpikeEffect.BLUE_HUE;

                Color baseColor = Color.getHSBColor(
                        hue,
                        SpikeEffect.TRAIL_SATURATION,
                        SpikeEffect.TRAIL_BRIGHTNESS
                );

                // 外層羽化光暈。
                int outerAlpha = (int) (100 * lifeRatio);
                g.setColor(new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        outerAlpha
                ));
                g.setStroke(new BasicStroke(
                        (float) (12.0 * lifeRatio),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);

                // 內層核心。
                int coreAlpha = (int) (225 * lifeRatio);
                g.setColor(new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        coreAlpha
                ));
                g.setStroke(new BasicStroke(
                        (float) (5.0 * lifeRatio),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
            }

            g.setStroke(originalStroke);

            if (originalAntialias != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialias);
            }
        }

        // 2. 繪製球落地時的煙霧。
        java.util.List<SpikeEffect.SmokeParticle> smokeParticles =
                spikeEffect.getSmokeParticles();

        if (smokeParticles.isEmpty()) {
            return;
        }

        Composite originalComposite = g.getComposite();
        Paint originalPaint = g.getPaint();

        int width = GameConfig.SCREEN_WIDTH;
        int height = GameConfig.SCREEN_HEIGHT;

        BufferedImage smokeLayer = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D smokeGraphics = smokeLayer.createGraphics();
        smokeGraphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        for (SpikeEffect.SmokeParticle particle : smokeParticles) {
            double lifeRatio = (double) particle.remainingFrames / particle.maxFrames;
            if (lifeRatio <= 0) {
                continue;
            }

            float baseAlpha = (float) (0.20 * lifeRatio);
            float radius = (float) particle.currentRadius;
            float centerX = (float) particle.x;
            float centerY = (float) particle.y;

            for (int i = 0; i < particle.blobCount; i++) {
                float blobRadius = radius * particle.br[i];
                float blobX = centerX + particle.ox[i];
                float blobY = centerY + particle.oy[i];

                float[] distances = {0.0f, 0.6f, 1.0f};
                Color[] colors = {
                        new Color(
                                0.18f,
                                0.18f,
                                0.18f,
                                baseAlpha * particle.ba[i]
                        ),
                        new Color(
                                0.12f,
                                0.12f,
                                0.12f,
                                baseAlpha * particle.ba[i] * 0.5f
                        ),
                        new Color(0f, 0f, 0f, 0f)
                };

                RadialGradientPaint gradient = new RadialGradientPaint(
                        new Point2D.Float(blobX, blobY),
                        blobRadius,
                        distances,
                        colors,
                        MultipleGradientPaint.CycleMethod.NO_CYCLE
                );

                smokeGraphics.setPaint(gradient);

                float[] offsets = particle.shapeOffset[i];
                Path2D.Float shape = new Path2D.Float();

                for (int pointIndex = 0; pointIndex < offsets.length; pointIndex++) {
                    double angle = 2.0 * Math.PI * pointIndex / offsets.length;
                    float multiplier = offsets[pointIndex];

                    float x = blobX
                            + (float) Math.cos(angle) * blobRadius * multiplier;

                    float y = blobY
                            + (float) Math.sin(angle) * blobRadius * multiplier * 0.6f;

                    if (pointIndex == 0) {
                        shape.moveTo(x, y);
                    } else {
                        shape.lineTo(x, y);
                    }
                }

                shape.closePath();

                AffineTransform rotation = AffineTransform.getRotateInstance(
                        particle.rot[i],
                        blobX,
                        blobY
                );

                Shape rotatedShape = rotation.createTransformedShape(shape);
                smokeGraphics.fill(rotatedShape);
            }
        }

        smokeGraphics.dispose();

        // 5 × 5 高斯近似模糊核心。
        int[] gaussianValues = {1, 4, 6, 4, 1};
        float[] kernelValues = new float[25];

        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                kernelValues[row * 5 + column] =
                        (gaussianValues[row] * gaussianValues[column]) / 256f;
            }
        }

        ConvolveOp blur = new ConvolveOp(
                new Kernel(5, 5, kernelValues),
                ConvolveOp.EDGE_NO_OP,
                null
        );

        BufferedImage blurredSmoke = blur.filter(smokeLayer, null);

        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                0.6f
        ));
        g.drawImage(blurredSmoke, 0, 0, null);

        g.setPaint(originalPaint);
        g.setComposite(originalComposite);
    }

    private void drawNetHitBox(Graphics2D g) {
        int x = (int) Math.round(
                GameConfig.NET_HITBOX_CENTER_X
                        - GameConfig.NET_HITBOX_WIDTH / 2.0
        );
        int y = (int) Math.round(GameConfig.NET_HITBOX_TOP_Y);
        int width = (int) Math.round(GameConfig.NET_HITBOX_WIDTH);
        int height = (int) Math.round(GameConfig.NET_HITBOX_HEIGHT);

        Graphics2D debugGraphics = (Graphics2D) g.create();

        debugGraphics.setColor(new Color(40, 210, 90, 70));
        debugGraphics.fillRect(x, y, width, height);

        debugGraphics.setColor(new Color(0, 135, 55));
        debugGraphics.setStroke(new BasicStroke(2));
        debugGraphics.drawRect(x, y, width, height);

        debugGraphics.dispose();
    }
}