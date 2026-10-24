package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import java.util.Objects;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * Minimal client-world harness for system tests.
 *
 * <p>It deliberately does not construct a {@code GameScreen}, renderer, asset
 * loader or network socket. Tests can install only the systems under test and
 * provide a session context explicitly, while Artemis still performs the same
 * injection and lifecycle callbacks as a production world.
 */
public final class ClientSystemHarness implements AutoCloseable {
  private final World world;

  public ClientSystemHarness(ClientActTransitionPort clientActTransition,
      ClientPlayerPort clientPlayer, BaseSystem... systems) {
    Objects.requireNonNull(clientActTransition, "clientActTransition");
    Objects.requireNonNull(clientPlayer, "clientPlayer");
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem());
    if (systems != null) {
      for (BaseSystem system : systems) {
        if (system != null) builder.with(system);
      }
    }
    this.world = new World(builder.build()
        .register("clientActTransition", clientActTransition)
        .register("clientPlayer", clientPlayer));
  }

  public World world() { return world; }

  public void step() { world.setDelta(1f / 25f); world.process(); }

  @Override public void close() { world.dispose(); }
}

