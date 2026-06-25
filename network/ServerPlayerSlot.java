/*
Server 端單一玩家席位，保存端點、輸入序列、控制指令與待確認的可靠事件。
所有跨接收執行緒與 Server tick 的可變資料都集中在此類別同步或以原子欄位存取。
*/
package network;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ServerPlayerSlot {
    private static final int MAX_PENDING_EVENTS = 8;

    final boolean redSide;
    final long clientNonce;
    final long sessionToken = ThreadLocalRandom.current().nextLong();
    final AtomicInteger latestInputMask = new AtomicInteger();
    final AtomicInteger pendingPressedMask = new AtomicInteger();
    final AtomicInteger lastInputSequence = new AtomicInteger(-1);
    final AtomicInteger latestScheduledServerTick = new AtomicInteger();
    final AtomicInteger lastControlSequence = new AtomicInteger(-1);
    final AtomicLong lastHeardNanos = new AtomicLong(System.nanoTime());
    final ConcurrentLinkedQueue<ServerControlCommand> pendingControls = new ConcurrentLinkedQueue<>();
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

    void acceptInput(UdpCodec.InputFrame input) {
        markHeard();
        if (input.sequence <= lastInputSequence.get()) {
            return;
        }

        lastInputSequence.set(input.sequence);
        latestScheduledServerTick.set(input.scheduledServerTick);
        int previousMask = latestInputMask.getAndSet(input.inputMask);
        int pressedSinceLastFrame = input.inputMask & ~previousMask;
        pendingPressedMask.getAndAccumulate(pressedSinceLastFrame, (current, pressed) -> current | pressed);
        gameplayReady = true;
    }

    boolean acceptControl(UdpCodec.ControlFrame control) {
        markHeard();
        if (control.sequence <= lastControlSequence.get()) {
            return false;
        }

        lastControlSequence.set(control.sequence);
        pendingControls.offer(new ServerControlCommand(control.sequence, control.action));
        return true;
    }

    int takeInputMaskForTick() {
        return latestInputMask.get() | pendingPressedMask.getAndSet(0);
    }

    int getLastControlSequence() {
        return lastControlSequence.get();
    }

    boolean isGameplayReady() {
        return gameplayReady;
    }

    ServerControlCommand pollControl() {
        return pendingControls.poll();
    }

    synchronized PendingEvent addPendingEvent(ReliableEvent event) {
        PendingEvent pending = new PendingEvent(event);
        pendingEvents.addLast(pending);
        while (pendingEvents.size() > MAX_PENDING_EVENTS) {
            pendingEvents.removeFirst();
        }
        return pending;
    }

    synchronized void acknowledgeEvent(int eventId) {
        for (Iterator<PendingEvent> iterator = pendingEvents.iterator(); iterator.hasNext();) {
            if (iterator.next().event.id <= eventId) {
                iterator.remove();
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
