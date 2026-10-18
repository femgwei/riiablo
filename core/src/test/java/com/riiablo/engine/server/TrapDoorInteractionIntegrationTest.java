package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrapDoorInteractionIntegrationTest extends RiiabloTest {
  @Test void nativeTrapDoorOpensThenUsesItsNearbyTileWithoutSelectingAnotherExit() {
    Objects.Entry hatch = Riiablo.files.objects.get(74); // ObjectsIds.h OBJECT_TRAP_DOOR
    assertNotNull(hatch);
    assertEquals(16, hatch.OperateFn);
    assertTrue(NativeObjectOperateTable.isNetworkDoor(hatch));
    ObjectInteractor interactor = new ObjectInteractor();
    WarpProbe probe = new WarpProbe();
    Map map = new Map(7, 0);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), interactor, probe)
        .build().register("map", map));
    try {
      Map.Zone town = new Map.Zone();
      town.addRoomEx(0, 0, 50, 50);
      Map.Zone other = new Map.Zone();
      int object = world.create();
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(object).base = hatch;
      world.getMapper(CofReference.class).create(object).set(hatch.Token, Engine.Object.MODE_NU);
      world.getMapper(Position.class).create(object).position.set(20, 20);
      world.getMapper(MapWrapper.class).create(object).zone = town;
      world.getMapper(Interactable.class).create(object).set(4f, interactor);
      addWarp(world, other, 20, 20);
      addWarp(world, town, 90, 90);
      int sewer = addWarp(world, town, 22, 20);
      world.process();
      interactor.interact(100, object);
      assertEquals(Engine.Object.MODE_ON, world.getMapper(CofReference.class).get(object).mode);
      assertEquals(0, probe.calls, "first click opens without travelling");
      assertTrue(world.getMapper(Interactable.class).has(object));
      interactor.interact(100, object);
      assertEquals(1, probe.calls);
      assertEquals(sewer, probe.target);
      world.delete(sewer);
      world.process();
      interactor.interact(100, object);
      assertEquals(1, probe.calls, "missing local tile must not choose a distant or foreign exit");
    } finally { world.dispose(); }
  }

  private static int addWarp(World world, Map.Zone zone, int x, int y) {
    int id = world.create();
    world.getMapper(Warp.class).create(id).index = 0;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(MapWrapper.class).create(id).zone = zone;
    return id;
  }

  @com.artemis.annotations.Wire(failOnNull = false)
  private static class WarpProbe extends WarpInteractor {
    int calls;
    int target;
    @Override public void interact(int player, int entity) { calls++; target = entity; }
  }
}
