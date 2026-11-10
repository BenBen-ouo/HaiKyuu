package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import model.AppVersion;
import model.ai.AiDifficulty;
import network.NetworkAddress;

/** 不依賴命令列的啟動選單；訪客 IP 直接在這個畫面輸入。 */
public final class LauncherPanel extends JPanel {
    private static final int ROW_GAP_PX = 12;
    private static final int SECTION_GAP_PX = 14;
    private static final int PADDING_Y_PX = 20;
    private static final int PADDING_X_PX = 36;
    @java.io.Serial
    private static final long serialVersionUID = 1L;
    public interface Actions {
        void startLocal();
        void startAiMatch(boolean playerRedSide, AiDifficulty difficulty);
        String startHost();
        String join(String ip);
    }

    private final JTextField joinIpField = new JTextField(16);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JSlider defenseSlider = new JSlider(1, 100, 50);
    private final JSlider attackSlider = new JSlider(1, 100, 50);

    public LauncherPanel(String localIp, Actions actions) {
        setLayout(new BorderLayout(0, SECTION_GAP_PX));
        setBorder(BorderFactory.createEmptyBorder(PADDING_Y_PX, PADDING_X_PX, PADDING_Y_PX, PADDING_X_PX));
        setBackground(new Color(241, 246, 250));

        JLabel title = new JLabel("HaiKyuu!!", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        add(title, BorderLayout.NORTH);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, ROW_GAP_PX));
        options.setOpaque(false);

        JButton localButton = new JButton("本地雙人");
        localButton.addActionListener(event -> actions.startLocal());
        options.add(localButton);

        JPanel aiRow = new JPanel(new GridLayout(1, 2, 20, 0));
        aiRow.setOpaque(false);
        JButton leftAiButton = new JButton("左方玩家對戰機器人");
        leftAiButton.addActionListener(event -> actions.startAiMatch(true, difficulty()));
        aiRow.add(leftAiButton);
        JButton rightAiButton = new JButton("右方玩家對戰機器人");
        rightAiButton.addActionListener(event -> actions.startAiMatch(false, difficulty()));
        aiRow.add(rightAiButton);
        options.add(aiRow);
        options.add(difficultyRow("防守能力", defenseSlider));
        options.add(difficultyRow("進攻能力", attackSlider));

        JButton hostButton = new JButton("創立房間");
        hostButton.addActionListener(event -> showResult(actions.startHost()));
        options.add(hostButton);

        JPanel hostIpRow = new JPanel(new BorderLayout(8, 0));
        hostIpRow.setOpaque(false);
        hostIpRow.add(new JLabel("創立房間後分享此 IP："), BorderLayout.WEST);
        boolean hasHostIp = NetworkAddress.isUsableLanIpv4(localIp);
        JTextField hostIpField = new JTextField(hasHostIp ? localIp : "無可用區網 IP");
        hostIpField.setEditable(false);
        hostIpRow.add(hostIpField, BorderLayout.CENTER);
        JButton copyButton = new JButton("複製");
        copyButton.setEnabled(hasHostIp);
        copyButton.addActionListener(event -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(localIp), null);
                statusLabel.setForeground(new Color(25, 110, 68));
                statusLabel.setText("已複製房主 IP。");
            } catch (IllegalStateException exception) {
                showResult("無法使用剪貼簿，請手動複製 IP。");
            }
        });
        hostIpRow.add(copyButton, BorderLayout.EAST);
        options.add(hostIpRow);

        JPanel joinRow = new JPanel(new BorderLayout(8, 0));
        joinRow.setOpaque(false);
        joinRow.add(new JLabel("房主 IP："), BorderLayout.WEST);
        joinRow.add(joinIpField, BorderLayout.CENTER);
        JButton joinButton = new JButton("加入房間");
        joinButton.addActionListener(event -> join(actions));
        joinIpField.addActionListener(event -> join(actions));
        joinRow.add(joinButton, BorderLayout.EAST);
        options.add(joinRow);

        add(options, BorderLayout.CENTER);
        statusLabel.setForeground(new Color(152, 35, 35));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(statusLabel, BorderLayout.CENTER);
        JLabel versionLabel = new JLabel(AppVersion.LABEL);
        versionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        versionLabel.setForeground(Color.GRAY);
        footer.add(versionLabel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private void join(Actions actions) {
        String ip = joinIpField.getText().trim();
        if (!isValidIpv4(ip)) {
            showResult("請輸入正確的 IPv4 位址，例如 192.168.1.10。");
            joinIpField.requestFocusInWindow();
            return;
        }
        showResult(actions.join(ip));
    }

    private AiDifficulty difficulty() {
        return new AiDifficulty(defenseSlider.getValue(), attackSlider.getValue());
    }

    private static JPanel difficultyRow(String name, JSlider slider) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        JLabel value = new JLabel();
        slider.setName(name);
        slider.setOpaque(false);
        slider.getAccessibleContext().setAccessibleName(name);
        Runnable refresh = () -> value.setText(slider.getValue() + "（" + AiDifficulty.label(slider.getValue()) + "）");
        slider.addChangeListener(event -> refresh.run());
        refresh.run();
        row.add(new JLabel(name), BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void showResult(String error) {
        statusLabel.setForeground(new Color(152, 35, 35));
        statusLabel.setText(error == null ? " " : error);
    }

    public static boolean isValidIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            }
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
}
