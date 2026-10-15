package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class NativeMummySequenceTest {
  @Test
  void sequenceUsesMonSeqPoint13NotCofMarkerAndFinishesAt16() {
    for (int speed : new int[] {128, 256, 512}) {
      Probe probe = new Probe();
      World world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, new CofManager(), new AnimStepper()).build());
      try {
        int id = world.create();
        AnimData anim = world.getMapper(AnimData.class).create(id);
        anim.speed = speed;
        anim.numFrames = 16 << 8;
        anim.keyframes = new byte[] {Engine.KEYFRAME_MIS, Engine.KEYFRAME_MIS};
        Sequence sequence = world.getMapper(Sequence.class).create(id)
            .sequence(Engine.Monster.MODE_SC, Engine.Monster.MODE_NU);
        sequence.nativeMummy = sequence.started = true;
        world.setDelta(SimulationClock.STEP_SECONDS);
        int eventTick = (13 * 256 + speed - 1) / speed;
        int endTick = (16 * 256 + speed - 1) / speed;
        for (int tick = 1; tick <= endTick; tick++) {
          world.process();
          assertEquals(tick < eventTick ? 0 : 1, probe.actions);
          assertEquals(tick < endTick ? 0 : 1, probe.finished);
        }
        assertEquals(Engine.KEYFRAME_MIS, probe.keyframe);
        sequence.sequence(Engine.Monster.MODE_NU, Engine.Monster.MODE_NU);
        assertFalse(sequence.nativeMummy, "pooled/new actions cannot retain sequence state");
        assertEquals(0, sequence.nativeMummyFrame);
      } finally { world.dispose(); }
    }
  }

  private static class Probe extends PassiveSystem {
    int actions, finished;
    byte keyframe;
    @Subscribe public void keyframe(AnimDataKeyframeEvent event) { actions++; keyframe = event.keyframe; }
    @Subscribe public void finished(AnimDataFinishedEvent event) { finished++; }
  }
}
