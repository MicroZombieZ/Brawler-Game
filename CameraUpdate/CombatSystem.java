import java.util.ArrayList;
import java.util.List;

/** Combat simulation in world units and seconds, independent of Swing and rendering. */
final class CombatSystem {
    enum Move {
        PUNCH("LMB  Punch", 1.7), PUMMEL("Q  Pummel", 45),
        SHOCKWAVE("E  Shockwave", 25), HAYMAKER("F  Hay-maker", 50);
        final String label;
        final double cooldown;
        Move(String label, double cooldown) { this.label = label; this.cooldown = cooldown; }
    }

    static final double BASE_HP = 100, GAUNTLET_HP = 115, STREAK_DURATION = 6.5;
    static final double MAX_MULTIPLIER = 1.35, MULTIPLIER_STEP = .05;
    static final double INNER_RADIUS = 2.5, OUTER_RADIUS = 5;
    static final class Dummy {
        final double spawnX, spawnZ;
        double x, z, health = 100, maxHealth = 100, vx, vz, slowTime, slowAmount, flash, damageTime, lastDamage;
        double respawnTime, lift;
        boolean playerBody;
        Dummy(double x, double z) { this.x = spawnX = x; this.z = spawnZ = z; }
        boolean alive() { return health > 0; }
    }

    final List<Dummy> dummies = new ArrayList<>();
    final List<GameEffect> effects = new ArrayList<>();
    final double[] cooldowns = new double[Move.values().length];
    boolean equipped;
    boolean godMode, cooldownsEnabled = true;
    boolean externalTargetUpdates;
    double dummyMaxHealth = 100;
    double health = BASE_HP, multiplier = 1, streakTime;
    double playerX, playerY, playerZ, speed;
    Move active;
    double attackTime, endLag, facing, attackMultiplier;
    double waveTime, waveX, waveZ;
    String message = "Equip Gauntlet with R to start training";
    double messageTime = 5;
    private Dummy grabbed;
    private boolean resolved;
    private int pummelHits;

    CombatSystem() {
        resetDummies();
    }

    void resetDummies() {
        clearDummies();
        dummies.add(new Dummy(-4, -3));
        dummies.add(new Dummy(0, -3));
        dummies.add(new Dummy(4, -3));
        dummies.add(new Dummy(0, 4));
        setDummyHealth(dummyMaxHealth);
    }

    void clearDummies() {
        if (grabbed != null) finish(0);
        dummies.clear();
    }

    void spawnDummy(double x, double z) {
        Dummy dummy = new Dummy(bound(x), bound(z));
        dummy.health = dummy.maxHealth = dummyMaxHealth;
        dummies.add(dummy);
        effect(GameEffect.Kind.RESPAWN,dummy.x,0,dummy.z,1,0);
    }

    void setDummyHealth(double value) {
        dummyMaxHealth = value;
        for (Dummy dummy : dummies) {
            if (!dummy.alive()) effect(GameEffect.Kind.RESPAWN,dummy.x,dummy.lift,dummy.z,1,0);
            dummy.health = dummy.maxHealth = value;
            dummy.respawnTime = 0;
        }
    }

    void cancelAttack() { finish(0); }

    void reset() {
        finish(0);
        equipped = godMode = false;
        cooldownsEnabled = true;
        health = BASE_HP; multiplier = 1; streakTime = 0;
        attackTime = waveTime = playerX = playerY = playerZ = speed = 0;
        java.util.Arrays.fill(cooldowns, 0);
        dummyMaxHealth = 100;
        resetDummies();
        effects.clear();
        tell("Arena reset - equip Gauntlet with R");
    }

    double maxHealth() { return equipped ? GAUNTLET_HP : BASE_HP; }
    boolean locked() { return active != null || endLag > 0; }

    void equip(boolean value) {
        if (equipped == value) return;
        // Switching classes cannot heal or clear cooldowns and recovery.
        health = Math.max(1, Math.min(value ? GAUNTLET_HP : BASE_HP,
                health + (value ? 15 : -15)));
        equipped = value;
        multiplier = 1;
        streakTime = 0;
        tell(value ? "Gauntlet equipped - LMB / Q / E / F" : "Gauntlet unequipped");
    }

