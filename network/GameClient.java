/*
純 UDP Client。
Client 以 60 tick/s 預測雙方輸入；Server 以 INPUT、WORLD_SNAPSHOT、COLLISION_EVENT 三層 UDP 資料校正。
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import model.GameModel;
import model.TeamInput;
import model.player.Player;
import model.player.Team;

public final class GameClient implements NetworkView {
    private static final long HELLO_INTERVAL_NANOS = 250_000_000L;
    private static final long CONTROL_RESEND_INTERVAL_NANOS = 60_000_000L;
    private static final long CONTROL_ACK_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long INPUT_HEARTBEAT_NANOS = 40_000_000L; // 25 Hz
    private static final long SERVER_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long TERMINATION_ACK_GRACE_NANOS = 2_000_000_000L;

    private final GameModel renderModel;
    private final String hostIp;
    private final InetAddress hostAddress;
    private final DatagramSocket socket;
    private final long clientNonce = ThreadLocalRandom.current().nextLong();
    private final ConcurrentLinkedQueue<UdpCodec.Decoded> incoming = new ConcurrentLinkedQueue<>();
    private final AtomicReference<UdpCodec.WorldSnapshotFrame> latestWorldSnapshot = new AtomicReference<>();
    private final AtomicReference<String> receiveFailure = new AtomicReference<>();
    private final Thread receiveThread;
    private final ScheduledExecutorService controlRetryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "haikyuu-udp-control-retry");
        thread.setDaemon(true);
        return thread;
    });
    private final Object pendingControlsLock = new Object();
    private final Deque<PendingControl> pendingControls = new ArrayDeque<>();
    private final ReliableEventInbox reliableEvents = new ReliableEventInbox();
    private final WorldSnapshotInbox snapshotInbox = new WorldSnapshotInbox();

    private volatile boolean assigned;
    private volatile boolean redSide;
    private volatile long sessionToken;
    private volatile boolean sessionEnded;
    private volatile String endMessage = "";
    private volatile String pendingAbortMessage;
    private volatile long lastServerPacketNanos;

    private int estimatedServerTick;
    private int inputSequence;
    private int controlSequence;
    private int lastControlStatusSequence;
    private int lastInputMask = Integer.MIN_VALUE;
    private int remoteInputMask;
    private int lastRemoteInputTick = -1;
    private boolean receivedRemoteInput;
    private int lastCollisionRevision;
    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;
    private boolean previousRestartDown;
    private boolean previousCancelDown;
    private long lastHelloNanos;
    private long lastInputSendNanos;
    private long terminationAckUntilNanos;

    // 物理球立即採用 Server 狀態；畫面球則由此校正器短暫平滑追上。
    private final BallRenderCorrection ballRenderCorrection = new BallRenderCorrection();
    private final PlayerRenderCorrection playerRenderCorrection = new PlayerRenderCorrection();

    public GameClient(GameModel renderModel, String hostIp) throws IOException {
        this.renderModel = renderModel;
        this.hostIp = hostIp;
        this.hostAddress = InetAddress.getByName(hostIp);
        this.socket = new DatagramSocket();

        receiveThread = new Thread(this::receiveLoop, "haikyuu-udp-client-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
        controlRetryExecutor.scheduleAtFixedRate(this::retryPendingControls, 10, 10, TimeUnit.MILLISECONDS);
        sendHello();
    }

    public void update(TeamInput keyboardInput, boolean restartDown, boolean cancelResetDown) {
        drainIncoming();

        if (sessionEnded) {
            closeAfterTerminationAckGrace();
            return;
        }

        String failure = receiveFailure.getAndSet(null);
        if (failure != null) {
            endLocally("網路接收失敗，連線已中止：" + failure);
            return;
        }

        if (!assigned) {
            resendHelloIfNeeded();
            return;
        }

        estimatedServerTick++;
        if (receivedRemoteInput) {
            processControls(restartDown, cancelResetDown);
        } else {
            previousRestartDown = restartDown;
            previousCancelDown = cancelResetDown;
        }

        TeamInput localInput = toWorldInput(keyboardInput);
        sendInputIfNeeded(localInput);

        if (receivedRemoteInput) {
            TeamInput remoteInput = Packet.decodeInput(remoteInputMask);
            if (redSide) {
                renderModel.updateForNetworkPrediction(localInput, remoteInput);
            } else {
                renderModel.updateForNetworkPrediction(remoteInput, localInput);
            }
        }

        ballRenderCorrection.advance();
        playerRenderCorrection.advance();

        if (receivedRemoteInput && System.nanoTime() - lastServerPacketNanos > SERVER_TIMEOUT_NANOS) {
            endLocally(pendingAbortMessage != null
                    ? pendingAbortMessage : GameServer.CLIENT_TIMEOUT_MESSAGE);
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (!socket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                if (!datagram.getAddress().equals(hostAddress) || datagram.getPort() != GameServer.UDP_PORT) {
                    continue;
                }

                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                if (decoded instanceof UdpCodec.WorldSnapshotFrame snapshot) {
                    // 即時快照只需要最新一份，避免畫面停頓後逐一補跑舊位置。
                    latestWorldSnapshot.accumulateAndGet(snapshot,
                            (current, newest) -> current == null
                                    || newest.snapshotSequence > current.snapshotSequence ? newest : current);
                } else if (decoded != null) {
                    incoming.offer(decoded);
                }
            } catch (SocketException ignored) {
                return;
            } catch (IOException exception) {
                if (!sessionEnded) {
                    receiveFailure.compareAndSet(null, exception.getMessage());
                }
                return;
            }
        }
    }

    private void drainIncoming() {
        UdpCodec.Decoded decoded;
        while ((decoded = incoming.poll()) != null) {
            if (decoded instanceof UdpCodec.Welcome welcome) {
                if (!sessionEnded) {
                    handleWelcome(welcome);
                }
            } else if (decoded instanceof UdpCodec.RemoteInputFrame remote) {
                if (!sessionEnded && isOwnToken(remote.token)) {
                    if (remote.serverTick > lastRemoteInputTick) {
                        lastRemoteInputTick = remote.serverTick;
                        remoteInputMask = remote.inputMask;
                        estimatedServerTick = Math.max(estimatedServerTick, remote.serverTick);
                        receivedRemoteInput = true;
                    }
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.Event event) {
                if (isOwnToken(event.token)) {
                    sendEventAck(event.eventId);
                    if (!sessionEnded) {
                        reliableEvents.offer(event);
                        applyReadyEvents();
                    }
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.ControlStatus status) {
                if (!sessionEnded && isOwnToken(status.token)
                        && status.statusSequence > lastControlStatusSequence) {
                    lastControlStatusSequence = status.statusSequence;
                    redResetConfirmed = status.redResetConfirmed;
                    blueResetConfirmed = status.blueResetConfirmed;
                    synchronized (pendingControlsLock) {
                        while (!pendingControls.isEmpty()
                                && status.acknowledgedSequence >= pendingControls.peekFirst().sequence) {
                            pendingControls.removeFirst();
                        }
                    }
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.MatchAborted aborted) {
                if (isOwnToken(aborted.token)) {
                    sendEventAck(aborted.eventId);
                    if (!sessionEnded) {
                        pendingAbortMessage = aborted.message;
                        reliableEvents.offer(aborted);
                        applyReadyEvents();
                    }
                }
            }
        }
    }

    private void handleWelcome(UdpCodec.Welcome welcome) {
        if (welcome.clientNonce != clientNonce) {
            return;
        }
        if (assigned && welcome.sessionToken != sessionToken) {
            return;
        }
        if (assigned) {
            // HELLO 重送造成的舊 WELCOME 不得覆蓋較新的事件與快照。
            return;
        }

        sessionToken = welcome.sessionToken;
        redSide = welcome.redSide;
        assigned = true;
        estimatedServerTick = welcome.serverTick;
        welcome.state.applyTo(renderModel);
        ballRenderCorrection.reset();
        playerRenderCorrection.reset();
        markServerPacketReceived();
    }

    private void applyReadyEvents() {
        UdpCodec.Decoded next;
        while ((next = reliableEvents.pollNext()) != null) {
            if (next instanceof UdpCodec.Event event) {
                applyOrderedEvent(event);
            } else if (next instanceof UdpCodec.MatchAborted aborted) {
                handleMatchAborted(aborted);
                break;
            }
        }
        UdpCodec.WorldSnapshotFrame snapshot = latestWorldSnapshot.getAndSet(null);
        if (snapshot != null && !sessionEnded && isOwnToken(snapshot.token)) {
            handleWorldSnapshot(snapshot);
            markServerPacketReceived();
        }
        applyPendingWorldSnapshot();
    }

    private void applyOrderedEvent(UdpCodec.Event event) {
        double visibleX = getRenderedBallX(renderModel.ball.x);
        double visibleY = getRenderedBallY(renderModel.ball.y);
        double visibleRotation = getRenderedBallRotation(renderModel.ball.rotationDegrees);
        PlayerVisualPositions playersBefore = captureVisiblePlayers();
        String previousMessage = renderModel.transientMessage;
        int previousMessageTimer = renderModel.transientMessageTimer;
        Boolean previousMessageColor = renderModel.transientMessageIsRed;

        lastCollisionRevision = Math.max(lastCollisionRevision, event.collisionRevision);
        estimatedServerTick = Math.max(estimatedServerTick, event.serverTick);
        event.state.applyTo(renderModel);
        if (event.type != Packet.EventType.SCORE && event.state.rallyOver) {
            // LANDING／RULE 不搶先顯示；FLOW 也不能重啟得分原因的顯示時間。
            renderModel.transientMessage = previousMessage;
            renderModel.transientMessageTimer = Math.min(previousMessageTimer, event.state.transientMessageTimer);
            renderModel.transientMessageIsRed = previousMessageColor;
        }
        if (event.type == Packet.EventType.FLOW
                && (renderModel.isLockedScorePhase()
                || renderModel.getServeHandler().isWaitingForServe())) {
            // 歸位與重新擺球是階段切換，球不應從上一個位置平滑滑入。
            ballRenderCorrection.reset();
            playerRenderCorrection.reset();
            return;
        }
        clearSpikeTrailIfLargeCorrection(visibleX, visibleY, visibleRotation);
        schedulePlayerCorrections(playersBefore, event.state.redTeam, event.state.blueTeam);
    }

    private void handleWorldSnapshot(UdpCodec.WorldSnapshotFrame frame) {
        UdpCodec.WorldSnapshotFrame ready = snapshotInbox.offer(frame, lastCollisionRevision);
        if (ready != null) {
            applyWorldSnapshot(ready);
        }
    }

    private void applyPendingWorldSnapshot() {
        UdpCodec.WorldSnapshotFrame ready = snapshotInbox.releaseAfterEvent(lastCollisionRevision);
        if (ready != null) {
            applyWorldSnapshot(ready);
        }
    }

    private void applyWorldSnapshot(UdpCodec.WorldSnapshotFrame frame) {
        double visibleX = getRenderedBallX(renderModel.ball.x);
        double visibleY = getRenderedBallY(renderModel.ball.y);
        double visibleRotation = getRenderedBallRotation(renderModel.ball.rotationDegrees);
        PlayerVisualPositions playersBefore = captureVisiblePlayers();

        estimatedServerTick = Math.max(estimatedServerTick, frame.serverTick);
        frame.snapshot.ball.applyTo(renderModel.ball);
        frame.snapshot.redTeam.applyMotionTo(renderModel.redTeam);
        frame.snapshot.blueTeam.applyMotionTo(renderModel.blueTeam);
        clearSpikeTrailIfLargeCorrection(visibleX, visibleY, visibleRotation);
        schedulePlayerCorrections(playersBefore, frame.snapshot.redTeam, frame.snapshot.blueTeam);
    }

    private void clearSpikeTrailIfLargeCorrection(double visibleX, double visibleY, double visibleRotation) {
        boolean largeCorrection = ballRenderCorrection.schedule(
                renderModel.ball.x,
                renderModel.ball.y,
                renderModel.ball.rotationDegrees,
                visibleX,
                visibleY,
                visibleRotation
        );
        if (largeCorrection) {
            renderModel.spikeEffect.clearSpikeTrail();
        }
    }

    private PlayerVisualPositions captureVisiblePlayers() {
        Player[] red = renderModel.redTeam.getPlayers();
        Player[] blue = renderModel.blueTeam.getPlayers();
        Player[] players = new Player[red.length + blue.length];
        System.arraycopy(red, 0, players, 0, red.length);
        System.arraycopy(blue, 0, players, red.length, blue.length);
        double[] x = new double[players.length];
        double[] y = new double[players.length];
        for (int i = 0; i < players.length; i++) {
            x[i] = playerRenderCorrection.renderedX(players[i]);
            y[i] = playerRenderCorrection.renderedY(players[i]);
        }
        return new PlayerVisualPositions(players, x, y);
    }

    private void schedulePlayerCorrections(PlayerVisualPositions before,
                                           Packet.TeamState redState, Packet.TeamState blueState) {
        for (int i = 0; i < redState.players.length; i++) {
            playerRenderCorrection.schedule(
                    before.players[i], before.x[i], before.y[i], redState.players[i].assetName
            );
        }
        for (int i = 0; i < blueState.players.length; i++) {
            int index = redState.players.length + i;
            playerRenderCorrection.schedule(
                    before.players[index], before.x[index], before.y[index], blueState.players[i].assetName
            );
        }
    }

    private static final class PlayerVisualPositions {
        final Player[] players;
        final double[] x;
        final double[] y;

        PlayerVisualPositions(Player[] players, double[] x, double[] y) {
            this.players = players;
            this.x = x;
            this.y = y;
        }
    }

    private void handleMatchAborted(UdpCodec.MatchAborted aborted) {
        sessionEnded = true;
        endMessage = aborted.message;
        pendingAbortMessage = null;
        terminationAckUntilNanos = System.nanoTime() + TERMINATION_ACK_GRACE_NANOS;
        controlRetryExecutor.shutdownNow();
    }

    private void processControls(boolean restartDown, boolean cancelResetDown) {
        boolean restartPressed = restartDown && !previousRestartDown;
        boolean cancelPressed = cancelResetDown && !previousCancelDown;
        previousRestartDown = restartDown;
        previousCancelDown = cancelResetDown;

        if (restartPressed) {
            queueControl(UdpCodec.ControlAction.RESET_REQUEST);
        } else if (cancelPressed) {
            queueControl(UdpCodec.ControlAction.CANCEL_RESET);
        }
    }

    private void queueControl(UdpCodec.ControlAction action) {
        PendingControl pending = new PendingControl(++controlSequence, action);
        synchronized (pendingControlsLock) {
            pendingControls.addLast(pending);
        }
        sendPendingControl(pending);
    }

    private void retryPendingControls() {
        if (sessionEnded || !assigned) {
            return;
        }
        PendingControl[] pending;
        synchronized (pendingControlsLock) {
            pending = pendingControls.toArray(new PendingControl[0]);
        }
        long now = System.nanoTime();
        for (PendingControl control : pending) {
            if (now - control.firstSentNanos >= CONTROL_ACK_TIMEOUT_NANOS) {
                endLocally("重設控制確認逾時，連線已中止。");
                return;
            }
            if (now - control.lastSentNanos >= CONTROL_RESEND_INTERVAL_NANOS) {
                sendPendingControl(control);
            }
        }
    }

    private void sendPendingControl(PendingControl pending) {
        if (!assigned || socket.isClosed()) {
            return;
        }
        try {
            byte[] data = UdpCodec.control(sessionToken, pending.sequence, pending.action);
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            pending.lastSentNanos = System.nanoTime();
        } catch (IOException exception) {
            endLocally();
        }
    }

    private void sendInputIfNeeded(TeamInput input) {
        int mask = Packet.encodeInput(input);
        long now = System.nanoTime();
        boolean changed = mask != lastInputMask;
        boolean heartbeat = now - lastInputSendNanos >= INPUT_HEARTBEAT_NANOS;
        if (!changed && !heartbeat) {
            return;
        }

        try {
            byte[] data = UdpCodec.input(sessionToken, ++inputSequence, estimatedServerTick + 1, mask);
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            lastInputMask = mask;
            lastInputSendNanos = now;
        } catch (IOException exception) {
            endLocally();
        }
    }

    private void sendEventAck(int eventId) {
        if (!assigned || socket.isClosed()) {
            return;
        }
        try {
            byte[] data = UdpCodec.eventAck(sessionToken, eventId);
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
        } catch (IOException exception) {
            endLocally();
        }
    }

    private void resendHelloIfNeeded() {
        if (System.nanoTime() - lastHelloNanos >= HELLO_INTERVAL_NANOS) {
            sendHello();
        }
    }

    private void sendHello() {
        try {
            byte[] data = UdpCodec.hello(clientNonce);
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            lastHelloNanos = System.nanoTime();
        } catch (IOException exception) {
            endLocally();
        }
    }

    private TeamInput toWorldInput(TeamInput keyboardInput) {
        return redSide ? keyboardInput.copy() : keyboardInput.mirroredHorizontally();
    }

    private boolean isOwnToken(long token) {
        return assigned && token == sessionToken;
    }

    private void markServerPacketReceived() {
        lastServerPacketNanos = System.nanoTime();
    }

    private void closeAfterTerminationAckGrace() {
        if (terminationAckUntilNanos > 0 && System.nanoTime() >= terminationAckUntilNanos) {
            socket.close();
            terminationAckUntilNanos = 0;
        }
    }

    private void endLocally() {
        endLocally(GameServer.MATCH_ABORTED_MESSAGE);
    }

    private void endLocally(String message) {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        endMessage = message;
        controlRetryExecutor.shutdownNow();
        socket.close();
    }

    @Override
    public boolean isBluePerspective() {
        return assigned && !redSide;
    }

    @Override
    public String getHeaderText() {
        if (!assigned) {
            return "UDP 5001：連線中";
        }
        return redSide
                ? "Player 1 / 紅隊    UDP " + hostIp + ":5001"
                : "Player 2 / 藍隊    UDP " + hostIp + ":5001";
    }

    @Override
    public String getConnectionMessage() {
        if (sessionEnded) {
            return endMessage;
        }
        if (!assigned) {
            return "正在連線到 " + hostIp + ":5001";
        }
        if (!receivedRemoteInput) {
            return redSide ? "已加入為 Player 1，等待 Player 2" : "已加入為 Player 2，等待 Player 1";
        }
        return "已連線";
    }

    @Override
    public String getResetMessage() {
        if (redResetConfirmed && !blueResetConfirmed) {
            return "紅隊已確認重設，等待藍隊，按 N 取消";
        }
        if (blueResetConfirmed && !redResetConfirmed) {
            return "藍隊已確認重設，等待紅隊，按 N 取消";
        }
        return null;
    }

    @Override
    public boolean isConnected() {
        return assigned && receivedRemoteInput && !sessionEnded;
    }

    @Override
    public boolean isSessionEnded() {
        return sessionEnded;
    }

    @Override
    public double getRenderedBallX(double authoritativeX) {
        return ballRenderCorrection.renderedX(authoritativeX);
    }

    @Override
    public double getRenderedBallY(double authoritativeY) {
        return ballRenderCorrection.renderedY(authoritativeY);
    }

    @Override
    public double getRenderedBallRotation(double authoritativeRotationDegrees) {
        return ballRenderCorrection.renderedRotation(authoritativeRotationDegrees);
    }

    @Override
    public double getRenderedPlayerX(Player player) {
        return playerRenderCorrection.renderedX(player);
    }

    @Override
    public double getRenderedPlayerY(Player player) {
        return playerRenderCorrection.renderedY(player);
    }

    @Override
    public String getRenderedPlayerAsset(Player player) {
        return assigned && player.redSide != redSide
                ? playerRenderCorrection.serverAsset(player)
                : player.assetName;
    }

    @Override
    public void close() {
        if (!sessionEnded && assigned) {
            sendDisconnectControl();
        }
        endLocally();
    }

    private void sendDisconnectControl() {
        PendingControl disconnect = new PendingControl(++controlSequence, UdpCodec.ControlAction.DISCONNECT);
        for (int i = 0; i < 3; i++) {
            try {
                byte[] data = UdpCodec.control(sessionToken, disconnect.sequence, disconnect.action);
                socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private static final class PendingControl {
        final int sequence;
        final UdpCodec.ControlAction action;
        final long firstSentNanos = System.nanoTime();
        volatile long lastSentNanos;

        PendingControl(int sequence, UdpCodec.ControlAction action) {
            this.sequence = sequence;
            this.action = action;
        }
    }
}
