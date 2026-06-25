/*
Server 接收到的可靠控制指令。
由 ServerPlayerSlot 保存，並由 GameServer 在權威 tick 中依序處理。
*/
package network;

final class ServerControlCommand {
    final int sequence;
    final UdpCodec.ControlAction action;

    ServerControlCommand(int sequence, UdpCodec.ControlAction action) {
        this.sequence = sequence;
        this.action = action;
    }
}
