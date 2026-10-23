import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded binary TCP protocol. No Java object deserialization or external dependencies. */
final class LobbyNetwork {
    static final int DEFAULT_PORT = 24560;
    private static final String VERSION = "CameraUpdate-LAN-3";
    private static final int HELLO = 1, WELCOME = 2, SNAPSHOT = 3, INPUT = 4, ACTION = 5, ERROR = 6;
    private static final int MAX_FRAME = 262144;
    interface Write { void write(DataOutputStream out) throws IOException; }
    static byte[] packet(int type, Write writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes); out.writeByte(type); writer.write(out);
        return bytes.toByteArray();
    }
    private static DataInputStream read(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > MAX_FRAME) throw new IOException("Invalid network message.");
        byte[] bytes = new byte[length]; input.readFully(bytes);
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    private static void send(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); thread.start(); return thread;
    }
    private static String cleanName(String name) {
        String value = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (value.isEmpty()) value = "Player";
        return value.substring(0, Math.min(20, value.length()));
    }
    static final class State {
        int id;
        boolean host, exhausted;
        String name, className;
        double x, y, z, yaw, stamina, health, multiplier, streak, recovery, attackTime, speed, respawn;
        double slowTime, slowAmount, flash, waveTime, waveX, waveZ, facing, knockX, knockZ;
        int active;
        final double[] cooldowns = new double[4];
    }
    static final class Snapshot {
        boolean started;
        final List<State> players = new ArrayList<>();
        final List<CombatSystem.Dummy> dummies = new ArrayList<>();
        final List<GameEffect> effects = new ArrayList<>();
        long lastEffectId;
        State player(int id) { return players.stream().filter(p -> p.id == id).findFirst().orElse(null); }
    }
    private static byte[] snapshot(LobbyWorld world) throws IOException {
        synchronized (world) {
            return packet(SNAPSHOT, out -> {
                out.writeBoolean(world.started); out.writeInt(world.players.size());
                for (LobbyWorld.Player p : world.players.values()) {
                    CombatSystem c = p.combat;
                    out.writeInt(p.id); out.writeBoolean(p.host); out.writeUTF(p.name); out.writeUTF(p.className);
                    for (double v : new double[]{p.x,p.y,p.z,p.cubeYaw,p.stamina,c.health,c.multiplier,c.streakTime,
                            c.endLag,c.attackTime,c.speed,p.respawnTime,p.body.slowTime,p.body.slowAmount,p.body.flash,
                            c.waveTime,c.waveX,c.waveZ,c.facing,p.body.vx,p.body.vz}) out.writeDouble(v);
                    out.writeBoolean(p.exhausted); out.writeInt(c.active == null ? -1 : c.active.ordinal());
                    for (double v : c.cooldowns) out.writeDouble(v);
                }
                out.writeInt(world.arena.dummies.size());
                for (CombatSystem.Dummy d : world.arena.dummies) {
                    for (double v : new double[]{d.x,d.z,d.health,d.maxHealth,d.slowTime,d.slowAmount,d.flash,
                            d.damageTime,d.lastDamage,d.respawnTime,d.lift}) out.writeDouble(v);
                }
                out.writeLong(world.lastEffectId); out.writeInt(world.effects.size());
                for (GameEffect effect : world.effects) {
                    out.writeLong(effect.id); out.writeByte(effect.kind.ordinal());
                    out.writeDouble(effect.x); out.writeDouble(effect.y); out.writeDouble(effect.z);
                    out.writeDouble(effect.facing); out.writeDouble(effect.strength); out.writeInt(effect.variant);
                }
            });
        }
    }
    private static Snapshot decodeSnapshot(DataInputStream in) throws IOException {
        Snapshot result = new Snapshot(); result.started = in.readBoolean();
        int count = in.readInt(); if (count < 0 || count > LobbyWorld.MAX_PLAYERS) throw new IOException("Invalid player count.");
        for (int i = 0; i < count; i++) {
            State p = new State(); p.id = in.readInt(); p.host = in.readBoolean(); p.name = in.readUTF(); p.className = in.readUTF();
            p.x=in.readDouble(); p.y=in.readDouble(); p.z=in.readDouble(); p.yaw=in.readDouble(); p.stamina=in.readDouble();
            p.health=in.readDouble(); p.multiplier=in.readDouble(); p.streak=in.readDouble(); p.recovery=in.readDouble();
            p.attackTime=in.readDouble(); p.speed=in.readDouble(); p.respawn=in.readDouble(); p.slowTime=in.readDouble();
            p.slowAmount=in.readDouble(); p.flash=in.readDouble(); p.waveTime=in.readDouble(); p.waveX=in.readDouble(); p.waveZ=in.readDouble();
            p.facing=in.readDouble();
            p.knockX=in.readDouble(); p.knockZ=in.readDouble();
            p.exhausted=in.readBoolean(); p.active=in.readInt();
            if (p.active < -1 || p.active >= 4) throw new IOException("Invalid ability.");
            for (int j = 0; j < 4; j++) p.cooldowns[j] = in.readDouble();
            result.players.add(p);
        }
        count=in.readInt(); if (count < 0 || count > 256) throw new IOException("Invalid target count.");
        for (int i = 0; i < count; i++) {
            CombatSystem.Dummy d = new CombatSystem.Dummy(in.readDouble(),in.readDouble());
            d.health=in.readDouble(); d.maxHealth=in.readDouble(); d.slowTime=in.readDouble(); d.slowAmount=in.readDouble();
            d.flash=in.readDouble(); d.damageTime=in.readDouble(); d.lastDamage=in.readDouble(); d.respawnTime=in.readDouble(); d.lift=in.readDouble();
            result.dummies.add(d);
        }
        result.lastEffectId=in.readLong(); count=in.readInt();
        if (count < 0 || count > 128) throw new IOException("Invalid effects count.");
        for (int i=0;i<count;i++) {
            long id=in.readLong(); int kind=in.readUnsignedByte();
            if (kind>=GameEffect.Kind.values().length) throw new IOException("Invalid effect kind.");
            GameEffect effect=new GameEffect(GameEffect.Kind.values()[kind],in.readDouble(),in.readDouble(),
                    in.readDouble(),in.readDouble(),in.readDouble(),in.readInt());
            effect.id=id; result.effects.add(effect);
        }
        return result;
    }

    static final class Server implements AutoCloseable {
        final LobbyWorld world = new LobbyWorld();
        final String hostKey = UUID.randomUUID().toString();
        private final ServerSocket listener;
        private final Map<Integer, Peer> peers = new ConcurrentHashMap<>();
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread t = new Thread(task,"lobby-simulation"); t.setDaemon(true); return t;
        });
        private volatile boolean closed;
        private int ticks;
        final class Peer {
            final Socket socket;
            final int id;
            final boolean host;
            final ArrayBlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(2);
            Peer(Socket socket, int id, boolean host) { this.socket = socket; this.id = id; this.host = host; }
            void offer(byte[] bytes) { if (!outgoing.offer(bytes)) { outgoing.poll(); outgoing.offer(bytes); } }
            void close() {
                try { socket.close(); } catch (IOException ignored) { }
                peers.remove(id, this); world.remove(id);
                if (host && !closed) Server.this.close();
            }
        }
        Server(int port) throws IOException {
            listener = new ServerSocket(); listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(port));
            daemon("lobby-accept", () -> {
                while (!closed) {
                    try {
                        Socket socket = listener.accept(); socket.setSoTimeout(6000); socket.setTcpNoDelay(true);
                        daemon("lobby-handshake", () -> serve(socket));
                    } catch (IOException error) { if (!closed) close(); }
                }
            });
            timer.scheduleAtFixedRate(() -> {
                if (closed) return;
                world.tick(.02);
                if (++ticks % 2 == 0) {
                    try { byte[] state = snapshot(world); for (Peer peer : peers.values()) peer.offer(state); }
                    catch (IOException error) { close(); }
                }
            }, 20, 20, TimeUnit.MILLISECONDS);
        }
        int port() { return listener.getLocalPort(); }
        private void serve(Socket socket) {
            Peer peer = null;
            try {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                DataInputStream hello = read(input);
                if (hello.readUnsignedByte() != HELLO || !hello.readUTF().equals(VERSION))
                    throw new IllegalArgumentException("Game versions differ. Copy the same game folder to every device.");
                String name = cleanName(hello.readUTF()); boolean host = hostKey.equals(hello.readUTF());
                LobbyWorld.Player player = world.add(name, host);
                peer = new Peer(socket, player.id, player.host);
                send(output, packet(WELCOME, out -> { out.writeInt(player.id); out.writeBoolean(player.host); }));
                peers.put(player.id, peer);
                Peer connected = peer;
                daemon("lobby-writer-" + player.id, () -> {
                    try {
                        while (!closed && !connected.socket.isClosed()) {
                            byte[] next = connected.outgoing.poll(1, TimeUnit.SECONDS);
                            if (next != null) send(output, next);
                        }
                    } catch (IOException | InterruptedException ignored) { }
                    finally { connected.close(); }
                });
                while (!closed) {
                    DataInputStream message = read(input);
                    int type = message.readUnsignedByte();
                    if (type == INPUT) world.input(player.id, message.readDouble(),message.readDouble(),message.readDouble(),message.readBoolean());
                    else if (type == ACTION) world.action(player.id,message.readUnsignedByte(),message.readUTF());
                    else throw new IOException("Unexpected message.");
                }
            } catch (IllegalArgumentException error) {
                try { send(new DataOutputStream(socket.getOutputStream()),packet(ERROR,out -> out.writeUTF(error.getMessage()))); }
                catch (IOException ignored) { }
            } catch (IOException ignored) { }
            finally {
                if (peer != null) peer.close();
                else try { socket.close(); } catch (IOException ignored) { }
            }
        }
        @Override public void close() {
            if (closed) return; closed = true; timer.shutdownNow();
            try { listener.close(); } catch (IOException ignored) { }
            for (Peer peer : peers.values()) peer.close();
        }
    }

    static final class Client implements AutoCloseable {
        private final Socket socket = new Socket();
        private final DataOutputStream output;
        private final ArrayBlockingQueue<byte[]> actions = new ArrayBlockingQueue<>(64);
        volatile Snapshot snapshot;
        volatile String error;
        volatile boolean closed;
        volatile double dx, dz, yaw;
        volatile boolean sprint;
        final int id;
        final boolean host;
        Client(String address, int port, String name, String key) throws IOException {
            try {
                socket.connect(new InetSocketAddress(address, port), 4000);
                socket.setTcpNoDelay(true); socket.setSoTimeout(6000);
                output = new DataOutputStream(socket.getOutputStream());
                DataInputStream input = new DataInputStream(socket.getInputStream());
                send(output,packet(HELLO,out -> { out.writeUTF(VERSION); out.writeUTF(cleanName(name)); out.writeUTF(key); }));
                DataInputStream reply = read(input);
                int type = reply.readUnsignedByte();
                if (type == ERROR) throw new IOException(reply.readUTF());
                if (type != WELCOME) throw new IOException("Invalid lobby response.");
                id = reply.readInt(); host = reply.readBoolean();
                daemon("lobby-client-read", () -> {
                    try {
                        while (!closed) {
                            DataInputStream state = read(input);
                            if (state.readUnsignedByte() != SNAPSHOT) throw new IOException("Invalid lobby state.");
                            snapshot = decodeSnapshot(state);
                        }
                    } catch (IOException failure) { fail("Disconnected from host: " + failure.getMessage()); }
                });
                daemon("lobby-client-write", () -> {
                    try {
                        while (!closed) {
                            send(output, packet(INPUT,out -> { out.writeDouble(dx); out.writeDouble(dz); out.writeDouble(yaw); out.writeBoolean(sprint); }));
                            byte[] action;
                            while ((action = actions.poll()) != null) send(output, action);
                            Thread.sleep(20);
                        }
                    } catch (IOException | InterruptedException failure) { fail("Connection lost."); }
                });
            } catch (IOException | RuntimeException error) {
                try { socket.close(); } catch (IOException ignored) { }
                throw error;
            }
        }
        void action(int type, String argument) {
            if (closed) return;
            try { if (!actions.offer(packet(ACTION,out -> { out.writeByte(type); out.writeUTF(argument); }))) fail("Too many queued actions."); }
            catch (IOException error) { fail("Could not send action."); }
        }
        private void fail(String message) { if (!closed) { error = message; close(); } }
        @Override public void close() {
            closed = true;
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    static String localAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> values = network.getInetAddresses();
                while (values.hasMoreElements()) {
                    InetAddress address = values.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) addresses.add(address.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        return addresses.isEmpty() ? "No local IPv4 found" : String.join(", ", addresses);
    }
}
