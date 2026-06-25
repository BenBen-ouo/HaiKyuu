/*
尚未收到 ACK 的可靠事件與最近一次傳送時間。
Server 依此判斷是否需要重新傳送同一個權威事件。
*/
package network;

final class PendingEvent {
    final ReliableEvent event;
    volatile long lastSentNanos;

    PendingEvent(ReliableEvent event) {
        this.event = event;
    }

    void markSent() {
        lastSentNanos = System.nanoTime();
    }
}
