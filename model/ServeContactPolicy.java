package model;

//ㄏ集中管理發球期間誰可以碰球。
// TODO：之後修發球得分 bug 時，可在這裡加入「發球方先碰到自家球員」與
// 「對手攔網先碰到」的判斷，不需要散落在 GameModel 或 RallyContactHandler。

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
