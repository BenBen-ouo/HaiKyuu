package model;

public class WingSpiker extends Player {
    public WingSpiker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
        attacking = input.wingAttack;
        // TODO: 之後新增大砲的移動、起跳、攻擊判定。
        applyGravity();
    }
}
