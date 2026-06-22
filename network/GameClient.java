/*
 * Player 2 的本地預測 Client。
 * Client 以 60 FPS 本地執行 GameModel；UDP 只帶輸入、主機事件與 10 Hz compact correction，
 * 因此畫面不再等待完整 GameModel 快照才更新。
 */
package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import model.GameModel;
import model.TeamInput;

public class GameClient implements NetworkView {
    private static final long UDP_HELLO_INTERVAL_NANOS = 250_000_000L;
    private static final long UDP_TIMEOUT_NANOS = 3_000_000_000L;

    private final GameModel renderModel;
    private final String hostIp;
    private final int tcpPort;
    private final Socket tcpSocket;
    private final ObjectOutputStream tcpOutput;
    private final ObjectInputStream tcpInput;
    private final DatagramSocket udpSocket;
    private final InetAddress hostAddress;
    private final int udpPort;
    private final long sessionToken;

    private final AtomicReference<UdpCodec.Correction> latestCorrection = new AtomicReference<>();
    private final AtomicReference<UdpCodec.RemoteInputFrame> latestRemoteInput = new AtomicReference<>();
    private final ConcurrentLinkedQueue<UdpCodec.Event> pendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicInteger lastReceivedEventId = new AtomicInteger();

    private volatile boolean tcpConnected = true;
    private volatile boolean udpReady;
    private volatile boolean receivedInitialState;
    private volatile boolean sessionEnded;
    private volatile String endMessage = "";
    private volatile boolean redResetConfirmed;
    private volatile boolean blueResetConfirmed;

    private int clientTick;
    private int inputSequence;
    private int lastAppliedCorrectionTick = -1;
    private int latestRemoteInputMask;
    private boolean previousRestartDown;
    private boolean previousCancelDown;
    private long lastUdpReceiveNanos;
    private long lastUdpHelloNanos;
    private Reconciliation reconciliation;

    private final Thread tcpControlThread;
    private final Thread udpReceiveThread;

    public GameClient(GameModel renderModel, String hostIp) throws IOException {
        this(renderModel, hostIp, GameServer.PORT);
    }

    public GameClient(GameModel renderModel, String hostIp, int tcpPort) throws IOException {
        this.renderModel = renderModel;
        this.hostIp = hostIp;
        this.tcpPort = tcpPort;
        this.hostAddress = InetAddress.getByName(hostIp);
        this.tcpSocket = new Socket(hostAddress, tcpPort);
        tcpSocket.setTcpNoDelay(true);
        tcpSocket.setSoTimeout(6000);

        this.tcpOutput = new ObjectOutputStream(tcpSocket.getOutputStream());
        tcpOutput.flush();
        this.tcpInput = new ObjectInputStream(tcpSocket.getInputStream());

        sendControl(new Packet.Hello(Packet.PROTOCOL_VERSION));
        Packet.Welcome welcome = readWelcome();
        this.sessionToken = welcome.sessionToken;
        this.udpPort = welcome.udpPort;
        tcpSocket.setSoTimeout(0);

        this.udpSocket = new DatagramSocket();
        this.udpSocket.setSoTimeout(0);

        this.tcpControlThread = new Thread(this::readTcpControls, "haikyuu-client-tcp-control");
        this.tcpControlThread.setDaemon(true);
        this.tcpControlThread.start();

        this.udpReceiveThread = new Thread(this::receiveUdpPackets, "haikyuu-client-udp-receive");
        this.udpReceiveThread.setDaemon(true);
        this.udpReceiveThread.start();

        sendUdpHello();
    }

    public void update(TeamInput blueInput, boolean restartDown, boolean cancelResetDown) {
        if (sessionEnded) {
            return;
        }

        clientTick++;
        processReliableControls(restartDown, cancelResetDown);
        drainNetworkUpdates();

        if (!udpReady) {
            resendUdpHelloIfNeeded();
            return;
        }

        if (System.nanoTime() - lastUdpReceiveNanos > UDP_TIMEOUT_NANOS) {
            endSession("主機 UDP 中斷，本局結束");
            return;
        }

        if (receivedInitialState) {
            TeamInput predictedRedInput = Packet.decodeInput(latestRemoteInputMask);
            renderModel.update(predictedRedInput, blueInput);
            applyReconciliationStep();
        }

        sendInput(blueInput);
    }

    private Packet.Welcome readWelcome() throws IOException {
        try {
            Object message = tcpInput.readObject();
            if (message instanceof Packet.Welcome welcome && welcome.assignedBluePlayer) {
                return welcome;
            }
            if (message instanceof Packet.Error error) {
                throw new IOException(error.message);
            }
            throw new IOException("主機沒有回傳有效的加入確認");
        } catch (ClassNotFoundException exception) {
            throw new IOException("主機回傳了無法辨識的資料", exception);
        }
    }

    private void processReliableControls(boolean restartDown, boolean cancelResetDown) {
        boolean restartPressed = restartDown && !previousRestartDown;
        boolean cancelPressed = cancelResetDown && !previousCancelDown;
        previousRestartDown = restartDown;
        previousCancelDown = cancelResetDown;

        try {
            if (restartPressed) {
                sendControl(new Packet.ResetRequest());
            }
            if (cancelPressed) {
                sendControl(new Packet.CancelReset());
            }
        } catch (IOException exception) {
            endSession("與主機的 TCP 控制連線中斷，本局結束");
        }
    }

    private void readTcpControls() {
        try {
            while (tcpConnected && !sessionEnded) {
                Object message = tcpInput.readObject();
                if (message instanceof Packet.Error error) {
                    endSession(error.message);
                    return;
                }
            }
        } catch (EOFException | SocketException ignored) {
            if (!sessionEnded) {
                endSession("主機已關閉，本局結束");
            }
        } catch (IOException | ClassNotFoundException exception) {
            if (!sessionEnded) {
                endSession("TCP 控制連線中斷，本局結束");
            }
        }
    }

