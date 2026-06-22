/*
獨立、無畫面的 UDP 權威 Server。
固定以 60 tick 執行 GameModel；兩個 Client 只傳輸按鍵與可靠控制，完整狀態只在事件或過網時送出。
*/
package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import model.GameConfig;
import model.GameModel;
import model.ServeState;
import model.TeamInput;

public final class GameServer implements AutoCloseable {
    public static final int UDP_PORT = 5001;

    private static final int TICKS_PER_SECOND = 60;
    private static final long TICK_NANOS = 1_000_000_000L / TICKS_PER_SECOND;
    private static final long CLIENT_TIMEOUT_NANOS = 3_000_000_000L;
    private static final int INPUT_RELAY_INTERVAL_TICKS = 5;
    private static final int MAX_PENDING_EVENTS = 8;

    private final GameModel model = new GameModel();
    private final DatagramSocket socket;
    private final String localIp;
    private final Object slotsLock = new Object();
    private final AtomicBoolean sessionEnded = new AtomicBoolean();

    private volatile PlayerSlot redPlayer;
    private volatile PlayerSlot bluePlayer;
    private volatile boolean running = true;
    private volatile String endMessage = "";

    private Thread receiveThread;
    private int serverTick;
    private int nextEventId;
    private int lastRedInputMask;
    private int lastBlueInputMask;
    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;

    public GameServer() throws SocketException {
        socket = new DatagramSocket(UDP_PORT);
        localIp = NetworkAddress.findLocalIpv4();
    }

