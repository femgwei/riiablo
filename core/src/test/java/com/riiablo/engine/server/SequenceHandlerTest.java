package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceHandlerTest {
  @Test
  void repeatedModeRestartsAnimationBeforeItCanSkipTheKeyframe() {
    AnimationResetProbe probe = new AnimationResetProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new SequenceHandler())
        .build());
    try {
      int entity = world.create();
      world.getMapper(CofReference.class).create(entity)
          .set("AM", Engine.Player.MODE_TH);
      AnimData anim = world.getMapper(AnimData.class).create(entity);
      anim.frame = 12 << 8; // already past AMTH1HT's MIS frame at index 9
      world.process();

      int baselineEvents = probe.modeEvents;
      Sequence sequence = world.getMapper(Sequence.class).create(entity)
          .sequence(Engine.Player.MODE_TH, Engine.Player.MODE_TN);
      world.process();

      assertTrue(sequence.started);
      assertEquals(baselineEvents + 1, probe.modeEvents,
          "a repeated TH action must force a new mode event");
      assertEquals(0, anim.frame,
          "the forced event must restart animation instead of inheriting a post-keyframe frame");
    } finally {
      world.dispose();
    }
  }

  private static final class AnimationResetProbe extends PassiveSystem {
    int modeEvents;

    @Subscribe
    public void onModeChanged(ModeChangeEvent event) {
      modeEvents++;
      AnimData anim = world.getMapper(AnimData.class).get(event.entityId);
      if (anim != null) {
        anim.frame = 0;
        anim.lastKeyframeIndex = -1;
      }
    }
  }
}
