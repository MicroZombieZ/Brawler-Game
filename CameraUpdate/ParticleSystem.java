import java.awt.Color;
import java.util.*;

/** Bounded, cosmetic world-space particles. Never changes gameplay state. */
final class ParticleSystem {
    enum Quality {
        OFF(0, 0), LOW(160, .35), MEDIUM(400, .65), HIGH(800, 1);
        final int limit;
        final double density;
        Quality(int limit, double density) { this.limit = limit; this.density = density; }
    }
    enum Shape { DUST, CUBE, SHARD, SPARK, RING }
    static final Color DUST = new Color(165, 156, 137), GOLD = new Color(255, 198, 91),
            BLUE = new Color(121, 217, 255), AIR = new Color(204, 237, 245);
    static final class Particle {
        Shape shape;
        Color color;
        double x, y, z, vx, vy, vz, gravity, size, growth, life, duration, angle, spin, delay;
        int bounces;
    }
    private static final class Track {
        double x, y, z, steps, aura, slow, trail, knock, multiplier;
        boolean initialized;
    }
    final List<Particle> particles = new ArrayList<>();
    private final Map<Integer, Track> tracks = new HashMap<>();
    private final Random random = new Random(47821);
    private Quality quality = Quality.MEDIUM;

    Quality quality() { return quality; }
    void quality(Quality value) {
        quality = value;
        while (particles.size() > value.limit) particles.remove(0);
        if (value == Quality.OFF) tracks.clear();
    }
    void clear() { particles.clear(); tracks.clear(); }
    void retainActors(Set<Integer> ids) { tracks.keySet().retainAll(ids); }
    private double between(double low, double high) { return low + random.nextDouble() * (high - low); }
    private Particle add(Shape shape, Color color, double x, double y, double z, double size, double life) {
        if (quality.limit == 0) return null;
        if (particles.size() >= quality.limit) particles.remove(0);
        Particle p = new Particle(); p.shape=shape; p.color=color; p.x=x; p.y=y; p.z=z;
        p.size=size; p.life=p.duration=life; p.angle=between(0,Math.PI*2); p.spin=between(-5,5);
        particles.add(p); return p;
    }
    private void burst(double x, double y, double z, Color color, int count, double power, Shape shape, double facing) {
        int total = (int) Math.ceil(count * quality.density);
        for (int i=0;i<total;i++) {
            double angle=between(0,Math.PI*2), speed=between(.3,power);
            Particle p=add(shape,color,x+between(-.12,.12),Math.max(.06,y),z+between(-.12,.12),
                    shape==Shape.DUST?between(.12,.24):between(.035,.105),between(.25,.85));
            p.vx=Math.cos(angle)*speed; p.vz=Math.sin(angle)*speed;
            p.vy=between(.3,power*.7); p.gravity=shape==Shape.DUST?.3:9;
            if (shape==Shape.SPARK) { p.vx-=Math.sin(facing)*power*.5; p.vz-=Math.cos(facing)*power*.5; }
            if (shape==Shape.DUST) p.growth=.25;
        }
    }
    private void ring(double x,double y,double z,Color color,double radius,double duration,double delay) {
        Particle p=add(Shape.RING,color,x,Math.max(.035,y),z,.1,duration);
        if(p!=null) { p.growth=radius/duration; p.delay=delay; p.spin=0; }
    }
    void emit(GameEffect event) {
        if(quality==Quality.OFF) return;
        double x=event.x,y=event.y,z=event.z;
        switch(event.kind) {
            case TAKEOFF:
                burst(x,.06,z,DUST,10,1.6,Shape.DUST,0); break;
            case DOUBLE_JUMP:
                ring(x,y+.15,z,AIR,1.2,.4,0);
                burst(x,y+.2,z,AIR,10,1.5,Shape.SHARD,0); break;
            case LAND:
                burst(x,.06,z,DUST,(int)(7+Math.min(18,event.strength)),1.5+Math.min(2,event.strength*.08),Shape.DUST,0);
                break;
            case GROUND_POUND:
                burst(x,.08,z,DUST,32,4,Shape.DUST,0);
                burst(x,.12,z,new Color(130,142,148),18,5,Shape.CUBE,0);
                ring(x,.04,z,AIR,2.4,.5,0); break;
            case SWING:
                burst(x-Math.sin(event.facing)*.7,y+.65,z-Math.cos(event.facing)*.7,GOLD,5,1.2,Shape.SPARK,event.facing); break;
            case GRAB:
                burst(x,y+.65,z,GOLD,12,2,Shape.SPARK,event.facing); break;
            case SLAM:
                burst(x,.06,z,DUST,22,3,Shape.DUST,0);
                burst(x,.1,z,new Color(137,149,156),12,3.5,Shape.CUBE,0);
                ring(x,.04,z,GOLD,1.3,.35,0); break;
            case HIT:
                boolean heavy=event.variant==CombatSystem.Move.HAYMAKER.ordinal();
                burst(x,y,z,GOLD,heavy?27:12,heavy?5:3,Shape.SPARK,event.facing);
                burst(x,y,z,new Color(255,234,176),heavy?12:5,heavy?3:1.8,Shape.SHARD,event.facing);
                if(heavy) { burst(x,.08,z,DUST,18,3,Shape.DUST,0); ring(x,.05,z,GOLD,1,.25,0); }
                break;
            case SHOCKWAVE:
                for(int i=0;i<24;i++) {
                    double angle=i*Math.PI*2/24;
                    burst(x+Math.cos(angle)*1.6,.05,z+Math.sin(angle)*1.6,DUST,2,2,Shape.DUST,0);
                }
                burst(x,.1,z,new Color(128,152,164),20,5,Shape.CUBE,0);
                ring(x,.04,z,DUST,CombatSystem.OUTER_RADIUS,.65,0); break;
            case DEFEAT:
                burst(x,y+.5,z,event.variant==0?new Color(220,122,103):BLUE,30,4,Shape.CUBE,0);
                burst(x,y+.5,z,AIR,12,2,Shape.SHARD,0); break;
            case RESPAWN:
                for(int i=0;i<(int)Math.ceil(24*quality.density);i++) {
                    double angle=between(0,Math.PI*2), radius=between(.8,1.5), height=between(.1,1.4);
                    Particle p=add(Shape.SPARK,BLUE,x+Math.cos(angle)*radius,y+height,z+Math.sin(angle)*radius,.055,.4);
                    p.vx=-Math.cos(angle)*radius/.4; p.vz=-Math.sin(angle)*radius/.4; p.vy=(.5-height)/.4;
                }
                ring(x,y+.05,z,BLUE,1.7,.4,.4); break;
        }
    }

