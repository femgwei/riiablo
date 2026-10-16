package com.riiablo.engine.server;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import java.util.Objects;

/** Advances the D2Common environment clock once per fixed simulation tick. */
public final class EnvironmentSystem extends BaseSystem {
  private static final String TAG = "EnvironmentSystem";

  private final EnvironmentCycle environment;
  private final Map map;

  public EnvironmentSystem(Map map, EnvironmentCycle environment) {
    this.map = Objects.requireNonNull(map, "map");
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  @Override
  protected void initialize() {
    logState("initialized");
  }

  @Override
  protected void processSystem() {
    if (environment.advance(map.getAct())) {
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
