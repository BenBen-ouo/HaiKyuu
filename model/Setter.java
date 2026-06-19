/*
舉球員 S 的角色邏輯。
目前只處理起跳與重力更新，觸球時的舉球動畫由 RallyContactHandler 觸發。
*/
package model;

public class Setter extends Player {
    public Setter(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
        vx = 0;

        if (input.setterJump && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED * 0.92;
            jumping = true;
        }

        applyGravity();
        updateActionAnimation();
    }
}