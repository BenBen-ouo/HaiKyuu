/*
保存單次來回中的觸球狀態。
包含紅藍觸球次數、最後觸球者、最後觸球隊伍與是否為攔網觸球。
*/
package model;

public class RallyState {
    private int redHitCount = 0;
    private int blueHitCount = 0;
    private Player redLastHitter = null;
    private Player blueLastHitter = null;
    private Boolean lastHitTeam = null;
    private boolean lastTouchWasBlock = false;

    // 記錄本回合是否有舉球接觸（用於限制舉球只能碰一次）
    private boolean redSetterTouched = false;
    private boolean blueSetterTouched = false;

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
        lastTouchWasBlock = false;
        redSetterTouched = false;
        blueSetterTouched = false;
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
            if (hitter instanceof Setter) {
                redSetterTouched = true;
            }
        } else {
            blueLastHitter = hitter;
            blueHitCount += hitter.blocking ? 0 : 1;
            if (hitter instanceof Setter) {
                blueSetterTouched = true;
            }
        }
    }

    public boolean hasSetterTouched(boolean redSide) {
        return redSide ? redSetterTouched : blueSetterTouched;
    }
}
