package model;

/** 防止鎖定階段持續按住的動作鍵在解鎖時自動觸發。 */
final class ActionReleaseGate {
    private boolean backJump;
    private boolean backDive;
    private boolean setterJump;
    private boolean quickAttack;
    private boolean wingAttack;
    private boolean serve;

    void observeLocked(TeamInput input) {
        backJump = input.backJump;
        backDive = input.backDive;
        setterJump = input.setterJump;
        quickAttack = input.quickAttack;
        wingAttack = input.wingAttack;
        serve = input.servePressed;
    }

    void filter(TeamInput input) {
        if (!input.backJump) backJump = false;
        if (!input.backDive) backDive = false;
        if (!input.setterJump) setterJump = false;
        if (!input.quickAttack) quickAttack = false;
        if (!input.wingAttack) wingAttack = false;
        if (!input.servePressed) serve = false;

        if (backJump) input.backJump = false;
        if (backDive) input.backDive = false;
        if (setterJump) input.setterJump = false;
        if (quickAttack) input.quickAttack = false;
        if (wingAttack) input.wingAttack = false;
        if (serve) input.servePressed = false;
    }

    void reset() {
        backJump = false;
        backDive = false;
        setterJump = false;
        quickAttack = false;
        wingAttack = false;
        serve = false;
    }
}
