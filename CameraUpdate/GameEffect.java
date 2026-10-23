/** A gameplay event; each device creates its own cosmetic particles from it. */
final class GameEffect {
    enum Kind { TAKEOFF, DOUBLE_JUMP, LAND, GROUND_POUND, SWING, GRAB, SLAM, HIT, SHOCKWAVE, DEFEAT, RESPAWN }
    final Kind kind;
    final double x, y, z, facing, strength;
    final int variant;
    long id;
    double time;
    GameEffect(Kind kind, double x, double y, double z, double facing, double strength, int variant) {
        this.kind = kind; this.x = x; this.y = y; this.z = z;
        this.facing = facing; this.strength = strength; this.variant = variant;
    }
}
