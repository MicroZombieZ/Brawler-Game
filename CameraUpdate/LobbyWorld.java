import java.util.*;

/** Authoritative LAN arena. Accessed only while holding the world's monitor. */
final class LobbyWorld {
    static final int MAX_PLAYERS = 8;
    final Map<Integer, Player> players = new LinkedHashMap<>();
    final CombatSystem arena = new CombatSystem();
    boolean started;
    private int nextId = 1;
    final Deque<GameEffect> effects = new ArrayDeque<>();
    long lastEffectId;
    private double effectTime;

    private void emit(GameEffect effect) {
        effect.id = ++lastEffectId; effect.time = effectTime;
        if (effects.size() >= 128) effects.removeFirst();
        effects.addLast(effect);
    }
    private void drainEffects(CombatSystem source) {
        for (GameEffect effect : source.effects) emit(effect);
        source.effects.clear();
    }

    static final class Player {
        final int id;
        final String name;
        final boolean host;
        final CombatSystem combat = new CombatSystem();
        final CombatSystem.Dummy body = new CombatSystem.Dummy(0, 0);
        String className = "";
        double x, y, z, verticalSpeed, yaw, cubeYaw, stamina = 100, respawnTime;
        double dx, dz;
        boolean sprint, exhausted, pounding;
        int jumps;
        Player(int id, String name, boolean host) {
            this.id = id; this.name = name; this.host = host;
            body.playerBody = true;
            combat.externalTargetUpdates = true;
            combat.dummies.clear();
            spawn();
        }
        void spawn() {
            x = -6 + ((id - 1) % 4) * 4;
            z = 5 + ((id - 1) / 4) * 2;
            y = verticalSpeed = 0;
            jumps = 0; pounding = exhausted = false;
            stamina = 100;
            combat.cancelAttack(); combat.health = combat.maxHealth();
            combat.multiplier = 1; combat.streakTime = 0;
            body.x = x; body.z = z; body.lift = 0;
            body.health = combat.health; body.maxHealth = combat.maxHealth();
            body.vx = body.vz = body.slowTime = body.slowAmount = 0;
        }
        void move(double dt, boolean held) {
            double turn = Math.IEEEremainder(yaw - cubeYaw, Math.PI * 2);
            cubeYaw = Math.IEEEremainder(cubeYaw + turn * (1 - Math.exp(-10 * dt)), Math.PI * 2);
            if (held) {
                verticalSpeed = 0; combat.speed = 0;
                stamina = Math.min(100, stamina + 15 * dt);
                if (stamina >= 25) exhausted = false;
                return;
            }
            y += verticalSpeed * dt - 9 * dt * dt;
            verticalSpeed -= 18 * dt;
            if (y <= 0) {
                if (jumps > 0 || pounding) combat.effect(pounding ? GameEffect.Kind.GROUND_POUND : GameEffect.Kind.LAND,
                        x, 0, z, Math.abs(verticalSpeed), 1);
                y = verticalSpeed = 0; jumps = 0; pounding = false;
            }
            double length = Math.hypot(dx, dz);
            double oldX = x, oldZ = z;
            boolean sprinting = false;
            if (length > 0) {
                double sprintTime = sprint && !exhausted ? Math.min(dt, stamina / 20) : 0;
                double speed = (4 + 3 * sprintTime / dt) * (1 - body.slowAmount);
                x = bound(x + (Math.cos(yaw) * dx + Math.sin(yaw) * dz) / length * speed * dt);
                z = bound(z + (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length * speed * dt);
                sprinting = sprintTime > 0 && Math.hypot(x - oldX, z - oldZ) > 1e-9;
                if (sprinting) {
                    stamina = Math.max(0, stamina - 20 * sprintTime);
                    if (stamina < 1e-9) { stamina = 0; exhausted = true; }
                }
            }
            if (!sprinting) {
                stamina = Math.min(100, stamina + 15 * dt);
                if (stamina >= 25) exhausted = false;
            }
            combat.speed = Math.hypot(x - oldX, z - oldZ) / dt;
        }
    }

