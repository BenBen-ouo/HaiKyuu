/*
負責判斷後排球員 Space / 0 目前應該是撲球還是起跳攻擊。
依照本隊是否已接起第一球，將輸入轉換成 backDive 或 backJump。
*/
package model;

public final class BackActionResolver {
    private BackActionResolver() {}

    public static void apply(TeamInput input, int hitCount) {
        boolean actionPressed = input.backJump || input.backDive;
        boolean firstTouchNotCompleted = hitCount == 0;

        input.backJump = actionPressed && !firstTouchNotCompleted;
        input.backDive = actionPressed && firstTouchNotCompleted;
    }
}
