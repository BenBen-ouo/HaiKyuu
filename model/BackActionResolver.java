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