    void observe(int id,double dt,double x,double y,double z,double facing,double speed,
            double health,double multiplier,boolean slowed,boolean knocked,CombatSystem.Move move,double attackTime) {
        if(quality==Quality.OFF || dt<=0) return;
        Track t=tracks.computeIfAbsent(id,ignored->new Track());
        double distance=t.initialized?Math.hypot(x-t.x,z-t.z):0;
        if(t.initialized && t.multiplier>1 && multiplier<=1 && health>0)
            burst(x,y+.6,z,GOLD,8,.8,Shape.SPARK,facing);
        t.x=x;t.y=y;t.z=z;t.multiplier=multiplier;t.initialized=true;
        if(health<=0) return;
        if(y<.08 && distance<2 && speed>.2 && !knocked) {
            t.steps+=distance;
            double stride=speed>4.5?.35:.6;
            if(t.steps>=stride) { t.steps%=stride; burst(x,.06,z,DUST,speed>4.5?5:3,.8,Shape.DUST,0); }
        }
        if(multiplier>1) {
            t.aura+=dt;
            double intensity=(multiplier-1)/.35;
            if(t.aura>=.12-.06*intensity) {
                t.aura=0;
                for(int side:new int[]{-1,1}) {
                    double gx=x+Math.cos(facing)*side*.65-Math.sin(facing)*.2;
                    double gz=z-Math.sin(facing)*side*.65-Math.cos(facing)*.2;
                    Particle p=add(Shape.DUST,new Color(255,184,70),gx,y+.6,gz,.10+.05*intensity,.2);
                    p.growth=.06;
                    if(intensity>.45) burst(gx,y+.6,gz,GOLD,1,.5,Shape.SPARK,facing);
                }
            }
        }
        if(slowed) {
            t.slow+=dt;
            if(t.slow>=.12) { t.slow=0; burst(x,y+.25,z,BLUE,2,.65,Shape.DUST,0); }
        }
        if(knocked && distance>.002 && distance<2) {
            t.knock+=dt;
            if(t.knock>=.045) {
                t.knock=0; burst(x,y+.3,z,AIR,2,.5,Shape.SPARK,facing);
                if(y<.08) burst(x,.05,z,DUST,3,1,Shape.DUST,0);
            }
        }
        if(move==CombatSystem.Move.HAYMAKER || (move==CombatSystem.Move.PUNCH && attackTime<.25)) {
            t.trail+=dt;
            if(t.trail>=.025) {
                t.trail=0;
                Color color=move==CombatSystem.Move.HAYMAKER?AIR:GOLD;
                for(int side:new int[]{-1,1}) {
                    double gx=x+Math.cos(facing)*side*.65-Math.sin(facing)*.5;
                    double gz=z-Math.sin(facing)*side*.65-Math.cos(facing)*.5;
                    Particle p=add(Shape.SPARK,color,gx,y+.55,gz,.05,.16);
                    p.vx=Math.sin(facing)*2; p.vz=Math.cos(facing)*2;
                }
            }
        }
    }

    void update(double dt) {
        if(dt<=0 || !Double.isFinite(dt)) return;
        Iterator<Particle> iterator=particles.iterator();
        while(iterator.hasNext()) {
            Particle p=iterator.next(); double step=dt;
            if(p.delay>0) { double waiting=Math.min(p.delay,step); p.delay-=waiting; step-=waiting; }
            if(step<=0) continue;
            p.life-=step;
            if(p.life<=0) { iterator.remove();continue; }
            p.x+=p.vx*step;p.z+=p.vz*step;
            p.y+=p.vy*step-.5*p.gravity*step*step; p.vy-=p.gravity*step;
            p.size=Math.max(.005,p.size+p.growth*step);p.angle+=p.spin*step;
            if(p.shape!=Shape.RING && p.y<p.size*.3) {
                p.y=p.size*.3;
                if((p.shape==Shape.CUBE || p.shape==Shape.SHARD) && p.bounces++<2 && Math.abs(p.vy)>.5) p.vy=-p.vy*.35;
                else p.vy=0;
                p.vx*=Math.exp(-5*step);p.vz*=Math.exp(-5*step);
            }
        }
    }
}
