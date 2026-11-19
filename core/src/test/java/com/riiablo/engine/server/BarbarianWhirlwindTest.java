package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BarbarianWhirlwindTest extends RiiabloTest {
  @Test
  void authoritativeWhirlwindCastConsumesOriginalTwentyFiveManaOnlyOnce() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false), actioneer,
            new WhirlwindSystem(), new Pathfinder(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = manaPlayer(world, skill, 1000f);
      world.process();
      com.riiablo.engine.server.event.SkillCastEvent cast =
          com.riiablo.engine.server.event.SkillCastEvent.obtain(
              id, skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      world.getSystem(EventSystem.class).dispatch(cast);
      assertTrue(cast.accepted, "server must accept the learned level-1 skill");
      assertEquals(25f, cast.manaCost, 0f, "original immediate drop is 6400/256 MP");
      assertEquals(975f, mana(world, id), 0f);
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(100, 0), skill.srvstfunc, skill.cltstfunc));
      world.setDelta(1f / 25f);
      for (int frame = 1; frame <= 60; frame++) {
        world.getMapper(Position.class).get(id).position.x = frame * 0.01f;
        world.process();
        assertEquals(975f, mana(world, id), 0f, "attack windows must not consume more mana");
      }
      world.getMapper(Position.class).get(id).position.set(100, 0);
      world.process();
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(id));
      assertEquals(975f, mana(world, id), 0f);
    } finally { world.dispose(); }
  }

  @Test
  void whirlwindManaBoundaryRetainsOneTwoHundredFiftySixthOfMana() {
    for (float initial : new float[] {25f - 1f / 256f, 25f}) {
      DummyFactory factory = new DummyFactory();
      World world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new ServerSkillSystem(false), factory)
          .build().register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0)));
      try {
        Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
        int id = manaPlayer(world, skill, initial);
        world.process();
        com.riiablo.engine.server.event.SkillCastEvent cast =
            com.riiablo.engine.server.event.SkillCastEvent.obtain(
                id, skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
        world.getSystem(EventSystem.class).dispatch(cast);
        assertEquals(initial == 25f, cast.accepted);
        assertEquals(initial == 25f ? 0f : initial, mana(world, id), 0f);
      } finally { world.dispose(); }
    }
  }

  private static int manaPlayer(World world, Skills.Entry skill, float mana) {
    Attributes attrs = attributes(1000, 1, 1, 100000);
    attrs.base().put(Stat.mana, mana);
    attrs.base().put(Stat.maxmana, 1000f);
    attrs.reset();
    int id = player(world, 0, 0, attrs);
    CharData data = CharData.createRemote("whirlwind", (byte) Riiablo.BARBARIAN);
    data.setSkillLevel(skill.Id, 1);
    for (String name : new String[] {skill.reqskill1, skill.reqskill2, skill.reqskill3}) {
      if (name != null && !name.trim().isEmpty()) {
        data.setSkillLevel(Riiablo.files.skills.get(name.trim()).Id, 1);
      }
    }
    data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 1)));
    world.getMapper(Player.class).get(id).data = data;
    return id;
  }

  @Test
  void originalWhirlwindManaTraceIncludesEightyFiveEncodedUnitsOfRecoveryPerTick() {
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false), new ManaRecoverySystem(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      // Original task 20261114-150656-a84e2745 resources.json: max 256000,
      // flat recovery 0, bonus 0. Compare the observed 60-frame mana series.
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = manaPlayer(world, skill, 1000f);
      world.setDelta(1f / 25f);
      world.process();
      com.riiablo.engine.server.event.SkillCastEvent cast =
          com.riiablo.engine.server.event.SkillCastEvent.obtain(
              id, skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      world.getSystem(EventSystem.class).dispatch(cast);
      assertTrue(cast.accepted);
      assertEquals(249600, Math.round(mana(world, id) * 256f));
      for (int frame = 1; frame <= 60; frame++) {
        world.process();
        assertEquals(249600 + frame * 85, Math.round(mana(world, id) * 256f),
            "original mana trace at relative frame " + frame);
      }
    } finally { world.dispose(); }
  }

  private static float mana(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.mana).asFixed();
  }

  @Test
  void originalHandAxeWithShieldUsesEightFrameWeaponInterval() {
    com.riiablo.codec.D2 previous = Riiablo.anim;
    Riiablo.anim = com.riiablo.codec.D2.loadFromFile(
        Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      CharData data = CharData.createRemote("whirlwind", (byte) Riiablo.BARBARIAN);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 1)));
      Item shield = new Item();
      shield.reset();
      shield.setBase(Riiablo.files.armor.get("buc"));
      data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(shield));
      world.getMapper(Player.class).get(id).data = data;
      com.riiablo.engine.server.component.CofReference cof = world.getMapper(
          com.riiablo.engine.server.component.CofReference.class).create(id);
      cof.set("BA", Engine.Player.MODE_A1);
      cof.wclass = Engine.WEAPON_1HS;
      world.process();
      assertNotNull(Riiablo.anim.getEntry("BAA11HS"));
      assertEquals(8, actioneer.whirlwindAttackInterval(id));
    } finally {
      world.dispose();
      Riiablo.anim = previous;
    }
  }

  @Test
  void originalSingleAxeAttackWindowsStartAtFrames3And7ThenUseWeaponInterval() {
    // Original 1.10f trace: runs/whirlwind-single-axe-level1.json.
    // Isolate scheduling here: that fixture's measured weapon interval is 8.
    Actioneer actioneer = new Actioneer() {
      @Override int whirlwindAttackInterval(int entityId) { return 8; }
    };
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      monster(world, 1, 0, attributes(100000, 0, 0, 0));
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(100, 0), skill.srvstfunc, skill.cltstfunc));
      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(id);
      java.util.List<Integer> windows = new java.util.ArrayList<>();
      world.setDelta(1f / 25f);
      for (int frame = 1; frame <= 55; frame++) {
        // This fixture has no integrator; move enough to keep the path active.
        world.getMapper(Position.class).get(id).position.x = frame * 0.01f;
        int before = runtime.handIndex;
        world.process();
        if (runtime.handIndex != before) windows.add(frame);
      }
      assertEquals(java.util.Arrays.asList(3, 7, 15, 23, 31, 39, 47, 55), windows);
      assertEquals(63f, runtime.nextAttackFrame, 0.0001f);
    } finally { world.dispose(); }
  }

  @Test
  void interruptedWhirlwindDoesNotDeleteReplacementSkillOrSequence() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(100, 0), skill.srvstfunc, skill.cltstfunc));
      Casting replacement = world.getMapper(Casting.class).get(id);
      replacement.skillId = Riiablo.files.skills.get("Berserk").Id;
      world.getMapper(Velocity.class).get(id).velocity.set(3, 0);
      world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id);
      world.getMapper(com.riiablo.engine.server.component.AnimData.class).create(id).override = 123;
      world.setDelta(1f / 25f);
      world.process();
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(id));
      assertEquals(replacement.skillId, world.getMapper(Casting.class).get(id).skillId);
      assertTrue(world.getMapper(com.riiablo.engine.server.component.Sequence.class).has(id));
      assertEquals(123, world.getMapper(com.riiablo.engine.server.component.AnimData.class).get(id).override);
      assertEquals(3f, world.getMapper(Velocity.class).get(id).velocity.x);
      assertFalse(world.getMapper(UnitStates.class).get(id).stateList.hasState(StateId.WHIRLWIND));
    } finally { world.dispose(); }
  }

  @Test
  void expansionWeaponSpeedBreakpointsMatchSrvDo076() {
    assertEquals(4, BarbarianSkills.getWhirlwindAttackInterval(11));
    assertEquals(6, BarbarianSkills.getWhirlwindAttackInterval(12));
    assertEquals(8, BarbarianSkills.getWhirlwindAttackInterval(15));
    assertEquals(10, BarbarianSkills.getWhirlwindAttackInterval(18));
    assertEquals(12, BarbarianSkills.getWhirlwindAttackInterval(20));
    assertEquals(14, BarbarianSkills.getWhirlwindAttackInterval(23));
    assertEquals(16, BarbarianSkills.getWhirlwindAttackInterval(26));
  }

  @Test
  void whirlwindFacingCompletesOneSpinEveryHalfSecond() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(10, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(10, 0), skill.srvstfunc, skill.cltstfunc));

      world.setDelta(WhirlwindSystem.ROTATION_PERIOD_SECONDS);
      world.process();
      Angle angle = world.getMapper(Angle.class).get(id);
      assertEquals(1f, angle.angle.x, 0.0001f);
      assertEquals(0f, angle.angle.y, 0.0001f);
      assertEquals(angle.angle.x, angle.target.x, 0.0001f);
      assertEquals(angle.angle.y, angle.target.y, 0.0001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void repeatedSkillStartDoesNotResetActiveWhirlwind() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int barbarian = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      int target = monster(world, 8, 0, attributes(1000, 0, 0, 0));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, target, new Vector2(8, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(8, 0),
          skill.srvstfunc, skill.cltstfunc));

      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(barbarian);
      assertNotNull(runtime);
      Vector2 destination = runtime.destination.cpy();
      runtime.elapsedFrames = 6f;
      runtime.strikeIndex = 2;

      // The target is now inside melee range.  A duplicate start must be
      // ignored instead of rejecting and deleting the active skill move.
      world.getMapper(Position.class).get(target).position.set(0.5f, 0);
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(0.5f, 0),
          skill.srvstfunc, skill.cltstfunc));

      WhirlwindRuntime retained = world.getMapper(WhirlwindRuntime.class).get(barbarian);
      assertNotNull(retained);
      assertEquals(destination.x, retained.destination.x, 0.001f);
      assertEquals(destination.y, retained.destination.y, 0.001f);
      assertEquals(6f, retained.elapsedFrames, 0.001f);
      assertEquals(2, retained.strikeIndex);
      assertTrue(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));
    } finally {
      world.dispose();
    }
  }

  @Test
  void straightSkillMoveCreatesStateAndDualWieldPulseUsesBothHands() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int barbarian = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      CharData data = CharData.createRemote("whirlwind", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(skill.Id, 20);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 40)));
      data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(weapon("hax", 100)));
      world.getMapper(Player.class).get(barbarian).data = data;
      int first = monster(world, 1, 0, attributes(100000, 0, 0, 0));
      int second = monster(world, 2, 0, attributes(100000, 0, 0, 0));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(10, 0));

      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, Engine.INVALID_ENTITY, new Vector2(10, 0),
          skill.srvstfunc, skill.cltstfunc));
      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(barbarian);
      assertNotNull(runtime);
      assertEquals(10f, runtime.destination.x, 0.001f);
      assertTrue(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));

      float firstBefore = hp(world, first);
      float secondBefore = hp(world, second);
      MathUtils.random.setSeed(0x7711L);
      world.setDelta(4f / WhirlwindSystem.GAME_FRAMES_PER_SECOND);
      world.process();

      assertEquals(1, runtime.strikeIndex, "initial native window uses one hand");
      world.getMapper(Position.class).get(barbarian).position.x += 0.2f;
      world.process();
      assertEquals(3, runtime.strikeIndex, "later native window uses both hands");
      assertTrue(hp(world, first) <= firstBefore);
      // Native hit rolls retain the 95% cap, so a synthetic test can legally
      // miss one of the first two windows.  Move the first target away and
      // keep resolving the active window until the second target receives
      // the left-hand hit; this verifies damage without making the test rely
      // on a particular RNG stream value.
      world.getMapper(Position.class).get(first).position.set(50, 0);
      for (int i = 0; i < 8 && hp(world, second) >= secondBefore; i++) {
        actioneer.resolveWhirlwindPulse(barbarian, runtime);
      }
      assertTrue(hp(world, second) < secondBefore);
      assertTrue(secondBefore - hp(world, second) > firstBefore - hp(world, first),
          "the second native attack must use the stronger left-hand weapon");
      data.getItems().getEquipped(BodyLoc.LARM).flags |= Item.ITEMFLAG_BROKEN;
      int strikes = runtime.strikeIndex;
      actioneer.resolveWhirlwindPulse(barbarian, runtime);
      assertEquals(strikes + 1, runtime.strikeIndex, "broken offhand must not create a second strike");
      assertFalse(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());

      world.getMapper(Position.class).get(barbarian).position.set(runtime.destination);
      world.process();
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(Casting.class).has(barbarian));
      assertFalse(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));
      assertTrue(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());
    } finally {
      world.dispose();
    }
  }

  @Test
  void meleeTargetIsRejectedAndDeathClearsSkillMoveState() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int barbarian = player(world, 0, 0, attributes(1000, 10, 10, 100000));
      int target = monster(world, 0.5f, 0, attributes(1000, 0, 0, 0));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, target, new Vector2(0.5f, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(0.5f, 0),
          skill.srvstfunc, skill.cltstfunc));
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(Casting.class).has(barbarian));

      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(8, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, Engine.INVALID_ENTITY, new Vector2(8, 0),
          skill.srvstfunc, skill.cltstfunc));
      assertTrue(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      Velocity deathVelocity = world.getMapper(Velocity.class).get(barbarian);
      deathVelocity.movementRunning = true;
      deathVelocity.setModeSpeedBonusPercent(50);
      actioneer.onDeath(DeathEvent.obtain(target, barbarian));
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));
      assertTrue(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());
      assertFalse(deathVelocity.movementRunning);
      assertEquals(0f, deathVelocity.modeSpeedBonusMultiplier);
    } finally {
      world.dispose();
    }
  }

  private static World world(Actioneer actioneer, DummyFactory factory) {
    return world(actioneer, factory, false);
  }

  @Test
  void longFrameDoesNotReplayMissedWindowsAndStateRemovalCleansUp() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      monster(world, 1, 0, attributes(100000, 0, 0, 0));
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(100, 0), skill.srvstfunc, skill.cltstfunc));
      world.setDelta(1f / 25f);
      world.process();
      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(id);
      assertEquals(0, runtime.strikeIndex);
      assertEquals(0, runtime.handIndex);
      assertEquals(3f, runtime.nextAttackFrame);
      world.getMapper(Position.class).get(id).position.x = 0.1f;
      world.setDelta(2f / 25f);
      world.process();
      assertEquals(1, runtime.strikeIndex);
      assertEquals(1, runtime.handIndex);
      assertEquals(7f, runtime.nextAttackFrame);
      world.getMapper(Position.class).get(id).position.x = 0.2f;
      world.setDelta(1f);
      world.process();
      assertEquals(2, runtime.strikeIndex, "one callback must not replay a backlog of windows");
      assertEquals(2, runtime.handIndex);
      assertEquals(38f, runtime.nextAttackFrame, "overdue timer restarts from current frame");
      Velocity velocity = world.getMapper(Velocity.class).get(id);
      velocity.setModeSpeedBonusPercent(30);
      world.getMapper(com.riiablo.engine.server.component.AnimData.class).create(id).override = 500;
      world.getMapper(UnitStates.class).get(id).stateList.removeState(StateId.WHIRLWIND);
      world.process();
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(id));
      assertFalse(world.getMapper(Casting.class).has(id));
      assertTrue(velocity.velocity.isZero());
      assertFalse(velocity.movementRunning);
      assertEquals(0f, velocity.modeSpeedBonusMultiplier);
      assertEquals(-1, world.getMapper(com.riiablo.engine.server.component.AnimData.class).get(id).override);
    } finally { world.dispose(); }
  }

  @Test
  void targetlessWindowsToggleHandWithoutInventingStrikes() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer, new DummyFactory());
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      int target = monster(world, 50, 0, attributes(100000, 0, 0, 0));
      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(100, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
          new Vector2(100, 0), skill.srvstfunc, skill.cltstfunc));
      world.setDelta(3f / 25f);
      world.process();
      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(id);
      assertEquals(0, runtime.strikeIndex);
      assertEquals(1, runtime.handIndex);
      assertEquals(Engine.INVALID_ENTITY, runtime.previousTargetId);
      world.getMapper(Position.class).get(target).position.set(1, 0);
      actioneer.resolveWhirlwindPulse(id, runtime);
      assertEquals(1, runtime.strikeIndex);
      assertEquals(2, runtime.handIndex);
    } finally { world.dispose(); }
  }

  private static World world(Actioneer actioneer, DummyFactory factory, boolean integrate) {
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new WhirlwindSystem(), new Pathfinder(), factory);
    if (integrate) builder.with(new DynamicUnitCollisionSystem(false), new VelocityAdder());
    return new World(builder.build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  @Test
  void whirlwindClearsOrdinaryRunAndClipsUsingTheIntegratorMultiplier() {
    for (float multiplier : new float[] {0f, 0.25f, 0.5f, 1f, 1.5f}) {
      Actioneer actioneer = new Actioneer();
      World world = world(actioneer, new DummyFactory(), true);
      try {
        Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
        int id = player(world, 0, 0, attributes(1000, 1, 1, 100000));
        Velocity velocity = world.getMapper(Velocity.class).get(id).setPlayer(6, 9);
        velocity.movementRunning = true;
        velocity.stateSpeedMultiplier = multiplier;
        velocity.nativePlayerVelocityBonus = (multiplier - 1f) * 100;
        world.getMapper(Casting.class).create(id)
            .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(1, 0));
        actioneer.onSkillStart(SkillStartEvent.obtain(id, skill.Id, Engine.INVALID_ENTITY,
            new Vector2(1, 0), skill.srvstfunc, skill.cltstfunc));
        assertFalse(velocity.movementRunning);
        // Even a stale run flag between start and processing must not change WW displacement.
        velocity.movementRunning = true;
        velocity.stateMovementLocked = multiplier == 0f;
        world.setDelta(1f);
        world.process();
        assertFalse(velocity.movementRunning);
        if (multiplier == 0f) {
          assertTrue(world.getMapper(Position.class).get(id).position.isZero());
          assertTrue(velocity.velocity.isZero());
          assertFalse(velocity.movementRunning);
          assertEquals(0f, velocity.modeSpeedBonusMultiplier);
          assertTrue(world.getMapper(WhirlwindRuntime.class).has(id));
          continue;
        }
        assertEquals(1f, world.getMapper(Position.class).get(id).position.x, 0.0001f);
        assertEquals(1f / multiplier, velocity.velocity.len(), 0.0001f);
        world.process();
        assertFalse(world.getMapper(WhirlwindRuntime.class).has(id));
        assertTrue(velocity.velocity.isZero());
      } finally {
        world.dispose();
      }
    }
  }

  private static int player(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Angle.class).create(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Velocity.class).create(id).set(6f, 9f);
    return id;
  }

  private static int monster(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 90);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static Item weapon(String code, int damage) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().get(Stat.mindamage).set(damage);
    item.attrs.base().get(Stat.maxdamage).set(damage);
    item.attrs.reset();
    return item;
  }

  private static float hp(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class DummyFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
