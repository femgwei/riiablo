package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Observable mode boundaries in real ECS ticks; no native resources or GL needed. */
class SimulationModeIntegrationTest {
  @ParameterizedTest
  @CsvSource({"LOCAL, true", "DEDICATED_SERVER, true", "REMOTE_CLIENT, false"})
  void onlyAuthorityBlocksMovementOnDynamicUnitFootprints(
      SimulationMode mode, boolean authoritative) {
    DynamicUnitCollisionSystem collision = mode.createDynamicUnitCollisionSystem();
    World world = new World(new WorldConfigurationBuilder().with(collision).build());
    try {
      int mover = world.create();
      int blocker = world.create();
      for (int entity : new int[] {mover, blocker}) {
        world.getMapper(Class.class).create(entity).type = Class.Type.PLR;
        world.getMapper(Position.class).create(entity).position.set(entity == mover ? 0 : 10, 0);
        world.getMapper(Size.class).create(entity).size = 1;
        world.getMapper(Velocity.class).create(entity);
      }
      world.process();
      assertEquals(!authoritative, collision.tryMove(mover, new Vector2(10, 0)));
      assertEquals(0f, world.getMapper(Position.class).get(mover).position.x,
          "collision permission does not itself integrate position");
      assertEquals(true, collision.tryMove(mover, new Vector2(5, 0)));
    } finally {
      world.dispose();
    }
  }

  @ParameterizedTest
  @CsvSource({"LOCAL, true", "DEDICATED_SERVER, true", "REMOTE_CLIENT, false"})
  void onlyAuthorityRecomputesNetworkedMovementModes(SimulationMode mode, boolean authoritative) {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), mode.createVelocityModeChanger()).build());
    try {
      int networked = movementEntity(world);
      int unnetworked = movementEntity(world);
      world.getMapper(Networked.class).create(networked);
      world.process();
      assertEquals(authoritative ? 0 : 7, world.getMapper(CofReference.class).get(networked).mode);
      assertEquals(authoritative ? -1 : 123, world.getMapper(AnimData.class).get(networked).override);
      assertEquals(0, world.getMapper(CofReference.class).get(unnetworked).mode);

      world.getMapper(Velocity.class).get(networked).velocity.set(1, 0);
      world.getMapper(Velocity.class).get(unnetworked).velocity.set(1, 0);
      world.process();
      assertEquals(authoritative ? 1 : 7, world.getMapper(CofReference.class).get(networked).mode);
      assertEquals(1, world.getMapper(CofReference.class).get(unnetworked).mode,
          "remote mode skips network replicas, not all local animation processing");
    } finally {
      world.dispose();
    }
  }

  private static int movementEntity(World world) {
    int entity = world.create();
    world.getMapper(MovementModes.class).create(entity).set((byte) 0, (byte) 1, (byte) 2);
    world.getMapper(Velocity.class).create(entity);
    world.getMapper(AnimData.class).create(entity).override = 123;
    world.getMapper(CofReference.class).create(entity).set("AM", (byte) 7);
    return entity;
  }
}
