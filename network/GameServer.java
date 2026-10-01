/*
獨立、無畫面的 UDP 權威 Server。
固定以 60 tick/s 執行 GameModel，並逐 tick 傳送球與球員即時快照。
INPUT 與 WORLD_SNAPSHOT 不可靠傳送；指定 COLLISION_EVENT 以 ACK 重送完整權威狀態。
*/
package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import model.GameConfig;
import model.GameModel;
import model.serve.ServeState;

public final class GameServer implements AutoCloseable {
    public static final int UDP_PORT = 5001;
    public static final String MATCH_ABORTED_MESSAGE = "對局結束，必須雙方重新開啟遊戲才能重玩。";
    public static final String SYNC_TIMEOUT_MESSAGE = "網路同步確認逾時，對局已中止。";
    public static final String CLIENT_TIMEOUT_MESSAGE = "玩家連線逾時，對局已中止。";

    private static final long TICK_NANOS = 1_000_000_000L / GameConfig.TICKS_PER_SECOND;
    private static final long CLIENT_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long INPUT_RELAY_INTERVAL_NANOS = 40_000_000L; // 25 Hz
    private static final long EVENT_RESEND_INTERVAL_NANOS = 60_000_000L;
    private static final long RELIABLE_ACK_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long TERMINATION_ACK_TIMEOUT_NANOS = 3_000_000_000L;