    public void run() {
        receiveThread = new Thread(this::receiveLoop, "haikyuu-udp-server-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        System.out.println("HaiKyuu UDP Server 已啟動（無畫面）");
        System.out.println("UDP 5001: " + localIp);
        System.out.println("Player 1 與 Player 2 都使用：java Main join <Server-IP>");

        long nextTick = System.nanoTime();
        while (running && !sessionEnded.get()) {
            updateOneTick();
            nextTick += TICK_NANOS;
            sleepUntil(nextTick);
        }

        if (!endMessage.isBlank()) {
            System.out.println(endMessage);
        }
    }

    private void updateOneTick() {
        if (sessionEnded.get()) {
            return;
        }

        serverTick++;
        PlayerSlot red = redPlayer;
        PlayerSlot blue = bluePlayer;
        if (hasTimedOut(red) || hasTimedOut(blue)) {
            endSession("有玩家網路中斷，本局結束");
            return;
        }

        if (serverTick % TICKS_PER_SECOND == 0) {
            if (red != null) {
                sendControlStatus(red, red.getLastControlSequence());
            }
            if (blue != null) {
                sendControlStatus(blue, blue.getLastControlSequence());
            }
        }

        if (red == null || blue == null || !red.isGameplayReady() || !blue.isGameplayReady()) {
            return;
        }

        ControlResult controlResult = processControls(red, blue);
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

        Packet.EventType eventType = detectSyncEvent(before);
        if (eventType != null) {
            sendReliableEvent(eventType, Packet.CompactState.from(model));
        }
    }

    private ControlResult processControls(PlayerSlot red, PlayerSlot blue) {
        boolean statusChanged = false;
        boolean resetApplied = false;

        ControlCommand command;
        while ((command = red.pollControl()) != null) {
            if (command.action == UdpCodec.ControlAction.DISCONNECT) {
                endSession("Player 1 已斷線，本局結束");
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
                endSession("Player 2 已斷線，本局結束");
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

        return new ControlResult(statusChanged, resetApplied);
    }

    private Packet.EventType detectSyncEvent(FrameState before) {
        if (model.didBallHitNetThisFrame()) {
            return Packet.EventType.NET_BOUNCE;
        }

        if (before.redScore != model.redScore || before.blueScore != model.blueScore) {
            return Packet.EventType.SCORE;
        }

        if (!before.rallyOver && model.isRallyOverForNetwork()) {
            return Packet.EventType.LANDING;
        }

        ServeState currentServeState = model.getServeHandler().getState();
        if (before.serveState == ServeState.READY
                && currentServeState != ServeState.READY
                && Math.abs(model.ball.vx) + Math.abs(model.ball.vy) > 0.01) {
            return Packet.EventType.SERVE;
        }

        if (!equalsNullable(before.transientMessage, model.transientMessage)
                && model.transientMessage != null) {
            return Packet.EventType.RULE;
        }

        boolean crossedNet = !before.rallyOver
                && !model.isRallyOverForNetwork()
                && ((before.ballX < GameConfig.NET_X && model.ball.x >= GameConfig.NET_X)
                || (before.ballX > GameConfig.NET_X && model.ball.x <= GameConfig.NET_X));
        return crossedNet ? Packet.EventType.NET_CROSS : null;
    }

    private void relayRemoteInputs(PlayerSlot red, PlayerSlot blue, int redMask, int blueMask) {
        boolean heartbeat = serverTick % INPUT_RELAY_INTERVAL_TICKS == 0;
        if (heartbeat || redMask != lastRedInputMask) {
            sendRemoteInput(blue, redMask);
            lastRedInputMask = redMask;
        }
        if (heartbeat || blueMask != lastBlueInputMask) {
            sendRemoteInput(red, blueMask);
            lastBlueInputMask = blueMask;
        }
    }

    private void sendRemoteInput(PlayerSlot target, int inputMask) {
        try {
            sendUdp(UdpCodec.remoteInput(target.sessionToken, serverTick, inputMask), target.address, target.port);
        } catch (IOException exception) {
            endSession("UDP 傳送失敗，本局結束");
        }
    }

    private void sendReliableEvent(Packet.EventType type, Packet.CompactState state) {
        ReliableEvent event = new ReliableEvent(++nextEventId, serverTick, type, state);
        sendReliableEvent(redPlayer, event);
        sendReliableEvent(bluePlayer, event);
    }

    private void sendReliableEvent(PlayerSlot target, ReliableEvent event) {
        if (target == null || !target.isGameplayReady()) {
            return;
        }
        target.addPendingEvent(event);
        try {
            sendUdp(
                    UdpCodec.event(target.sessionToken, event.id, event.serverTick, event.type, event.state),
                    target.address,
                    target.port
            );
        } catch (IOException exception) {
            endSession("UDP 傳送失敗，本局結束");
        }
    }

    private void resendPendingEvents(PlayerSlot target) {
        for (ReliableEvent event : target.pendingEventsSnapshot()) {
            try {
                sendUdp(
                        UdpCodec.event(target.sessionToken, event.id, event.serverTick, event.type, event.state),
                        target.address,
                        target.port
                );
            } catch (IOException exception) {
                endSession("UDP 傳送失敗，本局結束");
                return;
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (running && !sessionEnded.get() && !socket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                if (decoded == null) {
                    continue;
                }
                handleDatagram(decoded, datagram.getAddress(), datagram.getPort());
            } catch (SocketException exception) {
                return;
            } catch (IOException exception) {
                if (!sessionEnded.get()) {
                    System.err.println("UDP 接收失敗: " + exception.getMessage());
                }
            }
        }
    }

    private void handleDatagram(UdpCodec.Decoded decoded, InetAddress address, int port) {
        if (decoded instanceof UdpCodec.Hello hello) {
            handleHello(hello, address, port);
            return;
        }

        PlayerSlot slot = findSlotByToken(decoded, address, port);
        if (slot == null) {
            return;
        }

        if (decoded instanceof UdpCodec.InputFrame input) {
            if (slot.acceptInput(input)) {
                resendPendingEvents(slot);
            }
            return;
        }

        if (decoded instanceof UdpCodec.ControlFrame control) {
            if (slot.acceptControl(control)) {
                sendControlStatus(slot, control.sequence);
            } else if (control.token == slot.sessionToken) {
                sendControlStatus(slot, control.sequence);
            }
        }
    }

    private void handleHello(UdpCodec.Hello hello, InetAddress address, int port) {
        if (hello.protocolVersion != Packet.PROTOCOL_VERSION) {
            sendReject(hello.clientNonce, address, port, "網路協定版本不相容");
            return;
        }

        PlayerSlot slot;
        synchronized (slotsLock) {
            slot = findSlotByNonce(hello.clientNonce);
            if (slot == null) {
                if (redPlayer == null) {
                    slot = new PlayerSlot(true, hello.clientNonce, address, port);
                    redPlayer = slot;
                } else if (bluePlayer == null) {
                    slot = new PlayerSlot(false, hello.clientNonce, address, port);
                    bluePlayer = slot;
                }
            }

            if (slot != null) {
                slot.updateEndpoint(address, port);
                slot.markHeard();
            }
        }

        if (slot == null) {
            sendReject(hello.clientNonce, address, port, "Server 已有兩位玩家，無法加入");
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
            endSession("UDP 握手回覆失敗，本局結束");
        }
    }

    private PlayerSlot findSlotByToken(UdpCodec.Decoded decoded, InetAddress address, int port) {
        long token;
        if (decoded instanceof UdpCodec.InputFrame input) {
            token = input.token;
        } else if (decoded instanceof UdpCodec.ControlFrame control) {
            token = control.token;
        } else {
            return null;
        }

        PlayerSlot red = redPlayer;
        if (red != null && red.matches(token, address, port)) {
            return red;
        }
        PlayerSlot blue = bluePlayer;
        return blue != null && blue.matches(token, address, port) ? blue : null;
    }

    private PlayerSlot findSlotByNonce(long nonce) {
        PlayerSlot red = redPlayer;
        if (red != null && red.clientNonce == nonce) {
            return red;
        }
        PlayerSlot blue = bluePlayer;
        return blue != null && blue.clientNonce == nonce ? blue : null;
    }

    private void sendReject(long nonce, InetAddress address, int port, String message) {
        try {
            sendUdp(UdpCodec.reject(nonce, message), address, port);
        } catch (IOException ignored) {
            // 無法回覆拒絕訊息時，Client 會自行以逾時處理。
        }
    }

    private void sendControlStatus(PlayerSlot target, int acknowledgedSequence) {
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
            endSession("UDP 傳送失敗，本局結束");
        }
    }

    private void broadcastControlStatus() {
        PlayerSlot red = redPlayer;
        PlayerSlot blue = bluePlayer;
        if (red != null) {
            sendControlStatus(red, red.getLastControlSequence());
        }
        if (blue != null) {
            sendControlStatus(blue, blue.getLastControlSequence());
        }
    }

    private boolean hasTimedOut(PlayerSlot slot) {
        return slot != null && System.nanoTime() - slot.lastHeardNanos.get() > CLIENT_TIMEOUT_NANOS;
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

    private void endSession(String message) {
        if (!sessionEnded.compareAndSet(false, true)) {
            return;
        }

        endMessage = message;
        sendSessionEnd(redPlayer, message);
        sendSessionEnd(bluePlayer, message);
        running = false;
        socket.close();
    }

    private void sendSessionEnd(PlayerSlot target, String message) {
        if (target == null || target.address == null || target.port <= 0) {
            return;
        }
        try {
            byte[] data = UdpCodec.sessionEnd(target.sessionToken, message);
            // 斷線事件本身也用 UDP，因此在關閉 socket 前連續送三次降低遺失機率。
            for (int i = 0; i < 3; i++) {
                sendUdp(data, target.address, target.port);
            }
        } catch (IOException ignored) {
            // 對方已離線時無法送達是預期結果。
        }
    }

    private static void sleepUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return;
        }
        try {
            long millis = remaining / 1_000_000L;
            int nanos = (int) (remaining % 1_000_000L);
            Thread.sleep(millis, nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public String getLocalIp() {
        return localIp;
    }

    public String getEndMessage() {
        return endMessage;
    }

    @Override
    public void close() {
        if (!sessionEnded.get()) {
            endSession("Server 已關閉，本局結束");
        }
    }

    private static final class FrameState {
        final double ballX;
        final int redScore;
        final int blueScore;
        final boolean rallyOver;
        final ServeState serveState;
        final String transientMessage;

        private FrameState(
                double ballX,
                int redScore,
                int blueScore,
                boolean rallyOver,
                ServeState serveState,
                String transientMessage
        ) {
            this.ballX = ballX;
            this.redScore = redScore;
            this.blueScore = blueScore;
            this.rallyOver = rallyOver;
            this.serveState = serveState;
            this.transientMessage = transientMessage;
        }

        static FrameState capture(GameModel model) {
            return new FrameState(
                    model.ball.x,
                    model.redScore,
                    model.blueScore,
                    model.isRallyOverForNetwork(),
                    model.getServeHandler().getState(),
                    model.transientMessage
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

    private static final class ControlCommand {
        final int sequence;
        final UdpCodec.ControlAction action;

        ControlCommand(int sequence, UdpCodec.ControlAction action) {
            this.sequence = sequence;
            this.action = action;
        }
    }

    private static final class ReliableEvent {
        final int id;
        final int serverTick;
        final Packet.EventType type;
        final Packet.CompactState state;

        ReliableEvent(int id, int serverTick, Packet.EventType type, Packet.CompactState state) {
            this.id = id;
            this.serverTick = serverTick;
            this.type = type;
            this.state = state;
        }
    }

    private static final class PlayerSlot {
        final boolean redSide;
        final long clientNonce;
        final long sessionToken = ThreadLocalRandom.current().nextLong();
        final AtomicInteger latestInputMask = new AtomicInteger();
        final AtomicInteger pendingPressedMask = new AtomicInteger();
        final AtomicInteger lastInputSequence = new AtomicInteger(-1);
        final AtomicInteger lastAcknowledgedEventId = new AtomicInteger();
        final AtomicInteger lastControlSequence = new AtomicInteger(-1);
        final AtomicLong lastHeardNanos = new AtomicLong(System.nanoTime());
        final ConcurrentLinkedQueue<ControlCommand> pendingControls = new ConcurrentLinkedQueue<>();
        final Deque<ReliableEvent> pendingEvents = new ArrayDeque<>();

        volatile InetAddress address;
        volatile int port;
        volatile boolean gameplayReady;

        PlayerSlot(boolean redSide, long clientNonce, InetAddress address, int port) {
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

        boolean acceptInput(UdpCodec.InputFrame input) {
            if (input.sequence <= lastInputSequence.get()) {
                markHeard();
                acknowledgeEvents(input.lastReceivedEventId);
                return false;
            }
            lastInputSequence.set(input.sequence);
            int previousMask = latestInputMask.getAndSet(input.inputMask);
            int pressedSinceLastFrame = input.inputMask & ~previousMask;
            pendingPressedMask.getAndAccumulate(pressedSinceLastFrame, (current, pressed) -> current | pressed);
            gameplayReady = true;
            markHeard();
            acknowledgeEvents(input.lastReceivedEventId);
            return true;
        }

        boolean acceptControl(UdpCodec.ControlFrame control) {
            markHeard();
            if (control.sequence <= lastControlSequence.get()) {
                return false;
            }
            lastControlSequence.set(control.sequence);
            pendingControls.offer(new ControlCommand(control.sequence, control.action));
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

        ControlCommand pollControl() {
            return pendingControls.poll();
        }

        synchronized void addPendingEvent(ReliableEvent event) {
            pendingEvents.addLast(event);
            while (pendingEvents.size() > MAX_PENDING_EVENTS) {
                pendingEvents.removeFirst();
            }
        }

        synchronized void acknowledgeEvents(int eventId) {
            lastAcknowledgedEventId.accumulateAndGet(eventId, Math::max);
            while (!pendingEvents.isEmpty() && pendingEvents.peekFirst().id <= lastAcknowledgedEventId.get()) {
                pendingEvents.removeFirst();
            }
        }

        synchronized ReliableEvent[] pendingEventsSnapshot() {
            return pendingEvents.toArray(new ReliableEvent[0]);
        }
    }
}
