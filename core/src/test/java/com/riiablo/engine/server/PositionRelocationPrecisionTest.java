package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionRelocationPrecisionTest {
  @Test void sameProjectionWarpInvalidatesRawEvenWithoutAVisibleDisplacement() throws Exception {
    World world = new World();
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id).initializeNativeCell(10, 20);
      WarpInteractor warp = new WarpInteractor();
      warp.mPosition = world.getMapper(Position.class);
      warp.mMapWrapper = world.getMapper(MapWrapper.class);
      warp.mBox2DBody = world.getMapper(Box2DBody.class);
      warp.mUnitStates = world.getMapper(UnitStates.class);
      warp.actioneer = mock(Actioneer.class);
      java.lang.reflect.Method commit = WarpInteractor.class.getDeclaredMethod(
          "commitTransition", int.class, Map.Zone.class, Vector2.class);
      commit.setAccessible(true);
      commit.invoke(warp, id, new Map.Zone(), new Vector2(10, 20));
      assertEquals(new Vector2(10, 20), position.position);
      assertFalse(position.hasNativePrecision());
      assertThrows(IllegalStateException.class, position::nativePrecisionX);
    } finally { world.dispose(); }
  }

  @Test void spawnCorrectionLatchesInvalidationBeforeAnUnobservedReturnToOldProjection() {
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    World world = new World(new WorldConfigurationBuilder().with(collision).build());
    try {
      int player = world.create(), monster = world.create();
      for (int id : new int[] {player, monster}) {
        world.getMapper(Position.class).create(id).initializeNativeCell(10, 20);
        world.getMapper(Velocity.class).create(id);
        world.getMapper(Size.class).create(id).size = 1;
        world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type = id == player
            ? com.riiablo.engine.server.component.Class.Type.PLR
            : com.riiablo.engine.server.component.Class.Type.MON;
      }
      world.process();
      Position moved = world.getMapper(Position.class).get(monster);
      assertNotEquals(new Vector2(10, 20), moved.position);
      // Deliberately do not query raw while displaced: projection comparison
      // alone cannot detect this history after the vector returns to its old value.
      moved.position.set(10, 20);
      assertFalse(moved.hasNativePrecision());
      assertTrue(world.getMapper(Position.class).get(player).hasNativePrecision(),
          "the player was not relocated and must retain its raw state");
    } finally { world.dispose(); }
  }
}
