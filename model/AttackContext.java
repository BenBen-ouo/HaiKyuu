package model;

// 預留給之後扣球邏輯使用。
// 之後可以加入攻擊者、按鍵方向、球種、攻擊碰撞箱是否命中等資訊。

public class AttackContext {
    public final Player attacker;
    public final boolean redSide;

    public AttackContext(Player attacker, boolean redSide) {
        this.attacker = attacker;
        this.redSide = redSide;
    }
}
