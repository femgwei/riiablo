package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.UnitRandomSeed;
import java.util.function.IntUnaryOperator;

/** Live D2Common two-word pSeed, shared by AI, mode entry and audited native callbacks.
 * rngState is a diagnostic/legacy low-word mirror, never the persisted seed.
 * Explicit writes to the mirror reinitialize a stream for old tools/fixtures.
 */
public final class NativeUnitRandom implements IntUnaryOperator {
  private final D2Seed seed;
  private final Monster monster;
  private final Missile missile;
  private final UnitRandomSeed other;

  private NativeUnitRandom(Monster monster, Missile missile, D2Seed seed) {
    this(monster, missile, null, seed);
  }

  private NativeUnitRandom(Monster monster, Missile missile, UnitRandomSeed other, D2Seed seed) {
    this.monster = monster; this.missile = missile; this.other = other; this.seed = seed;
  }

  /** Block/dodge belong to the DEFENDER, including a player, not the attack stream. */
  public static NativeUnitRandom unit(com.artemis.World world, int gameSeed, int id) {
    if (world.getMapper(Monster.class).has(id)) return monster(world.getMapper(Monster.class).get(id), gameSeed, id);
    if (world.getMapper(Missile.class).has(id)) return missile(world.getMapper(Missile.class).get(id), gameSeed, id);
    UnitRandomSeed state = world.getMapper(UnitRandomSeed.class).create(id);
    if (state.seed == null || state.seed.getNLowSeed() != state.low) {
      state.seed = new D2Seed(state.low != 0 ? state.low : gameSeed ^ id * 0x45D9F3B, 666);
      state.low = state.seed.getNLowSeed();
    }
    return new NativeUnitRandom(null, null, state, state.seed);
  }

  public static NativeUnitRandom monster(Monster unit, int gameSeed, int id) {
    if (unit.nativeSeed == null || unit.nativeSeed.getNLowSeed() != unit.rngState) {
      unit.nativeSeed = new D2Seed(unit.rngState != 0 ? unit.rngState
          : gameSeed ^ id * 0x45D9F3B, 666);
      unit.rngState = unit.nativeSeed.getNLowSeed();
    }
    return new NativeUnitRandom(unit, null, unit.nativeSeed);
  }

  public static NativeUnitRandom missile(Missile unit, int gameSeed, int id) {
    if (unit.nativeSeed == null || unit.nativeSeed.getNLowSeed() != unit.rngState) {
      unit.nativeSeed = new D2Seed(unit.rngState != 0 ? unit.rngState
          : gameSeed ^ id * 0x45D9F3B, 666);
      unit.rngState = unit.nativeSeed.getNLowSeed();
    }
    return new NativeUnitRandom(null, unit, unit.nativeSeed);
  }

  private void mirror() {
    if (monster != null) monster.rngState = seed.getNLowSeed();
    else if (missile != null) missile.rngState = seed.getNLowSeed();
    else other.low = seed.getNLowSeed();
  }

  public int nextInt(int bound) {
    int value = Seed.rollLimitedRandomNumber(seed, bound); mirror(); return value;
  }
  @Override public int applyAsInt(int bound) { return nextInt(bound); }
  public long nextUnsigned() {
    long value = Seed.rollRandomNumber(seed) & 0xFFFFFFFFL; mirror(); return value;
  }
  /** AI uses SEED_RollPercentage (full uint64), not ITEMS' truncated uint32. */
  public int percentage() {
    int value = Seed.rollPercentage(seed); mirror(); return value;
  }
  public int state() { return seed.getNLowSeed(); }
  public int high() { return seed.getNHighSeed(); }
  /** Captures both native D2Seed words; the low-word mirror alone is not sufficient. */
  public Snapshot snapshot() { return new Snapshot(state(), high()); }
  /** Restores both native D2Seed words and updates the legacy low-word mirror. */
  public void restore(Snapshot snapshot) {
    if (snapshot == null) throw new NullPointerException("snapshot");
    seed.setSeeds(snapshot.lowSeed, snapshot.highSeed);
    mirror();
  }
  public boolean roll(int numerator, int denominator) {
    return numerator > 0 && denominator > 0 && nextInt(denominator) < numerator;
  }

  public static final class Snapshot {
    public final int lowSeed;
    public final int highSeed;

    public Snapshot(int lowSeed, int highSeed) {
      this.lowSeed = lowSeed;
      this.highSeed = highSeed;
    }
  }
}
