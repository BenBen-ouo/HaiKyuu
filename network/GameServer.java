/*
獨立、無畫面的 UDP 權威 Server。
固定以 60 tick/s 執行 GameModel。
INPUT 與 BALL_SNAPSHOT 不可靠傳送；指定 COLLISION_EVENT 以 ACK 重送完整權威狀態。
*/
package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import model.GameConfig;
import model.GameModel;
import model.ServeState;

public final class GameServer implements AutoCloseable {
    public static final int UDP_PORT = 5001;
    public static final String MATCH_ABORTED_MESSAGE = "對局結束，必須雙方重新開啟遊戲才能重玩。";

    private static final long TICK_NANOS = 1_000_000_000L / GameConfig.TICKS_PER_SECOND;
    private static final long CLIENT_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long INPUT_RELAY_INTERVAL_NANOS = 40_000_000L; // 25 Hz
    private static final long BALL_SNAPSHOT_INTERVAL_NANOS = 50_000_000L; // 20 Hz
    private static final long EVENT_RESEND_INTERVAL_NANOS = 200_000_000L;
    private static final long TERMINATION_ACK_TIMEOUT_NANOS = 2_000_000_000L;

    private final GameModel model = new GameModel();
    private final DatagramSocket socket;
    private final String localIp;
    private final Object slotsLock = new Object();
    private final AtomicBoolean terminationStarted = new AtomicBoolean();

    private volatile ServerPlayerSlot redPlayer;
    private volatile ServerPlayerSlot bluePlayer;
    private volatile boolean running = true;
    private volatile boolean terminating;
    private volatile String endMessage = "";

    private int serverTick;
    private int nextEventId;
    private int lastRedInputMask = Integer.MIN_VALUE;
    private int lastBlueInputMask = Integer.MIN_VALUE;
    private long lastRedInputRelayNanos;
    private long lastBlueInputRelayNanos;
    private long lastBallSnapshotNanos;
    private long terminationDeadlineNanos;
    private int nextBallSnapshotSequence;
    private int collisionRevision;
    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;

    public GameServer() throws SocketException {
        socket = new DatagramSocket(UDP_PORT);
        localIp = NetworkAddress.findLocalIpv4();
    }

