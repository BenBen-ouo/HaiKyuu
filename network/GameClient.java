/*
純 UDP Client。
本機以 60 FPS 預測雙方輸入；Server 僅轉傳輸入，並在過網或強制事件時回傳權威快照。
*/
package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import model.GameModel;
import model.TeamInput;

public final class GameClient implements NetworkView {
    private static final long HELLO_INTERVAL_NANOS = 250_000_000L;
    private static final long CONTROL_RESEND_INTERVAL_NANOS = 250_000_000L;
    private static final long SERVER_TIMEOUT_NANOS = 3_000_000_000L;
    private static final int INPUT_HEARTBEAT_TICKS = 5;
    private static final double HARD_SNAP_DISTANCE = 75.0;
    private static final int NET_CROSS_BLEND_FRAMES = 8;

    private final GameModel renderModel;
    private final String hostIp;
    private final InetAddress hostAddress;
    private final DatagramSocket socket;
    private final long clientNonce = ThreadLocalRandom.current().nextLong();
    private final ConcurrentLinkedQueue<UdpCodec.Decoded> incoming = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> receiveFailure = new AtomicReference<>();
    private final Thread receiveThread;

    private volatile boolean assigned;
    private volatile boolean redSide;
    private volatile long sessionToken;
    private volatile boolean sessionEnded;
    private volatile String endMessage = "";
    private volatile long lastServerPacketNanos;

    private int clientTick;
    private int inputSequence;
    private int lastInputMask = Integer.MIN_VALUE;
    private int remoteInputMask;
    private boolean receivedRemoteInput;
    private int lastReceivedEventId;
    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;
    private boolean previousRestartDown;
    private boolean previousCancelDown;
    private long lastHelloNanos;
    private long lastControlSendNanos;
    private PendingControl pendingControl;
    private Reconciliation reconciliation;

    public GameClient(GameModel renderModel, String hostIp) throws IOException {
        this.renderModel = renderModel;
        this.hostIp = hostIp;
        this.hostAddress = InetAddress.getByName(hostIp);
        this.socket = new DatagramSocket();

        receiveThread = new Thread(this::receiveLoop, "haikyuu-udp-client-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
        sendHello();
    }

    public void update(
            TeamInput redKeyboardInput,
            TeamInput blueKeyboardInput,
            boolean restartDown,
            boolean cancelResetDown
    ) {
        if (sessionEnded) {
            return;
        }

        clientTick++;
        drainIncoming();

        String failure = receiveFailure.getAndSet(null);
        if (failure != null) {
            endSession(failure);
            return;
        }

        if (!assigned) {
            resendHelloIfNeeded();
            return;
        }

        processControls(restartDown, cancelResetDown);
        resendPendingControlIfNeeded();

        TeamInput localInput = redSide ? redKeyboardInput : blueKeyboardInput;
        sendInputIfNeeded(localInput);

        if (receivedRemoteInput) {
            TeamInput remoteInput = Packet.decodeInput(remoteInputMask);
            if (redSide) {
                renderModel.update(localInput, remoteInput);
            } else {
                renderModel.update(remoteInput, localInput);
            }
            applyReconciliationStep();
        }

        if (receivedRemoteInput && System.nanoTime() - lastServerPacketNanos > SERVER_TIMEOUT_NANOS) {
            endSession("Server UDP 中斷，本局結束");
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (!socket.isClosed() && !sessionEnded) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                if (!datagram.getAddress().equals(hostAddress) || datagram.getPort() != GameServer.UDP_PORT) {
                    continue;
                }
                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                if (decoded != null) {
                    incoming.offer(decoded);
                }
            } catch (SocketException ignored) {
                return;
            } catch (IOException exception) {
                if (!sessionEnded) {
                    receiveFailure.compareAndSet(null, "UDP 接收失敗，本局結束");
                }
                return;
            }
        }
    }

