package com.riiablo.engine.server;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.EntityFactory;
import com.riiablo.map.Map;
import java.util.Objects;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Minimal authoritative world for isolated systems, without a Client, GL or socket. */
public final class ServerSystemHarness implements AutoCloseable {
  private final World world;
  private final AuthoritativeSimulation simulation;

  public ServerSystemHarness(ServerGameplayContext context, Map map, BaseSystem... systems) {
    this(context, map, config -> {}, systems);
  }

  public ServerSystemHarness(ServerGameplayContext context, Map map,
      java.util.function.Consumer<WorldConfiguration> bindings, BaseSystem... systems) {
    Objects.requireNonNull(context, "context");
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new EventSystem());
    EntityFactory factory = null;
    for (BaseSystem system : Objects.requireNonNull(systems, "systems")) {
      builder.with(Objects.requireNonNull(system, "system"));
      if (system instanceof EntityFactory) {
        if (factory != null) throw new IllegalArgumentException("Only one entity factory per world");
        factory = (EntityFactory) system;
      }
    }
    WorldConfiguration config = builder.build()
        .register("serverContext", context)
        .register("map", Objects.requireNonNull(map, "map"));
    if (factory != null) config.register("factory", factory);
    Objects.requireNonNull(bindings, "bindings").accept(config);
    world = new World(config);
    simulation = new AuthoritativeSimulation(world);
  }

  public World world() { return world; }
  public AuthoritativeSimulation simulation() { return simulation; }
  public void step() { simulation.tick(null, null); }
  @Override public void close() { world.dispose(); }
}
