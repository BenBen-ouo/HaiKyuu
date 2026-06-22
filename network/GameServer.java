/*
 * 主機權威的 LAN Server。
 * TCP 5000 處理握手與可靠控制；UDP 5001 處理低延遲輸入、事件與每秒 10 次 compact correction。
 */
package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import model.GameModel;
import model.TeamInput;

public class GameServer implements NetworkView {
    public static final int PORT = 5001;
    public static final int UDP_PORT = 5001;
    private static final int CORRECTION_INTERVAL_TICKS = 6; // 60 / 6 = 10 Hz
    private static final long UDP_TIMEOUT_NANOS = 3_000_000_000L;

    private final GameModel model;
    private final ServerSocket tcpServerSocket;
    private final DatagramSocket udpSocket;
    private final String localIp;
    private final Thread acceptThread;
    private final Thread udpReceiveThread;

    private volatile ClientConnection clientConnection;
    private volatile boolean closed;
    private volatile boolean sessionEnded;
    private volatile String endMessage = "";

    private int serverTick;
    private int nextEventId;

    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;
    private boolean previousRedRestartDown;
    private boolean previousRedCancelDown;

    public GameServer(GameModel model) throws IOException {
        this.model = model;
        this.tcpServerSocket = new ServerSocket(PORT);
        this.udpSocket = new DatagramSocket(UDP_PORT);
        this.localIp = NetworkAddress.findLocalIpv4();

        this.acceptThread = new Thread(this::acceptClients, "haikyuu-tcp-accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();

        this.udpReceiveThread = new Thread(this::receiveUdpPackets, "haikyuu-udp-receive");
        this.udpReceiveThread.setDaemon(true);
        this.udpReceiveThread.start();
    }

    public void update(TeamInput redInput, boolean redRestartDown, boolean redCancelDown) {
        if (closed || sessionEnded) {
            return;
        }

        ClientConnection connection = clientConnection;
        if (connection == null || !connection.isGameplayReady()) {
            previousRedRestartDown = redRestartDown;
            previousRedCancelDown = redCancelDown;
            clearResetConfirmation();
            return;
        }

        if (connection.isUdpTimedOut()) {
            endSession("Player 2 網路中斷，本局結束");
            return;
        }

        serverTick++;
        Packet.CompactState before = Packet.CompactState.from(model);
        TeamInput blueInput = Packet.decodeInput(connection.getLatestInputMask());

        boolean resetStateChanged = processResetInput(
                redRestartDown,
                redCancelDown,
                connection.consumeResetRequest(),
                connection.consumeCancelRequest()
        );

        if (!sessionEnded) {
            model.update(redInput, blueInput);
        }

        Packet.CompactState after = Packet.CompactState.from(model);
        connection.sendRemoteInput(serverTick, Packet.encodeInput(redInput));

        Packet.EventType eventType = resetStateChanged && redResetConfirmed == false && blueResetConfirmed == false
                ? Packet.EventType.RESET
                : detectEvent(before, after);
        if (eventType != null) {
            connection.sendEvent(++nextEventId, serverTick, eventType, after);
        }

        if (serverTick % CORRECTION_INTERVAL_TICKS == 0 || resetStateChanged) {
            connection.sendCorrection(
                    serverTick,
                    nextEventId,
                    redResetConfirmed,
                    blueResetConfirmed,
                    after
            );
        }
    }

    private Packet.EventType detectEvent(Packet.CompactState before, Packet.CompactState after) {
        if (before.redScore != after.redScore || before.blueScore != after.blueScore) {
            return Packet.EventType.SCORE;
        }

        if (!before.rallyOver && after.rallyOver) {
            return Packet.EventType.LANDING;
        }

        if (before.serveStateOrdinal == 0 && after.serveStateOrdinal != 0
                && Math.abs(after.ball.vx) + Math.abs(after.ball.vy) > 0.01) {
            return Packet.EventType.SERVE;
        }

        double velocityChange = Math.hypot(
                after.ball.vx - before.ball.vx,
                after.ball.vy - before.ball.vy
        );
        if (velocityChange > 2.0) {
            return Packet.EventType.BALL_IMPACT;
        }

        if ((before.transientMessage == null && after.transientMessage != null)
                || (before.transientMessage != null && !before.transientMessage.equals(after.transientMessage))) {
            return Packet.EventType.RULE;
        }

        return null;
    }

    private boolean processResetInput(
            boolean redRestartDown,
            boolean redCancelDown,
            boolean blueRestartPressed,
            boolean blueCancelPressed
    ) {
        boolean redRestartPressed = redRestartDown && !previousRedRestartDown;
        boolean redCancelPressed = redCancelDown && !previousRedCancelDown;
        boolean changed = false;

        previousRedRestartDown = redRestartDown;
        previousRedCancelDown = redCancelDown;

        if (redCancelPressed || blueCancelPressed) {
            changed = redResetConfirmed || blueResetConfirmed;
            clearResetConfirmation();
            return changed;
        }

        if (redRestartPressed && !redResetConfirmed) {
            redResetConfirmed = true;
            changed = true;
        }

        if (blueRestartPressed && !blueResetConfirmed) {
            blueResetConfirmed = true;
            changed = true;
        }

        if (redResetConfirmed && blueResetConfirmed) {
            model.restart();
            clearResetConfirmation();
            return true;
        }

        return changed;
    }

    private void acceptClients() {
        while (!closed && !sessionEnded) {
            try {
                Socket socket = tcpServerSocket.accept();
                socket.setTcpNoDelay(true);

                ClientConnection existing = clientConnection;
                if (existing != null && existing.isOpen()) {
                    closeQuietly(socket);
                    continue;
                }

                ClientConnection connection = new ClientConnection(socket);
                if (!connection.completeHandshake()) {
                    connection.close();
                    continue;
                }

                clientConnection = connection;
                clearResetConfirmation();
                connection.startControlReader();
            } catch (SocketException exception) {
                if (!closed && !sessionEnded) {
                    exception.printStackTrace();
                }
                return;
            } catch (IOException exception) {
                if (!closed && !sessionEnded) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private void receiveUdpPackets() {
        byte[] buffer = new byte[2048];
        while (!closed && !sessionEnded) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                udpSocket.receive(datagram);
                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                ClientConnection connection = clientConnection;
                if (decoded == null || connection == null) {
                    continue;
                }

                if (decoded instanceof UdpCodec.Hello hello) {
                    connection.acceptUdpHello(hello.token, datagram.getAddress(), datagram.getPort());
                } else if (decoded instanceof UdpCodec.InputFrame input) {
                    connection.acceptInput(input, datagram.getAddress(), datagram.getPort());
                }
            } catch (SocketException exception) {
                if (!closed && !sessionEnded) {
                    exception.printStackTrace();
                }
                return;
            } catch (IOException exception) {
                if (!closed && !sessionEnded) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private synchronized void sendUdp(byte[] bytes, InetAddress address, int port) throws IOException {
        if (closed || sessionEnded || address == null || port <= 0) {
            return;
        }
        udpSocket.send(new DatagramPacket(bytes, bytes.length, address, port));
    }

    private void onClientDisconnected(ClientConnection connection) {
        if (clientConnection == connection && !closed && !sessionEnded) {
            endSession("Player 2 已斷線，本局結束");
        }
    }

    private void endSession(String message) {
        if (sessionEnded) {
            return;
        }

        sessionEnded = true;
        endMessage = message;
        clearResetConfirmation();

        ClientConnection connection = clientConnection;
        if (connection != null) {
            connection.close();
        }

        closeQuietly(tcpServerSocket);
        udpSocket.close();
    }

    private void clearResetConfirmation() {
        redResetConfirmed = false;
        blueResetConfirmed = false;
    }

    public String getLocalIp() {
        return localIp;
    }

    @Override
    public boolean isBluePerspective() {
        return false;
    }

    @Override
    public String getHeaderText() {
        return "主機 / Player 1 / 紅隊    TCP " + localIp + ":" + PORT + "  UDP:" + UDP_PORT;
    }

    @Override
    public String getConnectionMessage() {
        if (sessionEnded) {
            return endMessage;
        }

        ClientConnection connection = clientConnection;
        if (connection == null || !connection.isOpen()) {
            return "等待 Player 2 連線";
        }

        return connection.isGameplayReady()
                ? "Player 2 已連線（本地預測 + UDP 校正）"
                : "Player 2 已加入，正在建立 UDP 即時連線";
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
        ClientConnection connection = clientConnection;
        return !sessionEnded && connection != null && connection.isGameplayReady();
    }

    @Override
    public boolean isSessionEnded() {
        return sessionEnded;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ClientConnection connection = clientConnection;
        if (connection != null) {
            connection.close();
        }
        closeQuietly(tcpServerSocket);
        udpSocket.close();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 關閉階段不再傳遞例外。
        }
    }

    private final class ClientConnection implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;
        private final long sessionToken = ThreadLocalRandom.current().nextLong();

        private final AtomicInteger latestInputMask = new AtomicInteger();
        private final AtomicInteger lastInputSequence = new AtomicInteger(-1);
        private final AtomicInteger lastAcknowledgedEventId = new AtomicInteger();
        private final AtomicBoolean resetRequested = new AtomicBoolean();
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final AtomicLong lastUdpInputNanos = new AtomicLong();

        private volatile InetAddress udpAddress;
        private volatile int udpPort;
        private volatile boolean udpReady;
        private volatile boolean open = true;
        private volatile OutgoingEvent lastEvent;
        private Thread controlReader;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.output = new ObjectOutputStream(socket.getOutputStream());
            this.output.flush();
            this.input = new ObjectInputStream(socket.getInputStream());
        }

        private boolean completeHandshake() {
            try {
                Object message = input.readObject();
                if (!(message instanceof Packet.Hello hello)) {
                    sendControl(new Packet.Error("缺少 TCP 握手封包"));
                    return false;
                }

                if (hello.protocolVersion != Packet.PROTOCOL_VERSION) {
                    sendControl(new Packet.Error("網路協定版本不相容"));
                    return false;
                }

                sendControl(new Packet.Welcome(
                        true,
                        "已加入為 Player 2 / 藍隊；等待 UDP 即時通道",
                        sessionToken,
                        UDP_PORT
                ));
                return true;
            } catch (IOException | ClassNotFoundException exception) {
                return false;
            }
        }

        private void startControlReader() {
            controlReader = new Thread(this::readControls, "haikyuu-tcp-control-read");
            controlReader.setDaemon(true);
            controlReader.start();
        }

        private void readControls() {
            try {
                while (open && !closed && !sessionEnded) {
                    Object message = input.readObject();
                    if (message instanceof Packet.ResetRequest) {
                        resetRequested.set(true);
                    } else if (message instanceof Packet.CancelReset) {
                        cancelRequested.set(true);
                    } else if (message instanceof Packet.Disconnect) {
                        return;
                    }
                }
            } catch (EOFException | SocketException ignored) {
                // 視為 client 主動關閉或網路中斷。
            } catch (IOException | ClassNotFoundException exception) {
                if (open && !closed && !sessionEnded) {
                    exception.printStackTrace();
                }
            } finally {
                close();
                onClientDisconnected(this);
            }
        }

        private void acceptUdpHello(long token, InetAddress address, int port) {
            if (token != sessionToken || !open) {
                return;
            }

            udpAddress = address;
            udpPort = port;
            udpReady = true;
            lastUdpInputNanos.set(System.nanoTime());

            try {
                sendCorrection(
                        serverTick,
                        nextEventId,
                        redResetConfirmed,
                        blueResetConfirmed,
                        Packet.CompactState.from(model)
                );
            } catch (RuntimeException ignored) {
                // UDP 建立失敗會由 timeout 結束本局。
            }
        }

        private void acceptInput(UdpCodec.InputFrame frame, InetAddress address, int port) {
            if (!udpReady || frame.token != sessionToken || !sameUdpEndpoint(address, port)) {
                return;
            }

            int previousSequence = lastInputSequence.get();
            if (frame.sequence <= previousSequence) {
                return;
            }

            lastInputSequence.set(frame.sequence);
            latestInputMask.set(frame.inputMask);
            lastAcknowledgedEventId.accumulateAndGet(frame.lastReceivedEventId, Math::max);
            lastUdpInputNanos.set(System.nanoTime());
        }

        private boolean sameUdpEndpoint(InetAddress address, int port) {
            return udpAddress != null && udpAddress.equals(address) && udpPort == port;
        }

        private int getLatestInputMask() {
            return latestInputMask.get();
        }

        private boolean consumeResetRequest() {
            return resetRequested.getAndSet(false);
        }

        private boolean consumeCancelRequest() {
            return cancelRequested.getAndSet(false);
        }

        private boolean isUdpTimedOut() {
            long last = lastUdpInputNanos.get();
            return udpReady && last > 0 && System.nanoTime() - last > UDP_TIMEOUT_NANOS;
        }

        private boolean isGameplayReady() {
            return isOpen() && udpReady;
        }

        private boolean isOpen() {
            return open && !socket.isClosed();
        }

        private void sendRemoteInput(int tick, int inputMask) {
            if (!udpReady) {
                return;
            }
            try {
                sendUdp(UdpCodec.remoteInput(sessionToken, tick, inputMask), udpAddress, udpPort);
            } catch (IOException exception) {
                endSession("UDP 傳送失敗，本局結束");
            }
        }

        private void sendCorrection(
                int tick,
                int latestEventId,
                boolean redConfirmed,
                boolean blueConfirmed,
                Packet.CompactState state
        ) {
            if (!udpReady) {
                return;
            }
            try {
                sendUdp(
                        UdpCodec.correction(
                                sessionToken,
                                tick,
                                latestEventId,
                                redConfirmed,
                                blueConfirmed,
                                state
                        ),
                        udpAddress,
                        udpPort
                );

                OutgoingEvent event = lastEvent;
                if (event != null && lastAcknowledgedEventId.get() < event.id) {
                    sendUdp(
                            UdpCodec.event(sessionToken, event.id, event.tick, event.type, event.state),
                            udpAddress,
                            udpPort
                    );
                }
            } catch (IOException exception) {
                endSession("UDP 傳送失敗，本局結束");
            }
        }

        private void sendEvent(int eventId, int tick, Packet.EventType type, Packet.CompactState state) {
            if (!udpReady) {
                return;
            }
            OutgoingEvent event = new OutgoingEvent(eventId, tick, type, state);
            lastEvent = event;
            try {
                sendUdp(UdpCodec.event(sessionToken, eventId, tick, type, state), udpAddress, udpPort);
            } catch (IOException exception) {
                endSession("UDP 傳送失敗，本局結束");
            }
        }

        private synchronized void sendControl(Packet.Message message) throws IOException {
            output.writeObject(message);
            output.flush();
            output.reset();
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            open = false;
            closeQuietly(socket);
        }
    }

    private static final class OutgoingEvent {
        final int id;
        final int tick;
        final Packet.EventType type;
        final Packet.CompactState state;

        OutgoingEvent(int id, int tick, Packet.EventType type, Packet.CompactState state) {
            this.id = id;
            this.tick = tick;
            this.type = type;
            this.state = state;
        }
    }
}
