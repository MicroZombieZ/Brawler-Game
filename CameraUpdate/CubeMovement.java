import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.*;
import javax.swing.*;

/** A dependency-free 3D movement demo. Run with: java CubeMovement.java */
public class CubeMovement extends JPanel {
    private static final double EDGE = 10, SPEED = 4;
    private static final double SPRINT_SPEED = 7, GRAVITY = 18, JUMP_SPEED = 7;
    private static final double MAX_STAMINA = 100, STAMINA_DRAIN = 20, STAMINA_RECOVERY = 15;
    private double stamina = MAX_STAMINA;
    private boolean exhausted;
    private final CombatSystem combat = new CombatSystem();
    private final ParticleSystem particles = new ParticleSystem();
    private final JComboBox<ParticleSystem.Quality> particleQuality = new JComboBox<>(ParticleSystem.Quality.values());
    private long lastParticleEvent;
    private boolean consoleOpen, consoleKeyHeld, resumeAfterConsole, showHitboxes, showStats, showDebugHud;
    private double speedMultiplier = 1, timeScale = 1;
    private final DevConsole console = new DevConsole(this::executeCommand);
    private final ButtonGroup classChoiceGroup = new ButtonGroup();
    private LobbyNetwork.Server lobbyServer;
    private LobbyNetwork.Client lobbyClient;
    private LobbyNetwork.Snapshot appliedSnapshot;
    private boolean lobbyOpen, connecting;
    private int connectionGeneration;
    private final LobbyPanel lobby = new LobbyPanel(() -> connectLobby(true), () -> connectLobby(false),
            () -> { if (lobbyClient != null) lobbyClient.action(4, ""); },
            () -> leaveLobby("Left lobby."), this::closeLobby);
    private static final double CAMERA_DISTANCE = 8, MAX_CAMERA_DISTANCE = 20, FIRST_PERSON_TRIGGER = 0.75, NEAR = 0.1;
    private static final double ZOOM_STEP = 0.75;
    private static final double CAMERA_RADIUS = 0.25;
    // Register future solid scenery here: {minX, minY, minZ, maxX, maxY, maxZ}.
    private final java.util.List<double[]> cameraObstacles = new ArrayList<>();
    private double desiredCameraDistance = CAMERA_DISTANCE;
    private double actualCameraDistance = CAMERA_DISTANCE, actualCameraPitch = Math.atan2(15, 22);
    private double cameraTargetY;
    private boolean thirdPerson = true;
    private final Set<Integer> keys = new HashSet<>();
    private double x, y, z, verticalSpeed;
    private int jumpsUsed;
    private boolean groundPounding;
    private double yaw, pitch = Math.atan2(15, 22);
    private double cubeYaw;
    private static final double TURN_RESPONSE = 10;
    private Robot mouseRobot;
    private Point mouseCenter;
    private boolean paused = true, escapeHeld;
    private boolean classMenuOpen, classKeyHeld;
    private String selectedClass, equippedClass;
    private double classMenuTime, classPreviewTime;
    private final JPanel classMenu = new JPanel(null) {
        @Override public void doLayout() { layoutClassMenu(); }
    };
    private final JPanel classChoices = new JPanel(null) {
        @Override public boolean contains(int px, int py) {
            if (getHeight() <= 0) return false;
            double row = (double) py / getHeight();
            return py >= 0 && py < getHeight()
                    && px >= getWidth() * .28 * (1 - row)
                    && px <= getWidth() * (.55 - .25 * row);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(36, 51, 72));
            Polygon band = new Polygon(
                    new int[]{getWidth() * 28 / 100, getWidth() * 55 / 100,
                            getWidth() * 30 / 100, 0},
                    new int[]{0, 0, getHeight(), getHeight()}, 4);
            g.fill(band);
            g.setColor(new Color(97, 164, 195));
            g.draw(band);
            g.dispose();
        }
    };
    private final JButton closeClasses = new JButton("Close");
    private final JLabel classTitle = new JLabel("Select a class", SwingConstants.CENTER);
    private final JButton equip = new JButton("Equip");
    private final JPanel menu = new JPanel();
    private final JLabel menuMessage = new JLabel("Mouse is unlocked", SwingConstants.CENTER);
    private long previous = System.nanoTime();

    public CubeMovement() {
        setPreferredSize(new Dimension(1000, 700));
        setBackground(new Color(24, 29, 39));
        setFocusable(true);
        setLayout(null);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(new Color(235, 240, 245));
        menu.setBorder(BorderFactory.createEmptyBorder(28, 40, 28, 40));
        JLabel title = new JLabel("Paused");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        JButton resume = new JButton("Resume");
        JButton quit = new JButton("Quit");
        JButton lan = new JButton("LAN Lobby");
        JPanel effectsSetting = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        effectsSetting.setOpaque(false); effectsSetting.add(new JLabel("Particles"));
        particleQuality.setSelectedItem(ParticleSystem.Quality.MEDIUM);
        particleQuality.addActionListener(e -> particles.quality((ParticleSystem.Quality) particleQuality.getSelectedItem()));
        effectsSetting.add(particleQuality);
        lan.addActionListener(e -> openLobby());
        resume.addActionListener(e -> setPaused(false));
        quit.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
        });
        for (JComponent item : new JComponent[]{title, menuMessage, resume, lan, effectsSetting, quit}) {
            item.setAlignmentX(Component.CENTER_ALIGNMENT);
            menu.add(item);
            menu.add(Box.createVerticalStrut(12));
        }
        add(menu);
        buildClassMenu();
        add(console, 0);
        add(lobby, 0);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("pressed F2"), "lobby");
        getActionMap().put("lobby", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (!consoleOpen) openLobby(); }
        });
        for (boolean released : new boolean[]{false, true}) {
            String action = released ? "consoleUp" : "console";
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0, released), action);
            getActionMap().put(action, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (released) consoleKeyHeld = false;
                    else if (!consoleKeyHeld) {
                        consoleKeyHeld = true;
                        if (!lobbyOpen) toggleConsole();
                    }
                }
            });
        }
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("pressed ESCAPE"), "pause");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("released ESCAPE"), "escapeUp");
        getActionMap().put("pause", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!escapeHeld) {
                    if (consoleOpen) toggleConsole();
                    else if (lobbyOpen) closeLobby();
                    else setPaused(!paused);
                }
                escapeHeld = true;
            }
        });
        getActionMap().put("escapeUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { escapeHeld = false; }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { mouseCenter = null; }
            @Override public void componentMoved(ComponentEvent e) { mouseCenter = null; }
        });
        addMouseWheelListener(e -> {
            if (paused) return;
            int rotation = e.getWheelRotation();
            if (!thirdPerson && rotation > 0) {
                // Scrolling back out of first person restores the third-person orbit.
                thirdPerson = true;
                desiredCameraDistance = 1.0;
            } else if (thirdPerson) {
                desiredCameraDistance = Math.max(0,
                        Math.min(MAX_CAMERA_DISTANCE, desiredCameraDistance + rotation * ZOOM_STEP));
                if (desiredCameraDistance <= FIRST_PERSON_TRIGGER) {
                    thirdPerson = false;
                    desiredCameraDistance = 0;
                }
            }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) attack(CombatSystem.Move.PUNCH);
            }
        });
        for (int key : new int[]{KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S,
                KeyEvent.VK_D, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                KeyEvent.VK_SHIFT, KeyEvent.VK_SPACE, KeyEvent.VK_CONTROL,
                KeyEvent.VK_Q, KeyEvent.VK_E, KeyEvent.VK_F}) {
            bind(key, false);
            bind(key, true);
        }
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0, false), "debugHud");
        getActionMap().put("debugHud", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showDebugHud = !showDebugHud; repaint(); }
        });
        for (boolean released : new boolean[]{false, true}) {
            String action = released ? "classesUp" : "classes";
            for (int modifiers : new int[]{0, InputEvent.SHIFT_DOWN_MASK}) {
                getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_R, modifiers, released), action);
            }
            getActionMap().put(action, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (released) classKeyHeld = false;
                    else if (consoleOpen || lobbyOpen) return;
                    else if (!classKeyHeld) {
                        classKeyHeld = true;
                        if (classMenuOpen) setPaused(false);
                        else openClassMenu();
                    }
                }
            });
        }
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                keys.clear();
            }
        });
        new javax.swing.Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min((now - previous) / 1e9, 0.05);
            previous = now;
            updateNetwork(dt);
            animateClassMenu(dt);
            if (!paused) {
                updateMouseLook();
                if (lobbyClient == null) advanceSimulation(dt);
            }
            updateParticles(dt);
            repaint();
        }).start();
    }

    private void buildClassMenu() {
        classMenu.setOpaque(false);
        classChoices.setOpaque(false);
        for (String name : new String[]{"Gauntlet", "Mage", "Ranger", "Rogue"}) {
            JToggleButton choice = new JToggleButton(name) {
                private Polygon shape() {
                    int slant = Math.min(26, getWidth() / 6);
                    return new Polygon(new int[]{slant, getWidth(), getWidth() - slant, 0},
                            new int[]{0, 0, getHeight(), getHeight()}, 4);
                }
                @Override public boolean contains(int px, int py) { return shape().contains(px, py); }
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(isSelected() ? new Color(67, 151, 186)
                            : getModel().isRollover() ? new Color(63, 86, 112) : new Color(43, 64, 89));
                    g.fill(shape());
                    g.setColor(isFocusOwner() ? Color.WHITE : new Color(135, 194, 216));
                    g.draw(shape());
                    g.setFont(getFont());
                    FontMetrics metrics = g.getFontMetrics();
                    g.setColor(Color.WHITE);
                    g.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2,
                            (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                    g.dispose();
                }
            };
            choice.setContentAreaFilled(false);
            choice.setBorderPainted(false);
            choice.setRolloverEnabled(true);
            choice.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            choice.addActionListener(e -> {
                selectedClass = name;
                classTitle.setText(name);
                classPreviewTime = 0;
                classTitle.setVisible(true);
                equip.setVisible(false);
                equip.setEnabled(true);
                layoutClassMenu();
            });
            classChoiceGroup.add(choice);
            classChoices.add(choice);
        }
        classMenu.add(classChoices);
        classTitle.setForeground(new Color(239, 245, 250));
        classTitle.setVisible(false);
        classMenu.add(classTitle);
        closeClasses.addActionListener(e -> setPaused(false));
        classMenu.add(closeClasses);
        equip.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        equip.setBackground(new Color(87, 185, 210));
        equip.setEnabled(false);
        equip.setVisible(false);
        equip.addActionListener(e -> {
            if (selectedClass == null) return;
            if (combat.locked() && !Objects.equals(equippedClass, selectedClass)) return;
            equippedClass = selectedClass;
            if (lobbyClient != null) lobbyClient.action(3, equippedClass);
            else combat.equip("Gauntlet".equals(equippedClass));
            setPaused(false);
        });
        classMenu.add(equip);
        classMenu.setVisible(false);
        add(classMenu);
    }

    @Override public void doLayout() {
        Dimension size = menu.getPreferredSize();
        menu.setBounds((getWidth() - size.width) / 2, (getHeight() - size.height) / 2,
                size.width, size.height);
        classMenu.setBounds(0, 0, getWidth(), getHeight());
        console.setBounds(12, 12, Math.max(1, getWidth() - 24), Math.max(120, getHeight() / 2));
        int lobbyWidth = Math.min(700, getWidth() - 24), lobbyHeight = Math.min(490, getHeight() - 24);
        lobby.setBounds((getWidth() - lobbyWidth) / 2, (getHeight() - lobbyHeight) / 2, lobbyWidth, lobbyHeight);
        layoutClassMenu();
    }

    private static double slideProgress(double time, double duration) {
        double t = Math.max(0, Math.min(1, time / duration));
        return 1 - Math.pow(1 - t, 3);
    }

    private void animateClassMenu(double dt) {
        if (!classMenuOpen) return;
        classMenuTime += dt;
        if (selectedClass != null) classPreviewTime += dt;
        layoutClassMenu();
    }

    private void layoutClassMenu() {
        int width = classMenu.getWidth(), height = classMenu.getHeight();
        if (width <= 0 || height <= 0) return;
        double remainingSlide = 1 - slideProgress(classMenuTime, .5);
        int sidebarY = (int) Math.round(-height * remainingSlide);
        // Follow the average slope of the band's two sides: down and to the left.
        int sidebarX = (int) Math.round(width * .265 * remainingSlide);
        classChoices.setBounds(sidebarX, sidebarY, width, height);
        int buttonWidth = Math.max(90, width * 23 / 100);
        int buttonHeight = Math.max(32, Math.min(72, height / 9));
        for (int i = 0; i < classChoices.getComponentCount(); i++) {
            double row = .14 + i * .21;
            int buttonX = (int) (width * (.28 - row * .25 + .02));
            classChoices.getComponent(i).setBounds(buttonX, (int) (height * row), buttonWidth, buttonHeight);
        }
        closeClasses.setBounds(20, 18, 90, 34);
        int previewX = width * 52 / 100, previewWidth = width - previewX - 20;
        classTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, Math.min(76, width / 13))));
        int titleY = (int) Math.round(height * .39 + height * .65 * (1 - slideProgress(classPreviewTime, .45)));
        classTitle.setBounds(previewX, titleY, previewWidth, Math.max(85, height / 6));
        int equipWidth = Math.min(180, previewWidth);
        int equipY = (int) Math.round(height * .76 + height * .35 * (1 - slideProgress(classPreviewTime - .14, .4)));
        equip.setBounds(previewX + (previewWidth - equipWidth) / 2, equipY, equipWidth, 52);
        equip.setVisible(selectedClass != null && classPreviewTime >= .14);
    }

    private void openClassMenu() {
        setPaused(true);
        classMenuTime = classPreviewTime = 0;
        classMenuOpen = true;
        menu.setVisible(false);
        classMenu.setVisible(true);
        doLayout();
        revalidate();
        repaint();
    }

    private void setPaused(boolean value) {
        if (!value && (consoleOpen || lobbyOpen || (lobbyClient != null
                && (lobbyClient.snapshot == null || !lobbyClient.snapshot.started)))) return;
        if (!value) {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window == null || !window.isFocused() || !isShowing()) return;
            try {
                mouseRobot = new Robot(getGraphicsConfiguration().getDevice());
            } catch (AWTException | SecurityException error) {
                menuMessage.setText("Mouse capture unavailable; try resuming again.");
                return;
            }
            Cursor hidden = Toolkit.getDefaultToolkit().createCustomCursor(
                    new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                    new Point(0, 0), "Hidden");
            setCursor(hidden);
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
        paused = value;
        if (!paused) classMenuOpen = false;
        keys.clear();
        mouseCenter = null;
        previous = System.nanoTime();
        menuMessage.setText("Mouse is unlocked");
        menu.setVisible(paused && !classMenuOpen && !consoleOpen && !lobbyOpen);
        classMenu.setVisible(paused && classMenuOpen && !consoleOpen && !lobbyOpen);
        if (!paused) {
            requestFocusInWindow();
            updateMouseLook();
        }
        revalidate();
        repaint();
    }

    private void updateMouseLook() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (paused || mouseRobot == null || !isShowing()) return;
        if (window == null || !window.isFocused()) {
            setPaused(true);
            return;
        }
        Point center = getLocationOnScreen();
        center.translate(getWidth() / 2, getHeight() / 2);
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer == null) { setPaused(true); return; }
        Point position = pointer.getLocation();
        // Sample relative motion before recentering; synthetic warp events are ignored.
        if (center.equals(mouseCenter)) {
            yaw = Math.IEEEremainder(yaw - (position.x - center.x) * 0.008, Math.PI * 2);
            x = clamp(x);
            z = clamp(z);
            pitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89),
                    pitch + (position.y - center.y) * 0.008));
        }
        mouseCenter = center;
        mouseRobot.mouseMove(center.x, center.y);
    }

    private void bind(int key, boolean released) {
        String name = key + ":" + released;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, released), name);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(key, InputEvent.SHIFT_DOWN_MASK, released), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (released) {
                    keys.remove(key);
                } else if (!paused && keys.add(key)) {
                    if (key == KeyEvent.VK_Q) { attack(CombatSystem.Move.PUMMEL); return; }
                    if (key == KeyEvent.VK_E) { attack(CombatSystem.Move.SHOCKWAVE); return; }
                    if (key == KeyEvent.VK_F) { attack(CombatSystem.Move.HAYMAKER); return; }
                    if (lobbyClient != null) {
                        if (key == KeyEvent.VK_SPACE) lobbyClient.action(1, "");
                        else if (key == KeyEvent.VK_CONTROL) lobbyClient.action(2, "");
                        return;
                    }
                    if (combat.health <= 0) return;
                    if (key == KeyEvent.VK_SPACE) {
                        // One jump from the ground and one additional jump in the air.
                        if (y == 0) {
                            verticalSpeed = JUMP_SPEED;
                            jumpsUsed = 1;
                            groundPounding = false;
                            combat.effect(GameEffect.Kind.TAKEOFF, x, y, z, 1, 1);
                        } else if (jumpsUsed < 2 && !groundPounding) {
                            verticalSpeed = JUMP_SPEED;
                            jumpsUsed++;
                            combat.effect(GameEffect.Kind.DOUBLE_JUMP, x, y, z, 1, 1);
                        }
                    } else if (key == KeyEvent.VK_CONTROL && y > 0 && !groundPounding) {
                        // Ground pound: drive the cube sharply downward until it lands.
                        verticalSpeed = -24;
                        groundPounding = true;
                    }
                }
            }
        });
    }

    private boolean held(int a, int b) { return keys.contains(a) || keys.contains(b); }

    private void openLobby() {
        setPaused(true);
        lobbyOpen = true;
        menu.setVisible(false); classMenu.setVisible(false);
        lobby.setVisible(true); doLayout();
        lobby.session(lobbyClient != null, lobbyClient != null && lobbyClient.host, connecting);
        lobby.name.requestFocusInWindow();
    }

    private void closeLobby() {
        if (connecting) return;
        if (lobbyClient != null && (lobbyClient.snapshot == null || !lobbyClient.snapshot.started)) {
            lobby.status.setText("Waiting for the host to start. Use Leave Lobby to return to solo play.");
            return;
        }
        lobbyOpen = false; lobby.setVisible(false);
        setPaused(true); setPaused(false);
    }

    private void connectLobby(boolean host) {
        if (connecting || lobbyClient != null) return;
        String address = lobby.address.getText().trim(), name = lobby.name.getText().trim();
        if (!host && address.isEmpty()) { lobby.status.setText("Enter the host's local IP address first."); return; }
        int port = (Integer) lobby.port.getValue();
        int generation = ++connectionGeneration;
        connecting = true; lobby.session(false, false, true);
        lobby.status.setText(host ? "Creating lobby..." : "Connecting to " + address + "...");
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() throws Exception {
                LobbyNetwork.Server server = host ? new LobbyNetwork.Server(port) : null;
                try {
                    LobbyNetwork.Client client = new LobbyNetwork.Client(host ? "127.0.0.1" : address,
                            host ? server.port() : port, name, host ? server.hostKey : "");
                    String addresses = host ? LobbyNetwork.localAddresses() : address;
                    return new Object[]{server, client, addresses};
                } catch (Exception error) { if (server != null) server.close(); throw error; }
            }
            @Override protected void done() {
                try {
                    Object[] result = get();
                    if (generation != connectionGeneration) {
                        ((LobbyNetwork.Client) result[1]).close();
                        if (result[0] != null) ((LobbyNetwork.Server) result[0]).close();
                        return;
                    }
                    lobbyServer = (LobbyNetwork.Server) result[0]; lobbyClient = (LobbyNetwork.Client) result[1];
                    appliedSnapshot = null; timeScale = speedMultiplier = 1;
                    particles.clear(); combat.effects.clear(); lastParticleEvent = 0;
                    combat.godMode = false; combat.cooldownsEnabled = true;
                    lobby.status.setText(host ? "Your local IP: " + result[2] + "   Port: " + port
                            : "Connected to " + address + ":" + port + ". Waiting for host.");
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    lobby.status.setText("Connection failed: " + cause.getMessage());
                } finally {
                    if (generation == connectionGeneration) {
                        connecting = false;
                        lobby.session(lobbyClient != null, lobbyClient != null && lobbyClient.host, false);
                    }
                }
            }
        }.execute();
    }

    private void leaveLobby(String reason) {
        connectionGeneration++;
        if (lobbyClient != null) lobbyClient.close();
        if (lobbyServer != null) lobbyServer.close();
        lobbyClient = null; lobbyServer = null; appliedSnapshot = null; connecting = false;
        executeCommand("reset");
        lobby.session(false, false, false);
        lobby.roster.setText("No lobby connected."); lobby.status.setText(reason);
        openLobby();
    }

    private void updateNetwork(double dt) {
        LobbyNetwork.Client client = lobbyClient;
        if (client == null) return;
        if (client.closed) { leaveLobby(client.error == null ? "Host disconnected." : client.error); return; }
        client.yaw = yaw;
        client.dx = paused ? 0 : (held(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0) - (held(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0);
        client.dz = paused ? 0 : (held(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0) - (held(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0);
        client.sprint = !paused && keys.contains(KeyEvent.VK_SHIFT);
        LobbyNetwork.Snapshot snapshot = client.snapshot;
        if (snapshot == null) return;
        LobbyNetwork.State local = snapshot.player(client.id);
        if (local == null) return;
        boolean first = appliedSnapshot == null;
        if (snapshot != appliedSnapshot) {
            boolean startNow = snapshot.started && (first || !appliedSnapshot.started);
            appliedSnapshot = snapshot;
            if (first) lastParticleEvent = snapshot.lastEffectId;
            else {
                for (GameEffect effect : snapshot.effects) if (effect.id > lastParticleEvent) particles.emit(effect);
                lastParticleEvent = snapshot.lastEffectId;
            }
            combat.dummies.clear(); combat.dummies.addAll(snapshot.dummies);
            combat.equipped = local.className.equals("Gauntlet");
            equippedClass = local.className.isEmpty() ? null : local.className;
            combat.health = local.health; combat.multiplier = local.multiplier; combat.streakTime = local.streak;
            combat.endLag = local.recovery; combat.attackTime = local.attackTime; combat.speed = local.speed;
            combat.facing = local.facing;
            combat.active = local.active < 0 ? null : CombatSystem.Move.values()[local.active];
            System.arraycopy(local.cooldowns, 0, combat.cooldowns, 0, 4);
            combat.waveTime = local.waveTime; combat.waveX = local.waveX; combat.waveZ = local.waveZ;
            stamina = local.stamina; exhausted = local.exhausted;
            combat.messageTime = local.health <= 0 || local.slowTime > 0 ? 1 : 0;
            combat.message = local.health <= 0 ? "Respawning in " + (int) Math.ceil(local.respawn) + "s"
                    : "Slowed " + (int) (local.slowAmount * 100) + "%";
            StringBuilder roster = new StringBuilder(snapshot.started ? "MATCH IN PROGRESS\n\n" : "WAITING FOR HOST\n\n");
            for (LobbyNetwork.State p : snapshot.players) roster.append(p.id == client.id ? "> " : "  ")
                    .append(p.name).append(p.host ? " (host)" : "").append("  -  ")
                    .append(p.className.isEmpty() ? "No class" : p.className).append('\n');
            lobby.roster.setText(roster.toString()); lobby.start.setEnabled(client.host && !snapshot.started);
            if (startNow) { lobbyOpen = false; lobby.setVisible(false); setPaused(true); setPaused(false); }
        }
        double blend = first || Math.hypot(local.x - x, local.z - z) > 5 ? 1 : 1 - Math.exp(-25 * dt);
        x += (local.x - x) * blend; y += (local.y - y) * blend; z += (local.z - z) * blend;
        cubeYaw = local.yaw;
    }

    private void toggleConsole() {
        if (!consoleOpen) {
            resumeAfterConsole = !paused;
            setPaused(true);
            consoleOpen = true;
            menu.setVisible(false);
            classMenu.setVisible(false);
            console.setVisible(true);
            doLayout();
            console.focusInput();
        } else {
            consoleOpen = false;
            console.setVisible(false);
            if (resumeAfterConsole) setPaused(false);
            else { setPaused(true); requestFocusInWindow(); }
        }
        repaint();
    }

    private void updateParticles(double dt) {
        boolean online = lobbyClient != null && appliedSnapshot != null && appliedSnapshot.started;
        if (!online && (paused || lobbyClient != null)) return;
        double step = online ? dt : dt * timeScale;
        particles.update(step);
        Set<Integer> actors = new HashSet<>();
        if (online) {
            for (LobbyNetwork.State player : appliedSnapshot.players) {
                actors.add(player.id);
                boolean local = player.id == lobbyClient.id;
                particles.observe(player.id,step,local?x:player.x,local?y:player.y,local?z:player.z,
                        player.yaw,player.speed,player.health,player.multiplier,player.slowTime>0,
                        Math.hypot(player.knockX,player.knockZ)>.3,
                        player.active<0?null:CombatSystem.Move.values()[player.active],player.attackTime);
            }
        } else {
            for (GameEffect effect : combat.effects) particles.emit(effect);
            combat.effects.clear();
            actors.add(0);
            particles.observe(0,step,x,y,z,cubeYaw,combat.speed,combat.health,combat.multiplier,
                    false,false,combat.active,combat.attackTime);
        }
        for (int i=0;i<combat.dummies.size();i++) {
            CombatSystem.Dummy dummy = combat.dummies.get(i);
            int id = -i-1; actors.add(id);
            particles.observe(id,step,dummy.x,dummy.lift,dummy.z,0,0,dummy.health,1,
                    dummy.slowTime>0,dummy.damageTime>0,null,0);
        }
        particles.retainActors(actors);
    }

    private void advanceSimulation(double realDt) {
        double remaining = realDt * timeScale;
        if (remaining == 0) combat.speed = 0;
        while (!paused && remaining > 1e-9) {
            double step = Math.min(remaining, .01);
            move(step);
            remaining -= step;
        }
    }

    private static double number(String token, double min, double max) {
        double value;
        try { value = Double.parseDouble(token); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Expected a number: " + token); }
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Value must be between " + min + " and " + max + ".");
        return value;
    }

    private static boolean switchValue(String token) {
        if (token.equals("on")) return true;
        if (token.equals("off")) return false;
        throw new IllegalArgumentException("Expected on or off.");
    }

    private static void arguments(String[] words, int count, String usage) {
        if (words.length != count) throw new IllegalArgumentException("Usage: " + usage);
    }

    String executeCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return "";
        String[] words = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
        if (lobbyClient != null && !Arrays.asList("help", "clear", "hitboxes", "stats", "particles").contains(words[0]))
            return "Leave the LAN lobby before using gameplay-changing dev commands.";
        try {
            switch (words[0]) {
                case "help":
                    arguments(words, 1, "help");
                    return "help - list commands\nclear - clear output\nheal - restore full HP\n"
                            + "hp <0..max HP> - set health\nstamina <0..100> - set stamina\n"
                            + "god on/off - toggle damage immunity\ncooldowns reset - ready all cooldowns\n"
                            + "cooldowns on/off - toggle cooldowns (recovery still applies)\n"
                            + "streak <1..1.35> - set multiplier, refresh 6.5s timer\nstreak reset - clear streak\n"
                            + "damage <amount> - damage yourself (respects god mode)\n"
                            + "tp <x> <z> - teleport within -10..10\nspeed <0..10> - movement multiplier\n"
                            + "dummy spawn - add a target 2 units ahead\ndummy reset - restore four targets\n"
                            + "dummy clear - remove all targets\ndummy hp <1..100000> - set max HP and refill all targets\n"
                            + "hitboxes on/off - show attack ranges\nstats on/off - detailed debug stats\n"
                            + "particles off|low|medium|high - local effects quality\nparticles clear - clear local effects\n"
                            + "timescale <0..4> - simulation speed; 1 normal, 0 frozen\n"
                            + "reset - restore player, arena, camera and dev settings to defaults\n"
                            + "Examples: hp 50 | cooldowns off | tp 0 -2 | timescale 0.5";
                case "clear":
                    arguments(words, 1, "clear"); console.clearOutput(); return "";
                case "heal":
                    arguments(words, 1, "heal");
                    if (combat.health<=0) combat.effect(GameEffect.Kind.RESPAWN,x,y,z,1,1);
                    combat.health = combat.maxHealth(); return "Health: " + combat.health;
                case "hp":
                    arguments(words, 2, "hp <amount>");
                    double hp = number(words[1], 0, combat.maxHealth());
                    if (hp == 0) {
                        if (combat.health>0) combat.effect(GameEffect.Kind.DEFEAT,x,y,z,1,1);
                        combat.cancelAttack(); combat.multiplier = 1; combat.streakTime = 0;
                    }
                    if (hp>0 && combat.health<=0) combat.effect(GameEffect.Kind.RESPAWN,x,y,z,1,1);
                    combat.health = hp; return "Health: " + hp;
                case "stamina":
                    arguments(words, 2, "stamina <0..100>");
                    stamina = number(words[1], 0, MAX_STAMINA);
                    if (stamina == 0) exhausted = true;
                    else if (stamina >= 25) exhausted = false;
                    return "Stamina: " + stamina;
                case "god":
                    arguments(words, 2, "god on/off"); combat.godMode = switchValue(words[1]);
                    return "God mode: " + (combat.godMode ? "on" : "off");
                case "cooldowns":
                    arguments(words, 2, "cooldowns reset|on|off");
                    if (words[1].equals("reset")) { Arrays.fill(combat.cooldowns, 0); return "All cooldowns ready."; }
                    boolean enabled = switchValue(words[1]);
                    combat.cooldownsEnabled = enabled;
                    if (!enabled) Arrays.fill(combat.cooldowns, 0);
                    return "Cooldowns: " + (enabled ? "on" : "off");
                case "streak":
                    arguments(words, 2, "streak <1..1.35>|reset");
                    double multiplier = words[1].equals("reset") ? 1 : number(words[1], 1, CombatSystem.MAX_MULTIPLIER);
                    combat.multiplier = multiplier;
                    combat.streakTime = multiplier > 1 ? CombatSystem.STREAK_DURATION : 0;
                    return "Damage multiplier: " + multiplier + "x; timer: " + combat.streakTime + "s";
                case "damage":
                    arguments(words, 2, "damage <amount>"); combat.damagePlayer(number(words[1], 0, 100000));
                    return "Health: " + combat.health + (combat.godMode ? " (god mode)" : "");
                case "tp":
                    arguments(words, 3, "tp <x> <z>");
                    double tx = number(words[1], -EDGE, EDGE), tz = number(words[2], -EDGE, EDGE);
                    combat.cancelAttack(); x = clamp(tx); z = clamp(tz); y = verticalSpeed = 0;
                    jumpsUsed = 0; groundPounding = false; combat.speed = 0;
                    combat.playerX = x; combat.playerY = 0; combat.playerZ = z;
                    return String.format(Locale.ROOT, "Position: %.2f, 0, %.2f (clamped to plane)", x, z);
                case "speed":
                    arguments(words, 2, "speed <0..10>"); speedMultiplier = number(words[1], 0, 10);
                    return "Movement speed multiplier: " + speedMultiplier;
                case "dummy":
                    if (words.length < 2) throw new IllegalArgumentException("Usage: dummy spawn|reset|clear|hp <amount>");
                    if (words[1].equals("hp")) {
                        arguments(words, 3, "dummy hp <1..100000>");
                        combat.setDummyHealth(number(words[2], 1, 100000));
                        return "Dummy max HP: " + combat.dummyMaxHealth + "; existing targets refilled.";
                    }
                    arguments(words, 2, "dummy spawn|reset|clear");
                    switch (words[1]) {
                        case "spawn": combat.spawnDummy(x - Math.sin(yaw) * 2, z - Math.cos(yaw) * 2); break;
                        case "reset": combat.resetDummies(); break;
                        case "clear": combat.clearDummies(); break;
                        default: throw new IllegalArgumentException("Usage: dummy spawn|reset|clear|hp <amount>");
                    }
                    return "Training dummies: " + combat.dummies.size();
                case "hitboxes":
                    arguments(words, 2, "hitboxes on/off"); showHitboxes = switchValue(words[1]);
                    return "Hitboxes: " + (showHitboxes ? "on" : "off");
                case "stats":
                    arguments(words, 2, "stats on/off"); showStats = switchValue(words[1]);
                    return "Detailed stats: " + (showStats ? "on" : "off");
                case "timescale":
                    arguments(words, 2, "timescale <0..4>"); timeScale = number(words[1], 0, 4);
                    return "Time scale: " + timeScale + " (movement, combat, effects and timers)";
                case "particles":
                    arguments(words,2,"particles off|low|medium|high|clear");
                    if (words[1].equals("clear")) { particles.clear(); return "Local particles cleared."; }
                    ParticleSystem.Quality quality;
                    try { quality=ParticleSystem.Quality.valueOf(words[1].toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException error) { throw new IllegalArgumentException("Usage: particles off|low|medium|high|clear"); }
                    particleQuality.setSelectedItem(quality);
                    return "Particle quality: " + quality + " (limit " + quality.limit + ")";
                case "reset":
                    arguments(words, 1, "reset");
                    combat.reset(); x = y = z = verticalSpeed = yaw = cubeYaw = 0;
                    particles.clear(); particleQuality.setSelectedItem(ParticleSystem.Quality.MEDIUM); lastParticleEvent=0;
                    jumpsUsed = 0; groundPounding = exhausted = false; stamina = MAX_STAMINA;
                    speedMultiplier = timeScale = 1; showHitboxes = showStats = false;
                    pitch = Math.atan2(15, 22); desiredCameraDistance = actualCameraDistance = CAMERA_DISTANCE;
                    actualCameraPitch = pitch; cameraTargetY = 0; thirdPerson = true; mouseCenter = null;
                    selectedClass = equippedClass = null; classChoiceGroup.clearSelection();
                    classTitle.setText("Select a class"); classTitle.setVisible(false); equip.setEnabled(false);
                    classMenuOpen = false; classMenu.setVisible(false); menu.setVisible(paused && !consoleOpen);
                    keys.clear();
                    return "Arena, player, camera and dev settings reset. Equip Gauntlet with R.";
                default: return "Unknown command: " + words[0] + ". Type help for commands.";
            }
        } catch (IllegalArgumentException error) { return "Error: " + error.getMessage(); }
    }

    private void attack(CombatSystem.Move move) {
        if (paused) return;
        if (lobbyClient != null) lobbyClient.action(0, move.name());
        else combat.attack(move, x, y, z, cubeYaw);
    }

    private void move(double dt) {
        if (paused) return;
        double startX = x, startZ = z;
        double turn = Math.IEEEremainder(yaw - cubeYaw, Math.PI * 2);
        cubeYaw = Math.IEEEremainder(cubeYaw + turn * (1 - Math.exp(-TURN_RESPONSE * dt)), Math.PI * 2);
        x = clamp(x);
        z = clamp(z);
        y += verticalSpeed * dt - 0.5 * GRAVITY * dt * dt;
        verticalSpeed -= GRAVITY * dt;
        if (y <= 0) {
            if (jumpsUsed>0 || groundPounding) combat.effect(groundPounding?GameEffect.Kind.GROUND_POUND:GameEffect.Kind.LAND,
                    x,0,z,Math.abs(verticalSpeed),1);
            y = 0;
            verticalSpeed = 0;
            jumpsUsed = 0;
            groundPounding = false;
        }
        double dx = (held(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0)
                  - (held(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0);
        double dz = (held(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0)
                  - (held(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0);
        double length = Math.hypot(dx, dz);
        if (combat.health <= 0) length = 0;
        boolean sprinting = false;
        if (length > 0) {
            // Normalize so diagonal movement has the same speed.
            double worldX = Math.cos(yaw) * dx + Math.sin(yaw) * dz;
            double worldZ = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
            boolean wantsSprint = keys.contains(KeyEvent.VK_SHIFT) && !exhausted && stamina > 0;
            // Use only the sprint time that the remaining stamina can pay for.
            double sprintTime = wantsSprint ? Math.min(dt, stamina / STAMINA_DRAIN) : 0;
            double speed = (dt > 0 ? SPEED + (SPRINT_SPEED - SPEED) * sprintTime / dt : SPEED) * speedMultiplier;
            double beforeX = x, beforeZ = z;
            x = clamp(x + worldX / length * speed * dt);
            z = clamp(z + worldZ / length * speed * dt);
            sprinting = sprintTime > 0 && Math.hypot(x - beforeX, z - beforeZ) > 1e-9;
            if (sprinting) {
                stamina = Math.max(0, stamina - STAMINA_DRAIN * sprintTime);
                if (stamina <= 1e-9) { stamina = 0; exhausted = true; }
            }
        }
        if (!sprinting) {
            stamina = Math.min(MAX_STAMINA, stamina + STAMINA_RECOVERY * dt);
            // Recover a useful amount before allowing sprint again, avoiding speed flicker.
            if (stamina >= 25) exhausted = false;
        }
        combat.update(dt, x, y, z, cubeYaw);
        x = clamp(combat.playerX);
        z = clamp(combat.playerZ);
        combat.speed = dt > 0 ? Math.hypot(x - startX, z - startZ) / dt : 0;
    }

    private double clamp(double value) {
        double halfExtent = 0.5 * (Math.abs(Math.cos(cubeYaw)) + Math.abs(Math.sin(cubeYaw)));
        return Math.max(-EDGE + halfExtent, Math.min(EDGE - halfExtent, value));
    }

    // Resolve the desired orbit against the ground and solid scenery.
    private void resolveCamera() {
        // Place the first-person eye high on the cube, while third person still orbits its center.
        double targetY = y + (thirdPerson ? 0.5 : 0.9);
        cameraTargetY = targetY;
        if (!thirdPerson) {
            actualCameraDistance = NEAR;
            actualCameraPitch = pitch;
            return;
        }
        double horizontal = desiredCameraDistance * Math.cos(pitch);
        double cameraY = Math.max(CAMERA_RADIUS, targetY + desiredCameraDistance * Math.sin(pitch));
        // At very low angles, keep the camera outside the player cube too.
        double vertical = cameraY - targetY;
        // Once zoomed into the cube, keep the camera at its center for first-person view.
        if (desiredCameraDistance > NEAR && Math.abs(vertical) < 0.5 + CAMERA_RADIUS) {
            horizontal = Math.max(horizontal,
                    (0.5 + CAMERA_RADIUS + 0.02) /
                    Math.max(Math.abs(Math.sin(yaw - cubeYaw)), Math.abs(Math.cos(yaw - cubeYaw))));
        }
        double[] start = {x, targetY, z};
        double[] offset = {Math.sin(yaw) * horizontal, vertical, Math.cos(yaw) * horizontal};
        double fraction = 1;
        for (double[] box : cameraObstacles) {
            fraction = Math.min(fraction, cameraHit(start, offset, box));
        }
        // Pull inward before a wall, then restore the desired distance when clear.
        double length = Math.sqrt(horizontal * horizontal + vertical * vertical);
        if (fraction < 1) fraction = Math.max(0, fraction - 0.02 / length);
        actualCameraDistance = Math.max(NEAR, length * fraction);
        actualCameraPitch = Math.atan2(vertical, horizontal);
    }

    // Swept camera volume: intersect the entire orbit arm with an expanded box.
    // Checking the segment prevents passing through thin objects between frames.
    private double cameraHit(double[] start, double[] offset, double[] box) {
        double entry = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double low = box[axis] - CAMERA_RADIUS, high = box[axis + 3] + CAMERA_RADIUS;
            if (Math.abs(offset[axis]) < 1e-10) {
                if (start[axis] < low || start[axis] > high) return 1;
            } else {
                double a = (low - start[axis]) / offset[axis];
                double b = (high - start[axis]) / offset[axis];
                entry = Math.max(entry, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (entry > exit) return 1;
            }
        }
        return entry;
    }

    // Orbit the cube's center using the collision-adjusted camera position.
    private double[] cameraPoint(double px, double py, double pz) {
        px -= x;
        py -= cameraTargetY;
        pz -= z;
        double rotatedX = Math.cos(yaw) * px - Math.sin(yaw) * pz;
        double rotatedZ = Math.sin(yaw) * px + Math.cos(yaw) * pz;
        double cy = Math.cos(actualCameraPitch), sy = Math.sin(actualCameraPitch);
        double cameraY = cy * py - sy * rotatedZ;
        double depth = actualCameraDistance - sy * py - cy * rotatedZ;
        return new double[]{rotatedX, cameraY, depth};
    }

    private double[] screenPoint(double[] point) {
        double focal = Math.min(getWidth(), getHeight()) * 1.05;
        return new double[]{getWidth() / 2.0 + point[0] * focal / point[2],
                getHeight() * 0.55 - point[1] * focal / point[2]};
    }

    private double[] nearIntersection(double[] a, double[] b) {
        double t = (NEAR - a[2]) / (b[2] - a[2]);
        return new double[]{a[0] + t * (b[0] - a[0]),
                a[1] + t * (b[1] - a[1]), NEAR};
    }

    private Path2D polygon(double[][] vertices) {
        Path2D path = new Path2D.Double();
        java.util.List<double[]> clipped = new ArrayList<>();
        double[] last = vertices[vertices.length - 1];
        double[] a = cameraPoint(last[0], last[1], last[2]);
        for (double[] vertex : vertices) {
            double[] b = cameraPoint(vertex[0], vertex[1], vertex[2]);
            if ((a[2] >= NEAR) != (b[2] >= NEAR)) clipped.add(nearIntersection(a, b));
            if (b[2] >= NEAR) clipped.add(b);
            a = b;
        }
        if (clipped.size() < 3) return path;
        for (int i = 0; i < clipped.size(); i++) {
            double[] p = screenPoint(clipped.get(i));
            if (i == 0) path.moveTo(p[0], p[1]); else path.lineTo(p[0], p[1]);
        }
        path.closePath();
        return path;
    }

    private void line(Graphics2D g, double ax, double az, double bx, double bz) {
        double[] a = cameraPoint(ax, 0, az), b = cameraPoint(bx, 0, bz);
        if (a[2] < NEAR && b[2] < NEAR) return;
        if (a[2] < NEAR) a = nearIntersection(a, b);
        else if (b[2] < NEAR) b = nearIntersection(a, b);
        a = screenPoint(a);
        b = screenPoint(b);
        g.draw(new java.awt.geom.Line2D.Double(a[0], a[1], b[0], b[1]));
    }

    private double[][] cubeVertices() {
        double[][] vertices = {{-.5,0,-.5},{.5,0,-.5},{.5,0,.5},{-.5,0,.5},
                {-.5,1,-.5},{.5,1,-.5},{.5,1,.5},{-.5,1,.5}};
        double cosine = Math.cos(cubeYaw), sine = Math.sin(cubeYaw);
        for (double[] vertex : vertices) {
            double localX = vertex[0], localZ = vertex[2];
            // Rotate around the upright Y axis to match camera-relative forward movement.
            vertex[0] = x + cosine * localX + sine * localZ;
            vertex[1] += y;
            vertex[2] = z - sine * localX + cosine * localZ;
        }
        return vertices;
    }

    private static final class RenderFace {
        final double[][] vertices;
        final Color color;
        final boolean effect, soft;
        RenderFace(double[][] vertices, Color color) { this(vertices,color,false,false); }
        RenderFace(double[][] vertices, Color color, boolean effect, boolean soft) {
            this.vertices=vertices;this.color=color;this.effect=effect;this.soft=soft;
        }
    }

    private void addParticleFaces(java.util.List<RenderFace> scene) {
        double[] right={Math.cos(yaw),0,-Math.sin(yaw)};
        double[] up={-Math.sin(yaw)*Math.sin(actualCameraPitch),Math.cos(actualCameraPitch),
                -Math.cos(yaw)*Math.sin(actualCameraPitch)};
        for(ParticleSystem.Particle p:particles.particles) {
            if(p.delay>0 || p.life<=0) continue;
            double fade=Math.max(0,Math.min(1,p.life/p.duration));
            int alpha=(int)((p.shape==ParticleSystem.Shape.DUST?90:220)*fade);
            if(alpha<2) continue;
            Color color=new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),alpha);
            if(p.shape==ParticleSystem.Shape.CUBE) {
                addBox(scene,p.x,p.y,p.z,p.size,p.size,p.size,p.angle,color);
            } else if(p.shape==ParticleSystem.Shape.RING) {
                double inner=Math.max(0,p.size-.055),outer=p.size+.055;
                for(int i=0;i<24;i++) {
                    double a=i*Math.PI*2/24,b=(i+1)*Math.PI*2/24;
                    scene.add(new RenderFace(new double[][]{
                            {p.x+Math.cos(a)*inner,p.y,p.z+Math.sin(a)*inner},
                            {p.x+Math.cos(a)*outer,p.y,p.z+Math.sin(a)*outer},
                            {p.x+Math.cos(b)*outer,p.y,p.z+Math.sin(b)*outer},
                            {p.x+Math.cos(b)*inner,p.y,p.z+Math.sin(b)*inner}},color,true,false));
                }
            } else if(p.shape==ParticleSystem.Shape.SPARK) {
                double tx=p.x-p.vx*.06,ty=p.y-p.vy*.06-.08,tz=p.z-p.vz*.06;
                double width=p.size*.4;
                scene.add(new RenderFace(new double[][]{
                        {p.x-right[0]*width,p.y,p.z-right[2]*width},
                        {p.x+right[0]*width,p.y,p.z+right[2]*width},
                        {tx+right[0]*width,ty,tz+right[2]*width},
                        {tx-right[0]*width,ty,tz-right[2]*width}},color,true,false));
            } else {
                int count=p.shape==ParticleSystem.Shape.SHARD?3:4;
                double[][] vertices=new double[count][3];
                for(int i=0;i<count;i++) {
                    double angle=p.angle+i*Math.PI*2/count;
                    double rx=Math.cos(angle)*p.size,uy=Math.sin(angle)*p.size;
                    vertices[i]=new double[]{p.x+right[0]*rx+up[0]*uy,p.y+up[1]*uy,p.z+right[2]*rx+up[2]*uy};
                }
                scene.add(new RenderFace(vertices,color,true,p.shape==ParticleSystem.Shape.DUST));
            }
        }
    }

    private void addBox(java.util.List<RenderFace> scene, double bx, double by, double bz,
            double width, double height, double depth, double rotation, Color color) {
        double[][] vertices = {{-.5,0,-.5},{.5,0,-.5},{.5,0,.5},{-.5,0,.5},
                {-.5,1,-.5},{.5,1,-.5},{.5,1,.5},{-.5,1,.5}};
        for (double[] vertex : vertices) {
            double vx = vertex[0] * width, vz = vertex[2] * depth;
            vertex[0] = bx + Math.cos(rotation) * vx + Math.sin(rotation) * vz;
            vertex[1] = by + vertex[1] * height;
            vertex[2] = bz - Math.sin(rotation) * vx + Math.cos(rotation) * vz;
        }
        int[][] faces = {{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{4,5,6,7},{0,3,2,1}};
        double[] shades = {.68, .8, .9, .74, 1, .5};
        for (int i = 0; i < faces.length; i++) {
            double[][] face = new double[4][];
            for (int j = 0; j < 4; j++) face[j] = vertices[faces[i][j]];
            scene.add(new RenderFace(face, new Color((int) (color.getRed() * shades[i]),
                    (int) (color.getGreen() * shades[i]), (int) (color.getBlue() * shades[i]), color.getAlpha())));
        }
    }

    private void drawWave(Graphics2D g, double time, double waveX, double waveZ) {
        if (time > 0) {
            double progress = 1 - time / .7;
            g.setStroke(new BasicStroke(3));
            for (double radius : new double[]{CombatSystem.INNER_RADIUS, CombatSystem.OUTER_RADIUS}) {
                double[][] ring = new double[80][3];
                for (int i = 0; i < ring.length; i++) {
                    double angle = i * Math.PI * 2 / ring.length;
                    ring[i] = new double[]{waveX + Math.cos(angle) * radius * progress,
                            .03, waveZ + Math.sin(angle) * radius * progress};
                }
                g.setColor(new Color(110, 222, 255, (int) (220 * (1 - progress))));
                g.draw(polygon(ring));
            }
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawCombatWorld(Graphics2D g) {
        drawWave(g, combat.waveTime, combat.waveX, combat.waveZ);
        if (lobbyClient != null && appliedSnapshot != null) for (LobbyNetwork.State player : appliedSnapshot.players)
            if (player.id != lobbyClient.id) drawWave(g, player.waveTime, player.waveX, player.waveZ);
        java.util.List<RenderFace> scene = new ArrayList<>();
        for (CombatSystem.Dummy dummy : combat.dummies) {
            if (!dummy.alive()) continue;
            Color color = dummy.flash > 0 ? new Color(255, 230, 180)
                    : dummy.slowTime > 0 ? new Color(108, 177, 213) : new Color(220, 122, 103);
            double height = combat.active == CombatSystem.Move.PUMMEL && dummy.damageTime > .8 ? .7 : 1.5;
            addBox(scene, dummy.x, dummy.lift, dummy.z, 1, height, 1, 0, color);
        }
        if (lobbyClient != null && appliedSnapshot != null) for (LobbyNetwork.State player : appliedSnapshot.players) {
            if (player.id == lobbyClient.id || player.health <= 0) continue;
            Color color = player.flash > 0 ? new Color(255, 232, 172)
                    : Color.getHSBColor((player.id * .21f) % 1, .55f, .95f);
            addBox(scene, player.x, player.y, player.z, 1, 1, 1, player.yaw, color);
            if (player.className.equals("Gauntlet")) {
                double extension = player.active < 0 ? 0 : .6 * Math.sin(Math.min(1, player.attackTime / .36) * Math.PI);
                for (int side : new int[]{-1, 1}) {
                    double localX = side * .65, localZ = -.2 - extension;
                    addBox(scene, player.x + Math.cos(player.yaw) * localX + Math.sin(player.yaw) * localZ,
                            player.y + .35, player.z - Math.sin(player.yaw) * localX + Math.cos(player.yaw) * localZ,
                            .38, .45, .5, player.yaw, new Color(232, 177, 87));
                }
            }
        }
        if (thirdPerson && (lobbyClient == null || combat.health > 0)) {
            int alpha = (int) Math.round(255 * Math.min(1, actualCameraDistance / 4));
            addBox(scene, x, y, z, 1, 1, 1, cubeYaw, new Color(97, 206, 246, alpha));
            if (combat.equipped) {
                double extension = combat.active == null ? 0 : .6 * Math.sin(Math.min(1, combat.attackTime / .36) * Math.PI);
                for (int side : new int[]{-1, 1}) {
                    double localX = side * .65, localZ = -.2 - extension;
                    addBox(scene, x + Math.cos(cubeYaw) * localX + Math.sin(cubeYaw) * localZ,
                            y + .35, z - Math.sin(cubeYaw) * localX + Math.cos(cubeYaw) * localZ,
                            .38, .45, .5, cubeYaw, new Color(232, 177, 87, alpha));
                }
            }
        }
        addParticleFaces(scene);
        scene.sort(Comparator.comparingDouble((RenderFace face) -> {
            double total = 0;
            for (double[] vertex : face.vertices) total += cameraPoint(vertex[0], vertex[1], vertex[2])[2];
            return -total / face.vertices.length;
        }));
        for (RenderFace face : scene) {
            Path2D path = polygon(face.vertices);
            g.setColor(face.color);
            if(face.soft) {
                java.awt.geom.Rectangle2D bounds=path.getBounds2D();
                double radius=Math.max(bounds.getWidth(),bounds.getHeight())/2;
                if(radius>.1 && radius<getWidth()*3) {
                    g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(bounds.getCenterX(),bounds.getCenterY()),
                            (float)radius,new float[]{0,1},new Color[]{face.color,new Color(face.color.getRed(),
                                    face.color.getGreen(),face.color.getBlue(),0)}));
                    g.fill(new java.awt.geom.Ellipse2D.Double(bounds.getX(),bounds.getY(),bounds.getWidth(),bounds.getHeight()));
                }
            } else g.fill(path);
            if(!face.effect) {
                g.setColor(new Color(16, 30, 43, face.color.getAlpha()));
                g.draw(path);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        if (lobbyClient != null && appliedSnapshot != null) for (LobbyNetwork.State player : appliedSnapshot.players) {
            if (player.id == lobbyClient.id || player.health <= 0) continue;
            double[] point = cameraPoint(player.x, player.y + 1.5, player.z);
            if (point[2] < NEAR) continue;
            double[] screen = screenPoint(point);
            int sx = (int) screen[0], sy = (int) screen[1];
            String label = player.name + "  " + (int) Math.ceil(player.health) + "/" + (player.className.equals("Gauntlet") ? 115 : 100);
            int width = g.getFontMetrics().stringWidth(label) + 16;
            g.setColor(new Color(15, 20, 28, 210)); g.fillRoundRect(sx - width / 2, sy - 20, width, 30, 8, 8);
            g.setColor(Color.WHITE); g.drawString(label, sx - width / 2 + 8, sy);
        }
        for (int i = 0; i < combat.dummies.size(); i++) {
            CombatSystem.Dummy dummy = combat.dummies.get(i);
            double[] point = cameraPoint(dummy.x, dummy.lift + 1.9, dummy.z);
            if (point[2] < NEAR) continue;
            double[] screen = screenPoint(point);
            int sx = (int) screen[0], sy = (int) screen[1];
            if (sx < -100 || sx > getWidth() + 100 || sy < -100 || sy > getHeight() + 100) continue;
            String label = dummy.alive() ? "Dummy " + (i + 1) + "  " + (int) Math.ceil(dummy.health) + "/" + (int) dummy.maxHealth
                    : "Respawn " + (int) Math.ceil(dummy.respawnTime) + "s";
            int labelWidth = Math.max(132, g.getFontMetrics().stringWidth(label) + 16);
            int labelLeft = sx - labelWidth / 2;
            g.setColor(new Color(15, 20, 28, 215));
            g.fillRoundRect(labelLeft, sy - 22, labelWidth, 66, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(label, labelLeft + 8, sy - 5);
            g.setColor(new Color(226, 109, 108));
            g.fillRect(labelLeft + 8, sy + 2, (int) ((labelWidth - 16) * dummy.health / dummy.maxHealth), 7);
            g.setColor(new Color(135, 214, 249));
            if (dummy.slowTime > 0) g.drawString("Slow " + (int) (dummy.slowAmount * 100) + "%  "
                    + String.format(Locale.ROOT, "%.1fs", dummy.slowTime), sx - 58, sy + 28);
            if (dummy.damageTime > 0) {
                g.setColor(new Color(255, 221, 138));
                g.drawString("-" + Math.round(dummy.lastDamage), sx - 12, sy - 35 - (int) ((1 - dummy.damageTime) * 35));
            }
        }
    }

    private void drawCombatHud(Graphics2D g) {
        int top = getHeight() - 210;
        int columnX = Math.max(325, getWidth() - 255);
        g.setColor(new Color(15, 20, 28, 215));
        g.fillRoundRect(columnX - 10, top, 240, 142, 12, 12);
        int row = top + 25;
        for (CombatSystem.Move move : CombatSystem.Move.values()) {
            double remaining = combat.cooldowns[move.ordinal()];
            g.setColor(!combat.equipped ? new Color(140, 149, 160) : remaining > 0
                    ? new Color(244, 193, 108) : new Color(125, 231, 182));
            g.drawString(move.label + (!combat.equipped ? "  --" : remaining > 0
                    ? String.format(Locale.ROOT, "  %.1fs", remaining) : "  Ready"), columnX, row);
            row += 29;
        }
        if (!paused) {
            g.setColor(new Color(245, 245, 245, 190));
            g.drawLine(getWidth() / 2 - 5, getHeight() / 2, getWidth() / 2 + 5, getHeight() / 2);
            g.drawLine(getWidth() / 2, getHeight() / 2 - 5, getWidth() / 2, getHeight() / 2 + 5);
        }
    }

    private void drawDebugOverlays(Graphics2D g) {
        if (showHitboxes) {
            double direction = combat.active != null ? combat.facing : cubeYaw;
            g.setStroke(new BasicStroke(2));
            for (double radius : new double[]{CombatSystem.INNER_RADIUS, CombatSystem.OUTER_RADIUS}) {
                double[][] ring = new double[80][3];
                for (int i = 0; i < ring.length; i++) {
                    double angle = i * Math.PI * 2 / ring.length;
                    ring[i] = new double[]{x + Math.cos(angle) * radius, .04, z + Math.sin(angle) * radius};
                }
                g.setColor(new Color(100, 215, 255, 180));
                g.draw(polygon(ring));
            }
            for (double radius : new double[]{1.8, 2}) {
                double[][] cone = new double[34][3];
                cone[0] = new double[]{x, y + .1, z};
                double halfAngle = Math.acos(.65);
                for (int i = 1; i < cone.length; i++) {
                    double angle = direction - halfAngle + 2 * halfAngle * (i - 1) / (cone.length - 2);
                    cone[i] = new double[]{x - Math.sin(angle) * radius, y + .1, z - Math.cos(angle) * radius};
                }
                g.setColor(radius == 2 ? new Color(255, 220, 100) : new Color(243, 145, 110));
                g.draw(polygon(cone));
            }
            double dx = -Math.sin(direction), dz = -Math.cos(direction);
            g.setColor(new Color(197, 159, 251));
            g.draw(polygon(new double[][]{{x - dz * .95, .05, z + dx * .95},
                    {x + dx * 3.6 - dz * .95, .05, z + dz * 3.6 + dx * .95},
                    {x + dx * 3.6 + dz * .95, .05, z + dz * 3.6 - dx * .95},
                    {x + dz * .95, .05, z - dx * .95}}));
            java.util.List<RenderFace> boxes = new ArrayList<>();
            for (CombatSystem.Dummy dummy : combat.dummies) if (dummy.alive())
                addBox(boxes, dummy.x, dummy.lift, dummy.z, 1, 1.5, 1, 0, Color.GREEN);
            g.setColor(new Color(136, 245, 154, 150));
            for (RenderFace face : boxes) g.draw(polygon(face.vertices));
            g.setStroke(new BasicStroke(1));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(Color.WHITE);
            g.drawString("Ranges: yellow Punch | orange Pummel | blue Shockwave | purple Hay-maker", 22, 106);
        }
        if (showStats) {
            int sx = Math.max(22, getWidth() - 322);
            g.setColor(new Color(15, 20, 28, 220));
            g.fillRoundRect(sx - 10, 112, 310, 198, 10, 10);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            g.setColor(new Color(173, 231, 243));
            String[] lines = {
                String.format(Locale.ROOT, "Position: %.2f %.2f %.2f", x, y, z),
                String.format(Locale.ROOT, "Vertical velocity: %.2f", verticalSpeed),
                String.format(Locale.ROOT, "Speed scale: %.2f", speedMultiplier),
                String.format(Locale.ROOT, "Time scale: %.2f", timeScale),
                "God: " + combat.godMode + " | Cooldowns: " + combat.cooldownsEnabled,
                "Targets: " + combat.dummies.size() + " | View: " + (thirdPerson ? "third" : "first"),
                "Ability: " + (combat.active == null ? "none" : combat.active),
                String.format(Locale.ROOT, "Attack %.2fs | Recovery %.2fs", combat.attackTime, combat.endLag),
                "Particles: " + particles.particles.size() + "/" + particles.quality().limit + " " + particles.quality()
            };
            for (int i = 0; i < lines.length; i++) g.drawString(lines[i], sx, 133 + i * 20);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        resolveCamera();
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(64, 76, 86));
        g.fill(polygon(new double[][]{{-EDGE,0,-EDGE},{EDGE,0,-EDGE},
                {EDGE,0,EDGE},{-EDGE,0,EDGE}}));
        g.setColor(new Color(89, 104, 114));
        for (int i = -(int) EDGE; i <= EDGE; i++) {
            line(g, i, -EDGE, i, EDGE);
            line(g, -EDGE, i, EDGE, i);
        }
        drawCombatWorld(g);
        drawDebugOverlays(g);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.setColor(new Color(230,236,242));
        if (showDebugHud) {
            g.drawString(String.format(Locale.ROOT, "Speed %.1f u/s  |  Max %.1f  |  Defense 0%%", combat.speed,
                    SPRINT_SPEED * speedMultiplier + (combat.active == CombatSystem.Move.HAYMAKER ? 12 : 0)), 22, 78);
            g.drawString(String.format(Locale.ROOT, "Damage modifier %.2fx%s", combat.multiplier,
                    combat.streakTime > 0 ? String.format(Locale.ROOT, "  (%.1fs)", combat.streakTime) : ""), 22, 102);
        }
        if (lobbyClient != null && appliedSnapshot != null) {
            g.setColor(new Color(160, 230, 192));
            g.drawString("LAN match: " + appliedSnapshot.players.size() + "/8 players  |  F2: roster / leave", 22, 126);
        }
        String classLabel = "Class: " + (equippedClass == null ? "None" : equippedClass);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(classLabel, Math.max(22, getWidth() - g.getFontMetrics().stringWidth(classLabel) - 22), 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        int barX = 22, barWidth = 250;
        int healthBarY = getHeight() - 102;
        int staminaBarY = getHeight() - 47;
        drawResourceBar(g, "HEALTH", combat.health, combat.maxHealth(), barX, healthBarY,
                barWidth, new Color(226, 87, 91), combat.health <= 0 ? "DEFEATED" : null);
        drawResourceBar(g, "STAMINA", stamina, MAX_STAMINA, barX, staminaBarY,
                barWidth, exhausted ? new Color(245, 158, 66) : new Color(87, 213, 151),
                exhausted ? "RECOVERING" : null);
        drawCombatHud(g);
        if (paused) {
            g.setColor(new Color(0, 0, 0, 155));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        g.dispose();
    }

    private void drawResourceBar(Graphics2D g, String label, double value, double maximum, int x, int y,
            int width, Color fill, String status) {
        int amount = (int) Math.round(Math.max(0, Math.min(1, value / Math.max(1, maximum))) * width);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(new Color(230, 236, 242));
        g.drawString(label + "  " + (int) Math.ceil(Math.max(0, value)) + " / " + (int) maximum
                + (status == null ? "" : "  " + status), x, y - 5);
        g.setColor(new Color(12, 17, 24, 235));
        g.fillRoundRect(x, y, width, 18, 8, 8);
        if (amount > 0) {
            g.setColor(fill);
            g.fillRoundRect(x + 2, y + 2, Math.max(0, amount - 4), 14, 6, 6);
        }
        g.setColor(new Color(180, 194, 208));
        g.drawRoundRect(x, y, width, 18, 8, 8);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Cube Movement");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            CubeMovement demo = new CubeMovement();
            window.setContentPane(demo);
            window.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent e) {
                    demo.escapeHeld = false;
                    demo.classKeyHeld = false;
                    demo.consoleKeyHeld = false;
                    demo.setPaused(true);
                }
            });
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    demo.connectionGeneration++;
                    if (demo.lobbyClient != null) demo.lobbyClient.close();
                    if (demo.lobbyServer != null) demo.lobbyServer.close();
                }
            });
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            demo.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> demo.setPaused(false));
        });
    }
}
