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
            Team redTeam,
            Team blueTeam
    ) {
        this.redHitCount = Math.max(0, redHitCount);
        this.blueHitCount = Math.max(0, blueHitCount);
        this.lastHitTeam = lastHitTeam;
        this.lastTouchWasBlock = lastTouchWasBlock;
        this.redLastHitter = playerAt(redTeam, redLastHitterIndex);
        this.blueLastHitter = playerAt(blueTeam, blueLastHitterIndex);
    }

    private Player playerAt(Team team, int index) {
        Player[] players = team.getPlayers();
        return index >= 0 && index < players.length ? players[index] : null;
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
