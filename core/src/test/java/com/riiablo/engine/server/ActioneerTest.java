package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.DefenseCalculator;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ActioneerTest extends RiiabloTest {
  @Test
  void playerSequenceSkillStartsWithItsTransitionAnimation() {
    Skills.Entry jab = new Skills.Entry();
    jab.anim = "SQ";
    jab.seqtrans = "A1";
    assertEquals(Engine.Player.MODE_A1, new Actioneer().getMode(jab, Class.Type.PLR));
  }

  @Test
  void nativeResurrectSkillMayExecuteAgainstDeadTarget() {
    Skills.Entry resurrect = new Skills.Entry();
    resurrect.srvdofunc = 97;
    assertTrue(Actioneer.allowsDeadTarget(resurrect));

    Skills.Entry attack = new Skills.Entry();
    attack.srvdofunc = 1;
    assertFalse(Actioneer.allowsDeadTarget(attack));
    assertFalse(Actioneer.allowsDeadTarget(null));
  }

  @Test
  void shieldBlockHonorsSkillsInterruptFlag() {
    Skills.Entry jab = new Skills.Entry();
    jab.skill = "Jab";
    jab.interrupt = false;
    assertFalse(Actioneer.blockReactionMayInterrupt(jab),
        "Jab-style skills must keep their active sequence when a block is rolled");

    Skills.Entry interruptible = new Skills.Entry();
    interruptible.interrupt = true;
    assertTrue(Actioneer.blockReactionMayInterrupt(interruptible),
        "interruptible skills may transition to the block reaction");
  }

  @Test
  void shieldBlockQueuesPlayerBlockAnimationWhenStandingStill() {
    ReactionHarness harness = new ReactionHarness();
    try {
      int player = harness.player(Engine.Player.MODE_NU);
      harness.actioneer.queueHitReaction(player, blockedResult());

      CofReference cof = harness.world.getMapper(CofReference.class).get(player);
      Sequence sequence = harness.world.getMapper(Sequence.class).get(player);
      assertEquals(Engine.Player.MODE_BL, cof.mode);
      assertEquals(Engine.Player.MODE_BL, sequence.mode1);
      assertEquals(Engine.Player.MODE_NU, sequence.mode2);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void shieldBlockDuringJabKeepsTheActiveSkillSequence() {
    Skills.Entry jab = Riiablo.files.skills.get("Jab");
    assertTrue(jab != null && !jab.interrupt,
        "vanilla Jab must be marked interrupt=0 in Skills.txt");
    ReactionHarness harness = new ReactionHarness();
    try {
      int player = harness.player(Engine.Player.MODE_A1);
      harness.world.getMapper(Casting.class).create(player)
          .set(jab.Id, Engine.INVALID_ENTITY, new Vector2());
      harness.world.getMapper(Sequence.class).create(player)
          .sequence(Engine.Player.MODE_A1, Engine.Player.MODE_NU);

      harness.actioneer.queueHitReaction(player, blockedResult());

      CofReference cof = harness.world.getMapper(CofReference.class).get(player);
      Sequence sequence = harness.world.getMapper(Sequence.class).get(player);
      assertEquals(Engine.Player.MODE_A1, cof.mode,
          "Jab's interrupt=0 keeps the attack animation instead of switching to BL");
      assertEquals(Engine.Player.MODE_A1, sequence.mode1);
      assertTrue(harness.world.getMapper(Casting.class).has(player));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void activeMeleeCastUsesItsFrozenPositionTick() {
    CombatPositionHistory history = new CombatPositionHistory();
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int attacker = unit(world, 0, 0);
      int target = unit(world, 5, 0);
      world.process();
      history.capture(world, 1);
      world.getMapper(Position.class).get(target).position.set(20, 0);
      history.capture(world, 2);
      world.getMapper(Casting.class).create(attacker).set(0, target, new Vector2(5, 0), 1);

      Actioneer actioneer = new Actioneer();
      actioneer.mClass = world.getMapper(Class.class);
      actioneer.mPosition = world.getMapper(Position.class);
      actioneer.mSize = world.getMapper(Size.class);
      actioneer.mCasting = world.getMapper(Casting.class);
      actioneer.mPlayer = world.getMapper(Player.class);
      actioneer.mMonster = world.getMapper(Monster.class);
      actioneer.mCofReference = world.getMapper(CofReference.class);
      actioneer.combatPositionHistory = history;
      assertTrue(actioneer.isInMeleeRange(attacker, target, 3));
      assertFalse(actioneer.isInMeleeRangeAtTick(attacker, target, 3, 2));
    } finally {
      world.dispose();
    }
  }

  @Test
  void playerNormalMeleeAttackRequiresTargetRange() {
    assertTrue(Actioneer.requiresNormalMeleeCastRange(
        SkillCodes.attack, 42, true, false));
  }

  @Test
  void rangedAndUntargetedNormalAttacksDoNotRequireMeleeRange() {
    assertFalse(Actioneer.requiresNormalMeleeCastRange(
        SkillCodes.attack, 42, true, true));
    assertFalse(Actioneer.requiresNormalMeleeCastRange(
        SkillCodes.attack, Engine.INVALID_ENTITY, true, false));
    assertFalse(Actioneer.requiresNormalMeleeCastRange(
        SkillCodes.attack, 42, false, false));
    assertFalse(Actioneer.requiresNormalMeleeCastRange(
        SkillCodes.throw_, 42, true, false));
  }

  private static int unit(World world, float x, float y) {
    int entity = world.create();
    world.getMapper(Class.class).create(entity).type = Class.Type.PLR;
    world.getMapper(Position.class).create(entity).position.set(x, y);
    world.getMapper(Size.class).create(entity).size = Size.MEDIUM;
    return entity;
  }

  private static CombatSystem.CombatResult blockedResult() {
    CombatSystem.CombatResult result = new CombatSystem.CombatResult();
    result.hit = true;
    result.blocked = true;
    result.blockAnimationAllowed = true;
    result.defenseType = DefenseCalculator.DEFENSE_BLOCK;
    return result;
  }

  private static final class ReactionHarness {
    final World world;
    final TestActioneer actioneer = new TestActioneer();

    ReactionHarness() {
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new CofManager())
          .build());
      world.process();
      actioneer.mClass = world.getMapper(Class.class);
      actioneer.mCasting = world.getMapper(Casting.class);
      actioneer.mSequence = world.getMapper(Sequence.class);
      actioneer.mAnimData = world.getMapper(AnimData.class);
      actioneer.mCofReference = world.getMapper(CofReference.class);
      actioneer.attach(world);
    }

    int player(byte initialMode) {
      int entity = world.create();
      world.getMapper(Class.class).create(entity).type = Class.Type.PLR;
      world.getMapper(CofReference.class).create(entity).set("AM", initialMode);
      world.getMapper(AnimData.class).create(entity).numFrames = 512;
      world.process();
      return entity;
    }

    void dispose() {
      world.dispose();
    }
  }

  private static final class TestActioneer extends Actioneer {
    void attach(World world) {
      setWorld(world);
    }
  }
}
