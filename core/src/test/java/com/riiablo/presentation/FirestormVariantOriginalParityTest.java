package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import java.util.*;
import org.junit.jupiter.api.Test;

class FirestormVariantOriginalParityTest extends RiiabloTest {
  private JsonValue original() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/firestorm-variants-1.10f.json"));
  }

  @Test void everyRecordedCltDo6SelectionUsesTheSameThreeAlternatives() {
    Missile maker = new Missile().set(Riiablo.files.Missiles.get("firestormmaker"), Vector2.Zero, 40);
    Set<String> selected = new HashSet<>(); int count = 0;
    for (JsonValue choice : original().get("callback_choices")) {
      maker.clientSeed = new D2Seed(choice.getInt("seed_low"), choice.getInt("seed_high"));
      String actual = MissileImpactPresentationSystem.fireWallChild(maker, maker.missile);
      assertEquals(choice.getString("missile"), actual); selected.add(actual); count++;
    }
    assertTrue(count >= 30);
    assertEquals(Set.of("firestorm", "firesmall", "firemedium"), selected);
  }

  @Test void capturedVariantGrowthLoopAndExtinctionRetainTheirOwnLifetimes() {
    int checked = 0; Set<String> rows = new HashSet<>();
    for (JsonValue unit : original().get("units")) {
      String name = unit.getString("missile");
      if (!"client".equals(unit.getString("side")) || !Set.of("firestorm", "firesmall", "firemedium").contains(name)) continue;
      JsonValue states = unit.get("states"); int life = states.child.getInt("total_frames");
      assertEquals(15, life);
      Missile missile = new Missile().set(Riiablo.files.Missiles.get(name), Vector2.Zero, life);
      missile.presentationOnly = true; missile.skillId = 225;
      assertTrue(NativeFirestormPresentation.handles(missile));
      JsonValue beforeRandom = states.child;
      for (JsonValue state : states) if (life - state.getInt("current_frame") == 11) beforeRandom = state;
      for (JsonValue state : states) {
        int age = life - state.getInt("current_frame");
        assertEquals(state.getInt("unit_44") >>> 8, NativeFirestormPresentation.groundFrame(
            missile.missile, life, beforeRandom.getInt("seed_low"), beforeRandom.getInt("seed_high"), 37, age,
            missile.missile.SubLoop > 0), name + " unit=" + unit.getInt("unit_id") + " age=" + age);
        checked++;
      }
      rows.add(name);
    }
    assertEquals(Set.of("firestorm", "firesmall", "firemedium"), rows); assertTrue(checked > 400);
  }

  @Test void actualProductionEmitsAllAlternativesWithoutChangingTheDamageRows() {
    Map<String, Object> result = FirestormOriginalAnalysis.simulate(original());
    int expectedDamage = 0;
    for (JsonValue unit : original().get("units"))
      if ("server".equals(unit.getString("side")) && "firestorm".equals(unit.getString("missile"))) expectedDamage++;
    assertEquals(expectedDamage, result.get("damage_ground_fire_births"));
    Map<?, ?> variants = (Map<?, ?>) result.get("visual_ground_fire_variants");
    assertEquals(Set.of("firestorm", "firesmall", "firemedium"), variants.keySet());
    for (Object value : (List<?>) result.get("ground_fire_births")) {
      Map<?, ?> birth = (Map<?, ?>) value;
      assertEquals(15, birth.get("lifetime"));
    }
  }
}
