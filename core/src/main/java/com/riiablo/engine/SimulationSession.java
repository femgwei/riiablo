package com.riiablo.engine;

import com.artemis.WorldConfiguration;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import java.util.Objects;

/**
 * Explicit bindings for one simulation session's world/environment boundary.
 *
 * <p>The host creates and owns these resources. This object neither copies nor
 * disposes them, and deliberately holds no World, socket, UI or global singleton.
 * A new game must supply its own map and environment; changing acts continues
 * to use the same live map. Other session services can migrate independently.
 */
public final class SimulationSession {
  private final SimulationMode mode;
  private final Map map;
  private final EnvironmentCycle environment;

  public SimulationSession(SimulationMode mode, Map map, EnvironmentCycle environment) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.map = Objects.requireNonNull(map, "map");
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  public SimulationMode mode() {
    return mode;
  }

  public Map map() {
    return map;
  }

  public EnvironmentCycle environment() {
    return environment;
  }

  /** Keeps unmigrated named consumers bound to exactly the same live objects. */
  public WorldConfiguration registerServices(WorldConfiguration config) {
    return Objects.requireNonNull(config, "config")
        .register("simulationSession", this)
        .register("map", map)
        .register("environment", environment);
  }
}
