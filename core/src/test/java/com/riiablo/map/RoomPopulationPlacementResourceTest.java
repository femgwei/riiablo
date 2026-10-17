package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.RoomActivationSystem;
import com.riiablo.engine.server.UnitCollisionGrid;
import com.riiablo.engine.server.component.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomPopulationPlacementResourceTest extends RiiabloTest {
  @Test void activationAvoidsPlayerAndReservesEveryUnitBeforeSubscriptionInsertion() {
    MonStats.Entry large = null;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      MonStats2.Entry stats = Riiablo.files.monstats2.get(row.MonStatsEx);
      if (row.killable && stats != null && stats.SizeX == 3 && !stats.critter
          && !NativeGroundPathProfile.hasNoPresence(row)) { large = row; break; }
    }
    assertNotNull(large);
    Map map = new Map(1, 0);
    Map.Zone zone = new Map.Zone(); zone.map = map;
    zone.width = zone.height = 40; zone.flags = new byte[1600];
    map.getZones().add(zone);
    Map.RoomEx a = zone.addRoomEx(0, 0, 20, 40), b = zone.addRoomEx(20, 0, 20, 40);
    a.setAdjacentRoomIds(new int[] {1}); b.setAdjacentRoomIds(new int[] {0});
    a.addMonsterSpawn(large.hcIdx, 10, 10);
    a.addMonsterSpawn(large.hcIdx, 10, 10);
    b.addMonsterSpawn(large.hcIdx, 20, 10);
    EntityFactory factory = mock(EntityFactory.class);
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    List<Integer> ids = new ArrayList<>(); List<Vector2> allocated = new ArrayList<>();
    World world = new World(new WorldConfigurationBuilder().with(collision, new RoomActivationSystem(), factory)
        .build().register("factory", factory).register("map", map));
    try {
      when(factory.createMonsterAtIntegerSelection(anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt()))
          .thenAnswer(call -> factory.createMonster(call.getArgument(0),
              (float) (int) call.getArgument(1), (float) (int) call.getArgument(2),
              call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6)));
      when(factory.createMonster(anyInt(), anyFloat(), anyFloat(), anyInt(), anyLong(), anyInt(), anyInt()))
          .thenAnswer(call -> {
            int id = world.create();
            MonStats.Entry row = Riiablo.files.monstats.get((int) call.getArgument(0));
            world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
            world.getMapper(Position.class).create(id).position.set((float) call.getArgument(1), (float) call.getArgument(2));
            world.getMapper(Velocity.class).create(id);
            world.getMapper(Size.class).create(id).size = 3;
            world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
                com.riiablo.engine.server.component.Class.Type.MON;
            ids.add(id); allocated.add(world.getMapper(Position.class).get(id).position.cpy());
            return id;
          });
      int player = world.create();
      world.getMapper(Player.class).create(player);
      world.getMapper(Position.class).create(player).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(player).set(map, zone);
      world.getMapper(Velocity.class).create(player);
      world.getMapper(Size.class).create(player).size = 2;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(player).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      world.process();
      assertEquals(3, ids.size(), "placement must preserve the queued population");
      for (int i = 0; i < ids.size(); i++) {
        Vector2 p = allocated.get(i);
        assertFalse(UnitCollisionGrid.placementOverlapsMarker((int) p.x, (int) p.y, 3, 10, 10, 2));
        for (int j = 0; j < i; j++) {
          Vector2 q = allocated.get(j);
          assertFalse(UnitCollisionGrid.placementOverlapsMarker((int) p.x, (int) p.y, 3, (int) q.x, (int) q.y, 3));
        }
      }
      world.process(); collision.rebuildNow();
      for (int i = 0; i < ids.size(); i++) assertEquals(allocated.get(i),
          world.getMapper(Position.class).get(ids.get(i)).position, "no later repair teleport");
      assertEquals(new Vector2(10, 10), world.getMapper(Position.class).get(player).position);
    } finally { world.dispose(); }
  }
}
