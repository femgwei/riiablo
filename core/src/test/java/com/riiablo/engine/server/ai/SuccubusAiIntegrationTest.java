package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class SuccubusAiIntegrationTest extends RiiabloTest {
  private static final float FRAME = Animation.FRAME_DURATION;

  private Fixture fixture(int difficulty, int distance) {
    Fixture f = new Fixture("succubus1", difficulty, distance);
    // The real row leaves Skill1/2 empty. Exercise the native dormant branches
    // on a detached row, retaining all other real-row fields and Skills rows.
    MonStats.Entry original = f.world.getMapper(Monster.class).get(f.actor).monstats;
    MonStats.Entry directed = new MonStats.Entry();
    try {
      for (java.lang.reflect.Field field : MonStats.Entry.class.getFields())
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()))
          field.set(directed,field.get(original));
    } catch (IllegalAccessException e) { throw new AssertionError(e); }
    directed.Skill1 = "Amplify Damage";
    directed.Skill2 = "Weaken";
    directed.Sk1mode = directed.Sk2mode = "S1";
    f.world.getMapper(Monster.class).get(f.actor).monstats = directed;
    Arrays.fill(f.ai.params, 0);
    f.ai.params[3] = 20;
    f.ai.params[4] = 17;
    f.ai.params[5] = 19;
    f.ai.params[6] = 50;
    targetStats(f, 100,100,100);
    return f;
  }

  private void targetStats(Fixture f, float hp, float maxHp, float maxMana) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.maxmana, maxMana);
    attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(f.target).attrs = attrs;
  }

  private void assertSlot(Fixture f, int slot) {
    MonStats.Entry mon = f.world.getMapper(Monster.class).get(f.actor).monstats;
    String[] skills = {mon.Skill1,mon.Skill2,mon.Skill3,mon.Skill4,mon.Skill5};
    String[] modes = {mon.Sk1mode,mon.Sk2mode,mon.Sk3mode,mon.Sk4mode,mon.Sk5mode};
    assertNotNull(f.casting(), "slot=" + slot + " table=" + Arrays.toString(skills));
    assertNotNull(Riiablo.files.skills.get(skills[slot]), skills[slot]);
    assertEquals(Riiablo.files.skills.get(skills[slot]).Id, f.casting().skillId);
    assertEquals(f.target, f.casting().targetId);
    assertEquals(Riiablo.files.MonMode.index(modes[slot]), f.sequence().mode1);
  }

  @Test void sevenRealRowsSelectDedicatedAi() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Succubus".equals(mon.AI)) continue;
      count++;
      System.out.println("Succubus row=" + mon.Id + " skills=" + Arrays.asList(
          mon.Skill1,mon.Skill2,mon.Skill3,mon.Skill4,mon.Skill5));
    }
    assertEquals(7, count);
    for (MonStats.Entry mon : Riiablo.files.monstats)
      if ("Succubus".equals(mon.AI))
        assertEquals("Succubus", AI.findAI(12, mon.AI).getClass().getSimpleName());
  }

  @Test void firstCursePrecedesMeleeAndOtherCursesAcrossDifficulties() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = fixture(difficulty,1)) {
        f.ai.params[0] = f.ai.params[2] = f.ai.params[7] = 100;
        f.ai.update(FRAME);
        assertSlot(f,0);
        f.sequence().started = true;
        f.ai.update(100);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void secondCurseUsesSelfLifeAndInclusiveThreshold() {
    try (Fixture f = fixture(0,1)) {
      f.ai.params[2] = 100;
      f.ai.params[6] = 101;
      f.ai.params[7] = 50;
      f.health(50,100);
      f.ai.update(FRAME);
      assertSlot(f,1);
    }
  }

  @Test void lifePercentageTruncatesEachFixedPointStatBeforeDivision() {
    try (Fixture f = fixture(0,1)) {
      f.ai.params[2] = 100;
      f.ai.params[6] = 50;
      // Native: int(50.1)/int(100.9) => 50%, not 49%.
      targetStats(f,50.1f,100.9f,200);
      f.ai.update(FRAME);
      assertSlot(f,0);
    }
  }

  @Test void thirdAndFourthCursesUseStrictMaxLifeVersusManaComparison() {
    for (boolean lifeGreater : new boolean[] {true,false}) {
      try (Fixture f = fixture(0,1)) {
        f.ai.params[2] = 100;
        f.ai.params[6] = 101;
        targetStats(f,100,100,lifeGreater ? 99 : 100);
        f.ai.update(FRAME);
        assertSlot(f,lifeGreater ? 2 : 3);
      }
    }
  }

  @Test void existingCurseAndDistanceEqualitySkipTheCurseChain() {
    for (boolean cursed : new boolean[] {true,false}) {
      try (Fixture f = fixture(0,1)) {
        f.ai.params[0] = f.ai.params[2] = 100;
        if (cursed) f.world.getMapper(UnitStates.class).create(f.target).init(f.world,f.target)
            .stateList.addState(StateId.AMPLIFYDAMAGE,100,1,f.actor);
        else f.ai.params[3] = 1;
        f.ai.update(FRAME);
        assertNotNull(f.casting());
        assertEquals(0,f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1,f.sequence().mode1);
      }
    }
  }

  @Test void failedMeleeAttackUsesSlotFourWait() {
    try (Fixture f = fixture(0,1)) {
      f.ai.update(FRAME);
      assertNull(f.casting());
      f.ai.params[0] = 100;
      f.ai.update(16 * FRAME);
      assertNull(f.casting());
      f.ai.update(FRAME + .00001f);
      assertEquals(0,f.casting().skillId);
    }
  }

  @Test void rangedSkillFiveUsesSlotSevenAsChanceBeforeApproach() {
    try (Fixture f = fixture(0,10)) {
      f.ai.params[1] = f.ai.params[7] = 100;
      f.ai.update(FRAME);
      assertSlot(f,4);
    }
  }

  @Test void failedRangedCastUsesApproachOrSlotFiveWait() {
    try (Fixture f = fixture(0,10)) {
      f.ai.update(FRAME);
      assertEquals("IDLE",f.ai.getState());
      f.ai.params[1] = 100;
      f.ai.update(18 * FRAME);
      assertEquals("IDLE",f.ai.getState());
      f.ai.update(FRAME + .00001f);
      assertEquals("APPROACH",f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void deathStopsFurtherDecisions() {
    try (Fixture f = fixture(0,1)) {
      f.ai.params[2] = 100;
      f.ai.update(FRAME);
      f.ai.kill();
      f.ai.update(100);
      assertNull(f.casting());
      assertEquals(Engine.Monster.MODE_DT,f.sequence().mode1);
    }
  }
}
