package com.riiablo.engine.server.quest;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.map.Map;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** World-owned quest-control RNG. Native game-seed derivation is not yet shared. */
@Wire(failOnNull = false)
public final class NativeQuestRandomState extends BaseSystem {
  @Wire(name = "map", failOnNull = false)
  private Map map;
  private final D2Seed seed = new D2Seed();
  private boolean restored;

  @Override protected void initialize() {
    if (!restored) Seed.initLowSeed(seed, map == null ? Riiablo.gameSeed : map.seed());
  }

  public D2Seed seed() { return seed; }

  /** Snapshot hooks; a world persistence caller is not installed yet. */
  public int lowSeed() { return seed.getNLowSeed(); }
  public int highSeed() { return seed.getNHighSeed(); }
  public void restore(int lowSeed, int highSeed) {
    seed.setSeeds(lowSeed, highSeed);
    restored = true;
  }

  @Override protected void processSystem() {}
}
