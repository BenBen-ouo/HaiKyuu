/*
純 UDP Client。
Client 以 60 tick/s 預測雙方輸入；Server 只轉傳輸入，並在指定事件送回權威快照。
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
    private static final long INPUT_HEARTBEAT_NANOS = 500_000_000L;
    private static final long SERVER_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long TERMINATION_ACK_GRACE_NANOS = 2_000_000_000L;

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
    private int lastAppliedEventId;
    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;
    private boolean previousRestartDown;
    private boolean previousCancelDown;
    private long lastHelloNanos;
    private long lastControlSendNanos;
    private long lastInputSendNanos;
    private long terminationAckUntilNanos;
    private PendingControl pendingControl;

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

    public void update(TeamInput keyboardInput, boolean restartDown, boolean cancelResetDown) {
        clientTick++;
        drainIncoming();

        if (sessionEnded) {
            closeAfterTerminationAckGrace();
            return;
        }

        String failure = receiveFailure.getAndSet(null);
        if (failure != null) {
            endLocally();
            return;
        }

        if (!assigned) {
            resendHelloIfNeeded();
            return;
        }

        processControls(restartDown, cancelResetDown);
        resendPendingControlIfNeeded();

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

        if (receivedRemoteInput && System.nanoTime() - lastServerPacketNanos > SERVER_TIMEOUT_NANOS) {
            endLocally();
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
                if (decoded != null) {
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
                    remoteInputMask = remote.inputMask;
                    receivedRemoteInput = true;
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.Event event) {
                if (!sessionEnded && isOwnToken(event.token)) {
                    handleEvent(event);
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.ControlStatus status) {
                if (!sessionEnded && isOwnToken(status.token)) {
                    redResetConfirmed = status.redResetConfirmed;
                    blueResetConfirmed = status.blueResetConfirmed;
                    if (pendingControl != null && status.acknowledgedSequence >= pendingControl.sequence) {
                        pendingControl = null;
                    }
                    markServerPacketReceived();
                }
            } else if (decoded instanceof UdpCodec.MatchAborted aborted) {
                if (isOwnToken(aborted.token)) {
                    handleMatchAborted(aborted);
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
        markServerPacketReceived();
    }

    private void handleEvent(UdpCodec.Event event) {
        sendEventAck(event.eventId);
        if (event.eventId <= lastAppliedEventId) {
            return;
        }

        lastAppliedEventId = event.eventId;
        event.state.applyTo(renderModel);
    }

    private void handleMatchAborted(UdpCodec.MatchAborted aborted) {
        sendEventAck(aborted.eventId);
        sessionEnded = true;
        endMessage = GameServer.MATCH_ABORTED_MESSAGE;
        terminationAckUntilNanos = System.nanoTime() + TERMINATION_ACK_GRACE_NANOS;
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
        if (pendingControl != null && System.nanoTime() - lastControlSendNanos >= CONTROL_RESEND_INTERVAL_NANOS) {
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
            byte[] data = UdpCodec.input(sessionToken, ++inputSequence, clientTick, mask);
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
        TeamInput input = copyInput(keyboardInput);
        if (!redSide) {
            boolean left = input.backLeft;
            input.backLeft = input.backRight;
            input.backRight = left;
        }
        return input;
    }

    private TeamInput copyInput(TeamInput source) {
        TeamInput input = new TeamInput();
        input.backLeft = source.backLeft;
        input.backRight = source.backRight;
        input.backJump = source.backJump;
        input.backDive = source.backDive;
        input.setterJump = source.setterJump;
        input.quickAttack = source.quickAttack;
        input.quickBlock = source.quickBlock;
        input.wingAttack = source.wingAttack;
        input.spikeFlat = source.spikeFlat;
        input.spikeShort = source.spikeShort;
        input.spikeLob = source.spikeLob;
        input.servePressed = source.servePressed;
        input.serveType = source.serveType;
        return input;
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
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        endMessage = GameServer.MATCH_ABORTED_MESSAGE;
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
        return "已連線（本地預測，事件／高球校正）";
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
            sendDisconnectControl();
        }
        endLocally();
    }

    private void sendDisconnectControl() {
        PendingControl disconnect = new PendingControl(++inputSequence, UdpCodec.ControlAction.DISCONNECT);
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

        PendingControl(int sequence, UdpCodec.ControlAction action) {
            this.sequence = sequence;
            this.action = action;
        }
    }
}