    private final GameModel model = new GameModel();
    private final DatagramSocket socket;
    private final String localIp;
    private final Object slotsLock = new Object();
    private final Object eventOrderLock = new Object();
    private final AtomicBoolean terminationStarted = new AtomicBoolean();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "haikyuu-udp-retry");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ServerPlayerSlot redPlayer;
    private volatile ServerPlayerSlot bluePlayer;
    private volatile boolean running = true;
    private volatile boolean terminating;
    private volatile String endMessage = "";

    private int serverTick;
    private final AtomicInteger nextEventId = new AtomicInteger();
    private final AtomicInteger nextControlStatusId = new AtomicInteger();
    private int lastRedInputMask = Integer.MIN_VALUE;
    private int lastBlueInputMask = Integer.MIN_VALUE;
    private long lastRedInputRelayNanos;
    private long lastBlueInputRelayNanos;
    private long terminationDeadlineNanos;
    private int nextWorldSnapshotSequence;
    private int collisionRevision;
    private volatile boolean redResetConfirmed;
    private volatile boolean blueResetConfirmed;

    public GameServer() throws SocketException {
        socket = new DatagramSocket(UDP_PORT);
        localIp = NetworkAddress.findLocalIpv4();
    }

    public void run() {
        Thread receiveThread = new Thread(this::receiveLoop, "haikyuu-udp-server-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
        retryExecutor.scheduleAtFixedRate(this::retryReliablePackets, 10, 10, TimeUnit.MILLISECONDS);

        System.out.println("HaiKyuu UDP Server 已啟動（無畫面）");
        System.out.println("UDP 5001: " + localIp);
        System.out.println("Player 1 與 Player 2 都使用：java -cp build Main join <Server-IP>");

        try {
            long nextTick = System.nanoTime();
            while (running) {
                long lateNanos = System.nanoTime() - nextTick;
                if (lateNanos >= 25_000_000L) {
                    TimingDiagnostics.record("server tick late "
                            + String.format("%.2f", lateNanos / 1_000_000.0) + " ms");
                }
                long tickStarted = System.nanoTime();
                updateOneTick();
                TimingDiagnostics.recordIfSlow("server full tick", tickStarted, 25);
                nextTick += TICK_NANOS;
                sleepUntil(nextTick);
            }
        } finally {
            retryExecutor.shutdownNow();
        }

        if (!endMessage.isBlank()) {
            System.out.println(endMessage);
        }
    }

    private void updateOneTick() {
        if (terminating) {
            updateTermination();
            return;
        }

        serverTick++;
        ServerPlayerSlot red = redPlayer;
        ServerPlayerSlot blue = bluePlayer;
        if (hasTimedOut(red) || hasTimedOut(blue)) {
            beginTermination(CLIENT_TIMEOUT_MESSAGE);
            return;
        }

        if (serverTick % GameConfig.TICKS_PER_SECOND == 0) {
            if (red != null) sendControlStatus(red, red.getLastControlSequence());
            if (blue != null) sendControlStatus(blue, blue.getLastControlSequence());
        }

        if (red == null || blue == null || !red.isGameplayReady() || !blue.isGameplayReady()) {
            return;
        }

        int previousRedControlSequence = red.getLastControlSequence();
        int previousBlueControlSequence = blue.getLastControlSequence();
        ControlResult controlResult = processControls(red, blue);
        if (terminating) {
            return;
        }
        if (controlResult.resetApplied) {
            sendReliableEvent(Packet.EventType.RESET, Packet.CompactState.from(model));
        }
        if (controlResult.statusChanged
                || red.getLastControlSequence() != previousRedControlSequence
                || blue.getLastControlSequence() != previousBlueControlSequence) {
            broadcastControlStatus();
        }

        FrameState before = FrameState.capture(model);
        int redMask = red.takeInputMaskForTick();
        int blueMask = blue.takeInputMaskForTick();
        long updateStarted = System.nanoTime();
        model.update(Packet.decodeInput(redMask), Packet.decodeInput(blueMask));
        TimingDiagnostics.recordIfSlow("server model update", updateStarted, 25);

        if (terminating) {
            return;
        }

        relayRemoteInputs(red, blue, redMask, blueMask);

        for (Packet.EventType eventType : detectSyncEvents(before)) {
            sendReliableEvent(eventType, Packet.CompactState.from(model));
        }
        sendWorldSnapshot();
    }

    private ControlResult processControls(ServerPlayerSlot red, ServerPlayerSlot blue) {
        boolean statusChanged = false;

        ServerControlCommand command;
        while ((command = red.pollControl()) != null) {
            if (command.action == UdpCodec.ControlAction.DISCONNECT) {
                beginTermination();
                return new ControlResult(false, false);
            }
            if (command.action == UdpCodec.ControlAction.CANCEL_RESET) {
                statusChanged |= clearResetConfirmation();
            } else if (command.action == UdpCodec.ControlAction.RESET_REQUEST && !redResetConfirmed) {
                redResetConfirmed = true;
                statusChanged = true;
            }
        }

        while ((command = blue.pollControl()) != null) {
            if (command.action == UdpCodec.ControlAction.DISCONNECT) {
                beginTermination();
                return new ControlResult(false, false);
            }
            if (command.action == UdpCodec.ControlAction.CANCEL_RESET) {
                statusChanged |= clearResetConfirmation();
            } else if (command.action == UdpCodec.ControlAction.RESET_REQUEST && !blueResetConfirmed) {
                blueResetConfirmed = true;
                statusChanged = true;
            }
        }

        if (redResetConfirmed && blueResetConfirmed) {
            model.restart();
            clearResetConfirmation();
            return new ControlResult(true, true);
        }

        return new ControlResult(statusChanged, false);
    }

    private List<Packet.EventType> detectSyncEvents(FrameState before) {
        List<Packet.EventType> events = new ArrayList<>(5);

        ServeState currentServeState = model.getServeHandler().getState();
        if (before.serveState == ServeState.WAITING_FOR_SERVE
                && currentServeState == ServeState.JUMP_TOSS) {
            events.add(Packet.EventType.JUMP_TOSS);
        }
        if ((before.serveState == ServeState.WAITING_FOR_SERVE
                || before.serveState == ServeState.JUMP_TOSS)
                && (currentServeState == ServeState.SERVE_LAUNCHED
                || currentServeState == ServeState.IN_PLAY)
                && Math.abs(model.ball.vx) + Math.abs(model.ball.vy) > 0.01) {
            events.add(Packet.EventType.SERVE);
        }

        if (model.didSetterContactThisFrame()) {
            events.add(Packet.EventType.SETTER_CONTACT);
        }

        if (model.didAirSetContactThisFrame()) {
            events.add(Packet.EventType.AIR_SET_CONTACT);
        }

        if (model.didFirstServeReceptionThisFrame() && !model.didSetterContactThisFrame()) {
            events.add(Packet.EventType.RECEPTION);
        }

        if (model.didBallLandThisFrame()) {
            events.add(Packet.EventType.LANDING);
        }

        boolean scoreChanged = before.redScore != model.redScore || before.blueScore != model.blueScore;
        boolean rallyEnded = !before.rallyOver && model.isRallyOverForNetwork();
        if ((scoreChanged || rallyEnded) && isRuleMessage(model.transientMessage)) {
            events.add(Packet.EventType.RULE);
        }
        if (scoreChanged || rallyEnded) {
            events.add(Packet.EventType.SCORE);
        }
        if ((before.rallyOver && !model.isRallyOverForNetwork())
                || (!before.lockedScorePhase && model.isLockedScorePhase())) {
            // 階段切換必須可靠同步，但不能再觸發一次得分原因顯示。
            events.add(Packet.EventType.FLOW);
        }

        return events;
    }

    private boolean isRuleMessage(String message) {
        return "四觸違規".equals(message)
                || "發球犯規".equals(message)
                || "後排三米線".equals(message)
                || "TOUCH OUT".equals(message);
    }

    private void relayRemoteInputs(ServerPlayerSlot red, ServerPlayerSlot blue, int redMask, int blueMask) {
        long now = System.nanoTime();
        if (redMask != lastRedInputMask || now - lastRedInputRelayNanos >= INPUT_RELAY_INTERVAL_NANOS) {
            sendRemoteInput(blue, redMask);
            lastRedInputMask = redMask;
            lastRedInputRelayNanos = now;
        }
        if (blueMask != lastBlueInputMask || now - lastBlueInputRelayNanos >= INPUT_RELAY_INTERVAL_NANOS) {
            sendRemoteInput(red, blueMask);
            lastBlueInputMask = blueMask;
            lastBlueInputRelayNanos = now;
        }
    }

    private void sendRemoteInput(ServerPlayerSlot target, int inputMask) {
        try {
            sendUdp(UdpCodec.remoteInput(target.sessionToken, serverTick, inputMask), target.address, target.port);
        } catch (IOException exception) {
            beginTermination();
        }
    }

    private void sendReliableEvent(Packet.EventType type, Packet.CompactState state) {
        PendingEvent redPending;
        PendingEvent bluePending;
        synchronized (eventOrderLock) {
            if (terminating) {
                return;
            }
            ReliableEvent event = new ReliableEvent(
                    nextEventId.incrementAndGet(),
                    serverTick,
                    type,
                    ++collisionRevision,
                    state
            );
            redPending = queueReliableEvent(redPlayer, event);
            bluePending = queueReliableEvent(bluePlayer, event);
        }
        if (redPending != null) sendEvent(redPlayer, redPending);
        if (bluePending != null) sendEvent(bluePlayer, bluePending);
    }

    private PendingEvent queueReliableEvent(ServerPlayerSlot target, ReliableEvent event) {
        if (target == null || !target.isGameplayReady()) {
            return null;
        }
        return target.addPendingEvent(event);
    }

    private void resendPendingEvents(ServerPlayerSlot target) {
        if (target == null) {
            return;
        }
        long now = System.nanoTime();
        for (PendingEvent pending : target.pendingEventsSnapshot()) {
            if (now - pending.lastSentNanos >= EVENT_RESEND_INTERVAL_NANOS) {
                sendEvent(target, pending);
            }
        }
    }

    private void retryReliablePackets() {
        if (!running) {
            return;
        }

        ServerPlayerSlot red = redPlayer;
        ServerPlayerSlot blue = bluePlayer;
        long now = System.nanoTime();
        if (!terminating && ((red != null && red.hasExpiredEvent(now, RELIABLE_ACK_TIMEOUT_NANOS))
                || (blue != null && blue.hasExpiredEvent(now, RELIABLE_ACK_TIMEOUT_NANOS)))) {
            beginTermination(SYNC_TIMEOUT_MESSAGE);
        }

        resendPendingEvents(red);
        resendPendingEvents(blue);
        if (terminating) {
            resendMatchAborted(red);
            resendMatchAborted(blue);
        }
    }

    private void sendEvent(ServerPlayerSlot target, PendingEvent pending) {
        try {
            ReliableEvent event = pending.event;
            sendUdp(
                    UdpCodec.event(
                            target.sessionToken,
                            event.id,
                            event.serverTick,
                            event.type,
                            event.collisionRevision,
                            event.state
                    ),
                    target.address,
                    target.port
            );
            pending.markSent();
        } catch (IOException exception) {
            beginTermination();
        }
    }

    /** 每個物理 tick 傳送最新球及球員；舊快照遺失時不重送。 */
    private void sendWorldSnapshot() {
        Packet.WorldSnapshot snapshot = Packet.WorldSnapshot.from(model, collisionRevision);
        int sequence = ++nextWorldSnapshotSequence;
        sendWorldSnapshot(redPlayer, sequence, snapshot);
        sendWorldSnapshot(bluePlayer, sequence, snapshot);
    }

    private void sendWorldSnapshot(ServerPlayerSlot target, int sequence, Packet.WorldSnapshot snapshot) {
        if (target == null || !target.isGameplayReady()) {
            return;
        }
        try {
            sendUdp(
                    UdpCodec.worldSnapshot(target.sessionToken, serverTick, sequence, snapshot),
                    target.address,
                    target.port
            );
        } catch (IOException exception) {
            beginTermination();
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (running && !socket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                if (decoded != null) {
                    handleDatagram(decoded, datagram.getAddress(), datagram.getPort());
                }
            } catch (SocketException ignored) {
                return;
            } catch (IOException exception) {
                beginTermination();
                return;
            }
        }
    }

    private void handleDatagram(UdpCodec.Decoded decoded, InetAddress address, int port) {
        if (decoded instanceof UdpCodec.Hello hello) {
            if (!terminating) {
                handleHello(hello, address, port);
            }
            return;
        }

        ServerPlayerSlot slot = findSlotByToken(decoded, address, port);
        if (slot == null) {
            return;
        }

        if (decoded instanceof UdpCodec.InputFrame input) {
            slot.acceptInput(input);
        } else if (decoded instanceof UdpCodec.EventAck ack) {
            slot.acknowledgeEvent(ack.eventId);
        } else if (decoded instanceof UdpCodec.ControlFrame control) {
            if (!slot.acceptControl(control)) {
                // 收到亂序指令只能確認已真正處理的連續序號，不能誤認後面的指令已執行。
                sendControlStatus(slot, slot.getLastControlSequence());
            }
        }
    }

    private void handleHello(UdpCodec.Hello hello, InetAddress address, int port) {
        ServerPlayerSlot slot;
        synchronized (slotsLock) {
            slot = findSlotByNonce(hello.clientNonce);
            if (slot == null) {
                if (redPlayer == null) {
                    slot = new ServerPlayerSlot(true, hello.clientNonce, address, port);
                    redPlayer = slot;
                } else if (bluePlayer == null) {
                    slot = new ServerPlayerSlot(false, hello.clientNonce, address, port);
                    bluePlayer = slot;
                }
            }

            if (slot != null) {
                slot.updateEndpoint(address, port);
                slot.markHeard();
            }
        }

        // 已有兩位玩家時，其他來源封包直接忽略。
        if (slot == null) {
            return;
        }

        try {
            sendUdp(
                    UdpCodec.welcome(
                            hello.clientNonce,
                            slot.sessionToken,
                            slot.redSide,
                            serverTick,
                            Packet.CompactState.from(model)
                    ),
                    address,
                    port
            );
        } catch (IOException exception) {
            beginTermination();
        }
    }

    private ServerPlayerSlot findSlotByToken(UdpCodec.Decoded decoded, InetAddress address, int port) {
        long token;
        if (decoded instanceof UdpCodec.InputFrame input) {
            token = input.token;
        } else if (decoded instanceof UdpCodec.EventAck ack) {
            token = ack.token;
        } else if (decoded instanceof UdpCodec.ControlFrame control) {
            token = control.token;
        } else {
            return null;
        }

        ServerPlayerSlot red = redPlayer;
        if (red != null && red.matches(token, address, port)) {
            return red;
        }
        ServerPlayerSlot blue = bluePlayer;
        return blue != null && blue.matches(token, address, port) ? blue : null;
    }

    private ServerPlayerSlot findSlotByNonce(long nonce) {
        ServerPlayerSlot red = redPlayer;
        if (red != null && red.clientNonce == nonce) return red;
        ServerPlayerSlot blue = bluePlayer;
        return blue != null && blue.clientNonce == nonce ? blue : null;
    }

    private synchronized void sendControlStatus(ServerPlayerSlot target, int acknowledgedSequence) {
        try {
            sendUdp(
                    UdpCodec.controlStatus(
                            target.sessionToken,
                            nextControlStatusId.incrementAndGet(),
                            acknowledgedSequence,
                            redResetConfirmed,
                            blueResetConfirmed
                    ),
                    target.address,
                    target.port
            );
        } catch (IOException exception) {
            beginTermination();
        }
    }

    private void broadcastControlStatus() {
        ServerPlayerSlot red = redPlayer;
        ServerPlayerSlot blue = bluePlayer;
        if (red != null) sendControlStatus(red, red.getLastControlSequence());
        if (blue != null) sendControlStatus(blue, blue.getLastControlSequence());
    }

    private boolean hasTimedOut(ServerPlayerSlot slot) {
        return slot != null && slot.hasTimedOut(System.nanoTime(), CLIENT_TIMEOUT_NANOS);
    }

    private boolean clearResetConfirmation() {
        boolean changed = redResetConfirmed || blueResetConfirmed;
        redResetConfirmed = false;
        blueResetConfirmed = false;
        return changed;
    }

    private synchronized void sendUdp(byte[] data, InetAddress address, int port) throws IOException {
        if (socket.isClosed() || address == null || port <= 0) {
            return;
        }
        socket.send(new DatagramPacket(data, data.length, address, port));
    }

    private void beginTermination() {
        beginTermination(MATCH_ABORTED_MESSAGE);
    }

    private void beginTermination(String reason) {
        int abortEventId;
        synchronized (eventOrderLock) {
            if (!terminationStarted.compareAndSet(false, true)) {
                return;
            }

            terminating = true;
            endMessage = reason;
            terminationDeadlineNanos = System.nanoTime() + TERMINATION_ACK_TIMEOUT_NANOS;
            abortEventId = nextEventId.incrementAndGet();
            if (redPlayer != null) redPlayer.beginMatchAbort(abortEventId);
            if (bluePlayer != null) bluePlayer.beginMatchAbort(abortEventId);
        }
        sendQueuedMatchAborted(redPlayer);
        sendQueuedMatchAborted(bluePlayer);
    }

    private void sendQueuedMatchAborted(ServerPlayerSlot target) {
        if (target == null) {
            return;
        }
        PendingMatchAbort pending = target.getPendingMatchAbort();
        if (pending != null) sendMatchAborted(target, pending);
    }

    private void updateTermination() {
        boolean allAcknowledged = isMatchAbortAcknowledged(redPlayer) && isMatchAbortAcknowledged(bluePlayer);
        if (allAcknowledged || System.nanoTime() >= terminationDeadlineNanos) {
            running = false;
            socket.close();
        }
    }

    private void resendMatchAborted(ServerPlayerSlot target) {
        if (target == null) {
            return;
        }
        PendingMatchAbort pending = target.getPendingMatchAbort();
        if (pending != null && System.nanoTime() - pending.lastSentNanos >= EVENT_RESEND_INTERVAL_NANOS) {
            sendMatchAborted(target, pending);
        }
    }

    private void sendMatchAborted(ServerPlayerSlot target, PendingMatchAbort pending) {
        try {
            sendUdp(UdpCodec.matchAborted(target.sessionToken, pending.eventId, endMessage), target.address, target.port);
            pending.markSent();
        } catch (IOException ignored) {
            // 對方已離線時，等待終止 ACK 期限結束即可。
        }
    }

    private boolean isMatchAbortAcknowledged(ServerPlayerSlot target) {
        return target == null
                || (target.getPendingMatchAbort() == null && !target.hasPendingEvents());
    }

    private static void sleepUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public String getLocalIp() {
        return localIp;
    }

    public String getEndMessage() {
        return endMessage;
    }

    @Override
    public void close() {
        if (!terminating) {
            beginTermination();
        }
    }

    private static final class FrameState {
        final int redScore;
        final int blueScore;
        final boolean rallyOver;
        final boolean lockedScorePhase;
        final ServeState serveState;

        FrameState(int redScore, int blueScore, boolean rallyOver,
                   boolean lockedScorePhase, ServeState serveState) {
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.rallyOver = rallyOver;
            this.lockedScorePhase = lockedScorePhase;
            this.serveState = serveState;
        }

        static FrameState capture(GameModel model) {
            return new FrameState(
                    model.redScore,
                    model.blueScore,
                    model.isRallyOverForNetwork(),
                    model.isLockedScorePhase(),
                    model.getServeHandler().getState()
            );
        }
    }

    private static final class ControlResult {
        final boolean statusChanged;
        final boolean resetApplied;

        ControlResult(boolean statusChanged, boolean resetApplied) {
            this.statusChanged = statusChanged;
            this.resetApplied = resetApplied;
        }
    }


}
