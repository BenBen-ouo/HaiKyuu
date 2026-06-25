/*
需要 Client ACK 的 Server 權威事件資料。
事件本身不可變；重送時間與 ACK 狀態由 PendingEvent 管理。
*/
package network;

final class ReliableEvent {
    final int id;
    final int serverTick;
    final Packet.EventType type;
    final int collisionRevision;
    final Packet.CompactState state;

    ReliableEvent(int id, int serverTick, Packet.EventType type, int collisionRevision, Packet.CompactState state) {
        this.id = id;
        this.serverTick = serverTick;
        this.type = type;
        this.collisionRevision = collisionRevision;
        this.state = state;
    }
}