    synchronized Player add(String name, boolean host) {
        if (players.size() >= MAX_PLAYERS) throw new IllegalArgumentException("Lobby is full (8 players).");
        if (host && players.values().stream().anyMatch(p -> p.host)) throw new IllegalArgumentException("Host already connected.");
        Player player = new Player(nextId++, name, host);
        players.put(player.id, player);
        return player;
    }
    synchronized void remove(int id) {
        Player removed = players.remove(id);
        if (removed == null) return;
        for (Player player : players.values()) if (player.combat.grabbedTarget() == removed.body) player.combat.cancelAttack();
    }
    synchronized void input(int id, double dx, double dz, double yaw, boolean sprint) {
        Player player = players.get(id);
        if (player == null) return;
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(yaw)) return;
        player.dx = Math.max(-1, Math.min(1, dx)); player.dz = Math.max(-1, Math.min(1, dz));
        player.yaw = Math.IEEEremainder(yaw, Math.PI * 2); player.sprint = sprint;
    }
    private boolean held(Player player) {
        return players.values().stream().anyMatch(other -> other.combat.grabbedTarget() == player.body);
    }
    synchronized void action(int id, int action, String argument) {
        Player player = players.get(id);
        if (player == null) return;
        if (action == 4 && player.host) { started = true; return; }
        if (action == 3) {
            if (player.combat.locked() || player.combat.health <= 0 || held(player)) return;
            if (!Arrays.asList("", "Gauntlet", "Mage", "Ranger", "Rogue").contains(argument)) return;
            player.className = argument; player.combat.equip(argument.equals("Gauntlet"));
            player.body.health = player.combat.health; player.body.maxHealth = player.combat.maxHealth();
            return;
        }
        if (!started || player.combat.health <= 0 || held(player)) return;
        if (action == 0) {
            try { player.combat.attack(CombatSystem.Move.valueOf(argument), player.x, player.y, player.z, player.cubeYaw); }
            catch (IllegalArgumentException ignored) { }
        } else if (action == 1 && !player.pounding && player.jumps < 2) {
            player.verticalSpeed = 7; player.jumps++; player.pounding = false;
            player.combat.effect(player.jumps == 1 ? GameEffect.Kind.TAKEOFF : GameEffect.Kind.DOUBLE_JUMP,
                    player.x, player.y, player.z, 1, 1);
        } else if (action == 2 && player.y > 0 && !player.pounding) {
            player.verticalSpeed = -24; player.pounding = true;
        }
    }

    synchronized void tick(double dt) {
        if (!started) return;
        effectTime += dt;
        while (!effects.isEmpty() && effects.peekFirst().time < effectTime - 2) effects.removeFirst();
        for (CombatSystem.Dummy dummy : arena.dummies) arena.updateDummy(dummy, dt);
        for (Player player : players.values()) {
            if (player.combat.health <= 0) {
                player.respawnTime -= dt;
                if (player.respawnTime <= 0) {
                    player.spawn();
                    player.combat.effect(GameEffect.Kind.RESPAWN, player.x, 0, player.z, 1, 1);
                }
                continue;
            }
            player.move(dt, held(player));
            player.body.x = player.x; player.body.z = player.z; player.body.lift = player.y;
            // Player bodies use the same knockback integration as dummies, once per tick.
            if (player.body.alive()) arena.updateDummy(player.body, dt);
            player.x = player.body.x; player.z = player.body.z;
        }
        for (Player player : players.values()) {
            player.combat.dummies.clear(); player.combat.dummies.addAll(arena.dummies);
            for (Player other : players.values()) if (other != player) player.combat.dummies.add(other.body);
            if (player.combat.health <= 0) continue;
            player.combat.update(dt, player.body.x, player.body.lift, player.body.z, player.cubeYaw);
            player.x = player.body.x = player.combat.playerX;
            player.z = player.body.z = player.combat.playerZ;
        }
        for (Player player : players.values()) {
            player.x = player.body.x; player.z = player.body.z; player.y = player.body.lift;
            if (player.combat.health > 0 && player.body.health <= 0) {
                player.combat.damagePlayer(player.combat.health);
                player.respawnTime = 3;
            } else player.combat.health = player.body.health;
            drainEffects(player.combat);
        }
        drainEffects(arena);
    }
    private static double bound(double value) { return Math.max(-9.29, Math.min(9.29, value)); }
}
