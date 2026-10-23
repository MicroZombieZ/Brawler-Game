import java.lang.reflect.*;
import javax.swing.SwingUtilities;

public class RotationCheck {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                CubeMovement demo = new CubeMovement();
                Field yaw = CubeMovement.class.getDeclaredField("yaw");
                Field cubeYaw = CubeMovement.class.getDeclaredField("cubeYaw");
                Field paused = CubeMovement.class.getDeclaredField("paused");
                Field x = CubeMovement.class.getDeclaredField("x");
                Field z = CubeMovement.class.getDeclaredField("z");
                for(Field field : new Field[]{yaw,cubeYaw,paused,x,z}) field.setAccessible(true);
                Method vertices = CubeMovement.class.getDeclaredMethod("cubeVertices");
                Method clamp = CubeMovement.class.getDeclaredMethod("clamp",double.class);
                vertices.setAccessible(true);
                clamp.setAccessible(true);
                for(double angle : new double[]{0,.4,Math.PI/4,Math.PI/2,Math.PI,-2.7}) {
                    yaw.setDouble(demo,angle);
                    cubeYaw.setDouble(demo,angle);
                    x.setDouble(demo,0);
                    z.setDouble(demo,0);
                    double[][] v = (double[][])vertices.invoke(demo);
                    double forwardX=(v[0][0]+v[1][0])/2;
                    double forwardZ=(v[0][2]+v[1][2])/2;
                    if(Math.abs(forwardX + .5*Math.sin(angle))>1e-9 ||
                            Math.abs(forwardZ + .5*Math.cos(angle))>1e-9)
                        throw new AssertionError("Cube front does not match camera-relative forward");
                    x.setDouble(demo,(double)clamp.invoke(demo,10.0));
                    z.setDouble(demo,(double)clamp.invoke(demo,-10.0));
                    for(double[] point : (double[][])vertices.invoke(demo)) {
                        if(Math.abs(point[0])>10+1e-9 || Math.abs(point[2])>10+1e-9 || point[1]<0)
                            throw new AssertionError("Rotated cube leaves plane");
                    }
                }
                Method move = CubeMovement.class.getDeclaredMethod("move",double.class);
                move.setAccessible(true);
                paused.setBoolean(demo,false);
                x.setDouble(demo,0);
                z.setDouble(demo,0);
                yaw.setDouble(demo,1);
                cubeYaw.setDouble(demo,0);
                move.invoke(demo,.05);
                double partial = cubeYaw.getDouble(demo);
                if(partial<=0 || partial>=1) throw new AssertionError("Rotation should lag");
                move.invoke(demo,.05);
                double split = cubeYaw.getDouble(demo);
                cubeYaw.setDouble(demo,0);
                move.invoke(demo,.1);
                if(Math.abs(cubeYaw.getDouble(demo)-split)>1e-9)
                    throw new AssertionError("Rotation depends on frame rate");
                for(int i=0;i<20;i++) move.invoke(demo,.05);
                if(Math.abs(cubeYaw.getDouble(demo)-1)>.001) throw new AssertionError("Rotation never catches up");
                cubeYaw.setDouble(demo,Math.toRadians(179));
                yaw.setDouble(demo,Math.toRadians(-179));
                move.invoke(demo,.01);
                double change=Math.IEEEremainder(cubeYaw.getDouble(demo)-Math.toRadians(179),Math.PI*2);
                if(change<=0 || change>Math.toRadians(2)) throw new AssertionError("Wrong turn across angle wrap");
                paused.setBoolean(demo,true);
                double before=cubeYaw.getDouble(demo);
                move.invoke(demo,.1);
                if(cubeYaw.getDouble(demo)!=before) throw new AssertionError("Paused rotation continued");
                System.out.println("Passed: rotation lag, catch-up, frame-rate independence, shortest turn, pause, plane bounds.");
            } catch(Exception e) { throw new RuntimeException(e); }
        });
        System.exit(0);
    }
}
