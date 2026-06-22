/*
主機權威的區域網路 Server。
Server 持有唯一真正的 GameModel；主機每幀送入紅隊輸入，Client 只送藍隊輸入，完成更新後回傳狀態快照。
*/
package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicReference;
import model.GameModel;
import model.TeamInput;

public class GameServer implements NetworkView {
    public static final int PORT = 5000;

    private final GameModel model;
    private final ServerSocket serverSocket;
    private final String localIp;
    private final Thread acceptThread;

    private volatile ClientConnection clientConnection;
    private volatile boolean closed;

    private boolean redResetConfirmed;
    private boolean blueResetConfirmed;
    private boolean previousRedRestartDown;
    private boolean previousBlueRestartDown;
    private boolean previousRedCancelDown;
    private boolean previousBlueCancelDown;

    public GameServer(GameModel model) throws IOException {
        this.model = model;
        this.serverSocket = new ServerSocket(PORT);
        this.localIp = NetworkAddress.findLocalIpv4();
        this.acceptThread = new Thread(this::acceptClients, "haikyuu-server-accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    public void update(TeamInput redInput, boolean redRestartDown, boolean redCancelDown) {
        ClientConnection connection = clientConnection;
        if (connection == null || !connection.isOpen()) {
            clearResetConfirmation();
            previousRedRestartDown = redRestartDown;
            previousRedCancelDown = redCancelDown;
            return;
        }

        Packet.Input remoteInput = connection.getLatestInput();
        TeamInput blueInput = remoteInput.teamInput.toTeamInput();

        processResetInput(
                redRestartDown,
                redCancelDown,
                remoteInput.restartDown,
                remoteInput.cancelResetDown
        );

        model.update(redInput, blueInput);
        connection.queueState(new Packet.State(
                Packet.GameState.from(model),
                redResetConfirmed,
                blueResetConfirmed
        ));
    }

    private void processResetInput(
            boolean redRestartDown,
            boolean redCancelDown,
            boolean blueRestartDown,
            boolean blueCancelDown
    ) {
        boolean redRestartPressed = redRestartDown && !previousRedRestartDown;
        boolean blueRestartPressed = blueRestartDown && !previousBlueRestartDown;
        boolean redCancelPressed = redCancelDown && !previousRedCancelDown;
        boolean blueCancelPressed = blueCancelDown && !previousBlueCancelDown;

        previousRedRestartDown = redRestartDown;
        previousBlueRestartDown = blueRestartDown;
        previousRedCancelDown = redCancelDown;
        previousBlueCancelDown = blueCancelDown;

        if (redCancelPressed || blueCancelPressed) {
            clearResetConfirmation();
            return;
        }

        if (redRestartPressed) {
            redResetConfirmed = true;
        }

        if (blueRestartPressed) {
            blueResetConfirmed = true;
        }

        if (redResetConfirmed && blueResetConfirmed) {
            model.restart();
            clearResetConfirmation();
        }
    }

    private void acceptClients() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                ClientConnection current = clientConnection;
                if (current != null && current.isOpen()) {
                    closeQuietly(socket);
                    continue;
                }

                ClientConnection connection = new ClientConnection(socket);
                clientConnection = connection;
                clearResetConfirmation();
                connection.start();
            } catch (SocketException exception) {
                if (!closed) {
                    exception.printStackTrace();
                }
                return;
            } catch (IOException exception) {
                if (!closed) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private void onClientDisconnected(ClientConnection connection) {
        if (clientConnection == connection) {
            clientConnection = null;
            clearResetConfirmation();
            previousBlueRestartDown = false;
            previousBlueCancelDown = false;
        }
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
        return "主機 / Player 1 / 紅隊    IP: " + localIp + ":" + PORT;
    }

    @Override
    public String getConnectionMessage() {
        return isConnected() ? "Player 2 已連線" : "等待 Player 2 連線";
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
        return connection != null && connection.isOpen();
    }

    @Override
    public void close() {
        closed = true;
        ClientConnection connection = clientConnection;
        if (connection != null) {
            connection.close();
        }
        closeQuietly(serverSocket);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception ignored) {
            // 關閉階段不需要再拋出例外。
        }
    }

    private final class ClientConnection implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;
        private final AtomicReference<Packet.Input> latestInput = new AtomicReference<>(
                new Packet.Input(Packet.TeamInputState.empty(), false, false)
        );
        private final AtomicReference<Packet.State> latestState = new AtomicReference<>();
        private final Object stateMonitor = new Object();

        private volatile boolean open = true;
        private Thread inputThread;
        private Thread outputThread;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.output = new ObjectOutputStream(socket.getOutputStream());
            this.output.flush();
            this.input = new ObjectInputStream(socket.getInputStream());
        }

        private void start() {
            inputThread = new Thread(this::readInputs, "haikyuu-server-input");
            outputThread = new Thread(this::writeStates, "haikyuu-server-output");
            inputThread.setDaemon(true);
            outputThread.setDaemon(true);

            try {
                sendImmediately(new Packet.Welcome(true, "已加入為 Player 2 / 藍隊"));
            } catch (IOException exception) {
                close();
                return;
            }

            queueState(new Packet.State(
                    Packet.GameState.from(model),
                    redResetConfirmed,
                    blueResetConfirmed
            ));

            inputThread.start();
            outputThread.start();
        }

        private boolean isOpen() {
            return open && !socket.isClosed();
        }

        private Packet.Input getLatestInput() {
            return latestInput.get();
        }

        private void queueState(Packet.State state) {
            if (!isOpen()) {
                return;
            }

            latestState.set(state);
            synchronized (stateMonitor) {
                stateMonitor.notifyAll();
            }
        }

        private void readInputs() {
            try {
                Object helloObject = input.readObject();
                if (!(helloObject instanceof Packet.Hello hello)
                        || hello.protocolVersion != Packet.PROTOCOL_VERSION) {
                    throw new IOException("Client 協定版本不相容");
                }

                while (isOpen()) {
                    Object message = input.readObject();
                    if (message instanceof Packet.Input receivedInput) {
                        latestInput.set(receivedInput);
                    }
                }
            } catch (EOFException | SocketException ignored) {
                // 對方正常關閉或斷線。
            } catch (IOException | ClassNotFoundException exception) {
                if (isOpen()) {
                    exception.printStackTrace();
                }
            } finally {
                close();
            }
        }

        private void writeStates() {
            try {
                while (isOpen()) {
                    Packet.State state = latestState.getAndSet(null);
                    if (state == null) {
                        synchronized (stateMonitor) {
                            stateMonitor.wait(250L);
                        }
                        continue;
                    }

                    sendImmediately(state);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                if (isOpen()) {
                    exception.printStackTrace();
                }
            } finally {
                close();
            }
        }

        private synchronized void sendImmediately(Packet.Message message) throws IOException {
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
            synchronized (stateMonitor) {
                stateMonitor.notifyAll();
            }
            closeQuietly(socket);
            onClientDisconnected(this);
        }
    }
}
