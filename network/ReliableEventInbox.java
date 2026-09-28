package network;

import java.util.TreeMap;

/** 暫存亂序的可靠事件，只交付下一個連續 ID。 */
final class ReliableEventInbox {
    private final TreeMap<Integer, UdpCodec.Decoded> pending = new TreeMap<>();
    private int lastAppliedId;

    void offer(UdpCodec.Event event) {
        offer(event.eventId, event);
    }

    void offer(UdpCodec.MatchAborted aborted) {
        offer(aborted.eventId, aborted);
    }

    private void offer(int eventId, UdpCodec.Decoded event) {
        if (eventId > lastAppliedId) {
            pending.putIfAbsent(eventId, event);
        }
    }

    UdpCodec.Decoded pollNext() {
        UdpCodec.Decoded next = pending.remove(lastAppliedId + 1);
        if (next != null) {
            lastAppliedId++;
        }
        return next;
    }

    int lastAppliedId() {
        return lastAppliedId;
    }
}
