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
}
