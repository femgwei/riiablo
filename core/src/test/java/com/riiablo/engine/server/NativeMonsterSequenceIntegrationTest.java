package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
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

class NativeMonsterSequenceIntegrationTest extends RiiabloTest {
  @Test void realMonSeqActionsRunAtTheirOwnPointsRegardlessOfCofMarkersAndSpeed() {
    for (String name : new String[] {"seq_ancientwhirlwind", "seq_ancientleap", "seq_genericspawner",
        "seq_mosquitoskill", "seq_vileres", "seq_undeadbestow", "seq_fethisraise",
        "seq_reanimatedcharge", "seq_siegebeastcharge", "seq_nihlathakarctic",
        "seq_serpentcharge", "seq_impinferno"}) {
      NativeMonsterSequence data = NativeMonsterSequence.load(name);
      for (int speed : new int[] {128, 256, 512}) {
        Probe probe = new Probe();
        World world = new World(new WorldConfigurationBuilder()
            .with(new EventSystem(), probe, new CofManager(), new AnimStepper()).build());
        try {
          int id = world.create();
          AnimData anim = world.getMapper(AnimData.class).create(id);
          anim.speed = speed; anim.numFrames = 50 << 8;
          anim.keyframes = new byte[] {Engine.KEYFRAME_MIS, Engine.KEYFRAME_MIS};
          Sequence sequence = world.getMapper(Sequence.class).create(id)
              .sequence(data.mode, Engine.Monster.MODE_NU);
          sequence.nativeMonster = data; sequence.started = true;
          world.setDelta(SimulationClock.STEP_SECONDS);
          int expectedActions = 0;
          for (int tick = 1; tick <= data.frames.length; tick++) {
            if (tick < data.actions.length && data.actions[tick] != 0
                && data.actions[tick] != Engine.KEYFRAME_SND) expectedActions++;
            world.process();
            assertEquals(expectedActions, probe.actions, name + " tick=" + tick);
            assertEquals(tick == data.frames.length ? 1 : 0, probe.finished);
          }
          sequence.sequence(Engine.Monster.MODE_NU, Engine.Monster.MODE_NU);
          assertNull(sequence.nativeMonster);
          assertEquals(0, sequence.nativeMonsterCursor);
        } finally { world.dispose(); }
      }
    }
  }
  @Test void chargeMovementPointsNeverDealDamageAndWaitForMeleeBeforeTheAttackPhase() {
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new AnimStepper()).build());
    try {
      NativeMonsterSequence data = NativeMonsterSequence.load("seq_reanimatedcharge");
      int source = world.create(), target = world.create();
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(source);
      com.riiablo.engine.server.component.Position destination = world.getMapper(
          com.riiablo.engine.server.component.Position.class).create(target);
      destination.position.set(20, 0);
      world.getMapper(com.riiablo.engine.server.component.Casting.class).create(source).set(
          com.riiablo.Riiablo.files.skills.get("Charge").Id, target, destination.position);
      AnimData anim = world.getMapper(AnimData.class).create(source);
      anim.speed = 256; anim.numFrames = 50 << 8;
      Sequence sequence = world.getMapper(Sequence.class).create(source).sequence(data.mode, Engine.Monster.MODE_NU);
      sequence.nativeMonster = data; sequence.started = true;
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int i = 0; i < 40; i++) world.process();
      assertEquals(0, probe.actions);
      assertEquals(0, probe.finished);
      destination.position.set(1, 0);
      for (int i = 0; i < 30; i++) world.process();
      assertEquals(1, probe.actions);
      assertEquals(1, probe.finished);
    } finally { world.dispose(); }
  }

  static class Probe extends PassiveSystem {
    int actions, finished;
    @Subscribe public void action(AnimDataKeyframeEvent event) { actions++; }
    @Subscribe public void finish(AnimDataFinishedEvent event) { finished++; }
  }
}
