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

/** Captured original vectors/positions through the production factory and mover. */
class BoneSpearOriginalMotionTest extends RiiabloTest {
  @Test void factoryAndFortyTickFlightMatchOriginalServerPath() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/bone-spear-1.10f.json"));
    JsonValue states = null;
    for (JsonValue unit : fixture.get("units")) {
      if ("server".equals(unit.getString("side")) && "bonespear".equals(unit.getString("missile"))) {
        assertNull(states, "one original server spear");
        states = unit.get("states");
      }
    }
    assertNotNull(states);
    assertEquals(40, states.size);
    ServerEntityFactory factory = new ServerEntityFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ItemManager(), new ObjectInteractor(),
            new WarpInteractor(), new ItemInteractor(), factory, new MissileCollisionSystem())
        .build().register("factory", factory).register("map", new Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      JsonValue first = states.child;
      int id = factory.createMissile(Riiablo.files.Missiles.get("bonespear"),
          new Vector2(first.getInt("velocity_x"), first.getInt("velocity_y")).nor(),
          new Vector2(first.getInt("x_fixed") / 65536f - .5f,
              first.getInt("y_fixed") / 65536f - .5f), -1, 1);
      world.setDelta(1f / 25f);
      for (JsonValue state : states) {
        if (state != first) world.process();
        Vector2 velocity = world.getMapper(Velocity.class).get(id).velocity;
        assertEquals(state.getInt("velocity_x"), Math.round(velocity.x * 65536f / 25f), "x speed");
        assertEquals(state.getInt("velocity_y"), Math.round(velocity.y * 65536f / 25f), "y speed");
        Position position = world.getMapper(Position.class).get(id);
        assertTrue(position.hasNativePrecision());
        assertEquals(state.getInt("x_fixed"), position.nativePrecisionX());
        assertEquals(state.getInt("y_fixed"), position.nativePrecisionY());
        Missile missile = world.getMapper(Missile.class).get(id);
        assertEquals(state.getInt("current_frame"), missile.nativeLifetimeFrames - missile.nativeFrame);
      }
      world.process();
      assertFalse(world.getEntityManager().isActive(id), "expires after forty native updates");
    } finally { world.dispose(); }
  }
}
