/** Deterministic checks for hit detection, cooldowns, recovery, and streak expiry. */
public class CombatCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-7, message + ": " + actual);
    }
    private static CombatSystem arena() {
        CombatSystem combat = new CombatSystem();
        combat.equip(true);
        for (CombatSystem.Dummy dummy : combat.dummies) { dummy.x = 9; dummy.z = 9; }
        return combat;
    }
    private static void advance(CombatSystem combat, double seconds) {
        for (double left = seconds; left > 1e-9;) {
            double dt = Math.min(.01, left);
            combat.update(dt, combat.playerX, combat.playerY, combat.playerZ);
            left -= dt;
        }
    }
    private static CombatSystem.Dummy target(CombatSystem combat, int index, double x, double z) {
        CombatSystem.Dummy dummy = combat.dummies.get(index);
        dummy.x = x; dummy.z = z;
        dummy.health = 100; dummy.vx = dummy.vz = 0;
        return dummy;
    }
    public static void main(String[] args) {
        CombatSystem combat = arena();
        near(combat.health, 115, "Reinforced HP");
        CombatSystem.Dummy dummy = target(combat, 0, 0, -1.4);
        check(combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0), "Punch accepted");
        check(!combat.attack(CombatSystem.Move.PUMMEL, 0, 0, 0, 0), "No overlapping attacks");
        advance(combat, .13);
        near(dummy.health, 77, "Punch base damage");
        near(combat.multiplier, 1.05, "First hit grants 0.05 stack");
        check(dummy.vz < 0, "Punch knockback");
        check(!combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0), "Cooldown enforced");
        advance(combat, 6);
        check(combat.streakTime > 0 && combat.streakTime < .6, "Streak lasts nearly 6.5 seconds");
        target(combat, 0, 0, -1.4);
        combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
        advance(combat, .13);
        near(dummy.health, 75.85, "Stored multiplier increases damage");
        check(combat.streakTime > 6.4, "Every hit refreshes 6.5-second timer");
        advance(combat, 6.6);
        near(combat.multiplier, 1, "Streak expires after inactivity");
        near(combat.streakTime, 0, "Expired timer clamps to zero");

        combat = arena();
        dummy = target(combat, 0, 0, 1.3);
        combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
        advance(combat, .5);
        near(dummy.health, 100, "Targets behind player are not hit");
        near(combat.multiplier, 1, "Misses give no stack");

        combat = arena();
        dummy = target(combat, 0, -1.4, 0);
        CombatSystem.Dummy oldDirection = target(combat, 1, 0, -1.4);
        combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
        combat.update(.13, 0, 0, 0, Math.PI / 2);
        near(dummy.health, 77, "Punch follows turn during startup");
        near(oldDirection.health, 100, "Punch leaves its original direction");
        check(dummy.vx < 0 && Math.abs(dummy.vz) < 1e-9, "Punch knockback follows turned facing");

        combat = arena();
        dummy = target(combat, 0, -1.4, 0);
        combat.attack(CombatSystem.Move.PUMMEL, 0, 0, 0, 0);
        combat.update(.21, 0, 0, 0, Math.PI / 2);
        check(combat.grabbedTarget() == dummy, "Pummel grab follows turning");
        combat.update(.25, 0, 0, 0, Math.PI);
        near(dummy.x, 0, "Grabbed target rotates with player X");
        near(dummy.z, 1.1, "Grabbed target stays in front after turning");

        combat = arena();
        combat.attack(CombatSystem.Move.HAYMAKER, 0, 0, 0, 0);
        combat.update(.05, 0, 0, 0, Math.PI / 2);
        near(combat.playerX, -.6, "Hay-maker steers with turning");
        near(combat.playerZ, 0, "Hay-maker stops using starting direction");
        near(combat.facing, Math.PI / 2, "Debug facing matches hit detection");

        combat = arena();
        dummy = target(combat, 0, 0, -1.4);
        combat.attack(CombatSystem.Move.PUMMEL, 0, 0, 0, 0);
        advance(combat, 1.21);
        near(dummy.health, 70, "Pummel deals 30 total damage");
        near(combat.multiplier, 1.15, "Each of Pummel's three hits grants a stack");
        check(combat.streakTime > 6.2, "Final Pummel hit refreshes timer");
        check(dummy.lift == 0 && combat.endLag > 0, "Grab releases into recovery");
        combat = arena();
        combat.attack(CombatSystem.Move.PUMMEL, 0, 0, 0, 0);
        advance(combat, .21);
        check(combat.active == null && combat.endLag > 1, "Missed grab has long recovery");

        combat = arena();
        CombatSystem.Dummy inner = target(combat, 0, 0, -2);
        CombatSystem.Dummy outer = target(combat, 1, 0, -4);
        CombatSystem.Dummy outside = target(combat, 2, 0, -6);
        combat.attack(CombatSystem.Move.SHOCKWAVE, 0, 0, 0, 0);
        advance(combat, .31);
        near(inner.health, 90, "Inner Shockwave damage");
        near(inner.slowAmount, .7, "Inner slow strength");
        near(outer.health, 100, "Outer ring does not damage");
        near(outer.slowAmount, .4, "Outer slow strength");
        near(outside.slowTime, 0, "Outside ring unaffected");
        advance(combat, 3.1);
        near(inner.slowAmount, 0, "Slows expire");

        combat = arena();
        dummy = target(combat, 0, 0, -2);
        combat.attack(CombatSystem.Move.HAYMAKER, 0, 0, 0, 0);
        combat.update(.3, 0, 0, 0);
        near(dummy.health, 55, "Dash sweep hits without tunneling");
        check(dummy.vz == -8 && combat.playerZ < 0, "Hay-maker launches and pushes");
        combat = arena();
        combat.attack(CombatSystem.Move.HAYMAKER, 0, 0, 0, 0);
        advance(combat, .31);
        check(combat.endLag > 1, "Missed dash has long recovery");
        check(!combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0), "Recovery blocks attacks");

        combat = arena();
        for (int i = 0; i < 8; i++) {
            target(combat, 0, 0, -1.4);
            combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
            advance(combat, 2.2);
        }
        near(combat.multiplier, 1.35, "Multiplier capped at 1.35x");
        combat.damagePlayer(115);
        near(combat.multiplier, 1, "Death clears streak");
        near(combat.health, 0, "Player damage respects HP");
        check(!combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0), "Dead player cannot attack");

        combat = arena();
        dummy = target(combat, 0, 0, -1);
        dummy.health = 10;
        combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
        advance(combat, .13);
        check(!dummy.alive() && dummy.respawnTime > 3.9, "Dummy defeat starts respawn");
        advance(combat, 4.1);
        near(dummy.health, 100, "Dummy respawns at full health");
        near(dummy.x, dummy.spawnX, "Dummy respawn X");
        near(dummy.z, dummy.spawnZ, "Dummy respawn Z");
        System.out.println("Passed: damage, aim, cooldowns, recovery, grabs, shockwave zones, dash sweep, knockback, streak refresh/expiry/cap, death, respawn.");
    }
}
