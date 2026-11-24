package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidWindOriginalParityTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/druid-wind-animation-1.10f.json"));
  }

  @Test void allObservedClientCursorsAndSkillRateOverridesMatch() {
    int checked = 0;
    for (JsonValue cast : fixture().get("cases")) {
      Missile m = new Missile(); m.skillId = cast.getInt("skill_id");
      m.missile = Riiablo.files.Missiles.get(cast.name);
      for (JsonValue s : cast.get("samples")) {
        assertEquals(s.getInt("rate"), NativeDruidWindPresentation.rate(m));
        assertEquals(s.getInt("cursor") >> 8, NativeDruidWindPresentation.frame(m.missile, 41,
            s.getInt("lifetime"), s.getInt("age"), NativeDruidWindPresentation.rate(m)), cast.name+" age="+s.getInt("age"));
        checked++;
      }
    }
    assertEquals(221, checked);
  }

  @Test void realProductionPreservesCountsLifetimesAndEveryLoadedObservedAge() {
    int checked = 0, unloadedBirths = 0;
    for (JsonValue cast : fixture().get("cases")) {
      Map<String, Object> port = DruidSurfaceOriginalAnalysis.simulate(cast);
      List<Map<?, ?>> births = births(port, cast.name);
      assertEquals("twister".equals(cast.name) ? 3 : 1, births.size());
      for (Map<?, ?> b : births) assertEquals("twister".equals(cast.name) ? 50 : 75, b.get("lifetime"));
      for (JsonValue s : cast.get("samples")) {
        int age = s.getInt("age");
        if (age == 0) {unloadedBirths++; continue;} // Loader first runs on the first world tick.
        Set<Integer> frames = new HashSet<>();
        for (Map<?, ?> b : births) for (Object value : (List<?>) b.get("states")) {
          Map<?, ?> state = (Map<?, ?>) value;
          if (Boolean.TRUE.equals(state.get("loaded")) && age == ((Number) state.get("age")).intValue())
            frames.add(((Number) state.get("frame")).intValue());
        }
        assertEquals(Collections.singleton(s.getInt("cursor") >> 8), frames, cast.name+" age="+age); checked++;
      }
    }
    assertEquals(217, checked); assertEquals(4, unloadedBirths);
  }

  @Test void lateAndNeverLoadedResourcesDoNotRestartAnimationOrExtendMissileLife() {
    for (JsonValue cast : fixture().get("cases")) for (int loadTick : new int[] {30, 1000}) {
      for (Map<?, ?> b : births(DruidSurfaceOriginalAnalysis.simulate(cast, loadTick), cast.name)) {
        int life = ((Number) b.get("lifetime")).intValue();
        assertEquals(life, b.get("removed_tick"));
        for (Object value : (List<?>) b.get("states")) {
          Map<?, ?> s = (Map<?, ?>) value;
          int age = ((Number) s.get("age")).intValue();
          if (Boolean.TRUE.equals(s.get("loaded"))) assertEquals(NativeDruidWindPresentation.frame(
              Riiablo.files.Missiles.get(cast.name), 41, life, age, "tornado".equals(cast.name) ? 512 : 256), s.get("frame"));
          if (age < loadTick) assertEquals(false, s.get("loaded"));
        }
      }
    }
  }

  @Test void coldReplicaClockIsContinuousAtSixtyHzAndCannotRewindOnSparseSnapshots() {
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), factory, new OverlayManager(),
        new MissileImpactPresentationSystem()).build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0,0))
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create(); Missile m = world.getMapper(Missile.class).create(id);
      m.skillId = 245; m.missile = Riiablo.files.Missiles.get("tornado"); m.nativeLifetimeFrames = 75; m.nativeFrame = 10;
      m.authoritative = false;
      world.getMapper(Position.class).create(id).position.set(10,20);
      world.getMapper(Velocity.class).create(id).velocity.set(5,5);
      world.setDelta(1f/60f);
      for (int i=0; i<60; i++) world.process();
      assertEquals(35, m.clientFrame); assertEquals(10, m.nativeFrame);
      assertEquals(10f, world.getMapper(Position.class).get(id).position.x);
      m.nativeFrame = 12; world.process(); assertTrue(m.clientFrame >= 35);
      m.nativeFrame = 50; world.process(); assertTrue(m.clientFrame >= 50);
      assertEquals(50, m.nativeFrame, "local rendering never advances the received simulation clock");
    } finally {world.dispose();}
  }

  @Test void countdownExitKeepsItsCurrentCursorInsteadOfInventingAJumpToTheOutro() {
    com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get("tornado");
    assertEquals(20, NativeDruidWindPresentation.frame(row,41,75,70,512));
    assertEquals(22, NativeDruidWindPresentation.frame(row,41,75,71,512));
    assertEquals(28, NativeDruidWindPresentation.frame(row,41,75,74,512));
    Missile unrelated = new Missile(); unrelated.skillId=41; unrelated.missile=row;
    assertFalse(NativeDruidWindPresentation.handles(unrelated));
  }

  private static List<Map<?, ?>> births(Map<String, Object> port, String name) {
    List<Map<?, ?>> result = new ArrayList<>();
    for (Object value : (List<?>) port.get("births")) {
      Map<?, ?> birth = (Map<?, ?>) value;
      if (name.equals(birth.get("missile")) && Boolean.TRUE.equals(birth.get("presentation_only"))) result.add(birth);
    }
    return result;
  }
}
