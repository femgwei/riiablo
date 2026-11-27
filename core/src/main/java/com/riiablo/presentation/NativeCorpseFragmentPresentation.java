package com.riiablo.presentation;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.missile.NativePoisonMissileMotion;
import com.riiablo.presentation.component.CorpseFragment;

/** Retail scatter/landing clock, with an explicitly bounded local residue budget. */
final class NativeCorpseFragmentPresentation {
  private NativeCorpseFragmentPresentation() {}

  // Retail CltDo11 marks the terminal sprite dead and releases its light;
  // the captured residue outlives the short Range=13 animation. Its eventual
  // room cleanup is not captured. 128 ticks is OUR bounded reclamation policy,
  // shared with the blood row's Range, not a measured retail residue lifetime.
  static final int RESIDUE_BUDGET_FRAMES = 128;

  interface Spawn {
    void create(Missiles.Entry row, Vector2 at, Vector2 facing);
  }

  /** Default retail blood policy: two gore fragments, three SubMissile2 bloods. */
  static void scatter(Missiles.Entry gore, Missiles.Entry blood, Vector2 at,
      D2Seed seed, Spawn spawn) {
    // CltDo32 consumes two [-1,1] direction-reference draws before scatter.
    // The family's helper then picks a quadrant and jitters each birth in
    // [-radius,radius-1], rotating the outward diagonal after each fragment.
    Seed.rollLimitedRandomNumber(seed, 3);
    Seed.rollLimitedRandomNumber(seed, 3);
    Vector2 lastGore = scatterFamily(gore, at, 2, 2, seed, spawn);
    // The helper mutates the creation parameters. CltDo32 does not restore
    // x/y until after its blood-family call, so blood jitters around the last
    // gore birth, rather than starting a fresh ring at the corpse centre.
    if (blood != null) scatterFamily(blood, lastGore, 1, 3, seed, spawn);
  }

  private static Vector2 scatterFamily(Missiles.Entry row, Vector2 at, int radius,
      int count, D2Seed seed, Spawn spawn) {
    int quadrant = Seed.rollLimitedRandomNumber(seed, 4);
    Vector2 last = new Vector2(at);
    for (int i = 0; i < count; i++, quadrant = (quadrant + 1) & 3) {
      Vector2 birth = new Vector2(Math.round(at.x) + Seed.rollLimitedRandomNumber(seed, radius * 2) - radius,
          Math.round(at.y) + Seed.rollLimitedRandomNumber(seed, radius * 2) - radius);
      Vector2 facing = new Vector2(quadrant < 2 ? -1 : 1,
          quadrant == 0 || quadrant == 3 ? -1 : 1);
      spawn.create(row, birth, facing);
      last.set(birth);
    }
    return last;
  }

  static Vector2 velocity(Missiles.Entry row, Vector2 facing, Vector2 out) {
    return NativePoisonMissileMotion.velocity(Vector2.Zero, facing,
        (Math.max(0, row.Vel) << 8) * 75 / 100, out);
  }

  static int frame(Missile missile, int frames) {
    int rate = missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256;
    return (int) Math.max(0, Math.min(frames - 1L, (long) missile.nativeFrame * rate / 256));
  }

  static void initialize(Missile missile, Animation animation) {
    int frames = animation.getNumFramesPerDir();
    if (frames > 0) animation.setFrame(frame(missile, frames));
  }

  static boolean advance(Missile missile, CorpseFragment fragment, Position position,
      Vector2 velocity, float delta) {
    fragment.time += Math.max(0f, delta);
    int age = (int) Math.floor(fragment.time * 25 + .00001);
    int rate = missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256;
    int landing = Math.max(0, (Math.max(1, missile.missile.AnimLen) - 1) * 256 / Math.max(1, rate));
    int steps = Math.min(age, landing) - Math.min(missile.nativeFrame, landing);
    if (steps > 0) NativePoisonMissileMotion.advance(position, velocity, steps);
    missile.nativeFrame = age;
    if (age >= landing) velocity.setZero();
    return age >= RESIDUE_BUDGET_FRAMES;
  }
}
