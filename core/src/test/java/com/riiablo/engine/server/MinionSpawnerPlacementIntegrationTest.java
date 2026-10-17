package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class MinionSpawnerPlacementIntegrationTest extends RiiabloTest {
  @Test void skillActionChecksLiveMotherAndSameTickChildrenBeforeAllocating() {
    checkPlacement(135);
  }

  @Test void vileMotherNestChecksLiveMotherAndSameTickChildrenBeforeAllocating() {
    checkPlacement(91);
  }

  private void checkPlacement(int function) {
    Map map = mock(Map.class);
    Map.Zone zone = mock(Map.Zone.class); zone.map = map;
    when(zone.contains(anyInt(), anyInt())).thenReturn(true);
    when(map.getZone(any(Vector2.class))).thenReturn(zone);
    EntityFactory factory = mock(EntityFactory.class);
    when(factory.createMonster(any(MonStats.Entry.class), anyFloat(), anyFloat())).thenCallRealMethod();
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder().with(events, collision,
        new Actioneer(), new Pathfinder(), new CofManager(), factory).build()
        .register("map", map).register("factory", factory));
    List<Integer> children = new ArrayList<>(); List<Vector2> selected = new ArrayList<>();
    try {
      MonStats.Entry mother = Riiablo.files.monstats.get("vilemother3");
      MonStats.Entry child = Riiablo.files.monstats.get("vilechild3");
      Skills.Entry skill = null;
      for (Skills.Entry row : Riiablo.files.skills) if (row.srvdofunc == function) { skill = row; break; }
      assertNotNull(skill);
      int source = unit(world, mother, 10, 10);
      world.getMapper(MapWrapper.class).create(source).set(map, zone);
      when(factory.createMonster(eq(child.hcIdx), anyFloat(), anyFloat())).thenAnswer(call -> {
        int id = unit(world, child, call.getArgument(1), call.getArgument(2));
        children.add(id); selected.add(world.getMapper(Position.class).get(id).position.cpy());
        return id;
      });
      world.process();
      Casting cast = world.getMapper(Casting.class).create(source).set(skill.Id, -1, new Vector2(10, 10));
      cast.nativeSummonMode = Engine.Monster.MODE_NU;
      for (int i = 0; i < 2; i++) {
        cast.nativeSummonClass = child.hcIdx; cast.nativeSummonPosition.set(10, 10);
        events.dispatch(AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_MIS));
        assertEquals(i + 1, children.size(), mockingDetails(factory).printInvocations());
        Vector2 p = selected.get(i);
        assertFalse(UnitCollisionGrid.placementOverlapsMarker((int) p.x, (int) p.y,
            Riiablo.files.monstats2.get(child.MonStatsEx).SizeX, 10, 10,
            Riiablo.files.monstats2.get(mother.MonStatsEx).SizeX));
      }
      assertNotEquals(selected.get(0), selected.get(1));
      if (function == 135) {
        events.dispatch(AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_MIS));
        assertEquals(2, children.size(), "one frozen summon per skill start");
      }
      when(map.isBlockedForPath(eq(zone), anyInt(), anyInt(), anyInt(), anyInt(), eq(false))).thenReturn(true);
      cast.nativeSummonClass = child.hcIdx;
      events.dispatch(AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_MIS));
      assertEquals(2, children.size(), "blocked search cannot allocate an overlapping child");
      world.process();
      for (int i = 0; i < children.size(); i++) assertEquals(selected.get(i),
          world.getMapper(Position.class).get(children.get(i)).position);
    } finally { world.dispose(); }
  }

  private int unit(World world, MonStats.Entry row, float x, float y) {
    int id = world.create();
    world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
        com.riiablo.engine.server.component.Class.Type.MON;
    world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Size.class).create(id).size = Riiablo.files.monstats2.get(row.MonStatsEx).SizeX;
    world.getMapper(Velocity.class).create(id);
    return id;
  }
}
