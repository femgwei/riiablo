package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** 1.10f Skills.txt contract for the native Necromancer summon family. */
class NativeNecromancerSummonDataTest extends RiiabloTest {
  @Test
  void skeletonAndMageRowsUseNativeRaiseFunction() {
    int[] ids = {SkillId.RAISE_SKELETON, SkillId.RAISE_SKELETAL_MAGE};
    for (int id : ids) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "missing Necromancer summon skill id=" + id);
      assertTrue(skill.srvdofunc == 31, skill.skill + " must use SrvDo031");
      assertTrue(skill.summon != null && !skill.summon.isEmpty(),
          skill.skill + " summon row missing");
      MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
      assertNotNull(summon, skill.skill + " summon MonStats row missing: " + skill.summon);
      assertTrue(PetType.canonical(skill.pettype).length() > 0,
          skill.skill + " PetType missing");
      assertTrue(skill.petmax != null && !skill.petmax.isEmpty(),
          skill.skill + " PetMax formula missing");
    }
  }
}
