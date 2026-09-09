package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.ai.NecroPet;
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

  @Test
  void reviveAndGolemRowsUseNativeFunctionsAndData() {
    int[] ids = {SkillId.CLAY_GOLEM, SkillId.BLOOD_GOLEM,
        SkillId.FIRE_GOLEM, SkillId.IRON_GOLEM, SkillId.REVIVE};
    int[] functions = {56, 56, 56, 57, 58};
    for (int i = 0; i < ids.length; i++) {
      Skills.Entry skill = Riiablo.files.skills.get(ids[i]);
      assertNotNull(skill, "missing Necromancer summon skill id=" + ids[i]);
      assertTrue(skill.srvdofunc == functions[i],
          skill.skill + " must use SrvDo" + functions[i]);
      if (ids[i] != SkillId.REVIVE) {
        assertTrue(skill.summon != null && !skill.summon.isEmpty(),
            skill.skill + " summon row missing");
        MonStats.Entry summon = findMonster(skill.summon);
        assertNotNull(summon,
            skill.skill + " summon MonStats row missing: " + skill.summon);
        assertEquals("NecroPet", summon.AI,
            skill.skill + " must use native AITHINK_Fn067_NecroPet");
        assertTrue(AI.findAI(-1, summon.AI) instanceof NecroPet,
            skill.skill + " must not fall through GenericMonster");
      }
      assertTrue(skill.pettype != null && !skill.pettype.isEmpty(),
          skill.skill + " PetType missing");
      assertTrue(skill.petmax != null && !skill.petmax.isEmpty(),
          skill.skill + " PetMax formula missing");
    }
  }

  @Test
  void itemsExposeNativeMetalBitForIronGolem() {
    assertTrue((Riiablo.files.weapons.get("ssd").bitfield1 & 2) != 0,
        "Short Sword must retain native Items.txt metal bit");
  }

  private static MonStats.Entry findMonster(String id) {
    MonStats.Entry exact = Riiablo.files.monstats.get(id);
    if (exact != null) return exact;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (row.Id != null && row.Id.equalsIgnoreCase(id)) return row;
    }
    return null;
  }
}
