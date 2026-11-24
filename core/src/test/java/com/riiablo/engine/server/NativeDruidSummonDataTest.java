package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Skills.txt contract for the D2MOO SrvDo114/115/119 Druid summon family. */
class NativeDruidSummonDataTest extends RiiabloTest {
  @Test
  void allDruidSummonRowsAreDataBacked() {
    int[] ids = {SkillId.RAVEN, SkillId.POISON_CREEPER, SkillId.OAK_SAGE,
        SkillId.SUMMON_SPIRIT_WOLF, SkillId.CARRION_VINE,
        SkillId.HEART_OF_WOLVERINE, SkillId.SUMMON_DIRE_WOLF,
        SkillId.SOLAR_CREEPER, SkillId.SPIRIT_OF_BARBS, SkillId.SUMMON_GRIZZLY};
    for (int id : ids) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "missing Druid skill id=" + id);
      assertTrue(skill.srvdofunc == 114 || skill.srvdofunc == 115 || skill.srvdofunc == 119,
          skill.skill + " must use native summon SrvDoFunc");
      assertNotNull(skill.summon, skill.skill + " summon row missing");
      assertTrue(!skill.summon.isEmpty(), skill.skill + " summon row empty");
      MonStats.Entry monster = Riiablo.files.monstats.get(skill.summon);
      assertNotNull(monster, skill.skill + " summon MonStats row missing: " + skill.summon);
      assertTrue(PetType.canonical(skill.pettype).length() > 0,
          skill.skill + " PetType missing");
    }
  }

  @Test
  void nativePetMaxFormulasMatchD2MooFamilies() {
    Skills.Entry raven = Riiablo.files.skills.get(SkillId.RAVEN);
    Skills.Entry spiritWolf = Riiablo.files.skills.get(SkillId.SUMMON_SPIRIT_WOLF);
    Skills.Entry direWolf = Riiablo.files.skills.get(SkillId.SUMMON_DIRE_WOLF);
    Skills.Entry grizzly = Riiablo.files.skills.get(SkillId.SUMMON_GRIZZLY);
    assertEquals(5, SkillFormula.evaluate(raven.petmax, raven, 20));
    assertEquals(5, SkillFormula.evaluate(spiritWolf.petmax, spiritWolf, 8));
    assertEquals(3, SkillFormula.evaluate(direWolf.petmax, direWolf, 8));
    assertEquals(1, SkillFormula.evaluate(grizzly.petmax, grizzly, 20));
  }

  @Test
  void plaguePoppyUsesNativeVinesAiAndVineAttackContract() {
    MonStats.Entry monster = Riiablo.files.monstats.get("plaguepoppy");
    assertNotNull(monster);
    assertEquals("Vines", monster.AI);
    assertEquals(15, monster.aidel[0]);
    assertEquals(100, monster.aip1[0], "Vine Attack delay is 100 frames");
    assertEquals(20, monster.aip2[0], "Vine target sight distance");
    assertEquals(25, monster.aip3[0], "Vine stall duration");
    assertEquals(10, monster.aip4[0], "Vine escape distance");
    assertEquals(35, monster.aip5[0], "Vine owner leash distance");
    assertEquals("Vine Attack", monster.Skill1);
    Skills.Entry attack = Riiablo.files.skills.get(monster.Skill1);
    assertNotNull(attack);
    assertEquals(130, attack.srvdofunc);
    assertEquals("plague vines", attack.srvmissilea);
  }

  @Test
  void grizzlyUsesNativeFn112AiParameters() {
    MonStats.Entry bear = Riiablo.files.monstats.get("druidbear");
    assertNotNull(bear);
    assertEquals("DruidBear", bear.AI);
    assertEquals(5, bear.Velocity);
    assertEquals(9, bear.Run);
    assertEquals(15, bear.aidel[0]);
    assertEquals(15, bear.aip1[0], "Fn112 stall duration");
    assertEquals(40, bear.aip2[0], "Fn112 run chance");
    assertEquals(50, bear.aip3[0], "Fn112 skill-vs-attack chance");
    assertEquals("BearSmite", bear.Skill1);
    assertEquals("seq_bearsmite", bear.Sk1mode);
  }

  @Test
  void corpseCyclersUseFn111WithSeparateLifeAndManaDelayMissiles() {
    int[] ids = {SkillId.CARRION_VINE, SkillId.SOLAR_CREEPER};
    for (int id : ids) {
      Skills.Entry summon = Riiablo.files.skills.get(id);
      MonStats.Entry vine = Riiablo.files.monstats.get(summon.summon);
      assertEquals("CycleOfLife", vine.AI);
      com.riiablo.codec.excel.NativeMonStats.Entry raw = Riiablo.files.NativeMonStats.get(vine.Id);
      assertNotNull(raw);
      int[] actual = {vine.aip1[0], vine.aip2[0], vine.aip3[0], vine.aip4[0], vine.aip5[0]};
      for (int i = 0; i < actual.length; i++) {
        assertEquals(raw.nativeInteger("aip" + (i + 1)), actual[i]);
      }
      Skills.Entry cycle = Riiablo.files.skills.get(vine.Skill1);
      assertEquals(63, cycle.srvstfunc);
      assertEquals("S1", vine.Sk1mode.toUpperCase(java.util.Locale.ROOT));
      com.riiablo.codec.excel.Missiles.Entry delay = Riiablo.files.Missiles.get(cycle.srvmissilea);
      assertEquals(id == SkillId.CARRION_VINE ? 29 : 33, delay.pSrvDoFunc);
      assertEquals(51, delay.pCltDoFunc);
      assertEquals("recycler vine", delay.CltSubMissile[0]);
      assertEquals("recycler explosion", delay.CltSubMissile[1]);
    }
  }
}
