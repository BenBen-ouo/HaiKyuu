package model.ai;

import model.GameConfig;

/** 僅依已觀察位置／速度估計自由飛行；不呼叫碰撞、不知道下一次觸球結果。 */
public record BallForecast(double x, double y, double velocityX, double velocityY) {
    public double xAt(int frames) { return x + velocityX * frames; }
    public double yAt(int frames) {
        return y + velocityY * frames + GameConfig.GRAVITY * frames * (frames + 1) / 2;
    }
    public int descendingArrival(double height) {
        for (int t = 1; t <= AiTuning.FORECAST_FRAMES; t++) {
            if (velocityY + GameConfig.GRAVITY * t > 0 && yAt(t) >= height) return t;
        }
        return AiTuning.FORECAST_FRAMES;
    }
}
