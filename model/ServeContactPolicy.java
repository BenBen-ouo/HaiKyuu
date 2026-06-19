/*
集中管理發球階段允許哪些隊伍碰球。
之後修正發球碰到自家球員或對手攔網得分規則時，優先改這裡。
*/
package model;

public class ServeContactPolicy {
    public boolean canTeamCollide(ServeState state, boolean redSide, boolean redServing,
                                  boolean serveLaunchedThisFrame) {
        if (state == ServeState.READY) {
            return false;
        }

        if (state == ServeState.SERVE_LAUNCHED) {
            return redSide != redServing;
        }

        return !(serveLaunchedThisFrame && redSide == redServing);
    }
}
