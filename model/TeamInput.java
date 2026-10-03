/*
單隊每幀輸入資料，保存後排、舉球員、快攻手、WS 與發球按鍵狀態。
提供複製與水平鏡像，讓網路 Client 可在不共享可變輸入物件的情況下轉換視角。
GameModel 會依球的位置填入球場側別資訊。
*/
package model;

import model.serve.ServeType;
import model.ball.Ball;
import model.player.Player;

public class TeamInput {
    public boolean backLeft;
    public boolean backRight;
    public boolean backJump;
    public boolean backDive;

    public boolean setterJump;

    public boolean quickAttack;
    // 本隊在這次球權內是否已完成第一次一般觸球。
    public boolean hasFirstRegularTouch;
    // 對手本次球權是否已完成一般接球；供 MB 攔網起跳計數啟用。
    public boolean opponentHasFirstRegularTouch;

    public boolean wingAttack;
    public boolean airSetModifier;

    // 僅供本機每幀角色判定，不進入網路輸入封包。
    public Ball ball;
    public boolean canBackAirSet;
    public boolean canWingAirSet;
    // 本機角色判定用：同隊上一位觸球者不能緊接著再攻擊。
    public Player lastHitter;
    // Client 的碰撞由 Server 判定，不能先自行播放揮臂命中動畫。
    public boolean canResolveAttack = true;

    // 扣球命中當下的球路修正鍵。
    public boolean spikeFlat;
    public boolean spikeShort;
    public boolean spikeLob;

    public boolean servePressed;
    public ServeType serveType = ServeType.NORMAL;

    // 由 GameModel 每一幀依照球的位置填入。
    // red 隊：球在網子左邊 = 本隊場。
    // blue 隊：球在網子右邊 = 本隊場。
    public boolean ballOnOwnSide;
    public boolean ballOnOpponentSide;

    /** 建立獨立副本，避免網路視角轉換修改鍵盤輸入物件。 */
    public TeamInput copy() {
        TeamInput copy = new TeamInput();
        copy.backLeft = backLeft;
        copy.backRight = backRight;
        copy.backJump = backJump;
        copy.backDive = backDive;
        copy.setterJump = setterJump;
        copy.quickAttack = quickAttack;
        copy.hasFirstRegularTouch = hasFirstRegularTouch;
        copy.opponentHasFirstRegularTouch = opponentHasFirstRegularTouch;
        copy.wingAttack = wingAttack;
        copy.airSetModifier = airSetModifier;
        copy.ball = ball;
        copy.canBackAirSet = canBackAirSet;
        copy.canWingAirSet = canWingAirSet;
        copy.lastHitter = lastHitter;
        copy.canResolveAttack = canResolveAttack;
        copy.spikeFlat = spikeFlat;
        copy.spikeShort = spikeShort;
        copy.spikeLob = spikeLob;
        copy.servePressed = servePressed;
        copy.serveType = serveType;
        copy.ballOnOwnSide = ballOnOwnSide;
        copy.ballOnOpponentSide = ballOnOpponentSide;
        return copy;
    }

    /** 回傳左右方向已鏡像的副本；其餘角色操作與球路按鍵不變。 */
    public TeamInput mirroredHorizontally() {
        TeamInput mirrored = copy();
        boolean originalLeft = mirrored.backLeft;
        mirrored.backLeft = mirrored.backRight;
        mirrored.backRight = originalLeft;
        return mirrored;
    }
}
