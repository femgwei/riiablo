package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS state transitions for A3 Mephisto tentacles. */
class TentacleAiIntegrationTest extends RiiabloTest {
  @Test
  void allTentacleRowsResolveDedicatedAisAndNativeSlots() {
    int tentacles = 0;
    int heads = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if ("Tentacle".equals(row.AI)) {
        tentacles++;
        assertTrue(AI.findAI(12, row.AI) instanceof Tentacle);
        assertNotNull(Riiablo.files.skills.get(row.Skill1));
        assertNotNull(Riiablo.files.skills.get(row.Skill2));
      } else if ("TentacleHead".equals(row.AI)) {
        heads++;
        assertTrue(AI.findAI(12, row.AI) instanceof TentacleHead);
        assertNotNull(Riiablo.files.skills.get(row.Skill1));
        assertNotNull(Riiablo.files.skills.get(row.Skill2));
      }
    }
    assertTrue(tentacles > 0);
    assertTrue(heads > 0);
  }

  @Test
  void tentacleStartsSubmergedSkillBeforeOrdinaryAttack() {
    try (Fixture f = fixture("Tentacle", 10f)) {
      int owner = owner(f, "mephisto");
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.ai.params[TentacleAI.PARAM_SUBMERGE_SECONDS] = 2;
      f.ai.params[TentacleAI.PARAM_STALL_DURATION] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("CAST", f.ai.getState());
      assertEquals(skillId(f, 0), f.casting().skillId);
      assertEquals(f.target, f.casting().targetId);
      assertFalse(f.ai.getState().equals("ATTACK"));
    }
  }

  @Test
  void tentacleRequiresLivingNativeOwnerAndDoesNotFallbackToPlayerTarget() {
    try (Fixture f = fixture("Tentacle", 1f)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    }
  }

  @Test
  void tentacleEmergesOnlyAfterSubmergeTimerAndActiveDistanceGate() {
    try (Fixture f = fixture("Tentacle", 1f)) {
      int owner = owner(f, "mephisto");
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.ai.params[TentacleAI.PARAM_SUBMERGE_SECONDS] = 1;
      f.ai.params[TentacleAI.PARAM_EMERGE_SECONDS] = 1;
      f.ai.params[TentacleAI.PARAM_ACTIVE_DISTANCE] = 5;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("CAST", f.ai.getState());
      f.finishAction();
      f.targetPosition().set(20, 10);
      f.ai.update(26 * Animation.FRAME_DURATION);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState(), "submerged unit stalls before emergence gate");
      f.targetPosition().set(11, 10);
      f.ai.update(2 * Animation.FRAME_DURATION);
      assertEquals(skillId(f, 1), f.casting().skillId);
      assertEquals(f.actor, f.casting().targetId);
    }
  }

  @Test
  void tentacleHeadUsesItsOwnEmergenceAndRangedAttackTarget() {
    try (Fixture f = fixture("TentacleHead", 1f)) {
      f.ai.params[TentacleAI.PARAM_ACTIVE_DISTANCE] = 5;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(skillId(f, 0), f.casting().skillId);
      f.finishAction();
      f.ai.update(26 * Animation.FRAME_DURATION);
      f.targetPosition().set(11, 10);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(skillId(f, 1), f.casting().skillId);
      assertEquals(f.actor, f.casting().targetId);
    }
  }

  @Test
  void tentacleAttackUsesNormalA1WhenSurfacedAndChanceSucceeds() {
    try (Fixture f = fixture("Tentacle", 1f)) {
      int owner = owner(f, "mephisto");
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.ai.params[TentacleAI.PARAM_ATTACK_CHANCE] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      f.finishAction();
      // Force surfaced state by waiting through submerge and emerge windows.
      f.ai.update(100 * Animation.FRAME_DURATION);
      f.finishAction();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  private static Fixture fixture(String ai, float distance) {
    String rowId = rowIdForAi(ai);
    Fixture f = new Fixture(rowId, 0, distance);
    f.ai.params[TentacleAI.PARAM_ATTACK_CHANCE] = 100;
    f.ai.params[TentacleAI.PARAM_SUBMERGE_CHANCE] = 0;
    f.ai.params[TentacleAI.PARAM_SUBMERGE_SECONDS] = 1;
    f.ai.params[TentacleAI.PARAM_EMERGE_SECONDS] = 1;
    f.ai.params[TentacleAI.PARAM_STALL_DURATION] = 1;
    f.ai.params[TentacleAI.PARAM_ACTIVE_DISTANCE] = 8;
    return f;
  }

  private static int owner(Fixture f, String rowId) {
    int owner = f.world.create();
    MonStats.Entry row = Riiablo.files.monstats.get(rowId);
    f.world.getMapper(Monster.class).create(owner).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    f.world.getMapper(Position.class).create(owner).position.set(10, 10);
    f.world.getMapper(CofReference.class).create(owner).mode = Engine.Monster.MODE_NU;
    f.world.process();
    return owner;
  }

  private static int skillId(Fixture f, int slot) {
    String name;
    switch (slot) {
      case 0: name = f.ai.monster.monstats.Skill1; break;
      case 1: name = f.ai.monster.monstats.Skill2; break;
      default: throw new IllegalArgumentException();
    }
    return Riiablo.files.skills.get(name).Id;
  }

  private static String rowIdForAi(String ai) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (ai.equals(row.AI)) return row.Id;
    }
    fail("missing AI " + ai);
    return null;
  }
}
