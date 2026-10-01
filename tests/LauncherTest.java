import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import view.LauncherPanel;

/** 不開實體視窗，檢查啟動選單的模式與內嵌 IP 欄位。 */
public final class LauncherTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(LauncherTest::testLauncherActions);
        System.out.println("LauncherTest passed");
    }

    private static void testLauncherActions() {
        int[] selected = new int[4];
        String[] joinedIp = new String[1];
        LauncherPanel panel = new LauncherPanel("192.168.1.20", new LauncherPanel.Actions() {
            @Override public void startLocal() { selected[0]++; }
            @Override public void startPractice() { selected[1]++; }
            @Override public String startHost() { selected[2]++; return null; }
            @Override public String join(String ip) { selected[3]++; joinedIp[0] = ip; return null; }
        });

        JTextField joinField = findEditableField(panel);
        check(joinField != null, "加入房間的 IP 欄位與模式按鈕在同一個啟動畫面");
        findButton(panel, "本地雙人").doClick();
        findButton(panel, "練習模式").doClick();
        findButton(panel, "創立房間").doClick();
        check(selected[0] == 1 && selected[1] == 1 && selected[2] == 1,
                "三個模式分別呼叫自己的啟動入口");

        joinField.setText(" 192.168.1.20 ");
        findButton(panel, "加入房間").doClick();
        check(selected[3] == 1 && "192.168.1.20".equals(joinedIp[0]),
                "在主選單輸入 IP 即可加入，不需第二個輸入視窗");
        joinField.setText("192.168.1.999");
        findButton(panel, "加入房間").doClick();
        check(selected[3] == 1 && !LauncherPanel.isValidIpv4("example.com"),
                "錯誤 IP 不會啟動 Client");
    }

    private static JTextField findEditableField(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTextField field && field.isEditable()) return field;
            if (child instanceof Container container) {
                JTextField found = findEditableField(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && label.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButtonOrNull(container, label);
                if (found != null) return found;
            }
        }
        throw new AssertionError("找不到按鈕：" + label);
    }

    private static JButton findButtonOrNull(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && label.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButtonOrNull(container, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
