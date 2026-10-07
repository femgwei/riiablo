package com.riiablo.engine.server;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;

/** Advances the D2Common environment clock once per fixed simulation tick. */
public final class EnvironmentSystem extends BaseSystem {
  private static final String TAG = "EnvironmentSystem";

  @Wire(name = "environment")
  protected EnvironmentCycle environment;
  @Wire(name = "map")
  protected Map map;

  @Override
  protected void initialize() {
    logState("initialized");
  }

  @Override
  protected void processSystem() {
    if (environment.advance(map == null ? 0 : map.getAct())) {
      logState("transition");
    }
  }

  private void logState(String reason) {
    Gdx.app.log(TAG, String.format(
        "[ENVIRONMENT] reason=%s cycle=%d period=%s ticks=%d eclipse=%s",
        reason, environment.cycleIndex(),
        EnvironmentCycle.periodName(environment.periodOfDay()),
        environment.ticks(), environment.eclipse()));
  }
}
