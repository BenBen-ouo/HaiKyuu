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