    private void receiveUdpPackets() {
        byte[] buffer = new byte[2048];
        while (!sessionEnded && !udpSocket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                udpSocket.receive(datagram);
                if (!datagram.getAddress().equals(hostAddress) || datagram.getPort() != udpPort) {
                    continue;
                }

                UdpCodec.Decoded decoded = UdpCodec.decode(datagram.getData(), datagram.getLength());
                if (decoded instanceof UdpCodec.Correction correction && correction.token == sessionToken) {
                    latestCorrection.set(correction);
                    udpReady = true;
                    lastUdpReceiveNanos = System.nanoTime();
                } else if (decoded instanceof UdpCodec.RemoteInputFrame input && input.token == sessionToken) {
                    latestRemoteInput.set(input);
                    lastUdpReceiveNanos = System.nanoTime();
                } else if (decoded instanceof UdpCodec.Event event && event.token == sessionToken) {
                    pendingEvents.offer(event);
                    lastUdpReceiveNanos = System.nanoTime();
                }
            } catch (SocketException ignored) {
                return;
            } catch (IOException exception) {
                if (!sessionEnded) {
                    endSession("UDP 接收失敗，本局結束");
                }
                return;
            }
        }
    }

    private void drainNetworkUpdates() {
        UdpCodec.RemoteInputFrame remoteInput = latestRemoteInput.getAndSet(null);
        if (remoteInput != null) {
            latestRemoteInputMask = remoteInput.inputMask;
        }

        UdpCodec.Event event;
        while ((event = pendingEvents.poll()) != null) {
            if (event.eventId <= lastReceivedEventId.get()) {
                continue;
            }

            lastReceivedEventId.set(event.eventId);
            event.state.applyTo(renderModel);
            reconciliation = null;
            applyVisualEvent(event);
        }

        UdpCodec.Correction correction = latestCorrection.getAndSet(null);
        if (correction == null || correction.serverTick <= lastAppliedCorrectionTick) {
            return;
        }

        lastAppliedCorrectionTick = correction.serverTick;
        redResetConfirmed = correction.redResetConfirmed;
        blueResetConfirmed = correction.blueResetConfirmed;

        if (!receivedInitialState) {
            correction.state.applyTo(renderModel);
            receivedInitialState = true;
            reconciliation = null;
            return;
        }

        correction.state.applyMetadataTo(renderModel);
        double difference = correction.state.maxPositionDifference(renderModel);

        if (difference > 45.0) {
            correction.state.applyTo(renderModel);
            reconciliation = null;
        } else if (difference > 0.25) {
            int frames = difference < 18.0 ? 4 : 2;
            reconciliation = new Reconciliation(correction.state, frames);
        }
    }

    private void applyVisualEvent(UdpCodec.Event event) {
        if (event.type == Packet.EventType.LANDING) {
            renderModel.spikeEffect.spawnSmoke(event.state.ball.x, event.state.ball.y);
        }

        if (event.type == Packet.EventType.SERVE) {
            renderModel.spikeEffect.stopSpikeTrail();
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

    private void sendInput(TeamInput blueInput) {
        try {
            byte[] bytes = UdpCodec.input(
                    sessionToken,
                    ++inputSequence,
                    clientTick,
                    Packet.encodeInput(blueInput),
                    lastReceivedEventId.get()
            );
            udpSocket.send(new DatagramPacket(bytes, bytes.length, hostAddress, udpPort));
        } catch (IOException exception) {
            endSession("UDP 傳送失敗，本局結束");
        }
    }

    private void resendUdpHelloIfNeeded() {
        long now = System.nanoTime();
        if (now - lastUdpHelloNanos >= UDP_HELLO_INTERVAL_NANOS) {
            sendUdpHello();
        }
    }

    private void sendUdpHello() {
        try {
            byte[] bytes = UdpCodec.hello(sessionToken);
            udpSocket.send(new DatagramPacket(bytes, bytes.length, hostAddress, udpPort));
            lastUdpHelloNanos = System.nanoTime();
        } catch (IOException exception) {
            endSession("無法建立 UDP 即時連線，本局結束");
        }
    }

    private synchronized void sendControl(Packet.Message message) throws IOException {
        tcpOutput.writeObject(message);
        tcpOutput.flush();
        tcpOutput.reset();
    }

    private void endSession(String message) {
        if (sessionEnded) {
            return;
        }

        sessionEnded = true;
        endMessage = message;
        tcpConnected = false;
        udpSocket.close();
        try {
            tcpSocket.close();
        } catch (IOException ignored) {
            // 關閉階段不需要處理。
        }
    }

    @Override
    public boolean isBluePerspective() {
        return true;
    }

    @Override
    public String getHeaderText() {
        return "Player 2 / 藍隊    TCP " + hostIp + ":" + tcpPort + "  UDP:" + udpPort;
    }

    @Override
    public String getConnectionMessage() {
        if (sessionEnded) {
            return endMessage;
        }
        if (!udpReady) {
            return "已加入主機，正在建立 UDP 即時連線";
        }
        if (!receivedInitialState) {
            return "正在同步第一份主機狀態";
        }
        return "已連線（本地預測 + UDP 校正）";
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
        return !sessionEnded && udpReady && receivedInitialState;
    }

    @Override
    public boolean isSessionEnded() {
        return sessionEnded;
    }

    @Override
    public void close() {
        if (!sessionEnded) {
            try {
                sendControl(new Packet.Disconnect());
            } catch (IOException ignored) {
                // TCP 已中斷時直接關閉即可。
            }
        }
        endSession("本局已關閉");
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
