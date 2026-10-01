/*
集中管理發球階段允許哪些隊伍碰球。
之後修正發球碰到自家球員或對手攔網得分規則時，優先改這裡。
*/
package model.serve;

public class ServeContactPolicy {
    public boolean canTeamCollide(ServeState state, boolean redSide, boolean redServing,
                                  boolean serveLaunchedThisFrame) {
        if (state == ServeState.WAITING_FOR_SERVE || state == ServeState.JUMP_TOSS) {
            return false;
        }

        // 出手當幀忽略發球員與球的殘留重疊；從下一幀起，發球方碰球也要能裁決犯規。
        return !(serveLaunchedThisFrame && redSide == redServing);
    }
}
