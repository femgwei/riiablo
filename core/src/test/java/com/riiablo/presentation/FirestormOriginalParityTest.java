package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.NativeFirestormMotion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirestormOriginalParityTest extends RiiabloTest {
  private JsonValue original() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/firestorm-1.10f.json"));
  }

  @Test void allCapturedServerMakerStepsRetainNativePrecisionAndTurns() {
    int ordinal = 0, samples = 0;
    for (JsonValue unit : original().get("units")) {
      if (!"server".equals(unit.getString("side")) || !"firestormmaker".equals(unit.getString("missile"))) continue;
      Missile maker = new Missile().set(Riiablo.files.Missiles.get("firestormmaker"), new Vector2(4523, 5642), 40);
      maker.damageLevel = 1;
      maker.chargedBoltPath = ordinal > 0;
      maker.chargedBoltTargetX = 4531;
      maker.chargedBoltTargetY = 5642;
      maker.chargedBoltMainDirection = 0;
      maker.chargedBoltSeedLow = 4531 + ordinal - 1;
      maker.chargedBoltSeedHigh = 666;
      for (JsonValue state : unit.get("states")) {
        int age = 40 - state.getInt("current_frame");
        NativeFirestormMotion.Sample at = NativeFirestormMotion.sample(maker, age);
        String context = "stream=" + ordinal + " age=" + age;
        assertEquals(state.getInt("x_fixed"), at.x, context + " x");
        assertEquals(state.getInt("y_fixed"), at.y, context + " y");
        assertEquals(state.getInt("velocity_x"), at.vx, context + " vx");
        assertEquals(state.getInt("velocity_y"), at.vy, context + " vy");
        samples++;
      }
      ordinal++;
    }
    assertEquals(3, ordinal);
    assertEquals(120, samples);
  }

  @Test void actualDccFrameSelectionsMatchIncludingReverseExtinctionAndLateLoad() {
    int count = 0;
    for (JsonValue sample : original().get("samples")) {
      Missile m = new Missile().set(Riiablo.files.Missiles.get(sample.getString("missile")), Vector2.Zero, 0);
      m.presentationOnly = true;
      m.nativeFrame = sample.getInt("native_age");
      int frames = sample.getInt("dcc_frames");
      assertEquals(sample.getInt("frame"), NativeFirestormPresentation.frameAt(m, frames, m.nativeFrame));
      count++;
    }
    assertEquals(204, count);
  }

  @Test void coldDccCannotKeepClientGroundFireAlivePastFifteenTicks() {
    com.riiablo.engine.EntityFactory factory = org.mockito.Mockito.mock(com.riiablo.engine.EntityFactory.class);
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new OverlayManager(),
            new MissileImpactPresentationSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create();
      Missile fire = world.getMapper(Missile.class).create(id)
          .set(Riiablo.files.Missiles.get("firestorm"), new Vector2(4524, 5642), 15);
      fire.presentationOnly = true;
      fire.authoritative = false;
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).initializeNativeCell(4524, 5642);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
      world.setDelta(1f / 25f);
      for (int tick = 1; tick < 15; tick++) {
        world.process();
        assertTrue(world.getEntityManager().isActive(id));
        assertEquals(tick, fire.nativeFrame);
      }
      world.process();
      assertFalse(world.getEntityManager().isActive(id));
    } finally { world.dispose(); }
  }

  @Test void realProductionSeparatesGroundDamageFromNativeClientFlames() {
    Map<String, Object> result = FirestormOriginalAnalysis.simulate(original());
    assertEquals(0, result.get("maker_frame_mismatches"));
    assertEquals(0, result.get("ground_fire_frame_mismatches"));
    assertEquals(36, result.get("damage_ground_fire_births"));
    assertTrue((int) result.get("visual_ground_fire_births") > 0);
    assertTrue(((List<?>) result.get("visual_lane_angles")).isEmpty());
    for (Object value : (List<?>) result.get("ground_fire_births")) {
      Map<?, ?> fire = (Map<?, ?>) value;
      assertEquals(15, fire.get("lifetime"));
    }
    for (Object value : (List<?>) result.get("head_births")) {
      Map<?, ?> head = (Map<?, ?>) value;
      assertEquals(40, head.get("lifetime"));
      assertEquals(9.375f, ((Number) ((List<?>) head.get("velocity")).get(0)).floatValue());
    }
  }
}
