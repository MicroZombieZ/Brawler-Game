import java.awt.event.*;
import java.lang.reflect.*;
import javax.swing.*;

public class MovementCheck {
    static Field field(String name) throws Exception {
        Field f = CubeMovement.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    static void key(CubeMovement demo, int code, boolean released, boolean shift) {
        Object action = demo.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(
                KeyStroke.getKeyStroke(code, shift ? InputEvent.SHIFT_DOWN_MASK : 0, released));
        if (action == null) throw new AssertionError("Missing input binding");
        demo.getActionMap().get(action).actionPerformed(null);
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                CubeMovement demo = new CubeMovement();
                demo.setSize(1000, 700);
                Field paused = field("paused"), z = field("z"), x = field("x");
                Field y = field("y"), velocity = field("verticalSpeed");
                Method move = CubeMovement.class.getDeclaredMethod("move", double.class);
                move.setAccessible(true);
                paused.setBoolean(demo, false);
                key(demo, KeyEvent.VK_W, false, false);
                move.invoke(demo, 0.1);
                check(Math.abs(z.getDouble(demo) + .4) < 1e-9, "Walk speed");
                key(demo, KeyEvent.VK_SHIFT, false, true);
                key(demo, KeyEvent.VK_D, false, true);
                double beforeX = x.getDouble(demo), beforeZ = z.getDouble(demo);
                move.invoke(demo, .1);
                check(Math.abs(Math.hypot(x.getDouble(demo)-beforeX,z.getDouble(demo)-beforeZ)-.7)<1e-9,
                        "Sprint diagonal speed");
                key(demo, KeyEvent.VK_SPACE, false, true);
                move.invoke(demo, .1);
                check(y.getDouble(demo) > 0, "Jump rises");
                double speed = velocity.getDouble(demo);
                key(demo, KeyEvent.VK_SPACE, true, true);
                key(demo, KeyEvent.VK_SPACE, false, true);
                check(velocity.getDouble(demo) > speed, "Second jump restores upward speed");
                speed = velocity.getDouble(demo);
                key(demo, KeyEvent.VK_SPACE, true, true);
                key(demo, KeyEvent.VK_SPACE, false, true);
                check(velocity.getDouble(demo) == speed, "No third jump");
                paused.setBoolean(demo, true);
                double height = y.getDouble(demo);
                move.invoke(demo, .1);
                check(y.getDouble(demo) == height, "Pause freezes jump");
                paused.setBoolean(demo, false);
                for (int i=0;i<100;i++) move.invoke(demo,.01);
                check(y.getDouble(demo)==0 && velocity.getDouble(demo)==0, "Landing and no held-space auto-jump");
                key(demo, KeyEvent.VK_SPACE, true, true);
                key(demo, KeyEvent.VK_SPACE, false, true);
                move.invoke(demo,.1);
                check(y.getDouble(demo)>0,"Jump again after landing");
                double currentX = x.getDouble(demo), currentY = y.getDouble(demo), currentZ = z.getDouble(demo);
                key(demo, KeyEvent.VK_R, false, true);
                move.invoke(demo,.1);
                check(paused.getBoolean(demo) && field("classMenuOpen").getBoolean(demo), "R opens class menu");
                check(x.getDouble(demo)==currentX && y.getDouble(demo)==currentY && z.getDouble(demo)==currentZ,
                        "Class menu freezes position without resetting");
                key(demo, KeyEvent.VK_R, false, true);
                check(field("classMenuOpen").getBoolean(demo), "Held R keeps menu open");
                JButton equip = (JButton) field("equip").get(demo);
                check(!equip.isEnabled(), "Equip requires a selection");
                JPanel classMenu = (JPanel) field("classMenu").get(demo);
                JPanel choices = (JPanel) field("classChoices").get(demo);
                Method animate = CubeMovement.class.getDeclaredMethod("animateClassMenu", double.class);
                animate.setAccessible(true);
                check(choices.getY() == -700 && choices.getX() == 265, "Sidebar starts above and to the right");
                animate.invoke(demo, .25);
                check(choices.getY() > -700 && choices.getY() < 0
                        && choices.getX() > 0 && choices.getX() < 265, "Sidebar slides down and left");
                check(Math.abs(choices.getX() + choices.getY() * 265.0 / 700) < 1,
                        "Sidebar follows the slope of its sides");
                animate.invoke(demo, .25);
                check(choices.getY() == 0 && choices.getX() == 0, "Sidebar settles on screen");
                ((JToggleButton) choices.getComponent(1)).doClick();
                check(((JLabel) field("classTitle").get(demo)).getText().equals("Mage"), "Selection previews title");
                check(field("equippedClass").get(demo)==null, "Selection alone does not equip");
                JLabel preview = (JLabel) field("classTitle").get(demo);
                int titleStart = preview.getY();
                animate.invoke(demo, .1);
                check(preview.getY() < titleStart && !equip.isVisible(), "Title rises before Equip appears");
                animate.invoke(demo, .1);
                check(equip.isVisible(), "Equip follows after delay");
                int equipStart = equip.getY();
                animate.invoke(demo, .5);
                check(equip.getY() < equipStart, "Equip slides upward");
                check(!choices.contains(equip.getX(), equip.getY()), "Sidebar does not block Equip clicks");
                equip.doClick();
                check(field("equippedClass").get(demo).equals("Mage"), "Equip applies selected class");
                ((JToggleButton) choices.getComponent(2)).doClick();
                check(field("equippedClass").get(demo).equals("Mage"), "Browsing preserves equipped class");
                check(!equip.isVisible(), "New selection restarts delayed Equip animation");
                ((JToggleButton) choices.getComponent(0)).doClick();
                equip.doClick();
                CombatSystem combat = (CombatSystem) field("combat").get(demo);
                check(combat.equipped && combat.health == 115, "Gauntlet equip applies Reinforced");
                paused.setBoolean(demo, false);
                x.setDouble(demo, 0); y.setDouble(demo, 0); z.setDouble(demo, -1.6);
                velocity.setDouble(demo, 0);
                java.util.Set<?> heldKeys = (java.util.Set<?>) field("keys").get(demo);
                heldKeys.clear();
                demo.dispatchEvent(new MouseEvent(demo, MouseEvent.MOUSE_PRESSED, 0, 0,
                        500, 350, 1, false, MouseEvent.BUTTON1));
                check(combat.active == CombatSystem.Move.PUNCH, "Left mouse starts Punch");
                paused.setBoolean(demo, true);
                double cooldown = combat.cooldowns[0];
                move.invoke(demo, .5);
                check(combat.cooldowns[0] == cooldown && combat.attackTime == 0, "Pause freezes combat");
                paused.setBoolean(demo, false);
                move.invoke(demo, .13);
                check(combat.dummies.get(1).health == 77, "Mouse attack damages target through game update");
                for (int i = 0; i < 15; i++) move.invoke(demo, .05);
                key(demo, KeyEvent.VK_Q, false, false);
                check(combat.active == CombatSystem.Move.PUMMEL, "Q starts Pummel");
                for (int i = 0; i < 35; i++) move.invoke(demo, .05);
                key(demo, KeyEvent.VK_E, false, true);
                check(combat.active == CombatSystem.Move.SHOCKWAVE, "Shift-E starts Shockwave");
                for (int i = 0; i < 25; i++) move.invoke(demo, .05);
                key(demo, KeyEvent.VK_F, false, false);
                check(combat.active == CombatSystem.Move.HAYMAKER, "F starts Hay-maker");
                for (CombatSystem.Move ability : CombatSystem.Move.values()) {
                    heldKeys.clear();
                    combat.active = null; combat.endLag = 0;
                    java.util.Arrays.fill(combat.cooldowns, 0);
                    x.setDouble(demo, 0); y.setDouble(demo, 0); z.setDouble(demo, 0);
                    velocity.setDouble(demo, 0);
                    field("stamina").setDouble(demo, 100);
                    field("exhausted").setBoolean(demo, false);
                    check(combat.attack(ability, 0, 0, 0, 0), "Start movement check for " + ability);
                    key(demo, KeyEvent.VK_D, false, false);
                    move.invoke(demo, .05);
                    check(Math.abs(x.getDouble(demo) - .2) < 1e-9, "Walk during " + ability);
                    key(demo, KeyEvent.VK_SHIFT, false, true);
                    move.invoke(demo, .05);
                    check(Math.abs(x.getDouble(demo) - .55) < 1e-9, "Sprint during " + ability);
                    key(demo, KeyEvent.VK_SPACE, false, true);
                    check(velocity.getDouble(demo) == 7, "Jump during " + ability);
                    move.invoke(demo, .01);
                    key(demo, KeyEvent.VK_CONTROL, false, true);
                    check(velocity.getDouble(demo) == -24, "Ground pound during " + ability);
                }
                heldKeys.clear();
                combat.active = null; combat.endLag = 1;
                java.util.Arrays.fill(combat.cooldowns, 0);
                double recoveryStartX = x.getDouble(demo);
                key(demo, KeyEvent.VK_D, false, false);
                move.invoke(demo, .05);
                check(Math.abs(x.getDouble(demo) - recoveryStartX - .2) < 1e-9, "Walk during recovery");
                check(!combat.attack(CombatSystem.Move.PUNCH, x.getDouble(demo), 0, z.getDouble(demo), 0),
                        "Recovery still blocks new attacks");
                System.out.println("Passed: movement, double jump, pause, class preview/equip, sidebar slide, delayed Equip animation.");
                System.out.println("Passed: Gauntlet equip, mouse/Q/E/F combat bindings, damage integration, combat pause.");
                System.out.println("Passed: walking, sprinting, jumping, and ground pound during every ability; movement during recovery.");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.exit(0);
    }
}
