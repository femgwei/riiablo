package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class DeathMaulSequenceTest extends RiiabloTest {
  @Test void mapsEveryPointAndEmitsOneActionAndOneFinishRegardlessOfCofMarkers() {
    try (Fixture f = new Fixture()) {
      int[] points = {0,0,1,1,2,2,3,3,4,4,5,6,7,7,7,7,8,8,8,8,
          9,9,9,9,9,10,10,10,10,11,11,11,11,12,13};
      for (int point = 1; point <= 35; point++) {
        f.tick();
        assertEquals(points[Math.min(34, point)] << 8, f.anim.frame);
        assertEquals(point < 13 ? 0 : 1, f.probe.actions);
        assertEquals(point == 35 ? 1 : 0, f.probe.finished);
      }
      for (int i = 0; i < 5; i++) f.tick();
      assertEquals(1, f.probe.actions);
      assertEquals(1, f.probe.finished);
      assertEquals(Engine.KEYFRAME_ATK, f.probe.keyframe);
    }
  }

  @Test void waitsForStartAndFreezePausesSequenceWithoutLosingAction() {
    try (Fixture f = new Fixture()) {
      f.seq.started = false;
      f.tick(); assertEquals(0, f.seq.nativeDeathMaulFrame);
      f.seq.started = true;
      for (int i = 0; i < 12; i++) f.tick();
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.id).init(f.id);
      states.stateList.addState(StateId.FREEZE, 100, 1, f.id);
      for (int i = 0; i < 20; i++) f.tick();
      assertEquals(12 << 8, f.seq.nativeDeathMaulFrame);
      assertEquals(0, f.probe.actions);
      states.stateList.removeState(StateId.FREEZE);
      f.tick(); assertEquals(1, f.probe.actions);
    }
  }

  @Test void newActionClearsNativeSequenceState() {
    Sequence seq = new Sequence();
    seq.nativeDeathMaul = true;
    seq.nativeDeathMaulFrame = 13 << 8;
    seq.sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    assertFalse(seq.nativeDeathMaul);
    assertEquals(0, seq.nativeDeathMaulFrame);
  }

  @Test void stateRateCrossesActionOnceAtFractionalAndAcceleratedSpeeds() {
    for (int modifier : new int[] {-50, 200}) {
      try (Fixture f = new Fixture()) {
        f.world.getMapper(UnitStates.class).create(f.id).init(f.id)
            .stateList.addState(StateId.COLD, 100, 1, f.id).animationRateModifier = modifier;
        int delta = AnimStepper.scaleStateAnimationSpeed(256, modifier);
        int ticks = (35 * 256 + delta - 1) / delta;
        for (int tick = 1; tick <= ticks; tick++) {
          f.tick();
          assertEquals(tick * delta < (13 << 8) ? 0 : 1, f.probe.actions);
        }
        assertEquals(1, f.probe.finished);
        assertEquals(35 << 8, f.seq.nativeDeathMaulFrame);
      }
    }
  }

  private static class Probe extends PassiveSystem {
    int actions, finished;
    byte keyframe;
    @Subscribe public void action(AnimDataKeyframeEvent event) { actions++; keyframe = event.keyframe; }
    @Subscribe public void finish(AnimDataFinishedEvent event) { finished++; }
  }

  private static class Fixture implements AutoCloseable {
    final Probe probe = new Probe();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new AnimStepper()).build());
    final int id = world.create();
    final AnimData anim = world.getMapper(AnimData.class).create(id);
    final Sequence seq = world.getMapper(Sequence.class).create(id)
        .sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
    Fixture() {
      seq.nativeDeathMaul = seq.started = true;
      anim.speed = 7; // Deliberately unrelated COF speed and markers.
      anim.numFrames = 14 << 8;
      anim.keyframes = new byte[] {Engine.KEYFRAME_MIS, Engine.KEYFRAME_ATK};
      world.setDelta(SimulationClock.STEP_SECONDS);
    }
    void tick() { world.process(); }
    public void close() { world.dispose(); }
  }
}
