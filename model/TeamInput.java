/*
單隊每幀輸入資料，保存後排、舉球員、快攻手、WS 與發球按鍵狀態。
GameModel 也會填入球目前是否在本隊場或對方場。
*/
package model;

public class TeamInput {
    public boolean backLeft;
    public boolean backRight;
    public boolean backJump;
    public boolean backDive;

    public boolean setterJump;

    public boolean quickAttack;
    public boolean quickBlock;

    public boolean wingAttack;

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
}