package com.riiablo.presentation;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.missile.NativeMoltenBoulder;
import com.riiablo.engine.server.missile.NativePoisonMissileMotion;

/** 1.10f CltDo50 elapsed-time gate, target-distance flight and fp11 graphics arc. */
final class NativeVolcanoPresentation {
  private NativeVolcanoPresentation() {}

  static boolean emits(int age, int period, int firstExclusive, int lastExclusive) {
    return age > firstExclusive && age < lastExclusive && age % Math.max(1, period) == 0;
  }

  static Flight flight(Missiles.Entry row, int level, int originX, int originY,
      int radius, D2Seed seed) {
    // CltDo50 resets the high half with InitLowSeed(TargetX) each emission,
    // then retains the resulting low half in TargetX for the next emission.
    D2Seed current = new D2Seed(seed.getNLowSeed(), 666);
    int dx = (int) Seed.rollLimitedRandomNumber(current, radius * 2 + 1) - radius;
    int dy = (int) Seed.rollLimitedRandomNumber(current, radius * 2 + 1) - radius;
    seed.setNLowSeed(current.getNLowSeed());
    if (dx == 0 && dy == 0) {dx = 1; dy = 1;}
    int speed = (Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8) * 75 / 100;
    Vector2 velocity = NativePoisonMissileMotion.velocity(Vector2.Zero, new Vector2(dx, dy), speed, new Vector2());
    int distance = Math.max(1, NativeMoltenBoulder.distance(dx, dy));
    int life = speed > 0 ? Math.max(1, (distance << 16) / (speed * 16)) : Math.max(1, row.Range);
    return new Flight(originX + dx, originY + dy, life,
        NativePoisonMissileMotion.frameStep(velocity.x), NativePoisonMissileMotion.frameStep(velocity.y));
  }

  static int heightFixed(int life, int height, int updates) {
    int z = height << 11, acceleration = -4096;
    life = Math.max(1, life);
    int velocity = (-z - acceleration * life * life / 2) / life;
    for (int tick = 0; tick < Math.max(0, updates); tick++) {z += velocity; velocity += acceleration;}
    return z;
  }

  static int rockFrame(int frames, int animationLength, int life, int age) {
    if (frames <= 0) return 0;
    // CltDo1 uses the table AnimLen for its countdown gate. The last cursor
    // can equal the asset frame count, where native DCC selection wraps to zero.
    return Math.max(0, age - Math.max(0, life - animationLength - 1)) % frames;
  }

  static final class Flight {
    final int targetX, targetY, lifetime, velocityX, velocityY;
    Flight(int x, int y, int life, int vx, int vy) {targetX=x; targetY=y; lifetime=life; velocityX=vx; velocityY=vy;}
  }
}
