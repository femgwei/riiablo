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
        .with(new EventSystem(), probe, new AnimStepper())
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
        .with(new EventSystem(), probe, new AnimDataResolver(), new AnimStepper())
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

    @Subscribe
    public void onKeyframe(AnimDataKeyframeEvent event) {
      if (event.keyframe == Engine.KEYFRAME_ATK) attackKeyframes++;
    }

    @Override protected void processSystem() {}
  }
}
