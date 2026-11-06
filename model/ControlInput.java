package model;

import model.serve.ServeType;

/** 鍵盤及封包可傳遞的操作狀態；不包含球、角色參照或規則判斷結果。 */
public class ControlInput {
    public boolean backLeft;
    public boolean backRight;
    public boolean backJump;
    public boolean backDive;
    public boolean setterJump;
    public boolean mbActionPressed;
    public boolean wingActionPressed;
    public boolean airSetModifier;
    public boolean spikeFlat;
    public boolean spikeShort;
    public boolean spikeLob;
    public boolean servePressed;
    public ServeType serveType = ServeType.NORMAL;

    public ControlInput copy() {
        ControlInput copy = new ControlInput();
        copyControlsTo(copy);
        return copy;
    }

    protected final void copyControlsTo(ControlInput copy) {
        copy.backLeft = backLeft;
        copy.backRight = backRight;
        copy.backJump = backJump;
        copy.backDive = backDive;
        copy.setterJump = setterJump;
        copy.mbActionPressed = mbActionPressed;
        copy.wingActionPressed = wingActionPressed;
        copy.airSetModifier = airSetModifier;
        copy.spikeFlat = spikeFlat;
        copy.spikeShort = spikeShort;
        copy.spikeLob = spikeLob;
        copy.servePressed = servePressed;
        copy.serveType = serveType;
    }

    /** 世界方向只在輸入邊界轉換一次；球路模式已由按鍵映射決定。 */
    public ControlInput mirroredHorizontally() {
        ControlInput mirrored = copy();
        mirrored.backLeft = backRight;
        mirrored.backRight = backLeft;
        return mirrored;
    }
}
