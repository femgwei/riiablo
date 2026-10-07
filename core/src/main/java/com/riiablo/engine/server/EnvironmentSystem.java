package com.riiablo.engine.server;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;

/** Advances the D2Common environment clock once per fixed simulation tick. */
public final class EnvironmentSystem extends BaseSystem {
  @Wire(name = "environment")
  protected EnvironmentCycle environment;
  @Wire(name = "map")
  protected Map map;

  @Override
  protected void processSystem() {
    environment.advance(map == null ? 0 : map.getAct());
  }
}
