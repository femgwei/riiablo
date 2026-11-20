package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.path.D2PathMisc;
import com.d2moo.common.path.D2PathStep;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;

/** Firestorm's ordinary centre path and native two-cell charged-bolt waypoints. */
public final class NativeFirestormMotion {
  private NativeFirestormMotion() {}

  public static boolean maker(Missiles.Entry row) {
    return row != null && "firestormmaker".equalsIgnoreCase(row.Missile);
  }

  public static boolean fire(Missiles.Entry row) {
    return row != null && "firestorm".equalsIgnoreCase(row.Missile);
  }

  public static Vector2 initialVelocity(Missiles.Entry row, int level, Vector2 facing, Vector2 out) {
    int speed = (Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8) * 75 / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, facing, speed, out);
  }

  /**
   * Rebuild the bounded path from immutable cast inputs. This makes seeking,
   * checkpoints and trail callbacks independent of the damage RNG and float drift.
   * For Firestorm only, chargedBoltSeedLow/High retain the initializer seed;
   * other charged bolts keep their existing incremental path implementation.
   */
  public static Sample sample(Missile m, int age) {
    int x = NativePoisonMissileMotion.precision(m.start.x);
    int y = NativePoisonMissileMotion.precision(m.start.y);
    Vector2 facing = new Vector2(m.chargedBoltTargetX - m.start.x,
        m.chargedBoltTargetY - m.start.y);
    if (facing.isZero()) facing.set(Vector2.X);
    Vector2 v = initialVelocity(m.missile, m.damageLevel, facing, new Vector2());
    int vx = NativePoisonMissileMotion.frameStep(v.x), vy = NativePoisonMissileMotion.frameStep(v.y);
    age = Math.max(0, Math.min(age, Math.max(1, m.nativeLifetimeFrames)));
    if (!m.chargedBoltPath) return new Sample(x + age * vx, y + age * vy, vx, vy);

    int count = Math.max(1, Math.min(77, m.nativeLifetimeFrames)) / 2;
    int[] px = new int[count + 1], py = new int[count + 1];
    px[0] = x; py[0] = y;
    D2Seed seed = new D2Seed(m.chargedBoltSeedLow, m.chargedBoltSeedHigh);
    for (int i = 1; i <= count; i++) {
      int slot = (int) Seed.rollRandomNumber(seed) & 31;
      int offset = slot == 31 ? 1 : slot % 3 - 1;
      int direction = (m.chargedBoltMainDirection + offset + 8) & 7;
      px[i] = px[i - 1] + (D2PathMisc.directionOffsetX(direction) << 17);
      py[i] = py[i - 1] + (D2PathMisc.directionOffsetY(direction) << 17);
    }
    int point = 0;
    for (int tick = 0; tick < age; tick++) {
      int dx = px[point] - x, dy = py[point] - y;
      int maximum = Math.max(Math.abs(vx), Math.abs(vy));
      boolean reached = Math.abs(dx) <= maximum && Math.abs(dy) <= maximum;
      x += reached ? dx : vx;
      y += reached ? dy : vy;
      if (reached && point < count) {
        point++;
        D2PathStep.DirectionVector direction = D2PathStep.directionVector(x, y, px[point], py[point]);
        int speed = (Math.max(0, m.missile.Vel + Math.max(1, m.damageLevel)
            * m.missile.VelLev / 8) << 8) * 75 / 100;
        vx = direction.x * speed >> 8;
        vy = direction.y * speed >> 8;
      }
    }
    return new Sample(x, y, vx, vy);
  }

  public static int cell(float coordinate) { return MathUtils.floor(coordinate + .5f); }

  public static final class Sample {
    public final int x, y, vx, vy;
    Sample(int x, int y, int vx, int vy) { this.x = x; this.y = y; this.vx = vx; this.vy = vy; }
    public Vector2 position(Vector2 out) { return out.set(x / 65536f - .5f, y / 65536f - .5f); }
    public Vector2 velocity(Vector2 out) { return out.set(vx * (25f / 65536f), vy * (25f / 65536f)); }
    public boolean crossedCell(Sample previous) { return (x >> 16) != (previous.x >> 16)
        || (y >> 16) != (previous.y >> 16); }
  }
}
