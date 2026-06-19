/*
單一視覺特效資料，保存圖片名稱、位置與剩餘顯示時間。
由 EffectManager 更新生命週期，由 EffectRenderer 負責繪製。
*/
package model;

public class VisualEffect {
    public final String assetName;
    public double x;
    public double y;
    public int remainingFrames;

    public VisualEffect(String assetName, double x, double y, int durationFrames) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
        this.remainingFrames = durationFrames;
    }

    public boolean isAlive() {
        return remainingFrames > 0;
    }

    public void update() {
        remainingFrames--;
    }
}
