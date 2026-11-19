package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Independent original 1.10f vectors/positions replayed through the real factory and mover. */
class FrozenOrbOriginalMotionTest extends RiiabloTest {
  @Test void creationAndNovaTurnsMatchCapturedServerStates() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/frozen-orb-motion-1.10f.json"));
    for (JsonValue scenario : fixture.get("scenarios")) {
      ServerEntityFactory factory = new ServerEntityFactory();
      World world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new ItemManager(), new ObjectInteractor(),
              new WarpInteractor(), new ItemInteractor(), factory, new MissileCollisionSystem())
          .build().register("factory", factory).register("map", new Map(0, 0))
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
      try {
        JsonValue first = scenario.get("states").child;
        int id = factory.createMissile(Riiablo.files.Missiles.get(scenario.getString("missile")),
            new Vector2(scenario.getInt("target_x"), scenario.getInt("target_y")),
            new Vector2(first.getInt("x_fixed") / 65536f - .5f,
                first.getInt("y_fixed") / 65536f - .5f), -1, 1);
        Missile missile = world.getMapper(Missile.class).get(id);
        if ("frozenorbnova".equals(scenario.getString("missile"))) {
          missile.frozenOrbNova = true;
          missile.frozenOrbTargetX = scenario.getInt("target_x");
          missile.frozenOrbTargetY = scenario.getInt("target_y");
        }
        world.setDelta(1f / 25f);
        for (JsonValue state : scenario.get("states")) {
          if (state != first) world.process();
          String context = scenario.getString("missile") + " original unit "
              + scenario.getInt("original_unit_id") + " frame " + state.getInt("game_frame");
          Vector2 velocity = world.getMapper(Velocity.class).get(id).velocity;
          assertEquals(state.getInt("velocity_x"), Math.round(velocity.x * 65536f / 25f), context);
          assertEquals(state.getInt("velocity_y"), Math.round(velocity.y * 65536f / 25f), context);
          Position position = world.getMapper(Position.class).get(id);
          assertTrue(position.hasNativePrecision(), context);
          assertEquals(state.getInt("x_fixed"), position.nativePrecisionX(), context);
          assertEquals(state.getInt("y_fixed"), position.nativePrecisionY(), context);
        }
      } finally { world.dispose(); }
    }
  }
}
