/*
程式進入點。
server 參數啟動完全無畫面的 UDP 權威 Server；join <IP> 啟動 Client，未帶參數則保留單機測試模式。
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
import network.NetworkView;
import view.GamePanel;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            startDedicatedServer();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (args.length > 1 && "join".equalsIgnoreCase(args[0])) {
                startClient(args[1]);
            } else if (args.length > 0 && "join".equalsIgnoreCase(args[0])) {
                String serverIp = JOptionPane.showInputDialog(null, "輸入 Server IPv4 位址：", "加入 UDP Server", JOptionPane.QUESTION_MESSAGE);
                if (serverIp != null && !serverIp.isBlank()) {
                    startClient(serverIp.trim());
                }
            } else {
                startLocalGame();
            }
        });
    }

    private static void startDedicatedServer() {
        try (GameServer server = new GameServer()) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "haikyuu-server-shutdown"));
            server.run();
        } catch (IOException exception) {
            System.err.println("無法啟動 UDP Server 5001：" + exception.getMessage());
        }
    }

    private static void startClient(String hostIp) {
        try {
            GameModel model = new GameModel();
            KeyboardController keyboard = new KeyboardController();
            GameClient client = new GameClient(model, hostIp);
            GameController controller = new GameController(model, keyboard, client);
            showWindow(model, keyboard, controller, client, "HaiKyuu!! - UDP Client");
        } catch (IOException exception) {
            showError("無法建立 UDP Client：\n" + exception.getMessage());
        }
    }

    private static void startLocalGame() {
        GameModel model = new GameModel();
        KeyboardController keyboard = new KeyboardController();
        GameController controller = new GameController(model, keyboard);
        showWindow(model, keyboard, controller, null, "HaiKyuu!! - 單機測試");
    }

    private static void showWindow(
            GameModel model,
            KeyboardController keyboard,
            GameController controller,
            NetworkView networkView,
            String title
    ) {
        GamePanel panel = new GamePanel(model, controller, networkView);
        panel.addKeyListener(keyboard);

        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        if (networkView != null) {
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    networkView.close();
                }
            });
        }
        frame.setVisible(true);
        panel.requestFocusInWindow();
        panel.startGameLoop();
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "HaiKyuu!! 連線錯誤", JOptionPane.ERROR_MESSAGE);
    }
}