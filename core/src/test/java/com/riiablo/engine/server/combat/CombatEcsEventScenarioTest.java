package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/**
 * Headless ECS event-stage test. It verifies the real animation event source
 * used by Actioneer without loading COFs, starting GameScreen, or opening a
 * graphics window.
 */
class CombatEcsEventScenarioTest {
  @Test
  void animationKeyframeAndFinishReachCombatEventBus() {
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new AnimStepper(), probe)
        .build());
    try {
      int entityId = world.create();
      AnimData anim = world.getMapper(AnimData.class).create(entityId);
      anim.speed = 128;
      anim.frame = 0;
      anim.numFrames = 512;
      anim.keyframes = new byte[] {Engine.KEYFRAME_ATK};

      // IntervalIteratingSystem uses Animation.FRAME_DURATION; each process
      // call advances one fixed animation tick and stays completely headless.
      world.setDelta(Animation.FRAME_DURATION);
      world.process();

      System.out.println("[COMBAT_ECS_SCENARIO] entity=" + entityId
          + " keyframes=" + probe.keyframes
          + " finishes=" + probe.finishes
          + " keyframe=" + probe.lastKeyframe);
      assertEquals(1, probe.keyframes,
          "animation active frame must publish one combat keyframe event");
      assertEquals(0, probe.finishes,
          "the first tick must not finish a one-frame animation before dispatch");

      for (int i = 0; i < 8 && probe.finishes == 0; i++) {
        world.process();
      }
      assertTrue(probe.finishes >= 1,
          "animation wrap must publish AnimDataFinishedEvent for Actioneer");
    } finally {
      world.dispose();
    }
  }

  private static final class Probe extends BaseSystem {
    int keyframes;
    int finishes;
    byte lastKeyframe;

    @Subscribe
    public void onKeyframe(AnimDataKeyframeEvent event) {
      keyframes++;
      lastKeyframe = event.keyframe;
    }

    @Subscribe
    public void onFinished(AnimDataFinishedEvent event) {
      finishes++;
    }

    @Override
    protected void processSystem() {}
  }
}
