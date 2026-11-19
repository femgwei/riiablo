package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.D2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Regression tests for the native eanimdata/AnimStepper melee boundary. */
class MonsterMeleeAnimationRegressionTest extends RiiabloTest {
  @Test void completeRecordedFallenAttacksMatchProductionSequenceDuration() throws Exception {
    replayRecordedAttacks("fallen-attack-durations.csv", 12);
  }

  @Test void nativeScheduledAttackOffsetsMatchProductionMarkerDispatch() throws Exception {
    replayRecordedAttacks("fallen-attack-event-schedule.csv", 15);
  }

  private void replayRecordedAttacks(String fixture, int count) throws Exception {
    D2 previous = Riiablo.anim;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
        getClass().getResourceAsStream("/native/" + fixture),
        java.nio.charset.StandardCharsets.UTF_8))) {
      java.util.List<String> rows = reader.lines().skip(1).collect(java.util.stream.Collectors.toList());
      assertEquals(count, rows.size());
      for (String row : rows) {
        String[] fields = row.split(",");
        byte mode = Byte.parseByte(fields[0]);
        int duration = Integer.parseInt(fields[3]);
        Probe probe = new Probe();
        World world = new World(new WorldConfigurationBuilder()
            .with(new EventSystem(), probe, new CofManager(), new AnimDataResolver(),
                new SequenceHandler(), new AnimStepper())
            .build().register("map", new Map(0, 0)));
        try {
          int id = world.create();
          world.getMapper(Class.class).create(id).type = Class.Type.MON;
          world.getMapper(Monster.class).create(id);
          world.getMapper(CofReference.class).create(id).set("FA", mode);
          world.getSystem(EventSystem.class).dispatch(ModeChangeEvent.obtain(id, mode, true));
          world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id)
              .sequence(mode, Engine.Monster.MODE_NU);
          AnimData resolved = world.getMapper(AnimData.class).get(id);
          int marker = -1;
          for (int i = 0; i < (resolved.numFrames >>> 8); i++) {
            if (resolved.keyframes[i] == Engine.KEYFRAME_ATK) marker = i;
          }
          assertTrue(marker > 0);
          world.setDelta(Animation.FRAME_DURATION);
          for (int tick = 1; tick <= duration; tick++) {
            probe.tick = tick;
            world.process();
            if (tick < duration) {
              assertEquals(mode, world.getMapper(CofReference.class).get(id).mode, row);
              assertEquals(0, probe.finished, row);
            }
          }
          assertEquals(1, probe.finished, row);
          assertEquals(1, probe.attackKeyframes, row);
          assertEquals(marker, probe.attackTick, "port marker dispatch: " + row);
          if (fields.length > 4) {
            assertEquals(Integer.parseInt(fields[4]), probe.attackTick,
                "native planned MODECHANGE id1 offset: " + row);
          }
          assertEquals(Engine.Monster.MODE_NU, world.getMapper(CofReference.class).get(id).mode, row);
          assertTrue(!world.getMapper(com.riiablo.engine.server.component.Sequence.class).has(id), row);
        } finally { world.dispose(); }
      }
    } finally { Riiablo.anim = previous; }
  }

  @Test
  void fallenAttackKeyframeIsInsideDeclaredAnimationFrameRange() {
    D2 animData = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    D2.Entry entry = animData.getEntry("FAA1HTH");
    assertNotNull(entry);
    assertEquals(10, entry.framesPerDir);

    int attackFrame = -1;
    for (int i = 0; i < entry.data.length; i++) {
      if (entry.data[i] == Engine.KEYFRAME_ATK) {
        attackFrame = i;
        break;
      }
    }
    assertEquals(7, attackFrame,
        "the native Fallen A1 ATK marker must remain in the 10-frame animation");
    assertTrue(attackFrame < entry.framesPerDir);
  }

  @Test
  void animStepperDispatchesFallenAttackKeyframeBeforeWrap() {
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new AnimStepper())
        .build()
        .register("map", new Map(0, 0)));
    try {
      int entity = world.create();
      AnimData anim = world.getMapper(AnimData.class).create(entity);
      anim.speed = 256;
      anim.frame = 0;
      anim.numFrames = 10 << 8;
      // eanimdata records are fixed at 144 bytes, while Fallen A1 uses 10
      // directional frames and places ATK at frame 7.
      anim.keyframes = new byte[144];
      anim.keyframes[7] = Engine.KEYFRAME_ATK;

      world.setDelta(Animation.FRAME_DURATION);
      for (int i = 0; i < 10; i++) world.process();

      assertEquals(1, probe.attackKeyframes,
          "Fallen A1 must emit exactly one ATK event during one animation");
    } finally {
      world.dispose();
    }
  }

  @Test
  void resolverAndStepperUseNativeFallenCofData() {
    D2 previous = Riiablo.anim;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new AnimDataResolver(), new AnimStepper())
        .build()
        .register("map", new Map(0, 0)));
    try {
      int entity = world.create();
      world.getMapper(Class.class).create(entity).type = Class.Type.MON;
      world.getMapper(Monster.class).create(entity);
      world.getMapper(CofReference.class).create(entity)
          // Native monster COF token for fallen1 is FA (not the monstats id).
          .set("FA", (byte) Class.Type.MON.getMode("A1"));
      world.getSystem(EventSystem.class).dispatch(
          ModeChangeEvent.obtain(entity, (byte) Class.Type.MON.getMode("A1"), true));

      AnimData anim = world.getMapper(AnimData.class).get(entity);
      assertNotNull(anim);
      assertEquals(10 << 8, anim.numFrames);
      assertEquals(Engine.KEYFRAME_ATK, anim.keyframes[7]);

      world.setDelta(Animation.FRAME_DURATION);
      for (int i = 0; i < 10; i++) world.process();
      assertEquals(1, probe.attackKeyframes,
          "the real FAA1HTH record must emit ATK through AnimStepper");
    } finally {
      world.dispose();
      Riiablo.anim = previous;
    }
  }

  private static final class Probe extends BaseSystem {
    int attackKeyframes;
    int finished;
    int tick, attackTick;

    @Subscribe public void onFinished(com.riiablo.engine.server.event.AnimDataFinishedEvent event) {
      finished++;
    }

    @Subscribe
    public void onKeyframe(AnimDataKeyframeEvent event) {
      if (event.keyframe == Engine.KEYFRAME_ATK) {
        attackKeyframes++;
        attackTick = tick;
      }
    }

    @Override protected void processSystem() {}
  }
}
