/*
定義扣球特效資料，包含扣球軌跡與落地煙霧粒子的生命週期管理，並提供 HSB 色彩配置。
*/
package model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpikeEffect {
    // HSB 顏色設定（供後續調整亮度）
    // 預設紅隊使用 Hue 0.0（紅色），藍隊使用 Hue 0.61（藍色）
    public static float RED_HUE = 0.0f;
    public static float BLUE_HUE = 0.61f;
    public static float TRAIL_SATURATION = 0.9f;
    public static float TRAIL_BRIGHTNESS = 1.0f;

    public static class TrailPoint {
        public double x, y;
        public int remainingFrames;
        public int maxFrames;
        public boolean isRedTeam;

        public TrailPoint(double x, double y, int maxFrames, boolean isRedTeam) {
            this.x = x;
            this.y = y;
            this.maxFrames = maxFrames;
            this.remainingFrames = maxFrames;
            this.isRedTeam = isRedTeam;
        }
    }

    public static class SmokeParticle {
        public double x, y;
        public double vx, vy;
        public double startRadius;
        public double currentRadius;
        public double maxRadius;
        public int remainingFrames;
        public int maxFrames;

        // 用於產生不規則形狀的子彈子（多個重疊漸層）
        public int blobCount;
        public float[] ox, oy; // offsets
        public float[] br; // radius multiplier
        public float[] ba; // alpha multiplier
        public float[] rot; // rotation per blob (radians)
        public float[][] shapeOffset; // per-blob radial offsets for polygon shape
        
        public SmokeParticle(double x, double y, double vx, double vy, double startRadius, double maxRadius, int maxFrames) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.startRadius = startRadius;
            this.currentRadius = startRadius;
            this.maxRadius = maxRadius;
            this.maxFrames = maxFrames;
            this.remainingFrames = maxFrames;

            // 初始化不規則 blob
            this.blobCount = 3;
            this.ox = new float[blobCount];
            this.oy = new float[blobCount];
            this.br = new float[blobCount];
            this.ba = new float[blobCount];
            this.rot = new float[blobCount];

            int shapePoints = 6; // 每個 blob 的多邊形點數
            this.shapeOffset = new float[blobCount][shapePoints];

            for (int i = 0; i < blobCount; i++) {
                // x 偏移較大、y 偏移較小以產生扁平外觀
                this.ox[i] = (float) ((Math.random() * 2.0 - 1.0) * startRadius * 0.5);
                this.oy[i] = (float) ((Math.random() * 2.0 - 1.0) * startRadius * 0.25);
                this.br[i] = (float) (0.6 + Math.random() * 0.9); // radius multiplier 0.6 - 1.5
                this.ba[i] = (float) (0.6 + Math.random() * 0.6); // alpha multiplier 0.6 - 1.2
                this.rot[i] = (float) ((Math.random() * 2.0 - 1.0) * Math.PI * 0.35); // -~63deg..+~63deg

                // shape offsets: 每個點的 radius multiplier（0.6 - 1.4）
                for (int j = 0; j < shapePoints; j++) {
                    this.shapeOffset[i][j] = (float) (0.6 + Math.random() * 0.8);
                }
            }
        }

        public void update() {
            x += vx;
            y += vy;
            // 阻尼減速與紊流，無定風速影響，使粒子自然擴散
            vx = vx * 0.91 + (Math.random() - 0.5) * 0.12;
            vy = vy * 0.91 - 0.08 + (Math.random() - 0.5) * 0.08; // 稍微向上飄移
            
            double progress = 1.0 - ((double) remainingFrames / maxFrames);
            currentRadius = startRadius + (maxRadius - startRadius) * progress;
            remainingFrames--;
        }
    }

    private final List<TrailPoint> trailPoints = new ArrayList<>();
    private final List<SmokeParticle> smokeParticles = new ArrayList<>();
    private boolean spikeTrailActive = false;
    private boolean currentSpikeIsRed = false;
    private boolean useSmokeEffect2 = false; // 是否使用 smoke_effect2.jpg

    public void startSpikeTrail(boolean isRedTeam) {
        this.spikeTrailActive = true;
        this.currentSpikeIsRed = isRedTeam;
    }

    public void stopSpikeTrail() {
        this.spikeTrailActive = false;
    }

    public boolean isSpikeTrailActive() {
        return spikeTrailActive;
    }

    public boolean shouldUseSmokeEffect2() {
        return useSmokeEffect2;
    }

    public List<TrailPoint> getTrailPoints() {
        return trailPoints;
    }

    public List<SmokeParticle> getSmokeParticles() {
        return smokeParticles;
    }

    public void addTrailPoint(double x, double y) {
        if (spikeTrailActive) {
            trailPoints.add(new TrailPoint(x, y, 15, currentSpikeIsRed));
        }
    }

    public void spawnSmoke(double x, double y) {
        // 判定落地點是在左半場還是右半場
        // 網子 x 座標是 GameConfig.NET_X，左半場使用 smoke2.png，右半場使用 smoke.png
        this.useSmokeEffect2 = (x <= GameConfig.NET_X);

        // 只產生一個煙霧圖層，移除先前的粒子後直接在落地點淡出
        smokeParticles.clear();
        double startRad = 25;
        double maxRad = 55;
        int maxFr = 50;
        smokeParticles.add(new SmokeParticle(x, y, 0, -0.3, startRad, maxRad, maxFr));
    }

    public void update() {
        // 更新軌跡點
        Iterator<TrailPoint> trailIt = trailPoints.iterator();
        while (trailIt.hasNext()) {
            TrailPoint p = trailIt.next();
            p.remainingFrames--;
            if (p.remainingFrames <= 0) {
                trailIt.remove();
            }
        }

        // 更新煙霧粒子
        Iterator<SmokeParticle> smokeIt = smokeParticles.iterator();
        while (smokeIt.hasNext()) {
            SmokeParticle p = smokeIt.next();
            p.update();
            if (p.remainingFrames <= 0) {
                smokeIt.remove();
            }
        }
    }

    public void clear() {
        trailPoints.clear();
        smokeParticles.clear();
        spikeTrailActive = false;
        useSmokeEffect2 = false;
    }
}
