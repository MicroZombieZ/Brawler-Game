import java.lang.reflect.Method;
import javax.swing.*;

/** Cosmetic lifecycle, event correctness, rendering, and quality-limit checks. */
public class ParticleCheck {
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static GameEffect event(GameEffect.Kind kind) { return new GameEffect(kind,0,0,0,0,3,0); }
    static long count(CombatSystem combat,GameEffect.Kind kind) {
        return combat.effects.stream().filter(e->e.kind==kind).count();
    }
    static void particles() {
        ParticleSystem system=new ParticleSystem();
        for(ParticleSystem.Quality quality:ParticleSystem.Quality.values()) {
            system.clear();system.quality(quality);
            for(GameEffect.Kind kind:GameEffect.Kind.values()) system.emit(event(kind));
            check(system.particles.size()<=quality.limit,"Quality particle cap: "+quality);
            check(quality==ParticleSystem.Quality.OFF?system.particles.isEmpty():!system.particles.isEmpty(),"Quality emission: "+quality);
        }
        system.clear(); system.quality(ParticleSystem.Quality.HIGH);
        system.emit(event(GameEffect.Kind.RESPAWN));
        check(system.particles.stream().anyMatch(p->p.delay>0),"Respawn pulse waits for inward particles");
        ParticleSystem.Particle moving=system.particles.get(0);double oldX=moving.x,oldZ=moving.z;
        system.update(.1);
        check(moving.x!=oldX || moving.z!=oldZ,"Particles move through world space");
        for(int i=0;i<20;i++) system.update(.1);
        check(system.particles.isEmpty(),"Particles and delayed pulses expire");
        system.emit(event(GameEffect.Kind.GROUND_POUND));
        double life=system.particles.get(0).life;system.update(0);
        check(system.particles.get(0).life==life,"Zero simulation time freezes particles");
        for(int i=0;i<500;i++) system.emit(event(GameEffect.Kind.GROUND_POUND));
        check(system.particles.size()==800,"Burst storms remain capped");
        system.quality(ParticleSystem.Quality.LOW);check(system.particles.size()==160,"Lower quality trims existing particles");
        system.quality(ParticleSystem.Quality.OFF);check(system.particles.isEmpty(),"Off clears existing particles");
        system.quality(ParticleSystem.Quality.MEDIUM);
        system.observe(1,.1,0,0,0,0,7,115,1,false,false,null,0);
        system.observe(1,.1,1,0,0,0,7,115,1,false,false,null,0);
        check(!system.particles.isEmpty(),"Sprinting emits ground dust");
        system.clear();system.observe(1,.2,0,0,0,0,0,115,1.35,true,false,null,0);
        check(system.particles.stream().anyMatch(p->p.color.equals(ParticleSystem.BLUE)),"Slow emits blue wisps");
        check(system.particles.stream().anyMatch(p->p.color.getRed()==255),"Streak emits gauntlet glow");
    }
    static void events() {
        CombatSystem combat=new CombatSystem();combat.equip(true);
        for(CombatSystem.Dummy d:combat.dummies) { d.x=9;d.z=9; }
        combat.attack(CombatSystem.Move.PUNCH,0,0,0,0);combat.update(.13,0,0,0);
        check(count(combat,GameEffect.Kind.SWING)==1 && count(combat,GameEffect.Kind.HIT)==0,"Miss has swing, no impact burst");
        combat.reset();combat.equip(true);combat.dummies.get(1).z=-1.4;
        combat.attack(CombatSystem.Move.PUMMEL,0,0,0,0);
        for(int i=0;i<125;i++) combat.update(.01,0,0,0);
        check(count(combat,GameEffect.Kind.GRAB)==1,"Pummel grab flash once");
        check(count(combat,GameEffect.Kind.SLAM)==1,"Pummel slam debris once");
        check(count(combat,GameEffect.Kind.HIT)==3,"Pummel emits three confirmed impacts");
        combat.reset();combat.equip(true);combat.dummies.get(1).z=-1.4;combat.dummies.get(1).health=1;
        combat.attack(CombatSystem.Move.PUNCH,0,0,0,0);combat.update(.13,0,0,0);
        check(count(combat,GameEffect.Kind.DEFEAT)==1,"Dummy defeat fragments once");
        combat.update(4.1,0,0,0);check(count(combat,GameEffect.Kind.RESPAWN)==1,"Dummy respawn emits gather/pulse");
        combat.effects.clear();combat.damagePlayer(115);combat.damagePlayer(20);
        check(count(combat,GameEffect.Kind.DEFEAT)==1,"Defeat fragments only on transition to dead");
        LobbyWorld world=new LobbyWorld();LobbyWorld.Player p=world.add("Host",true);world.action(p.id,4,"");
        world.action(p.id,1,"");world.tick(.02);world.action(p.id,1,"");world.tick(.02);
        world.action(p.id,2,"");for(int i=0;i<20;i++) world.tick(.02);
        check(world.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.TAKEOFF),"LAN takeoff event");
        check(world.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.DOUBLE_JUMP),"LAN double jump event");
        check(world.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.GROUND_POUND),"LAN ground pound impact event");
        long previous=0;
        for(GameEffect effect:world.effects) { check(effect.id>previous,"LAN effect IDs are ordered");previous=effect.id; }
        for(int i=0;i<150;i++) world.tick(.02);
        check(world.effects.isEmpty(),"Host drops old effect history");
    }
    static void game() throws Exception {
        CubeMovement game=new CubeMovement();game.setSize(1000,700);game.doLayout();
        check(game.executeCommand("particles low").contains("160"),"Console selects low quality");
        check(game.executeCommand("particles high").contains("800"),"Console selects high quality");
        check(game.executeCommand("particles invalid").startsWith("Error:"),"Validate quality command");
        ParticleSystem system=(ParticleSystem)MovementCheck.field("particles").get(game);
        CombatSystem combat=(CombatSystem)MovementCheck.field("combat").get(game);
        Method update=CubeMovement.class.getDeclaredMethod("updateParticles",double.class);update.setAccessible(true);
        MovementCheck.field("paused").setBoolean(game,false);
        for(GameEffect.Kind kind:GameEffect.Kind.values()) combat.effects.add(event(kind));
        update.invoke(game,.016);check(!system.particles.isEmpty() && combat.effects.isEmpty(),"Game consumes events once");
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(1000,700,java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=image.createGraphics();game.paint(g);g.dispose();
        double life=system.particles.get(0).life;
        MovementCheck.field("paused").setBoolean(game,true);update.invoke(game,.2);
        check(system.particles.get(0).life==life,"Solo pause freezes particle lifetimes");
        game.executeCommand("particles clear");check(system.particles.isEmpty(),"Console clears particles");
        game.executeCommand("reset");check(system.quality()==ParticleSystem.Quality.MEDIUM,"Reset restores default quality");
        System.out.println("Passed: particle types, caps, motion, expiry/delay, quality, trails/status, confirmed combat events, LAN movement effects, rendering, commands, pause/reset.");
    }
    public static void main(String[] args) {
        try {
            particles();events();
            SwingUtilities.invokeAndWait(()->{try{game();}catch(Exception error){throw new RuntimeException(error);}});
            System.exit(0);
        } catch(Throwable error) { error.printStackTrace();System.exit(1); }
    }
}
