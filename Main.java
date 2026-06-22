/*
程式進入點，提供區域網路主機與加入模式。
主機同時是 Server 與 Player 1；Client 連入後為 Player 2，兩端皆開啟自己的遊戲視窗。
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
        SwingUtilities.invokeLater(() -> launch(args));
    }

    private static void launch(String[] args) {
        if (args.length > 0 && "host".equalsIgnoreCase(args[0])) {
            startHost();
            return;
        }

        if (args.length > 1 && "join".equalsIgnoreCase(args[0])) {
            startClient(args[1]);
            return;
        }

        String[] options = {"主機", "加入", "離開"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "選擇區域網路模式",
                "HaiKyuu!! 區域網路連線",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            startHost();
        } else if (choice == 1) {
            String hostIp = JOptionPane.showInputDialog(
                    null,
                    "輸入主機 IPv4 位址：",
                    "加入 Player 2",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (hostIp != null && !hostIp.isBlank()) {
                startClient(hostIp.trim());
            }
        }
    }

    private static void startHost() {
        try {
            GameModel model = new GameModel();
            KeyboardController keyboard = new KeyboardController();
            GameServer server = new GameServer(model);
            GameController controller = new GameController(model, keyboard, server);
            showWindow(model, keyboard, controller, server, "HaiKyuu!! - 主機 / Player 1 / 紅隊");
        } catch (IOException exception) {
            showError("無法建立主機（TCP 5000 或 UDP 5001 可能已被使用）：\n" + exception.getMessage());
        }
    }

    private static void startClient(String hostIp) {
        try {
            GameModel model = new GameModel();
            KeyboardController keyboard = new KeyboardController();
            GameClient client = new GameClient(model, hostIp);
            GameController controller = new GameController(model, keyboard, client);
            showWindow(model, keyboard, controller, client, "HaiKyuu!! - Player 2 / 藍隊");
        } catch (IOException exception) {
            showError("無法連線到主機 " + hostIp + ":5000\n" + exception.getMessage());
        }
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
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                networkView.close();
            }
        });
        frame.setVisible(true);

        panel.requestFocusInWindow();
        panel.startGameLoop();
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "HaiKyuu!! 連線錯誤",
                JOptionPane.ERROR_MESSAGE
        );
    }
}
