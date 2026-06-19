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