    public void run() {
        Thread receiveThread = new Thread(this::receiveLoop, "haikyuu-udp-server-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        System.out.println("HaiKyuu UDP Server 已啟動（無畫面）");
        System.out.println("UDP 5001: " + localIp);
        System.out.println("Player 1 與 Player 2 都使用：java -cp build Main join <Server-IP>");

        long nextTick = System.nanoTime();
        while (running) {
            updateOneTick();
            nextTick += TICK_NANOS;
            sleepUntil(nextTick);
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
            beginTermination();
            return;
        }

        resendPendingEvents(red);
        resendPendingEvents(blue);

        if (serverTick % GameConfig.TICKS_PER_SECOND == 0) {
            if (red != null) sendControlStatus(red, red.getLastControlSequence());
            if (blue != null) sendControlStatus(blue, blue.getLastControlSequence());
        }

        if (red == null || blue == null || !red.isGameplayReady() || !blue.isGameplayReady()) {
            return;
        }

        ControlResult controlResult = processControls(red, blue);
        if (terminating) {
            return;
        }
        if (controlResult.resetApplied) {
            sendReliableEvent(Packet.EventType.RESET, Packet.CompactState.from(model));
        }
        if (controlResult.statusChanged) {
            broadcastControlStatus();
        }

        FrameState before = FrameState.capture(model);
        int redMask = red.takeInputMaskForTick();
        int blueMask = blue.takeInputMaskForTick();
        model.update(Packet.decodeInput(redMask), Packet.decodeInput(blueMask));

        relayRemoteInputs(red, blue, redMask, blueMask);

        for (Packet.EventType eventType : detectSyncEvents(before)) {
            sendReliableEvent(eventType, Packet.CompactState.from(model));
        }
        sendBallSnapshotIfNeeded();
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
        List<Packet.EventType> events = new ArrayList<>(3);

        ServeState currentServeState = model.getServeHandler().getState();
        if (before.serveState == ServeState.READY
                && currentServeState != ServeState.READY
                && Math.abs(model.ball.vx) + Math.abs(model.ball.vy) > 0.01) {
            events.add(Packet.EventType.SERVE);
        }

        if (model.didSetterContactThisFrame()) {
            events.add(Packet.EventType.SETTER_CONTACT);
        }

        if (model.didBallLandThisFrame()) {
            events.add(Packet.EventType.LANDING);
        }

        boolean scoreChanged = before.redScore != model.redScore || before.blueScore != model.blueScore;
        if (scoreChanged && isRuleMessage(model.transientMessage)) {
            events.add(Packet.EventType.RULE);
        }
        if (scoreChanged || (before.rallyOver && !model.isRallyOverForNetwork())) {
            // SCORE 也負責 Server 完成下一次發球準備後的位置同步。
            events.add(Packet.EventType.SCORE);
        }

        return events;
    }

    private boolean isRuleMessage(String message) {
        return "四觸違規".equals(message)
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
        ReliableEvent event = new ReliableEvent(
                ++nextEventId,
                serverTick,
                type,
                ++collisionRevision,
                state
        );
        queueReliableEvent(redPlayer, event);
        queueReliableEvent(bluePlayer, event);
    }

    private void queueReliableEvent(ServerPlayerSlot target, ReliableEvent event) {
        if (target == null || !target.isGameplayReady()) {
            return;
        }
        PendingEvent pending = target.addPendingEvent(event);
        sendEvent(target, pending);
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

    /** 回合中每秒 20 次送出球專用快照；不含完整 GameModel。 */
    private void sendBallSnapshotIfNeeded() {
        if (!model.getServeHandler().shouldUpdateBall()) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastBallSnapshotNanos < BALL_SNAPSHOT_INTERVAL_NANOS) {
            return;
        }

        lastBallSnapshotNanos = now;
        Packet.BallSnapshot snapshot = Packet.BallSnapshot.from(model, collisionRevision);
        int sequence = ++nextBallSnapshotSequence;
        sendBallSnapshot(redPlayer, sequence, snapshot);
        sendBallSnapshot(bluePlayer, sequence, snapshot);
    }

    private void sendBallSnapshot(ServerPlayerSlot target, int sequence, Packet.BallSnapshot snapshot) {
        if (target == null || !target.isGameplayReady()) {
            return;
        }
        try {
            sendUdp(
                    UdpCodec.ballSnapshot(target.sessionToken, serverTick, sequence, snapshot),
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
            if (slot.acceptControl(control)) {
                sendControlStatus(slot, control.sequence);
            } else if (control.token == slot.sessionToken) {
                sendControlStatus(slot, control.sequence);
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

    private void sendControlStatus(ServerPlayerSlot target, int acknowledgedSequence) {
        try {
            sendUdp(
                    UdpCodec.controlStatus(
                            target.sessionToken,
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
        if (!terminationStarted.compareAndSet(false, true)) {
            return;
        }

        terminating = true;
        endMessage = MATCH_ABORTED_MESSAGE;
        terminationDeadlineNanos = System.nanoTime() + TERMINATION_ACK_TIMEOUT_NANOS;
        queueMatchAborted(redPlayer);
        queueMatchAborted(bluePlayer);
    }

    private void queueMatchAborted(ServerPlayerSlot target) {
        if (target == null) {
            return;
        }
        PendingMatchAbort pending = target.beginMatchAbort(++nextEventId);
        sendMatchAborted(target, pending);
    }

    private void updateTermination() {
        resendMatchAborted(redPlayer);
        resendMatchAborted(bluePlayer);

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
            sendUdp(UdpCodec.matchAborted(target.sessionToken, pending.eventId), target.address, target.port);
            pending.markSent();
        } catch (IOException ignored) {
            // 對方已離線時，等待終止 ACK 期限結束即可。
        }
    }

    private boolean isMatchAbortAcknowledged(ServerPlayerSlot target) {
        return target == null || target.getPendingMatchAbort() == null;
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
        final ServeState serveState;

        FrameState(int redScore, int blueScore, boolean rallyOver, ServeState serveState) {
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.rallyOver = rallyOver;
            this.serveState = serveState;
        }

        static FrameState capture(GameModel model) {
            return new FrameState(
                    model.redScore,
                    model.blueScore,
                    model.isRallyOverForNetwork(),
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