    boolean attack(Move move, double x, double y, double z, double yaw) {
        if (!equipped || locked() || (cooldownsEnabled && cooldowns[move.ordinal()] > 0) || health <= 0) return false;
        if (move != Move.PUNCH && y > .05) {
            tell("Land before using " + move.label.substring(3));
            return false;
        }
        active = move;
        attackTime = 0;
        facing = yaw;
        playerX = x; playerY = y; playerZ = z;
        attackMultiplier = multiplier;
        resolved = false;
        grabbed = null;
        pummelHits = 0;
        cooldowns[move.ordinal()] = cooldownsEnabled ? move.cooldown : 0;
        effect(GameEffect.Kind.SWING, x, y, z, 1, move.ordinal());
        return true;
    }

    void update(double dt, double x, double y, double z) {
        update(dt, x, y, z, facing);
    }

    void update(double dt, double x, double y, double z, double currentFacing) {
        facing = Math.IEEEremainder(currentFacing, Math.PI * 2);
        playerX = x; playerY = y; playerZ = z;
        for (int i = 0; i < cooldowns.length; i++) cooldowns[i] = Math.max(0, cooldowns[i] - dt);
        endLag = Math.max(0, endLag - dt);
        streakTime = Math.max(0, streakTime - dt);
        if (streakTime == 0) multiplier = 1;
        waveTime = Math.max(0, waveTime - dt);
        messageTime = Math.max(0, messageTime - dt);
        if (!externalTargetUpdates) for (Dummy dummy : dummies) updateDummy(dummy, dt);
        if (active == null) return;
        double before = attackTime;
        attackTime += dt;
        switch (active) {
            case PUNCH:
                if (!resolved && attackTime >= .12) {
                    resolved = true;
                    Dummy target = inFront(2.0);
                    if (target != null) hit(target, 23, 1.5);
                    else tell("Punch missed");
                }
                if (attackTime >= .36) finish(.08);
                break;
            case PUMMEL:
                if (!resolved && attackTime >= .2) {
                    resolved = true;
                    grabbed = inFront(1.8);
                    if (grabbed == null) { tell("Pummel missed - recovering"); finish(1.1); break; }
                    grabbed.vx = grabbed.vz = 0;
                    effect(GameEffect.Kind.GRAB, grabbed.x, grabbed.lift, grabbed.z, 1, 0);
                }
                if (grabbed != null) {
                    grabbed.x = bound(playerX - Math.sin(facing) * 1.1);
                    grabbed.z = bound(playerZ - Math.cos(facing) * 1.1);
                    grabbed.lift = attackTime < .4 ? .5 * Math.sin((attackTime - .2) / .2 * Math.PI) : 0;
                    while (pummelHits < 3 && attackTime >= .45 + pummelHits * .25) {
                        if (grabbed.alive()) {
                            if (pummelHits == 0) effect(GameEffect.Kind.SLAM, grabbed.x, 0, grabbed.z, 1, 0);
                            hit(grabbed, 10, 0);
                        }
                        pummelHits++;
                    }
                }
                if (attackTime >= 1.2) finish(.25);
                break;
            case SHOCKWAVE:
                if (!resolved && attackTime >= .3) {
                    resolved = true;
                    waveTime = .7; waveX = playerX; waveZ = playerZ;
                    effect(GameEffect.Kind.SHOCKWAVE, playerX, 0, playerZ, 1, 0);
                    int affected = 0;
                    for (Dummy dummy : dummies) {
                        double distance = Math.hypot(dummy.x - playerX, dummy.z - playerZ);
                        if (!dummy.alive() || distance > OUTER_RADIUS) continue;
                        affected++;
                        dummy.slowTime = 3;
                        dummy.slowAmount = distance <= INNER_RADIUS ? .7 : .4;
                        if (distance <= INNER_RADIUS) hit(dummy, 10, 0);
                    }
                    tell("Shockwave: " + affected + " targets slowed");
                }
                if (attackTime >= .65) finish(.2);
                break;
            case HAYMAKER:
                double step = Math.max(0, Math.min(attackTime, .3) - Math.min(before, .3)) * 12;
                double nextX = bound(playerX - Math.sin(facing) * step);
                double nextZ = bound(playerZ - Math.cos(facing) * step);
                Dummy target = sweptTarget(playerX, playerZ, nextX, nextZ);
                boolean wall = Math.abs(nextX - playerX) + Math.abs(nextZ - playerZ) < step * .5;
                playerX = nextX; playerZ = nextZ;
                if (target != null) {
                    hit(target, 45, 8);
                    tell("Hay-maker connected");
                    finish(.3);
                } else if (attackTime >= .3 || wall) {
                    tell("Hay-maker missed - recovering");
                    finish(1.2);
                }
                break;
        }
    }

