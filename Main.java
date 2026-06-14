import controller.GameController;
import controller.KeyboardController;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import model.GameModel;
import view.GamePanel;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameModel model = new GameModel();
            KeyboardController keyboard = new KeyboardController();
            GameController controller = new GameController(model, keyboard);
            GamePanel panel = new GamePanel(model, controller);
            panel.addKeyListener(keyboard);

            JFrame frame = new JFrame("HaiKyuu!!");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.requestFocusInWindow();
            panel.startGameLoop();
        });
    }
}
