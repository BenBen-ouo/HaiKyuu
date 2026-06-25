/*
對局終止封包的 ACK 與重送狀態。
與一般遊戲事件分開管理，確保斷線時仍能嘗試通知已連線的 Client。
*/
package network;

final class PendingMatchAbort {
    final int eventId;
    volatile long lastSentNanos;

    PendingMatchAbort(int eventId) {
        this.eventId = eventId;
    }

    void markSent() {
        lastSentNanos = System.nanoTime();
    }
}
