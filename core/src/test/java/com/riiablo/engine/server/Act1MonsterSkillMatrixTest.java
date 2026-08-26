package com.riiablo.engine.server;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

/** Emits the data-driven Act I monster skill surface before native porting. */
class Act1MonsterSkillMatrixTest extends RiiabloTest {
  private static final Set<Integer> IMPLEMENTED_SRV_DO = Set.of(
      0, 1, 3, 5, 7, 8, 22, 27, 77, 83, 85, 86, 87, 90, 91, 95, 96, 97, 150);

  @Test
  void dumpAct1MonsterSkillMatrix() {
    Set<String> monsterIds = new LinkedHashSet<>();
    for (Levels.Entry level : Riiablo.files.Levels) {
      if (level.Act != 1) continue;
      addAll(monsterIds, level.mon);
      addAll(monsterIds, level.nmon);
      addAll(monsterIds, level.umon);
      addAll(monsterIds, level.cmon);
    }

    Map<String, String> rows = new LinkedHashMap<>();
    for (String monsterId : monsterIds) {
      MonStats.Entry monster = Riiablo.files.monstats.get(monsterId);
      if (monster == null) continue;
      for (int slot = 1; slot <= 8; slot++) {
        String skillName = skill(monster, slot);
        if (skillName == null || skillName.isEmpty()) continue;
        Skills.Entry skill = Riiablo.files.skills.get(skillName);
        if (skill == null) {
          rows.put(monster.Id + "#" + slot,
              "monster=" + monster.Id + " slot=" + slot + " skill=" + skillName
                  + " status=MISSING_SKILL_ROW");
          continue;
        }
        rows.put(monster.Id + "#" + slot,
            "monster=" + monster.Id + " slot=" + slot + " skill=" + skill.skill
                + " srvstfunc=" + skill.srvstfunc + " srvdofunc=" + skill.srvdofunc
                + " cltdofunc=" + skill.cltdofunc
                + " monanim=" + skill.monanim + " srvmissilea=" + skill.srvmissilea
                + " srvmissileb=" + skill.srvmissileb + " stsound=" + skill.stsound
                + " dosound=" + skill.dosound + " status="
                + (IMPLEMENTED_SRV_DO.contains(skill.srvdofunc) ? "IMPLEMENTED" : "MISSING"));
      }
    }

    int missing = 0;
    for (String row : rows.values()) {
      if (row.endsWith("status=MISSING")) missing++;
      System.out.println("[ACT1_MONSTER_SKILL_MATRIX] " + row);
    }
    System.out.println("[ACT1_MONSTER_SKILL_MATRIX_SUMMARY] monsters=" + monsterIds.size()
        + " skillRows=" + rows.size() + " missingSrvDo=" + missing);
  }

  private static void addAll(Set<String> result, String[] values) {
    if (values == null) return;
    for (String value : values) if (value != null && !value.isEmpty()) result.add(value);
  }

  private static String skill(MonStats.Entry monster, int slot) {
    switch (slot) {
      case 1: return monster.Skill1;
      case 2: return monster.Skill2;
      case 3: return monster.Skill3;
      case 4: return monster.Skill4;
      case 5: return monster.Skill5;
      case 6: return monster.Skill6;
      case 7: return monster.Skill7;
      case 8: return monster.Skill8;
      default: return null;
    }
  }
}
