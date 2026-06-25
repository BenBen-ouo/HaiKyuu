/*
測試畫面的除錯顯示設定。
目前只管理碰撞箱是否繪製；不改變任何碰撞、動畫、網路同步或規則判定。
F3 可在執行期間切換，預設維持顯示以保留既有測試畫面。
*/
package view;

public final class DebugSettings {
    private static volatile boolean hitBoxesVisible = false;

    private DebugSettings() {
    }

    public static boolean areHitBoxesVisible() {
        return hitBoxesVisible;
    }

    public static void setHitBoxesVisible(boolean visible) {
        hitBoxesVisible = visible;
    }

    public static void toggleHitBoxesVisible() {
        hitBoxesVisible = !hitBoxesVisible;
    }
}
