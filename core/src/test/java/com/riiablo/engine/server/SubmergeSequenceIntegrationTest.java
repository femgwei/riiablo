package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class SubmergeSequenceIntegrationTest extends RiiabloTest {
  @Test
  void frogModeUpdateHoldsSourceZeroWithoutInventingAnAttackPoint() {
    Probe probe = new Probe();
    World world = world(probe);
    try {
      int id = subject(world, 12, 256);
      Sequence seq = world.getMapper(Sequence.class).get(id);
      seq.nativeSubmerge = false;
      seq.nativeFrogHidden = true;
      world.getMapper(CofReference.class).get(id).token = "FD";
      // In particular, S1's unrelated emergence COF markers must not fire.
      for (int tick = 0; tick < 100; tick++) world.process();
      assertTrue(seq.nativeSubmergeHeld);
      assertEquals(0, world.getMapper(AnimData.class).get(id).frame);
      assertEquals(0, world.getMapper(AnimData.class).get(id).override);
      assertEquals(0, probe.actions);
      assertEquals(0, probe.finished);
      assertEquals(1, probe.updates);
      seq.sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
      assertFalse(seq.nativeFrogHidden);
      assertFalse(seq.nativeSubmergeHeld);
    } finally { world.dispose(); }
  }

  @Test
  void frogFreezePreventsTheModeUpdateUntilThawed() {
    Probe probe = new Probe();
    World world = world(probe);
    try {
      int id = subject(world, 12, 256);
      Sequence seq = world.getMapper(Sequence.class).get(id);
      seq.nativeSubmerge = false;
      seq.nativeFrogHidden = true;
      world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(StateId.FREEZE, 100, 1, -1);
      for (int tick = 0; tick < 30; tick++) world.process();
      assertFalse(seq.nativeSubmergeHeld);
      assertEquals(0, probe.updates);
      world.getMapper(UnitStates.class).get(id).stateList.removeState(StateId.FREEZE);
      world.process();
      assertTrue(seq.nativeSubmergeHeld);
      assertEquals(1, probe.updates);
    } finally { world.dispose(); }
  }
  @Test
  void nativeSequenceData() {
    D2 anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"Tentacle".equals(row.AI) && !"TentacleHead".equals(row.AI)) continue;
      Skills.Entry down = Riiablo.files.skills.get(row.Skill1);
      Skills.Entry up = Riiablo.files.skills.get(row.Skill2);
      assertEquals("TentacleHead".equals(row.AI) ? "seq_tentheadsubmerge" : "seq_tentaclesubmerge", row.Sk1mode);
      assertEquals("S1", row.Sk2mode);
      assertEquals(51, down.srvstfunc);
      assertEquals(94, down.srvdofunc);
      assertEquals(52, up.srvstfunc);
      assertEquals(0, up.srvdofunc);
      assertEquals("TentacleHead".equals(row.AI) ? 21 : 13,
          anim.getEntry((row.Code + "S1HTH").toUpperCase()).framesPerDir);
    }
    String text = Riiablo.mpqs.resolve("data\\global\\excel\\monseq.txt").readString("windows-1252");
    for (String name : new String[] {"seq_tentaclesubmerge", "seq_tentheadsubmerge"}) {
      int last = name.equals("seq_tentaclesubmerge") ? 12 : 20;
      int count = 0;
      for (String line : text.split("\\r?\\n")) {
        String[] col = line.split("\\t", -1);
        if (!name.equals(col[0])) continue;
        assertEquals("S1", col[1]);
        assertEquals(last - count, Integer.parseInt(col[2]));
        assertEquals(count < 4 || last - count <= (last == 12 ? 3 : 2) ? "1" : "", col[4]);
        count++;
      }
      assertEquals(last + 1, count);
    }
  }

  @Test
  void reverseSequenceExecutesNativeActionPointsThenHoldsWithoutFinishingOrRepeating() {
    for (int last : new int[] {12, 20}) {
      for (int speed : new int[] {128, 256, 512}) {
        Probe probe = new Probe();
        World world = world(probe);
        try {
          int id = subject(world, last, speed);
          Sequence seq = world.getMapper(Sequence.class).get(id);
          AnimData anim = world.getMapper(AnimData.class).get(id);
          for (int tick = 0; tick < 100; tick++) world.process();
          assertTrue(seq.nativeSubmergeHeld, "SrvDo094 must hold at zero");
          assertEquals(0, anim.frame);
          assertEquals(0, anim.override);
          assertEquals(last == 12 ? 8 : 7, probe.actions, "COF markers must not replace MonSeq events");
          assertEquals(0, probe.finished, "submerged SQ does not transition to neutral");
          assertTrue(world.getMapper(Casting.class).has(id));
          assertTrue(world.getMapper(Sequence.class).has(id));
          seq.sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
          assertFalse(seq.nativeSubmerge);
          assertFalse(seq.nativeSubmergeHeld);
          assertEquals(-1, seq.nativeSubmergeLastPoint);
        } finally { world.dispose(); }
      }
    }
  }

  @Test
  void frozenSequenceDoesNotAdvanceOrExecuteActionPoints() {
    Probe probe = new Probe();
    World world = world(probe);
    try {
      int id = subject(world, 12, 256);
      world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(StateId.FREEZE, 100, 1, -1);
      for (int tick = 0; tick < 30; tick++) world.process();
      assertEquals(0, probe.actions);
      assertEquals(0, world.getMapper(Sequence.class).get(id).nativeSubmergeFrame);
      world.getMapper(UnitStates.class).get(id).stateList.removeState(StateId.FREEZE);
      for (int tick = 0; tick < 30; tick++) world.process();
      assertTrue(world.getMapper(Sequence.class).get(id).nativeSubmergeHeld);
    } finally { world.dispose(); }
  }

  private World world(Probe probe) {
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new Actioneer(), new Pathfinder(), new CofManager(), new AnimStepper(), factory)
        .build().register("map", new Map(1, 0)).register("factory", factory));
    world.setDelta(SimulationClock.STEP_SECONDS);
    return world;
  }

  @Test
  void disappearingPreyDoesNotPreventTheSelfSequenceFromHolding() {
    Probe probe = new Probe();
    World world = world(probe);
    try {
      int id = subject(world, 12, 256);
      int prey = world.create(); // No attributes: equivalent to a removed target pointer.
      world.getMapper(Casting.class).get(id).targetId = prey;
      for (int tick = 0; tick < 30; tick++) world.process();
      assertTrue(world.getMapper(Sequence.class).get(id).nativeSubmergeHeld);
      assertEquals(8, probe.actions);
    } finally { world.dispose(); }
  }

  @Test
  void unportedSubmergeSequenceStillWarnsAndCannotPretendToHold() {
    com.riiablo.logger.Logger logger = com.riiablo.logger.LogManager.getLogger(Actioneer.class);
    com.riiablo.logger.Appender previous = logger.getAppender();
    Runnable restoreLevel = logger.temporaryLevel(com.riiablo.logger.Level.WARN);
    java.util.List<String> warnings = new java.util.ArrayList<>();
    logger.addAppender(new com.riiablo.logger.Appender() {
      @Override public com.riiablo.logger.Encoder encoder() { return previous.encoder(); }
      @Override public void append(com.riiablo.logger.LogEvent event) {
        warnings.add(event.message().format());
        previous.append(event);
      }
    });
    World world = null;
    try {
      world = world(new Probe());
      int id = subject(world, 12, 256);
      Sequence seq = world.getMapper(Sequence.class).get(id);
      seq.nativeSubmerge = false;
      seq.mode1 = Engine.Monster.MODE_XX;
      AnimData anim = world.getMapper(AnimData.class).get(id);
      anim.keyframes = null;
      world.process();
      anim.frame = 0;
      world.getSystem(EventSystem.class).dispatch(AnimDataKeyframeEvent.obtain(id, Engine.KEYFRAME_ATK));
      assertTrue(warnings.stream().anyMatch(text -> text.startsWith("Unported Submerge sequence")));
      assertFalse(seq.nativeSubmergeHeld);
      assertEquals(-1, anim.override);
    } finally {
      if (world != null) world.dispose();
      logger.addAppender(previous);
      restoreLevel.run();
    }
  }

  private int subject(World world, int last, int speed) {
    int id = world.create();
    world.getMapper(CofReference.class).create(id).set(last == 12 ? "TN" : "TE", Engine.Monster.MODE_S1);
    world.getMapper(Casting.class).create(id).set(Riiablo.files.skills.get("Submerge").Id, Engine.INVALID_ENTITY,
        new com.badlogic.gdx.math.Vector2(10, 10));
    AnimData anim = world.getMapper(AnimData.class).create(id);
    anim.speed = speed;
    anim.numFrames = (last + 1) << 8;
    anim.keyframes = new byte[] {Engine.KEYFRAME_MIS}; // must be ignored
    Sequence seq = world.getMapper(Sequence.class).create(id)
        .sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
    seq.started = seq.nativeSubmerge = true;
    seq.nativeSubmergeLastFrame = last;
    return id;
  }

  private static class Probe extends PassiveSystem {
    int actions, finished, updates;
    @Subscribe public void update(com.riiablo.engine.server.event.SubmergeSequenceUpdateEvent event) { updates++; }
    @Subscribe public void action(SkillDoEvent event) {
      assertEquals(94, event.srvdofunc);
      actions++;
    }
    @Subscribe public void finished(AnimDataFinishedEvent event) { finished++; }
  }
}
