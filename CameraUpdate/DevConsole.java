import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;

/** Text console view; command execution stays in the game's simulation controller. */
final class DevConsole extends JPanel {
    private final JTextArea output = new JTextArea();
    final JTextField input = new JTextField();
    private final List<String> history = new ArrayList<>();
    private int historyIndex;
    private String draft = "";

    DevConsole(Function<String, String> execute) {
        super(new BorderLayout(0, 8));
        setBackground(new Color(17, 23, 33));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(92, 173, 201)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel title = new JLabel("DEV CONSOLE    |    ` / Esc: close    |    Up / Down: history");
        title.setForeground(new Color(151, 219, 240));
        add(title, BorderLayout.NORTH);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        output.setForeground(new Color(223, 235, 244));
        output.setBackground(getBackground());
        add(new JScrollPane(output), BorderLayout.CENTER);
        input.setFont(output.getFont());
        input.setBackground(new Color(30, 42, 57));
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        add(input, BorderLayout.SOUTH);
        input.addActionListener(e -> {
            String command = input.getText().trim();
            input.setText("");
            if (command.isEmpty()) return;
            history.add(command);
            if (history.size() > 100) history.remove(0);
            historyIndex = history.size();
            draft = "";
            append("> " + command);
            String response = execute.apply(command);
            if (!response.isEmpty()) append(response);
        });
        bindHistory(KeyEvent.VK_UP, -1);
        bindHistory(KeyEvent.VK_DOWN, 1);
        input.getInputMap().put(KeyStroke.getKeyStroke('`'), "ignoreBacktick");
        input.getActionMap().put("ignoreBacktick", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { }
        });
        append("Gameplay is paused. Type help for commands.");
        setVisible(false);
    }

    private void bindHistory(int key, int direction) {
        String action = "history" + direction;
        input.getInputMap().put(KeyStroke.getKeyStroke(key, 0), action);
        input.getActionMap().put(action, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (historyIndex == history.size()) draft = input.getText();
                historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
                input.setText(historyIndex == history.size() ? draft : history.get(historyIndex));
            }
        });
    }

    void focusInput() { input.requestFocusInWindow(); }
    void clearOutput() { output.setText(""); }
    private void append(String text) {
        output.append(text + "\n");
        if (output.getDocument().getLength() > 24000) {
            try { output.getDocument().remove(0, output.getDocument().getLength() - 20000); }
            catch (javax.swing.text.BadLocationException error) { throw new IllegalStateException(error); }
        }
        output.setCaretPosition(output.getDocument().getLength());
    }
}
