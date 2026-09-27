/*
發球流程狀態列舉。
用來區分等待發球、球已發出但動作未完成、以及正式進入來回。
*/
package model.serve;

public enum ServeState {
    WAITING_FOR_SERVE,
    SERVE_LAUNCHED,
    IN_PLAY
}
