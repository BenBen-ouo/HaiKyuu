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

    // 鎖定本隊的所有攻擊與攔網輸入（發球保護期間使用）
    public static void lockAttackAndBlock(TeamInput input) {
        // 禁止起跳攻擊（後排/WS/MB 起跳）與主攻鍵
        input.backJump = false;
        input.wingAttack = false;
        input.quickAttack = false;
        // 禁止快速攔網輸入（如有映射）
        input.quickBlock = false;
        // 也保留撲球輸入不被觸發
        input.backDive = false;
    }
}