    private void drainIncoming() {
        UdpCodec.Decoded decoded;
        while ((decoded = incoming.poll()) != null) {
            if (decoded instanceof UdpCodec.Welcome welcome) {
                handleWelcome(welcome);
            } else if (decoded instanceof UdpCodec.Reject reject) {
                if (reject.clientNonce == clientNonce) {
                    endSession(reject.message == null ? "Server 拒絕連線" : reject.message);
                }
            } else if (decoded instanceof UdpCodec.RemoteInputFrame remote) {
                if (isOwnToken(remote.token)) {
                    remoteInputMask = remote.inputMask;
                    receivedRemoteInput = true;
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.Event event) {
                if (isOwnToken(event.token)) {
                    handleEvent(event);
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.ControlStatus status) {
                if (isOwnToken(status.token)) {
                    redResetConfirmed = status.redResetConfirmed;
                    blueResetConfirmed = status.blueResetConfirmed;
                    if (pendingControl != null && status.acknowledgedSequence >= pendingControl.sequence) {
                        pendingControl = null;
                    }
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.SessionEnd end) {
                if (isOwnToken(end.token)) {
                    endSession(end.message == null ? "本局結束" : end.message);
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

        sessionToken = welcome.sessionToken;
        redSide = welcome.redSide;
        assigned = true;
        welcome.state.applyTo(renderModel);
        reconciliation = null;
        markServerPacketReceived();
    }

    private void handleEvent(UdpCodec.Event event) {
        if (event.eventId <= lastReceivedEventId) {
            return;
        }

        lastReceivedEventId = event.eventId;
        if (event.type == Packet.EventType.NET_CROSS) {
            event.state.applyMetadataTo(renderModel);
            double difference = event.state.maxPositionDifference(renderModel);
            if (difference >= HARD_SNAP_DISTANCE) {
                event.state.applyTo(renderModel);
                reconciliation = null;
            } else if (difference > 0.25) {
                reconciliation = new Reconciliation(event.state, NET_CROSS_BLEND_FRAMES);
            }
            return;
        }

        event.state.applyTo(renderModel);
        reconciliation = null;
        if (event.type == Packet.EventType.LANDING || event.type == Packet.EventType.SCORE) {
            renderModel.spikeEffect.spawnSmoke(event.state.ball.x, event.state.ball.y);
        }
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
        pendingControl = new PendingControl(++inputSequence, action);
        sendPendingControl();
    }

    private void resendPendingControlIfNeeded() {
        if (pendingControl == null) {
            return;
        }
        if (System.nanoTime() - lastControlSendNanos >= CONTROL_RESEND_INTERVAL_NANOS) {
            sendPendingControl();
        }
    }

    private void sendPendingControl() {
        if (pendingControl == null || !assigned) {
            return;
        }
        try {
            byte[] data = UdpCodec.control(sessionToken, pendingControl.sequence, pendingControl.action);
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            lastControlSendNanos = System.nanoTime();
        } catch (IOException exception) {
            endSession("UDP 傳送失敗，本局結束");
        }
    }

    private void sendInputIfNeeded(TeamInput input) {
        int mask = Packet.encodeInput(input);
        boolean changed = mask != lastInputMask;
        boolean heartbeat = clientTick % INPUT_HEARTBEAT_TICKS == 0;
        if (!changed && !heartbeat) {
            return;
        }

        try {
            byte[] data = UdpCodec.input(
                    sessionToken,
                    ++inputSequence,
                    clientTick,
                    mask,
                    lastReceivedEventId
            );
            socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
            lastInputMask = mask;
        } catch (IOException exception) {
            endSession("UDP 傳送失敗，本局結束");
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
            endSession("無法連線到 UDP Server，本局結束");
        }
    }

    private void applyReconciliationStep() {
        if (reconciliation == null) {
            return;
        }

        double amount = 1.0 / reconciliation.framesRemaining;
        reconciliation.target.blendPositionsInto(renderModel, amount);
        reconciliation.framesRemaining--;
        if (reconciliation.framesRemaining <= 0) {
            reconciliation.target.applyTo(renderModel);
            reconciliation = null;
        }
    }

    private boolean isOwnToken(long token) {
        return assigned && token == sessionToken;
    }

    private void markServerPacketReceived() {
        lastServerPacketNanos = System.nanoTime();
    }

    private void endSession(String message) {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        endMessage = message;
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
        return "已連線（本地預測，過網／事件校正）";
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
    public void close() {
        if (!sessionEnded && assigned) {
            PendingControl disconnect = new PendingControl(++inputSequence, UdpCodec.ControlAction.DISCONNECT);
            for (int i = 0; i < 3; i++) {
                try {
                    byte[] data = UdpCodec.control(sessionToken, disconnect.sequence, disconnect.action);
                    socket.send(new DatagramPacket(data, data.length, hostAddress, GameServer.UDP_PORT));
                } catch (IOException ignored) {
                    break;
                }
            }
        }
        endSession("本局已關閉");
    }

    private static final class PendingControl {
        final int sequence;
        final UdpCodec.ControlAction action;

        PendingControl(int sequence, UdpCodec.ControlAction action) {
            this.sequence = sequence;
            this.action = action;
        }
    }

    private static final class Reconciliation {
        final Packet.CompactState target;
        int framesRemaining;

        Reconciliation(Packet.CompactState target, int framesRemaining) {
            this.target = target;
            this.framesRemaining = framesRemaining;
        }
    }
}
