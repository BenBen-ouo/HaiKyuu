/*
Player 2 使用的區域網路 Client。
Client 不執行遊戲物理，只傳送鏡像視角轉換後的藍隊按鍵並套用主機的最新狀態快照。
*/
package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicReference;
import model.GameModel;
import model.TeamInput;

public class GameClient implements NetworkView {
    private final GameModel renderModel;
    private final String hostIp;
    private final int port;
    private final Socket socket;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;
    private final AtomicReference<Packet.State> latestState = new AtomicReference<>();

    private volatile boolean connected = true;
    private volatile boolean redResetConfirmed;
    private volatile boolean blueResetConfirmed;
    private Thread receiveThread;

    public GameClient(GameModel renderModel, String hostIp) throws IOException {
        this(renderModel, hostIp, GameServer.PORT);
    }

    public GameClient(GameModel renderModel, String hostIp, int port) throws IOException {
        this.renderModel = renderModel;
        this.hostIp = hostIp;
        this.port = port;
        this.socket = new Socket(hostIp, port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(6000);

        this.output = new ObjectOutputStream(socket.getOutputStream());
        output.flush();
        this.input = new ObjectInputStream(socket.getInputStream());

        sendImmediately(new Packet.Hello(Packet.PROTOCOL_VERSION));
        verifyWelcome();
        socket.setSoTimeout(0);

        receiveThread = new Thread(this::receiveStates, "haikyuu-client-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void update(TeamInput blueInput, boolean restartDown, boolean cancelResetDown) {
        applyLatestState();

        if (!connected) {
            return;
        }

        try {
            sendImmediately(new Packet.Input(
                    Packet.TeamInputState.from(blueInput),
                    restartDown,
                    cancelResetDown
            ));
        } catch (IOException exception) {
            disconnect();
        }
    }

    private void verifyWelcome() throws IOException {
        try {
            Object message = input.readObject();
            if (message instanceof Packet.Welcome welcome && welcome.assignedBluePlayer) {
                return;
            }

            if (message instanceof Packet.Error error) {
                throw new IOException(error.message);
            }

            throw new IOException("主機沒有回傳有效的加入確認");
        } catch (ClassNotFoundException exception) {
            throw new IOException("主機回傳了無法辨識的資料", exception);
        }
    }

    private void receiveStates() {
        try {
            while (connected) {
                Object message = input.readObject();
                if (message instanceof Packet.State state) {
                    latestState.set(state);
                } else if (message instanceof Packet.Error error) {
                    throw new IOException(error.message);
                }
            }
        } catch (EOFException | SocketException ignored) {
            // 主機關閉或區網連線中斷。
        } catch (IOException | ClassNotFoundException exception) {
            if (connected) {
                exception.printStackTrace();
            }
        } finally {
            disconnect();
        }
    }

    private void applyLatestState() {
        Packet.State state = latestState.getAndSet(null);
        if (state == null) {
            return;
        }

        state.gameState.applyTo(renderModel);
        redResetConfirmed = state.redResetConfirmed;
        blueResetConfirmed = state.blueResetConfirmed;
    }

    private synchronized void sendImmediately(Packet.Message message) throws IOException {
        output.writeObject(message);
        output.flush();
        output.reset();
    }

    private void disconnect() {
        if (!connected) {
            return;
        }

        connected = false;
        try {
            socket.close();
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
        return "Player 2 / 藍隊    主機: " + hostIp + ":" + port;
    }

    @Override
    public String getConnectionMessage() {
        return connected ? "已連線" : "已與主機斷線，主機會保留目前比賽等待重新加入";
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
        return connected;
    }

    @Override
    public void close() {
        disconnect();
    }
}