    void updateDummy(Dummy dummy, double dt) {
        dummy.flash = Math.max(0, dummy.flash - dt);
        dummy.damageTime = Math.max(0, dummy.damageTime - dt);
        dummy.slowTime = Math.max(0, dummy.slowTime - dt);
        if (dummy.slowTime == 0) dummy.slowAmount = 0;
        if (!dummy.alive()) {
            dummy.respawnTime -= dt;
            if (dummy.respawnTime <= 0) {
                dummy.health = dummy.maxHealth;
                dummy.x = dummy.spawnX; dummy.z = dummy.spawnZ;
                dummy.vx = dummy.vz = dummy.slowTime = dummy.slowAmount = dummy.lift = 0;
                effect(GameEffect.Kind.RESPAWN, dummy.x, 0, dummy.z, 1, 0);
            }
            return;
        }
        // Exact integration of exponential friction makes pushes independent of frame rate.
        double decay = Math.exp(-5 * dt), displacement = (1 - decay) / 5;
        dummy.x = bound(dummy.x + dummy.vx * displacement);
        dummy.z = bound(dummy.z + dummy.vz * displacement);
        dummy.vx *= decay; dummy.vz *= decay;
    }

    private Dummy inFront(double reach) {
        Dummy nearest = null;
        double best = reach;
        for (Dummy dummy : dummies) {
            if (!dummy.alive() || Math.abs(playerY - dummy.lift) > 1.3) continue;
            double dx = dummy.x - playerX, dz = dummy.z - playerZ;
            double distance = Math.hypot(dx, dz);
            double forward = -Math.sin(facing) * dx - Math.cos(facing) * dz;
            if (distance <= best && (distance < .35 || forward >= distance * .65)) {
                nearest = dummy; best = distance;
            }
        }
        return nearest;
    }

    private Dummy sweptTarget(double ax, double az, double bx, double bz) {
        Dummy nearest = null;
        double best = Double.POSITIVE_INFINITY;
        double dx = bx - ax, dz = bz - az, lengthSq = dx * dx + dz * dz;
        for (Dummy dummy : dummies) {
            if (!dummy.alive()) continue;
            double forward = -Math.sin(facing) * (dummy.x - ax) - Math.cos(facing) * (dummy.z - az);
            if (forward < -.1) continue;
            double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1,
                    ((dummy.x - ax) * dx + (dummy.z - az) * dz) / lengthSq));
            if (Math.hypot(dummy.x - ax - t * dx, dummy.z - az - t * dz) <= .95 && t < best) {
                nearest = dummy; best = t;
            }
        }
        return nearest;
    }

    private void hit(Dummy dummy, double baseDamage, double push) {
        double damage = baseDamage * attackMultiplier;
        dummy.health = Math.max(0, dummy.health - damage);
        dummy.lastDamage = damage; dummy.damageTime = 1; dummy.flash = .18;
        dummy.vx = -Math.sin(facing) * push;
        dummy.vz = -Math.cos(facing) * push;
        effect(GameEffect.Kind.HIT, dummy.x, dummy.lift + .6, dummy.z, damage, active.ordinal());
        if (!dummy.alive()) {
            dummy.respawnTime = 4;
            if (!dummy.playerBody) effect(GameEffect.Kind.DEFEAT, dummy.x, dummy.lift, dummy.z, 1, 0);
        }
        // Every damaging hit grants a stack, including each individual Pummel hit.
        multiplier = Math.min(MAX_MULTIPLIER, Math.round((multiplier + MULTIPLIER_STEP) * 100) / 100.0);
        streakTime = STREAK_DURATION;
    }

    private void finish(double recovery) {
        if (grabbed != null) grabbed.lift = 0;
        grabbed = null;
        active = null;
        endLag = recovery;
    }

    Dummy grabbedTarget() { return grabbed; }

    void damagePlayer(double damage) {
        if (godMode || health <= 0 || !Double.isFinite(damage) || damage <= 0) return;
        health = Math.max(0, health - damage);
        if (health == 0) {
            effect(GameEffect.Kind.DEFEAT, playerX, playerY, playerZ, 1, 1);
            finish(0);
            multiplier = 1; streakTime = 0;
            tell("Defeated");
        }
    }

    private static double bound(double value) { return Math.max(-9.5, Math.min(9.5, value)); }
    void effect(GameEffect.Kind kind, double x, double y, double z, double strength, int variant) {
        if (effects.size() >= 128) effects.remove(0);
        effects.add(new GameEffect(kind, x, y, z, facing, strength, variant));
    }
    private void tell(String text) { message = text; messageTime = 2.5; }
}
