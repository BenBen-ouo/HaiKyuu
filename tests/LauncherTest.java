import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import model.AppVersion;
import network.NetworkAddress;
import view.LauncherPanel;

/** 不開實體視窗，檢查啟動選單的模式與內嵌 IP 欄位。 */
public final class LauncherTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(LauncherTest::testLauncherActions);
        System.out.println("LauncherTest passed");
    }

    private static void testLauncherActions() {
        check("HaiKyuu!! - 房主 IP 192.168.1.20".equals(Main.networkWindowTitle("192.168.1.20")),
                "房主與加入者使用同一個顯示房主 IP 的視窗標題");
        check(NetworkAddress.isUsableLanIpv4("192.168.1.20")
                && Main.hostedIpError("192.168.1.20") == null,
                "創立房間允許區網 IPv4");
        for (String invalid : new String[] {"127.0.0.1", "0.0.0.0", "169.254.1.2", "bad ip"}) {
            check(!NetworkAddress.isUsableLanIpv4(invalid)
                    && Main.hostedIpError(invalid) != null,
                    "創立房間拒絕無法分享的 IP：" + invalid);
        }
        check(Main.hostedIpError(null) != null, "創立房間拒絕找不到 IP");
        int[] selected = new int[5];
        String[] joinedIp = new String[1];
        LauncherPanel panel = new LauncherPanel("192.168.1.20", new LauncherPanel.Actions() {
            @Override public void startLocal() { selected[0]++; }
            @Override public void startPractice() { selected[1]++; }
            @Override public void startBluePractice() { selected[2]++; }
            @Override public String startHost() { selected[3]++; return null; }
            @Override public String join(String ip) { selected[4]++; joinedIp[0] = ip; return null; }
        });

        JTextField joinField = findEditableField(panel);
        check(joinField != null, "加入房間的 IP 欄位與模式按鈕在同一個啟動畫面");
        findButton(panel, "本地雙人").doClick();
        findButton(panel, "紅隊練習模式").doClick();
        findButton(panel, "藍隊練習模式").doClick();
        findButton(panel, "創立房間").doClick();
        check(selected[0] == 1 && selected[1] == 1 && selected[2] == 1 && selected[3] == 1,
                "本地雙人及雙側練習模式分別呼叫自己的啟動入口");

        joinField.setText(" 192.168.1.20 ");
        findButton(panel, "加入房間").doClick();
        check(selected[4] == 1 && "192.168.1.20".equals(joinedIp[0]),
                "在主選單輸入 IP 即可加入，不需第二個輸入視窗");
        joinField.setText("192.168.1.999");
        findButton(panel, "加入房間").doClick();
        check(selected[4] == 1 && !LauncherPanel.isValidIpv4("example.com"),
                "錯誤 IP 不會啟動 Client");

        LauncherPanel noLanPanel = new LauncherPanel("127.0.0.1", new LauncherPanel.Actions() {
            @Override public void startLocal() {}
            @Override public void startPractice() {}
            @Override public void startBluePractice() {}
            @Override public String startHost() { return Main.hostedIpError("127.0.0.1"); }
            @Override public String join(String ip) { return null; }
        });

        check("v1.0.0".equals(AppVersion.LABEL)
                && hasLabelContaining(panel, AppVersion.LABEL)
                && hasLabelContaining(panel, "HaiKyuu!!")
                && !hasLabelContaining(panel, "HaiKyuu!!  " + AppVersion.LABEL),
                "啟動頁版本號與標題分開顯示");
        check(!findButton(noLanPanel, "複製").isEnabled(), "沒有區網 IP 時不允許複製 loopback");
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

    private static boolean hasLabelContaining(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && label.getText().contains(text)) return true;
            if (child instanceof Container container && hasLabelContaining(container, text)) return true;
        }
        return false;
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
