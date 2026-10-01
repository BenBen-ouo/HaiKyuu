/*
程式進入點。
未帶參數開啟 GUI 選單；server 啟動無畫面的權威 Server，join <IP> 直接啟動 Client。
*/
import controller.GameController;
import controller.KeyboardController;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.GameModel;
import network.GameClient;
import network.GameServer;
import network.NetworkAddress;
import network.NetworkView;
import network.TimingDiagnostics;
import view.GamePanel;
import view.LauncherPanel;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            startDedicatedServer();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (args.length > 0 && "local".equalsIgnoreCase(args[0])) {
                startLocalGame(false);
            } else if (args.length > 0 && "practice".equalsIgnoreCase(args[0])) {
                startLocalGame(true);
            } else if (args.length > 1 && "join".equalsIgnoreCase(args[0])) {
                String error = startClient(args[1], null, networkWindowTitle(args[1]));
                if (error != null) showError(error);
            } else if (args.length > 0 && "host".equalsIgnoreCase(args[0])) {
                String error = startHostedGame();
                if (error != null) showError(error);
            } else {
                showLauncher();
            }
        });
    }

    private static void showLauncher() {
        JFrame frame = new JFrame("HaiKyuu!! - 啟動選單");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        LauncherPanel panel = new LauncherPanel(NetworkAddress.findLocalIpv4(), new LauncherPanel.Actions() {
            @Override
            public void startLocal() {
                startLocalGame(false);
                frame.dispose();
            }

            @Override
            public void startPractice() {
                startLocalGame(true);
                frame.dispose();
            }

            @Override
            public String startHost() {
                String error = startHostedGame();
                if (error == null) frame.dispose();
                return error;
            }

            @Override
            public String join(String ip) {
                String error = startClient(ip, null, networkWindowTitle(ip));
                if (error == null) frame.dispose();
                return error;
            }
        });
        frame.add(panel);
        frame.pack();
        frame.setSize(480, 340);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void startDedicatedServer() {
        TimingDiagnostics.start("server");
        try (GameServer server = new GameServer()) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "haikyuu-server-shutdown"));
            server.run();
        } catch (IOException exception) {
            System.err.println("無法啟動 UDP Server 5001：" + exception.getMessage());
        }
    }

    private static String startHostedGame() {
        String hostIp = NetworkAddress.findLocalIpv4();
        String addressError = hostedIpError(hostIp);
        if (addressError != null) return addressError;
        try {
            GameServer server = new GameServer(hostIp);
            TimingDiagnostics.start("host");
            Thread serverThread = new Thread(server::run, "haikyuu-host-server");
            serverThread.start();
            // 房主與訪客都建立 GameClient；房主不直接操作 Server 的 GameModel。
            String error = startClient(server.getLocalIp(), server,
                    networkWindowTitle(server.getLocalIp()));
            if (error != null) server.close();
            return error;
        } catch (IOException exception) {
            return "無法創立房間（UDP 5001）：" + exception.getMessage();
        }
    }

    private static String startClient(String hostIp, GameServer hostedServer, String title) {
        TimingDiagnostics.start(hostedServer == null ? "client" : "host");
        try {
            GameModel model = new GameModel();
            KeyboardController keyboard = new KeyboardController();
            GameClient client = new GameClient(model, hostIp);
            GameController controller = new GameController(model, keyboard, client);
            showWindow(model, keyboard, controller, client, hostedServer, title);
            return null;
        } catch (IOException exception) {
            return "無法建立 UDP Client：" + exception.getMessage();
        }
    }

    static String networkWindowTitle(String hostIp) {
        return "HaiKyuu!! - 房主 IP " + hostIp;
    }

    static String hostedIpError(String hostIp) {
        return NetworkAddress.isUsableLanIpv4(hostIp) ? null
                : "無法創立房間：找不到可用的區網 IPv4 位址，請先連上 Wi-Fi 或有線網路。";
    }

    private static void startLocalGame(boolean practiceMode) {
        GameModel model = new GameModel(practiceMode);
        KeyboardController keyboard = new KeyboardController();
        GameController controller = new GameController(model, keyboard);
        showWindow(model, keyboard, controller, null, null,
                practiceMode ? "HaiKyuu!! - 練習模式" : "HaiKyuu!! - 本地雙人");
    }

    private static void showWindow(
            GameModel model,
            KeyboardController keyboard,
            GameController controller,
            NetworkView networkView,
            GameServer hostedServer,
            String title
    ) {
        GamePanel panel = new GamePanel(model, controller, networkView);
        panel.addKeyListener(keyboard);

        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                panel.stopGameLoop();
                if (networkView != null) networkView.close();
                if (hostedServer != null) hostedServer.close();
            }
        });
        frame.setVisible(true);
        panel.requestFocusInWindow();
        panel.startGameLoop();
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "HaiKyuu!! 連線錯誤", JOptionPane.ERROR_MESSAGE);
    }
}
