import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.*;

/** Command behavior and console integration checks, run without a display. */
public class ConsoleCheck {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static Object get(CubeMovement demo, String name) throws Exception { return MovementCheck.field(name).get(demo); }
    static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-7, message + ": " + actual);
    }
    static void action(CubeMovement demo, String name) { demo.getActionMap().get(name).actionPerformed(null); }
    static void run() throws Exception {
        CubeMovement demo = new CubeMovement();
        demo.setSize(1000, 700); demo.doLayout();
        CombatSystem combat = (CombatSystem) get(demo, "combat");
        check(demo.executeCommand("help").contains("dummy hp"), "Help lists commands");
        check(demo.executeCommand("clear").isEmpty(), "Clear output");
        demo.executeCommand("hp 20"); near(combat.health, 20, "Set HP");
        demo.executeCommand("heal"); near(combat.health, 100, "Heal");
        demo.executeCommand("god on"); demo.executeCommand("damage 30"); near(combat.health, 100, "God immunity");
        demo.executeCommand("god off"); demo.executeCommand("damage 30"); near(combat.health, 70, "Damage");
        demo.executeCommand("stamina 0"); check((boolean) get(demo, "exhausted"), "Empty stamina exhausts");
        demo.executeCommand("stamina 100"); check(!(boolean) get(demo, "exhausted"), "Stamina refill clears exhaustion");
        combat.equip(true);
        demo.executeCommand("cooldowns off");
        check(combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0), "No-cooldown attack");
        near(combat.cooldowns[0], 0, "Disabled cooldown stays zero");
        combat.cancelAttack(); demo.executeCommand("cooldowns on");
        combat.attack(CombatSystem.Move.PUNCH, 0, 0, 0, 0);
        near(combat.cooldowns[0], 1.7, "Cooldown restored");
        demo.executeCommand("cooldowns reset"); near(combat.cooldowns[0], 0, "Cooldown reset");
        demo.executeCommand("streak 1.35"); near(combat.multiplier, 1.35, "Set streak at maximum"); near(combat.streakTime, 6.5, "Streak timer");
        demo.executeCommand("streak reset"); near(combat.multiplier, 1, "Clear streak");
        demo.executeCommand("tp 10 -10");
        near((double) get(demo, "x"), 9.5, "Teleport clamps X");
        near((double) get(demo, "z"), -9.5, "Teleport clamps Z");
        demo.executeCommand("dummy clear"); check(combat.dummies.isEmpty(), "Clear targets");
        demo.executeCommand("tp 0 0"); demo.executeCommand("dummy spawn");
        check(combat.dummies.size() == 1, "Spawn target"); near(combat.dummies.get(0).z, -2, "Spawn ahead");
        demo.executeCommand("dummy hp 250"); near(combat.dummies.get(0).health, 250, "Dummy HP refill");
        combat.dummies.get(0).health = 0; combat.dummies.get(0).respawnTime = .01;
        combat.update(.02, 0, 0, 0); near(combat.dummies.get(0).health, 250, "Respawn honors custom HP");
        demo.executeCommand("dummy reset"); check(combat.dummies.size() == 4, "Restore four targets");
        near(combat.dummies.get(0).maxHealth, 250, "Dummy settings apply to restored targets");
        demo.executeCommand("hitboxes on"); demo.executeCommand("stats on");
        check((boolean) get(demo, "showHitboxes") && (boolean) get(demo, "showStats"), "Enable overlays");
        java.awt.Graphics2D graphics = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB).createGraphics();
        demo.paint(graphics); graphics.dispose();
        demo.executeCommand("hitboxes off"); demo.executeCommand("stats off");
        check(!(boolean) get(demo, "showHitboxes") && !(boolean) get(demo, "showStats"), "Disable overlays");
        for (String invalid : new String[]{"hp", "heal extra", "hp -1", "hp NaN", "speed Infinity", "speed -1",
                "timescale 5", "tp 0 NaN", "streak 1.36", "streak 3", "dummy hp 0", "dummy missing", "god maybe", "cooldowns maybe"}) {
            check(demo.executeCommand(invalid).startsWith("Error:"), "Reject invalid command: " + invalid);
        }
        near((double) get(demo, "x"), 0, "Invalid teleport leaves position unchanged");
        check(demo.executeCommand("unknown").startsWith("Unknown command"), "Unknown command feedback");

        demo.executeCommand("reset");
        combat = (CombatSystem) get(demo, "combat");
        check(!combat.equipped && combat.dummies.size() == 4 && combat.dummyMaxHealth == 100, "Reset combat and arena");
        near(combat.health, 100, "Reset player health");
        check(!combat.godMode && combat.cooldownsEnabled && get(demo, "equippedClass") == null, "Reset flags and class");
        near((double) get(demo, "speedMultiplier"), 1, "Reset speed");
        near((double) get(demo, "timeScale"), 1, "Reset time scale");

        Method advance = CubeMovement.class.getDeclaredMethod("advanceSimulation", double.class); advance.setAccessible(true);
        MovementCheck.field("paused").setBoolean(demo, false);
        MovementCheck.key(demo, KeyEvent.VK_W, false, false);
        demo.executeCommand("speed 2"); advance.invoke(demo, .1);
        near((double) get(demo, "z"), -.8, "Speed multiplier changes movement");
        demo.executeCommand("tp 0 0"); demo.executeCommand("speed 1"); demo.executeCommand("timescale 0.5");
        combat.cooldowns[0] = 1;
        advance.invoke(demo, .1);
        near((double) get(demo, "z"), -.2, "Slow time changes movement");
        near(combat.cooldowns[0], .95, "Slow time changes cooldowns");
        demo.executeCommand("timescale 0"); advance.invoke(demo, .1);
        near((double) get(demo, "z"), -.2, "Zero time freezes world");
        near(combat.cooldowns[0], .95, "Zero time freezes cooldowns");

        action(demo, "console"); action(demo, "consoleUp");
        check((boolean) get(demo, "consoleOpen") && (boolean) get(demo, "paused"), "Console opens and pauses");
        action(demo, "classes"); action(demo, "classesUp");
        check(!(boolean) get(demo, "classMenuOpen"), "Typing R cannot open class menu");
        advance.invoke(demo, .1); near(combat.cooldowns[0], .95, "Console freezes simulation");
        DevConsole console = (DevConsole) get(demo, "console");
        console.input.setText("hp 42"); console.input.postActionEvent(); near(combat.health, 42, "Enter executes command");
        console.input.getActionMap().get("history-1").actionPerformed(null);
        check(console.input.getText().equals("hp 42"), "Up recalls history");
        console.input.getActionMap().get("history1").actionPerformed(null);
        check(console.input.getText().isEmpty(), "Down restores draft");
        demo.executeCommand("reset");
        check((boolean) get(demo, "consoleOpen") && (boolean) get(demo, "paused"), "Reset preserves console pause");
        action(demo, "pause"); action(demo, "escapeUp");
        check(!(boolean) get(demo, "consoleOpen"), "Escape closes console");
        // Open from an existing pause: closing the console must preserve that pause.
        action(demo, "console"); action(demo, "consoleUp");
        action(demo, "console"); action(demo, "consoleUp");
        check((boolean) get(demo, "paused"), "Console opened from pause stays paused on close");

        combat.equip(true); combat.dummies.get(1).z = -1;
        combat.attack(CombatSystem.Move.PUMMEL, 0, 0, 0, 0); combat.update(.21, 0, 0, 0);
        demo.executeCommand("dummy clear"); combat.update(1, 0, 0, 0);
        check(combat.active == null && combat.dummies.isEmpty(), "Clearing targets releases active grab");
        System.out.println("Passed: every console command, validation, custom dummy HP/respawn, speed/time scale, overlays, pause, text input/history, reset, grab cleanup.");
    }
    public static void main(String[] args) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { run(); } catch (Exception error) { throw new RuntimeException(error); }
            });
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
