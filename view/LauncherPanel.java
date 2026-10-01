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
import javax.swing.SwingConstants;

/** 不依賴命令列的啟動選單；訪客 IP 直接在這個畫面輸入。 */
public final class LauncherPanel extends JPanel {
    public interface Actions {
        void startLocal();
        void startPractice();
        String startHost();
        String join(String ip);
    }

    private final JTextField joinIpField = new JTextField(16);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public LauncherPanel(String localIp, Actions actions) {
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        setBackground(new Color(241, 246, 250));

        JLabel title = new JLabel("HaiKyuu!!", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        add(title, BorderLayout.NORTH);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 12));
        options.setOpaque(false);

        JButton localButton = new JButton("本地雙人");
        localButton.addActionListener(event -> actions.startLocal());
        options.add(localButton);

        JButton practiceButton = new JButton("練習模式");
        practiceButton.addActionListener(event -> actions.startPractice());
        options.add(practiceButton);

        JButton hostButton = new JButton("創立房間");
        hostButton.addActionListener(event -> showResult(actions.startHost()));
        options.add(hostButton);

        JPanel hostIpRow = new JPanel(new BorderLayout(8, 0));
        hostIpRow.setOpaque(false);
        hostIpRow.add(new JLabel("創立房間後分享此 IP："), BorderLayout.WEST);
        JTextField hostIpField = new JTextField(localIp);
        hostIpField.setEditable(false);
        hostIpRow.add(hostIpField, BorderLayout.CENTER);
        JButton copyButton = new JButton("複製");
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
        add(statusLabel, BorderLayout.SOUTH);
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
