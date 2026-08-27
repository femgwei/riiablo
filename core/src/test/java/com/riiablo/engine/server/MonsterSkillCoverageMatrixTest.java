package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.ai.AI;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Data-driven coverage for monster skills across all five acts.
 *
 * <p>The test deliberately reports unsupported server functions instead of
 * silently treating a data row as implemented.  This gives the porting work
 * a stable, repeatable inventory while still asserting that all referenced
 * rows and AI implementations are loadable.</p>
 */
class MonsterSkillCoverageMatrixTest extends RiiabloTest {
  private static final Set<Integer> SUPPORTED_SRV_DO = Set.of(
      0, 1, 3, 5, 7, 8, 22, 23, 24, 26, 27, 28, 67, 77, 83, 85, 86, 87,
      90, 91, 95, 96, 97, 98, 109, 129, 150);

  @Test
  void dumpAllActsMonsterSkillMatrix() {
    Set<String> monsterIds = new LinkedHashSet<>();
    Map<String, Integer> firstAct = new LinkedHashMap<>();
    @SuppressWarnings("unchecked")
    Set<String>[] actRosters = new Set[] {new LinkedHashSet<>(), new LinkedHashSet<>(),
        new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>()};
    for (Levels.Entry level : Riiablo.files.Levels) {
      if (level.Act < 0 || level.Act > 4) continue;
      addAll(monsterIds, firstAct, actRosters[level.Act], level.Act, level.mon);
      addAll(monsterIds, firstAct, actRosters[level.Act], level.Act, level.nmon);
      addAll(monsterIds, firstAct, actRosters[level.Act], level.Act, level.umon);
      addAll(monsterIds, firstAct, actRosters[level.Act], level.Act, level.cmon);
    }

    Set<String> missing = new LinkedHashSet<>();
    Set<String> unsupported = new LinkedHashSet<>();
    Set<String> missileFailures = new LinkedHashSet<>();
    int skillRows = 0;
    int projectileRows = 0;

    for (String id : monsterIds) {
      MonStats.Entry monster = Riiablo.files.monstats.get(id);
      if (monster == null) {
        missing.add("monstats:" + id);
        continue;
      }
      AI ai = AI.findAI(-1, monster.AI);
      if (ai == null) missing.add(id + ":ai:" + monster.AI);

      for (int slot = 0; slot < 8; slot++) {
        String name = skillName(monster, slot);
        if (!hasText(name)) continue;
        skillRows++;
        Skills.Entry skill = Riiablo.files.skills.get(name);
        if (skill == null) {
          missing.add(id + "#" + (slot + 1) + ":skill:" + name);
          continue;
        }
        int fn = skill.srvdofunc;
        boolean projectile = hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
            || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
            || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
            || hasText(skill.cltmissilec) || hasText(skill.cltmissiled);
        if (projectile) projectileRows++;
        if (!SUPPORTED_SRV_DO.contains(fn)) unsupported.add(id + "#" + (slot + 1)
            + ":" + name + ":srvDo=" + fn);
        if (requiresProjectile(fn) && !projectile) missileFailures.add(id + "#" + (slot + 1)
            + ":missing_missile");
        for (String missileName : missileNames(skill)) {
          if (!hasText(missileName)) continue;
          if (Riiablo.files.Missiles.get(missileName) == null) {
            missileFailures.add(id + "#" + (slot + 1) + ":missile=" + missileName);
          }
        }
        if ((fn == 87 || fn == 91) && !hasText(monster.spawn)) {
          missing.add(id + "#" + (slot + 1) + ":spawn");
        } else if ((fn == 87 || fn == 91) && Riiablo.files.monstats.get(monster.spawn) == null) {
          missing.add(id + "#" + (slot + 1) + ":spawn=" + monster.spawn);
        }
        System.out.println("[MONSTER_SKILL_MATRIX] act=" + (firstAct.get(id) + 1)
            + " monster=" + id + " slot=" + (slot + 1) + " skill=" + name
            + " srvDo=" + fn + " projectile=" + projectile + " ai=" + monster.AI
            + " status=" + (SUPPORTED_SRV_DO.contains(fn) ? "PASS" : "UNSUPPORTED"));
      }
    }

    System.out.println("[MONSTER_SKILL_MATRIX_SUMMARY] acts=5 monsters=" + monsterIds.size()
        + " skillRows=" + skillRows + " projectileSkills=" + projectileRows
        + " actRoster=" + rosterCounts(actRosters)
        + " missingRows=" + missing + " unsupportedSrvDo=" + unsupported
        + " missileFailures=" + missileFailures);
    assertFalse(monsterIds.isEmpty(), "All-act monster roster is empty");
    assertTrue(skillRows > 0, "All-act monster skills are missing");
    assertTrue(missing.isEmpty(), "Monster skill data references missing rows: " + missing);
    assertTrue(missileFailures.isEmpty(), "Monster missile references are invalid: " + missileFailures);
  }

  private static String rosterCounts(Set<String>[] rosters) {
    int[] counts = new int[rosters.length];
    for (int i = 0; i < rosters.length; i++) counts[i] = rosters[i].size();
    return java.util.Arrays.toString(counts);
  }

  private static boolean requiresProjectile(int fn) {
    return fn == 3 || fn == 5 || fn == 8 || fn == 22 || fn == 24 || fn == 28
        || fn == 85 || fn == 95;
  }

  private static String[] missileNames(Skills.Entry skill) {
    return new String[] {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
        skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
        skill.cltmissilec, skill.cltmissiled};
  }

  private static String skillName(MonStats.Entry monster, int slot) {
    switch (slot) {
      case 0: return monster.Skill1;
      case 1: return monster.Skill2;
      case 2: return monster.Skill3;
      case 3: return monster.Skill4;
      case 4: return monster.Skill5;
      case 5: return monster.Skill6;
      case 6: return monster.Skill7;
      case 7: return monster.Skill8;
      default: return null;
    }
  }

  private static void addAll(Set<String> out, Map<String, Integer> firstAct,
      Set<String> actRoster, int act, String[] values) {
    if (values == null) return;
    for (String value : values) {
      if (!hasText(value)) continue;
      out.add(value);
      actRoster.add(value);
      firstAct.putIfAbsent(value, act);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
