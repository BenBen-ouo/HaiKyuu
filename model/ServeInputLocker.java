/*
發球流程用的輸入鎖定工具。
可禁止發球方 backPlayer 移動、撲球、跳躍，或等待發球鍵放開後再恢復操作。
*/
package model;

public final class ServeInputLocker {
    private ServeInputLocker() {}

    public static void lockBackPlayer(TeamInput input) {
        input.backLeft = false;
        input.backRight = false;
        input.backJump = false;
        input.backDive = false;
    }

    public static void suppressBackActionUntilReleased(TeamInput input) {
        input.backJump = false;
        input.backDive = false;
    }
}
