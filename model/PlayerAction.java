/*
列出球員目前可能處於的動作狀態。
用來區分待機、跑步、攻擊準備、攻擊揮臂、攔網、撲球、舉球與接球。
*/
package model;

public enum PlayerAction {
    IDLE,
    RUN_APPROACH,
    RUN_LOOP,
    RUN_RETURN,
    ATTACK_READY,
    ATTACK_SWING,
    BLOCK,
    DIVE,
    SETTING,
    RECEIVING
}