/*
繪製扣球軌跡與落地煙霧。
煙霧圖層與模糊器會重複使用，避免每幀配置全螢幕影像；輸出效果維持原本的 5×5 高斯近似模糊。
*/
package view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.List;
import model.GameConfig;
import model.SpikeEffect;

public class SpikeEffectRenderer {
    private static final float[] GRADIENT_DISTANCES = {0.0f, 0.6f, 1.0f};
    private final ConvolveOp smokeBlur = new ConvolveOp(
            new Kernel(5, 5, createGaussianKernel()),
            ConvolveOp.EDGE_NO_OP,
            null
    );

    private BufferedImage smokeLayer;
    private BufferedImage blurredSmoke;

    public void draw(Graphics2D g, SpikeEffect spikeEffect) {
        drawTrail(g, spikeEffect.getTrailPoints());
        drawSmoke(g, spikeEffect.getSmokeParticles());
    }

    private void drawTrail(Graphics2D g, List<SpikeEffect.TrailPoint> points) {
        if (points.size() < 2) {
            return;
        }

        Graphics2D trailGraphics = (Graphics2D) g.create();
        try {
            trailGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            for (int i = 0; i < points.size() - 1; i++) {
                SpikeEffect.TrailPoint first = points.get(i);
                SpikeEffect.TrailPoint second = points.get(i + 1);

                // 不把紅、藍兩隊不同扣球的軌跡接成一條線。
                if (first.isRedTeam != second.isRedTeam) {
                    continue;
                }

                double lifeRatio = averageLifeRatio(first, second);
                if (lifeRatio <= 0) {
                    continue;
                }

                Color baseColor = Color.getHSBColor(
                        first.isRedTeam ? SpikeEffect.RED_HUE : SpikeEffect.BLUE_HUE,
                        SpikeEffect.TRAIL_SATURATION,
                        SpikeEffect.TRAIL_BRIGHTNESS
                );
                drawTrailSegment(trailGraphics, first, second, baseColor, lifeRatio, 12.0f, 100);
                drawTrailSegment(trailGraphics, first, second, baseColor, lifeRatio, 5.0f, 225);
            }
        } finally {
            trailGraphics.dispose();
        }
    }

    private double averageLifeRatio(SpikeEffect.TrailPoint first, SpikeEffect.TrailPoint second) {
        return (
                (double) first.remainingFrames / first.maxFrames
                        + (double) second.remainingFrames / second.maxFrames
        ) / 2.0;
    }

    private void drawTrailSegment(
            Graphics2D g,
            SpikeEffect.TrailPoint first,
            SpikeEffect.TrailPoint second,
            Color baseColor,
            double lifeRatio,
            float baseStrokeWidth,
            int baseAlpha
    ) {
        g.setColor(new Color(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                (int) (baseAlpha * lifeRatio)
        ));
        g.setStroke(new BasicStroke(
                (float) (baseStrokeWidth * lifeRatio),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));
        g.drawLine((int) first.x, (int) first.y, (int) second.x, (int) second.y);
    }

    private void drawSmoke(Graphics2D g, List<SpikeEffect.SmokeParticle> smokeParticles) {
        if (smokeParticles.isEmpty()) {
            return;
        }

        ensureSmokeBuffers();
        clearImage(smokeLayer);
        clearImage(blurredSmoke);

        Graphics2D smokeGraphics = smokeLayer.createGraphics();
        try {
            smokeGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            for (SpikeEffect.SmokeParticle particle : smokeParticles) {
                drawSmokeParticle(smokeGraphics, particle);
            }
        } finally {
            smokeGraphics.dispose();
        }

        smokeBlur.filter(smokeLayer, blurredSmoke);

        Graphics2D outputGraphics = (Graphics2D) g.create();
        try {
            outputGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            outputGraphics.drawImage(blurredSmoke, 0, 0, null);
        } finally {
            outputGraphics.dispose();
        }
    }

    private void drawSmokeParticle(Graphics2D g, SpikeEffect.SmokeParticle particle) {
        double lifeRatio = (double) particle.remainingFrames / particle.maxFrames;
        if (lifeRatio <= 0) {
            return;
        }

        float baseAlpha = (float) (0.20 * lifeRatio);
        float radius = (float) particle.currentRadius;
        float centerX = (float) particle.x;
        float centerY = (float) particle.y;

        for (int i = 0; i < particle.blobCount; i++) {
            float blobRadius = radius * particle.br[i];
            float blobX = centerX + particle.ox[i];
            float blobY = centerY + particle.oy[i];
            g.setPaint(createSmokeGradient(blobX, blobY, blobRadius, baseAlpha, particle.ba[i]));
            g.fill(createSmokeShape(particle, i, blobX, blobY, blobRadius));
        }
    }

    private RadialGradientPaint createSmokeGradient(
            float blobX,
            float blobY,
            float blobRadius,
            float baseAlpha,
            float alphaMultiplier
    ) {
        Color[] colors = {
                new Color(0.18f, 0.18f, 0.18f, baseAlpha * alphaMultiplier),
                new Color(0.12f, 0.12f, 0.12f, baseAlpha * alphaMultiplier * 0.5f),
                new Color(0f, 0f, 0f, 0f)
        };
        return new RadialGradientPaint(
                new Point2D.Float(blobX, blobY),
                blobRadius,
                GRADIENT_DISTANCES,
                colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE
        );
    }

    private Shape createSmokeShape(
            SpikeEffect.SmokeParticle particle,
            int blobIndex,
            float blobX,
            float blobY,
            float blobRadius
    ) {
        float[] offsets = particle.shapeOffset[blobIndex];
        Path2D.Float shape = new Path2D.Float();

        for (int pointIndex = 0; pointIndex < offsets.length; pointIndex++) {
            double angle = 2.0 * Math.PI * pointIndex / offsets.length;
            float multiplier = offsets[pointIndex];
            float x = blobX + (float) Math.cos(angle) * blobRadius * multiplier;
            float y = blobY + (float) Math.sin(angle) * blobRadius * multiplier * 0.6f;

            if (pointIndex == 0) {
                shape.moveTo(x, y);
            } else {
                shape.lineTo(x, y);
            }
        }

        shape.closePath();
        AffineTransform rotation = AffineTransform.getRotateInstance(
                particle.rot[blobIndex],
                blobX,
                blobY
        );
        return rotation.createTransformedShape(shape);
    }

    private void ensureSmokeBuffers() {
        int width = GameConfig.SCREEN_WIDTH;
        int height = GameConfig.SCREEN_HEIGHT;
        if (smokeLayer != null && smokeLayer.getWidth() == width && smokeLayer.getHeight() == height) {
            return;
        }

        smokeLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        blurredSmoke = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private void clearImage(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
    }

    private static float[] createGaussianKernel() {
        int[] gaussianValues = {1, 4, 6, 4, 1};
        float[] kernelValues = new float[25];

        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                kernelValues[row * 5 + column] =
                        (gaussianValues[row] * gaussianValues[column]) / 256f;
            }
        }
        return kernelValues;
    }
}
