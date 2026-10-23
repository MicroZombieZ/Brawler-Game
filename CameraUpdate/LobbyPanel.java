import java.awt.*;
import javax.swing.*;

/** LAN connection and waiting-room controls; the game owns session lifecycle. */
final class LobbyPanel extends JPanel {
    final JTextField name = new JTextField("Player", 16);
    final JTextField address = new JTextField("", 16);
    final JSpinner port = new JSpinner(new SpinnerNumberModel(LobbyNetwork.DEFAULT_PORT, 1024, 65535, 1));
    final JLabel status = new JLabel("Host a lobby or enter the host's local IP to join.");
    final JTextArea roster = new JTextArea(8, 36);
    final JButton host = new JButton("Host Lobby"), join = new JButton("Join Lobby");
    final JButton start = new JButton("Start Match"), leave = new JButton("Leave Lobby"), back = new JButton("Back");

    LobbyPanel(Runnable onHost, Runnable onJoin, Runnable onStart, Runnable onLeave, Runnable onBack) {
        super(new BorderLayout(12, 12));
        setBackground(new Color(231, 239, 246));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6)); heading.setOpaque(false);
        JLabel title = new JLabel("LAN Lobby"); title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        heading.add(title); heading.add(new JLabel("Up to 8 players on the same Wi-Fi / local network"));
        heading.add(status); add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 14)); center.setOpaque(false);
        JPanel fields = new JPanel(new GridLayout(3, 2, 12, 8)); fields.setOpaque(false);
        fields.add(new JLabel("Your name")); fields.add(name);
        fields.add(new JLabel("Host IP (for joining)")); fields.add(address);
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        fields.add(new JLabel("Port")); fields.add(port); center.add(fields, BorderLayout.NORTH);
        roster.setEditable(false); roster.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        roster.setText("No lobby connected.\n\nHost: share your local IP address with friends.\nJoin: enter that address and use the same port.");
        center.add(new JScrollPane(roster), BorderLayout.CENTER); add(center, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)); buttons.setOpaque(false);
        for (JButton button : new JButton[]{host, join, start, leave, back}) buttons.add(button);
        host.addActionListener(e -> onHost.run()); join.addActionListener(e -> onJoin.run());
        start.addActionListener(e -> onStart.run()); leave.addActionListener(e -> onLeave.run()); back.addActionListener(e -> onBack.run());
        add(buttons, BorderLayout.SOUTH);
        session(false, false, false);
        setVisible(false);
    }
    void session(boolean connected, boolean isHost, boolean busy) {
        host.setEnabled(!connected && !busy); join.setEnabled(!connected && !busy);
        name.setEnabled(!connected && !busy); address.setEnabled(!connected && !busy); port.setEnabled(!connected && !busy);
        start.setEnabled(connected && isHost && !busy); leave.setEnabled(connected && !busy);
        back.setEnabled(!busy);
    }
}
