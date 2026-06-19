/*
依照球目前的 x 位置，更新紅藍雙方輸入中的 ballOnOwnSide 狀態。
讓角色邏輯可以直接知道球在本隊場或對方場。
*/
package model;

public final class BallSideTracker {
    private BallSideTracker() {}

    public static void updateInputs(Ball ball, TeamInput redInput, TeamInput blueInput) {
        fillBallSideInfo(ball, redInput, true);
        fillBallSideInfo(ball, blueInput, false);
    }

    private static void fillBallSideInfo(Ball ball, TeamInput input, boolean redSide) {
        boolean ownSide = SideRules.isBallOnOwnSide(redSide, ball.x);
        input.ballOnOwnSide = ownSide;
        input.ballOnOpponentSide = !ownSide;
    }
}
