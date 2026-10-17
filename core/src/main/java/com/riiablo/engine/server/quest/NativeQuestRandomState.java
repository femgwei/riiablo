package com.riiablo.engine.server.quest;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.map.Map;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** World-owned quest-control RNG, matching D2Game's QUESTS_GetGlobalSeed. */
@Wire(failOnNull = false)
public final class NativeQuestRandomState extends BaseSystem {
  @Wire(name = "map", failOnNull = false)
  private Map map;
  private final D2Seed seed = new D2Seed();

  @Override protected void initialize() {
    Seed.initLowSeed(seed, map == null ? Riiablo.gameSeed : map.seed());
  }

  public D2Seed seed() { return seed; }

  @Override protected void processSystem() {}
}
