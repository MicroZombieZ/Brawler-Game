import java.awt.event.KeyEvent;
import java.lang.reflect.*;
import javax.swing.SwingUtilities;

public class StaminaCheck {
    static Field field(String name) throws Exception {
        Field result = CubeMovement.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                CubeMovement demo = new CubeMovement();
                Field stamina = field("stamina"), z = field("z"), paused = field("paused");
                Field exhausted = field("exhausted");
                Method move = CubeMovement.class.getDeclaredMethod("move", double.class);
                move.setAccessible(true);
                paused.setBoolean(demo, false);
                MovementCheck.key(demo, KeyEvent.VK_SHIFT, false, true);
                stamina.setDouble(demo, 50);
                move.invoke(demo,.1);
                check(Math.abs(stamina.getDouble(demo)-51.5)<1e-9,"Shift alone should recover");
                MovementCheck.key(demo, KeyEvent.VK_W, false, true);
                move.invoke(demo,.1);
                check(Math.abs(stamina.getDouble(demo)-49.5)<1e-9,"Moving sprint drains");
                MovementCheck.key(demo, KeyEvent.VK_S, false, true);
                move.invoke(demo,.1);
                check(Math.abs(stamina.getDouble(demo)-51)<1e-9,"Opposing keys should recover");
                MovementCheck.key(demo, KeyEvent.VK_S, true, true);
                z.setDouble(demo,-9.5);
                move.invoke(demo,.1);
                check(Math.abs(stamina.getDouble(demo)-52.5)<1e-9,"Blocked movement should recover");
                stamina.setDouble(demo,100);
                for(int i=0;i<100;i++) {
                    z.setDouble(demo,0);
                    move.invoke(demo,.05);
                }
                check(stamina.getDouble(demo)==0 && exhausted.getBoolean(demo),"Five seconds exhausts stamina");
                z.setDouble(demo,0);
                move.invoke(demo,.1);
                check(Math.abs(z.getDouble(demo)+.4)<1e-9,"Exhaustion uses walking speed");
                paused.setBoolean(demo,true);
                double before = stamina.getDouble(demo);
                move.invoke(demo,1.0);
                check(stamina.getDouble(demo)==before,"Pause freezes stamina");
                paused.setBoolean(demo,false);
                MovementCheck.key(demo,KeyEvent.VK_SHIFT,true,false);
                for(int i=0;i<100;i++) move.invoke(demo,.1);
                check(stamina.getDouble(demo)==100 && !exhausted.getBoolean(demo),"Recovery caps at full");
                stamina.setDouble(demo,10);
                MovementCheck.key(demo,KeyEvent.VK_R,false,false);
                move.invoke(demo,.1);
                check(stamina.getDouble(demo)==10,"Class menu preserves stamina without refilling");
                System.out.println("Passed: sprint drain, idle/walk recovery, opposing keys, edges, exhaustion, pause, class menu.");
            } catch(Exception e) { throw new RuntimeException(e); }
        });
        System.exit(0);
    }
}
