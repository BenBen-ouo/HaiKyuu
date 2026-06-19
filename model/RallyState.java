package model;

public class RallyState {
    private int redHitCount = 0;
    private int blueHitCount = 0;
    private Player redLastHitter = null;
    private Player blueLastHitter = null;
    private Boolean lastHitTeam = null;
    private boolean lastTouchWasBlock = false;

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
        lastTouchWasBlock = false;
    }

    public void resetAll() {
        resetCounters();
        lastHitTeam = null;
    }

    public int getHitCount(boolean redSide) {
        return redSide ? redHitCount : blueHitCount;
    }

    public Player getLastHitter(boolean redSide) {
        if (lastTouchWasBlock) {
            return null;
        }

        return redSide ? redLastHitter : blueLastHitter;
    }

    public Boolean getLastHitTeam() {
        return lastHitTeam;
    }

    public void setLastHitTeam(Boolean team) {
        lastHitTeam = team;
    }

    public boolean wasLastTouchBlock() {
        return lastTouchWasBlock;
    }

    public void recordHit(boolean redSide, Player hitter) {
        lastHitTeam = redSide;
        lastTouchWasBlock = hitter.blocking;

        if (redSide) {
            redLastHitter = hitter;
            redHitCount += hitter.blocking ? 0 : 1;
        } else {
            blueLastHitter = hitter;
            blueHitCount += hitter.blocking ? 0 : 1;
        }
    }
}
