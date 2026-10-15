package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class MonInfernoIntegrationTest extends RiiabloTest {
  @Test void horrorIgnoresBackingCofMarkersAndFreezeStopsItsSequenceClock() {
    try (Fixture f = new Fixture("frozenhorror1", 323, Engine.Monster.MODE_A2)) {
      AnimData anim = f.world.getMapper(AnimData.class).get(f.source);
      anim.keyframes = new byte[] {Engine.KEYFRAME_MIS};
      NativeMonInferno channel = f.cast().monInferno;
      f.states().addState(StateId.FREEZE, 10, 1, f.target);
      for (int i = 0; i < 12; i++) f.tick();
      assertEquals(0, channel.frame);
      assertTrue(f.ids.isEmpty());
      f.states().removeState(StateId.FREEZE);
      f.untilFirst();
      assertEquals(9, f.frames.get(0));
      assertEquals(8 << 8, anim.frame, "SQ seeks InfernoAnim-1, not the non-SQ source point");
      f.tick();
      assertEquals(9 << 8, anim.frame);
      f.tick();
      assertEquals(8 << 8, anim.frame, "next pulse rewinds the sequence cursor");
      assertEquals(2, f.ids.size());
    }
  }

  @Test void horrorCreationFailureEndsAfterNativeTailAndDeathCancelsPendingSequence() {
    try (Fixture f = new Fixture("frozenhorror1", 323, Engine.Monster.MODE_A2)) {
      f.fail = true;
      NativeMonInferno channel = f.cast().monInferno;
      while (!channel.started) f.tick();
      assertEquals(9, channel.frame);
      assertEquals(10, channel.endFrame);
      f.tick();
      assertNull(f.cast());
      assertTrue(f.ids.isEmpty());
      f.begin();
      f.world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.DeathEvent.obtain(f.target, f.source));
      for (int i = 0; i < 30; i++) f.tick();
      assertNull(f.cast());
      assertTrue(f.ids.isEmpty());
    }
  }
  @Test void horrorUsesMonSeqActionNineAndPeriodicTwoFrameUpdates() {
    try (Fixture f = new Fixture("frozenhorror1", 323, Engine.Monster.MODE_A2)) {
      NativeMonInferno channel = f.cast().monInferno;
      assertNotNull(channel);
      f.untilFirst();
      assertEquals(9, f.frames.get(0));
      while (f.cast() != null) f.tick();
      for (int i = 1; i < f.frames.size(); i++)
        assertEquals(2, f.frames.get(i) - f.frames.get(i - 1));
      assertTrue(f.ids.size() > 2);
      assertEquals(channel.endFrame, channel.frame);
      assertEquals(1, channel.tailFrames);
      assertEquals(1, f.probe.finished);
    }
  }
  @Test void diablo152UsesRealScMarkerPeriodicMissilesAndSixFrameTail() {
    for (String row : new String[] {"diablo", "diabloclone"}) {
      try (Fixture f = new Fixture(row, 193, Engine.Monster.MODE_SC)) {
        f.untilFirst();
        NativeMonInferno channel = f.cast().monInferno;
        assertEquals(2, channel.interval);
        assertEquals(6, channel.tailFrames);
        while (f.cast() != null) f.tick();
        assertTrue(f.ids.size() >= 2);
        for (int i = 1; i < f.frames.size(); i++)
          assertEquals(2, f.frames.get(i) - f.frames.get(i - 1));
        int last = f.frames.get(f.frames.size() - 1);
        assertTrue(last >= channel.deadline && last < channel.deadline + 2);
        assertEquals(last + 6, channel.frame);
        assertEquals(1, f.probe.finished);
        for (int id : f.ids) {
          Missile missile = f.world.getMapper(Missile.class).get(id);
          assertEquals(193, missile.skillId);
          assertEquals(f.source, missile.ownerId);
          assertEquals(f.monster().monstats.Sk1lvl, missile.damageLevel);
          assertEquals(NativeMonInferno.missileFrames(f.skill, missile.missile, missile.damageLevel),
              missile.nativeLifetimeFrames);
          assertEquals(NativeMonInferno.velocity(missile.missile, missile.damageLevel),
              f.world.getMapper(Velocity.class).get(id).velocity.len(), .001f);
        }
      }
    }
  }

  @Test void diablo152StateOffAndCreationFailureUseTailWithoutGenericFallback() {
    try (Fixture f = new Fixture("diablo", 193, Engine.Monster.MODE_SC)) {
      f.untilFirst();
      NativeMonInferno channel = f.cast().monInferno;
      int due = channel.nextUpdate;
      f.states().removeState(StateId.INFERNO);
      while (channel.frame < due) f.tick();
      assertEquals(2, f.ids.size());
      assertEquals(-1, channel.nextUpdate);
      assertEquals(due + 6, channel.endFrame);
      while (f.cast() != null) f.tick();
      assertEquals(2, f.ids.size());
      f.begin(); f.fail = true;
      channel = f.cast().monInferno;
      while (!channel.started) f.tick();
      assertEquals(0, channel.missiles);
      assertEquals(-1, channel.nextUpdate);
      assertEquals(channel.frame + 6, channel.endFrame);
      while (f.cast() != null) f.tick();
      assertEquals(2, f.ids.size());
    }
  }

  @Test void explicitInfernoVelocityDoesNotBypassSlowMissiles() {
    try (Fixture f = new Fixture()) {
      com.riiablo.engine.server.state.UnitState slow = f.states().addState(StateId.SLOWMISSILES, 100, 1, f.target);
      slow.setStatContribution(Stat.skill_handofathena, 0,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, 33);
      f.untilFirst();
      Missile missile = f.world.getMapper(Missile.class).get(f.ids.get(0));
      assertTrue(missile.missile.CanSlow);
      assertEquals(NativeMonInferno.velocity(missile.missile, missile.damageLevel) * .33f,
          f.world.getMapper(Velocity.class).get(f.ids.get(0)).velocity.len(), .001f);
    }
  }
  @Test void ownerDeathCancelsAllQueuedUpdates() {
    try (Fixture f = new Fixture()) {
      f.untilFirst();
      f.world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.DeathEvent.obtain(f.target, f.source));
      assertNull(f.cast());
      for (int i = 0; i < 40; i++) f.tick();
      assertEquals(1, f.ids.size());
    }
  }

  @Test void missingUnitMayUseRealPathPointButZeroEitherCoordinateFails() {
    try (Fixture f = new Fixture()) {
      f.untilFirst();
      NativeMonInferno cast = f.cast().monInferno;
      f.world.getMapper(Pathfind.class).create(f.source).target.set(10, 14);
      f.world.delete(f.target);
      int due = cast.nextUpdate;
      while (cast.frame < due) f.tick();
      assertEquals(2, f.ids.size());
      f.world.getMapper(Pathfind.class).get(f.source).target.set(0, 14);
      due = cast.nextUpdate;
      while (cast.frame < due) f.tick();
      assertEquals(2, f.ids.size());
      assertEquals(-1, cast.nextUpdate);
    }
  }
  @Test void baalAndMegademonUseTheSame95ClockWithTheirOwnRealModes() {
    for (String row : new String[] {"baalcrab", "megademon1"}) {
      int skillId = row.equals("baalcrab") ? 317 : 319;
      try (Fixture f = new Fixture(row, skillId, Engine.Monster.MODE_S1)) {
        f.untilFirst();
        NativeMonInferno cast = f.cast().monInferno;
        while (f.cast() != null) f.tick();
        assertTrue(f.ids.size() >= 2, row + " must emit more than a generic one-shot");
        for (int i = 1; i < f.frames.size(); i++)
          assertEquals(2, f.frames.get(i) - f.frames.get(i - 1));
        assertEquals(cast.endFrame, cast.frame);
        assertEquals(1, cast.tailFrames);
      }
    }
  }
  @Test void realCofFirstMarkerThenPeriodicMissilesAndIndependentTail() {
    try (Fixture f = new Fixture()) {
      NativeMonInferno cast = f.cast().monInferno;
      assertNotNull(cast);
      assertTrue(f.frames.isEmpty(), "start must not create a missile");
      assertEquals(0, f.states().getState(StateId.INFERNO).duration, "boolean toggle, not TTL");
      f.untilFirst();
      int first = f.frames.get(0);
      while (f.cast() != null) f.tick();
      List<Integer> expected = new ArrayList<>();
      int last = first;
      expected.add(last);
      while (last < cast.deadline) { last += cast.interval; expected.add(last); }
      assertEquals(expected, f.frames);
      assertEquals(last + cast.tailFrames, cast.frame);
      assertEquals(1, f.probe.finished);
      assertTrue(f.states().hasState(StateId.INFERNO), "AI turns off the native toggle");
      for (int id : f.ids) {
        Missile missile = f.world.getMapper(Missile.class).get(id);
        assertEquals(f.source, missile.ownerId);
        assertEquals(178, missile.skillId);
        assertEquals(f.monster().monstats.Sk1lvl, missile.damageLevel);
        assertEquals(NativeMonInferno.missileFrames(f.skill, missile.missile, missile.damageLevel),
            missile.nativeLifetimeFrames);
        assertTrue(MissileCollisionSystem.usesFrameLifetime(missile));
        assertEquals(NativeMonInferno.velocity(missile.missile, missile.damageLevel),
            f.world.getMapper(Velocity.class).get(id).velocity.len(), .001f);
      }
    }
  }

  @Test void disablingStateStillAllowsAlreadyScheduledLastMissileThenTail() {
    try (Fixture f = new Fixture()) {
      f.untilFirst();
      NativeMonInferno cast = f.cast().monInferno;
      int due = cast.nextUpdate;
      f.states().removeState(StateId.INFERNO);
      while (cast.frame < due) f.tick();
      assertEquals(2, f.ids.size());
      assertEquals(-1, cast.nextUpdate);
      assertEquals(due + cast.tailFrames, cast.endFrame);
      while (f.cast() != null) f.tick();
      assertEquals(2, f.ids.size());
    }
  }

  @Test void creationFailureSchedulesTailWithoutFakeSuccessOrRetry() {
    try (Fixture f = new Fixture()) {
      f.fail = true;
      NativeMonInferno cast = f.cast().monInferno;
      while (!cast.started) f.tick();
      assertEquals(0, cast.missiles);
      assertEquals(-1, cast.nextUpdate);
      assertEquals(cast.frame + 14, cast.endFrame);
      while (f.cast() != null) f.tick();
      assertTrue(f.ids.isEmpty());
    }
  }

  @Test void removedTargetEndsAtScheduledCallbackButDeadTargetRemainsAddressable() {
    try (Fixture f = new Fixture()) {
      f.untilFirst();
      Attributes hp = f.world.getMapper(AttributesWrapper.class).get(f.target).attrs;
      hp.base().put(Stat.hitpoints, 0); hp.reset();
      NativeMonInferno cast = f.cast().monInferno;
      int due = cast.nextUpdate;
      while (cast.frame < due) f.tick();
      assertEquals(2, f.ids.size(), "native coordinate lookup doesn't test HP");
      f.world.delete(f.target);
      due = cast.nextUpdate;
      while (cast.frame < due) f.tick();
      assertEquals(2, f.ids.size());
      assertEquals(-1, cast.nextUpdate);
      while (f.cast() != null) f.tick();
    }
  }

  @Test void freezePausesEngineActionAndReplacingCastCancelsOldCallbacks() {
    try (Fixture f = new Fixture()) {
      f.untilFirst();
      NativeMonInferno old = f.cast().monInferno;
      int frame = old.frame;
      f.states().addState(StateId.FREEZE, 100, 1, -1);
      for (int i = 0; i < 30; i++) f.tick();
      assertEquals(frame, old.frame);
      assertEquals(1, f.ids.size());
      f.states().removeState(StateId.FREEZE);
      f.cast().set(0, f.target, new Vector2(13, 10));
      assertNull(f.cast().monInferno);
      f.world.getMapper(Sequence.class).remove(f.source);
      f.world.getMapper(Casting.class).remove(f.source);
      for (int i = 0; i < 30; i++) f.tick();
      assertEquals(1, f.ids.size());
      f.begin();
      assertNotSame(old, f.cast().monInferno);
      assertEquals(0, f.cast().monInferno.frame);
      f.untilFirst();
      assertEquals(2, f.ids.size());
    }
  }

  private static final class Probe extends PassiveSystem {
    int finished;
    @Subscribe public void finished(AnimDataFinishedEvent event) { finished++; }
  }

  private static final class Fixture implements AutoCloseable {
    final Probe probe = new Probe();
    final World world;
    final Skills.Entry skill;
    final byte mode;
    final List<Integer> frames = new ArrayList<>(), ids = new ArrayList<>();
    final int source, target;
    boolean fail;
    int ticks;

    Fixture() { this("fetishshaman1", 178, Engine.Monster.MODE_A1); }

    Fixture(String rowId, int skillId, byte mode) {
      this.skill = Riiablo.files.skills.get(skillId);
      this.mode = mode;
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, new Actioneer(), new Pathfinder(), new CofManager(),
              new AnimStepper(), new ServerSkillSystem(), factory)
          .build().register("map", new Map(1, 0)).register("factory", factory));
      world.setDelta(SimulationClock.STEP_SECONDS);
      source = world.create();
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(rowId);
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(UnitStates.class).create(source).init(source);
      target = world.create();
      world.getMapper(Position.class).create(target).position.set(13, 10);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 100); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(target).attrs = attrs;
      when(factory.createMissile(any(Missiles.Entry.class), any(), any(), anyInt(), anyInt()))
          .thenAnswer(call -> {
            if (fail) return -1;
            int id = world.create();
            Missiles.Entry missile = call.getArgument(0);
            world.getMapper(Missile.class).create(id).set(missile, call.getArgument(2), missile.Range)
                .setOwner(call.getArgument(3));
            world.getMapper(Velocity.class).create(id);
            frames.add(cast().monInferno.frame); ids.add(id);
            return id;
          });
      world.process();
      begin();
    }

    void begin() {
      world.getMapper(Sequence.class).create(source).sequence(mode, Engine.Monster.MODE_NU)
          .started = true;
      world.getMapper(Casting.class).create(source).set(skill.Id, target, new Vector2(13, 10));
      D2.Entry entry = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"))
          .getEntry((monster().monstats.Code + (mode == Engine.Monster.MODE_A1 ? "A1"
              : mode == Engine.Monster.MODE_A2 ? "A2"
              : mode == Engine.Monster.MODE_SC ? "SC" : "S1") + "HTH").toUpperCase());
      AnimData anim = world.getMapper(AnimData.class).create(source);
      anim.frame = 0; anim.speed = entry.speed; anim.numFrames = entry.framesPerDir << 8;
      anim.keyframes = entry.data; anim.lastKeyframeIndex = -1;
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(source, skill.Id, target,
          new Vector2(13, 10), 53, skill.cltstfunc));
    }

    void untilFirst() {
      NativeMonInferno cast = cast().monInferno;
      while (!cast.started) tick();
      assertTrue(cast.missiles > 0);
    }
    void tick() { assertTrue(ticks++ < 500, "channel stalled"); world.process(); }
    Casting cast() { return world.getMapper(Casting.class).get(source); }
    Monster monster() { return world.getMapper(Monster.class).get(source); }
    com.riiablo.engine.server.state.StateList states() {
      return world.getMapper(UnitStates.class).get(source).stateList;
    }
    @Override public void close() { world.dispose(); }
  }
}
