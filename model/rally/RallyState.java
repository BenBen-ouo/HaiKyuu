/*
保存單次來回中的觸球狀態。
包含紅藍觸球次數、最後觸球者、最後觸球隊伍與是否為攔網觸球。
*/
package model.rally;

import model.player.Player;
import model.player.Setter;
import model.player.Team;

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
    private boolean redSetterTouchedFirst = false;
    private boolean blueSetterTouchedFirst = false;
    private boolean redBlockUsed = false;
    private boolean blueBlockUsed = false;

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
        lastTouchWasBlock = false;
        redSetterTouched = false;
        blueSetterTouched = false;
        redSetterTouchedFirst = false;
        blueSetterTouchedFirst = false;
        redBlockUsed = false;
        blueBlockUsed = false;
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

    /* 0=back、1=setter、2=MB、3=WS，-1 表示沒有最後觸球者。 */
    public int getLastHitterIndex(boolean redSide, Team team) {
        Player hitter = redSide ? redLastHitter : blueLastHitter;
        Player[] players = team.getPlayers();
        for (int i = 0; i < players.length; i++) {
            if (players[i] == hitter) {
                return i;
            }
        }
        return -1;
    }

    public void applyNetworkState(
            int redHitCount,
            int blueHitCount,
            Boolean lastHitTeam,
            boolean lastTouchWasBlock,
            int redLastHitterIndex,
            int blueLastHitterIndex,
            boolean redSetterTouched,
            boolean blueSetterTouched,
            boolean redSetterTouchedFirst,
            boolean blueSetterTouchedFirst,
            boolean redBlockUsed,
            boolean blueBlockUsed,
            Team redTeam,
            Team blueTeam
    ) {
        this.redHitCount = Math.max(0, redHitCount);
        this.blueHitCount = Math.max(0, blueHitCount);
        this.lastHitTeam = lastHitTeam;
        this.lastTouchWasBlock = lastTouchWasBlock;
        this.redLastHitter = playerAt(redTeam, redLastHitterIndex);
        this.blueLastHitter = playerAt(blueTeam, blueLastHitterIndex);
        this.redSetterTouched = redSetterTouched;
        this.blueSetterTouched = blueSetterTouched;
        this.redSetterTouchedFirst = redSetterTouchedFirst;
        this.blueSetterTouchedFirst = blueSetterTouchedFirst;
        this.redBlockUsed = redBlockUsed;
        this.blueBlockUsed = blueBlockUsed;
    }

    private Player playerAt(Team team, int index) {
        Player[] players = team.getPlayers();
        return index >= 0 && index < players.length ? players[index] : null;
    }

    public void recordHit(boolean redSide, Player hitter, boolean counts) {
        lastHitTeam = redSide;
        lastTouchWasBlock = false;

        if (redSide) {
            redLastHitter = hitter;
            if (hitter instanceof Setter && !redSetterTouched) {
                redSetterTouchedFirst = redHitCount == 0;
            }
            if (counts) redHitCount++;
            if (hitter instanceof Setter) {
                redSetterTouched = true;
            }
        } else {
            blueLastHitter = hitter;
            if (hitter instanceof Setter && !blueSetterTouched) {
                blueSetterTouchedFirst = blueHitCount == 0;
            }
            if (counts) blueHitCount++;
            if (hitter instanceof Setter) {
                blueSetterTouched = true;
            }
        }
    }

    public void recordBlock(boolean redSide, Player blocker) {
        lastHitTeam = redSide;
        lastTouchWasBlock = true;

        if (redSide) {
            redLastHitter = blocker;
            redBlockUsed = true;
        } else {
            blueLastHitter = blocker;
            blueBlockUsed = true;
        }
    }

    public boolean hasSetterTouched(boolean redSide) {
        return redSide ? redSetterTouched : blueSetterTouched;
    }

    public boolean wasSetterTouchedFirst(boolean redSide) {
        return redSide ? redSetterTouchedFirst : blueSetterTouchedFirst;
    }

    public boolean canSetterTouch(boolean redSide) {
        if (!hasSetterTouched(redSide)) {
            return true;
        }
        return redSide
                ? redSetterTouchedFirst && redHitCount == 2
                : blueSetterTouchedFirst && blueHitCount == 2;
    }

    public boolean hasBlocked(boolean redSide) {
        return redSide ? redBlockUsed : blueBlockUsed;
    }

    public void resetHitCount(boolean redSide) {
        if (redSide) {
            redHitCount = 0;
            redSetterTouched = false;
            redSetterTouchedFirst = false;
        } else {
            blueHitCount = 0;
            blueSetterTouched = false;
            blueSetterTouchedFirst = false;
        }
    }
}
