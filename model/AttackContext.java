/*
預留給之後扣球判斷使用的攻擊上下文資料。
之後可加入攻擊者、攻擊方向、按鍵球種與攻擊碰撞箱命中資訊。
*/
package model;

public class AttackContext {
    public final Player attacker;
    public final boolean redSide;

    public AttackContext(Player attacker, boolean redSide) {
        this.attacker = attacker;
        this.redSide = redSide;
    }
}
