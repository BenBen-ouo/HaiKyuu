/*
發球流程用的輸入鎖定工具。
可禁止發球方 backPlayer 移動與一般動作；跳發起跳與擊球由 ServeHandler 單獨放行。
*/
package model.serve;

import model.TeamInput;
import model.player.BackPlayer;

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
