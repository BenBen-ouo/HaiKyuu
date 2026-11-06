/*
Server 端單一玩家席位，保存端點、輸入序列、控制指令與待確認的可靠事件。
所有跨接收執行緒與 Server tick 的可變資料都集中在此類別同步或以原子欄位存取。
*/
package network;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ServerPlayerSlot {
    private static final int MAX_PENDING_INPUT_SAMPLES = 16;

    final boolean redSide;
    final long clientNonce;
    final long sessionToken = ThreadLocalRandom.current().nextLong();
    private final Deque<Integer> pendingInputMasks = new ArrayDeque<>();
    private int lastInputSequence = -1;
    private int lastReceivedInputMask;
    private int heldInputMask;
    private boolean inputOverflowed;
    final AtomicInteger lastControlSequence = new AtomicInteger();
    final AtomicLong lastHeardNanos = new AtomicLong(System.nanoTime());
    private final TreeMap<Integer, ServerControlCommand> pendingControls = new TreeMap<>();
    final Deque<PendingEvent> pendingEvents = new ArrayDeque<>();

    volatile InetAddress address;
    volatile int port;
    volatile boolean gameplayReady;
    private PendingMatchAbort pendingMatchAbort;

    ServerPlayerSlot(boolean redSide, long clientNonce, InetAddress address, int port) {
        this.redSide = redSide;
        this.clientNonce = clientNonce;
        this.address = address;
        this.port = port;
    }

    void updateEndpoint(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    void markHeard() {
        lastHeardNanos.set(System.nanoTime());
    }

    boolean matches(long token, InetAddress address, int port) {
        return sessionToken == token
                && this.address != null
                && this.address.equals(address)
                && this.port == port;
    }

    boolean hasTimedOut(long nowNanos, long timeoutNanos) {
        return nowNanos - lastHeardNanos.get() > timeoutNanos;
    }

    synchronized void acceptInput(UdpCodec.InputFrame input) {
        markHeard();
        if (input.sequence <= lastInputSequence || inputOverflowed) {
            return;
        }

        lastInputSequence = input.sequence;
        // 相同的持續輸入不占佇列；動作及其修正鍵保留原樣本，不跨時間 OR。
        if (input.inputMask != lastReceivedInputMask) {
            if (pendingInputMasks.size() == MAX_PENDING_INPUT_SAMPLES) {
                inputOverflowed = true;
                return;
            }
            pendingInputMasks.addLast(input.inputMask);
            lastReceivedInputMask = input.inputMask;
        }
        gameplayReady = true;
    }

    synchronized boolean acceptControl(UdpCodec.ControlFrame control) {
        markHeard();
        if (control.sequence <= lastControlSequence.get()) {
            return false;
        }

        return pendingControls.putIfAbsent(
                control.sequence,
                new ServerControlCommand(control.sequence, control.action)
        ) == null;
    }

    synchronized int takeInputMaskForTick() {
        Integer sample = pendingInputMasks.pollFirst();
        if (sample != null) heldInputMask = sample;
        return heldInputMask;
    }

    synchronized boolean hasInputOverflowed() {
        return inputOverflowed;
    }

    int getLastControlSequence() {
        return lastControlSequence.get();
    }

    boolean isGameplayReady() {
        return gameplayReady;
    }

    synchronized ServerControlCommand pollControl() {
        int nextSequence = lastControlSequence.get() + 1;
        ServerControlCommand command = pendingControls.remove(nextSequence);
        if (command != null) {
            lastControlSequence.set(nextSequence);
        }
        return command;
    }

    synchronized PendingEvent addPendingEvent(ReliableEvent event) {
        PendingEvent pending = new PendingEvent(event);
        pendingEvents.addLast(pending);
        return pending;
    }

    synchronized void acknowledgeEvent(int eventId) {
        for (Iterator<PendingEvent> iterator = pendingEvents.iterator(); iterator.hasNext();) {
            if (iterator.next().event.id == eventId) {
                iterator.remove();
                break;
            }
        }
        if (pendingMatchAbort != null && pendingMatchAbort.eventId == eventId) {
            pendingMatchAbort = null;
        }
        markHeard();
    }

    synchronized PendingEvent[] pendingEventsSnapshot() {
        return pendingEvents.toArray(new PendingEvent[0]);
    }

    synchronized boolean hasExpiredEvent(long nowNanos, long timeoutNanos) {
        return !pendingEvents.isEmpty()
                && nowNanos - pendingEvents.peekFirst().firstSentNanos >= timeoutNanos;
    }

    synchronized boolean hasPendingEvents() {
        return !pendingEvents.isEmpty();
    }

    synchronized PendingMatchAbort beginMatchAbort(int eventId) {
        if (pendingMatchAbort == null) {
            pendingMatchAbort = new PendingMatchAbort(eventId);
        }
        return pendingMatchAbort;
    }

    synchronized PendingMatchAbort getPendingMatchAbort() {
        return pendingMatchAbort;
    }
}
