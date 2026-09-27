/*
攔網專用碰撞箱。
幾何判定沿用角色碰撞箱，但與一般觸球碰撞箱分開保存，避免攔網是否計次依賴角色狀態。
*/
package model.player;

public final class BlockHitBox extends HitBox {
    public BlockHitBox(Player owner) {
        super(owner);
    }
}
