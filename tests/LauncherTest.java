import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import model.AppVersion;
import model.ai.AiDifficulty;
import javax.swing.JSlider;
import network.NetworkAddress;
import view.LauncherPanel;

/** 不開實體視窗，檢查啟動選單的模式與內嵌 IP 欄位。 */
public final class LauncherTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(LauncherTest::testLauncherActions);
        System.out.println("LauncherTest passed");
    }

    private static void testLauncherActions() {
        check("HaiKyuu!! - 左方玩家對戰機器人（防守 50／進攻 50）".equals(
                Main.aiWindowTitle(true, AiDifficulty.DEFAULT)), "左方 AI 對戰視窗標題使用新名稱");
        check("HaiKyuu!! - 右方玩家對戰機器人（防守 82／進攻 17）".equals(
                Main.aiWindowTitle(false, new AiDifficulty(82, 17))), "右方 AI 對戰標題保留本場難度");
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
        AiDifficulty[] received = new AiDifficulty[1];
        LauncherPanel panel = new LauncherPanel("192.168.1.20", new LauncherPanel.Actions() {
            @Override public void startLocal() { selected[0]++; }
            @Override public void startAiMatch(boolean playerRedSide, AiDifficulty difficulty) {
                selected[playerRedSide ? 1 : 2]++;
                received[0] = difficulty;
            }
            @Override public String startHost() { selected[3]++; return null; }
            @Override public String join(String ip) { selected[4]++; joinedIp[0] = ip; return null; }
        });

        JTextField joinField = findEditableField(panel);
        check(joinField != null, "加入房間的 IP 欄位與模式按鈕在同一個啟動畫面");
        findButton(panel, "本地雙人").doClick();
        findButton(panel, "左方玩家對戰機器人").doClick();
        findButton(panel, "右方玩家對戰機器人").doClick();
        findButton(panel, "創立房間").doClick();
        check(selected[0] == 1 && selected[1] == 1 && selected[2] == 1 && selected[3] == 1,
                "本地雙人及左右 AI 對戰分別呼叫自己的啟動入口");

        check(AiDifficulty.DEFAULT.equals(received[0]), "兩個模式預設能力都是 50");
        findSlider(panel, "防守能力").setValue(82);
        findSlider(panel, "進攻能力").setValue(17);
        findButton(panel, "右方玩家對戰機器人").doClick();
        check(new AiDifficulty(82, 17).equals(received[0]), "兩個拉條獨立傳遞給所選模式");

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
            @Override public void startAiMatch(boolean playerRedSide, AiDifficulty difficulty) {}
            @Override public String startHost() { return Main.hostedIpError("127.0.0.1"); }
            @Override public String join(String ip) { return null; }
        });

        check(AppVersion.LABEL.matches("v\\d+\\.\\d+\\.\\d+")
                && hasLabelContaining(panel, AppVersion.LABEL)
                && hasLabelContaining(panel, "HaiKyuu!!")
                && !hasLabelContaining(panel, "HaiKyuu!!  " + AppVersion.LABEL),
                "啟動頁版本號與標題分開顯示");
        check(!findButton(noLanPanel, "複製").isEnabled(), "沒有區網 IP 時不允許複製 loopback");
        panel.setSize(584, 391);
        layoutTree(panel);
        check(findButton(panel, "本地雙人").getHeight() >= 22
                        && findButton(panel, "本地雙人").getHeight() <= 32
                        && findSlider(panel, "防守能力").getHeight() >= 16,
                "精簡啟動視窗維持正常按鈕高度與可操作拉條");
        JButton local = findButton(panel, "本地雙人");
        check(local.getWidth() == local.getParent().getWidth()
                        && local.getWidth() == findButton(panel, "創立房間").getWidth(),
                "主要單顆按鈕拉寬至內容區且寬度一致");
        for (String text : new String[]{"左方玩家對戰機器人", "右方玩家對戰機器人"}) {
            JButton button = findButton(panel, text);
            check(button.getFontMetrics(button.getFont()).stringWidth(text)
                            + button.getInsets().left + button.getInsets().right <= button.getWidth(),
                    "拉寬後完整顯示 AI 對戰按鈕文字");
        }
        var localTop = SwingUtilities.convertPoint(local, 0, 0, panel);
        JButton aiLeft = findButton(panel, "左方玩家對戰機器人");
        var aiTop = SwingUtilities.convertPoint(aiLeft, 0, 0, panel);
        JButton aiRight = findButton(panel, "右方玩家對戰機器人");
        var aiRightTop = SwingUtilities.convertPoint(aiRight, 0, 0, panel);
        check(aiTop.x == localTop.x && aiLeft.getWidth() == aiRight.getWidth()
                        && aiRightTop.x + aiRight.getWidth() == localTop.x + local.getWidth(),
                "左右 AI 按鈕平分內容區並與其他按鈕對齊");
        var sliderTop = SwingUtilities.convertPoint(findSlider(panel, "防守能力"), 0, 0, panel);
        check(localTop.y >= 20 && aiTop.y - localTop.y - local.getHeight() >= 12
                        && sliderTop.y - aiTop.y - aiLeft.getHeight() >= 12,
                "主選單保留外邊距以及按鈕與拉條之間的留白");
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static JSlider findSlider(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JSlider slider && name.equals(slider.getName())) return slider;
            if (child instanceof Container container) {
                JSlider found = findSlider(container, name);
                if (found != null) return found;
            }
        }
        return null;
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
