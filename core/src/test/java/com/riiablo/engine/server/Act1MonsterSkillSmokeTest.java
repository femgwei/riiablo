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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Data-driven, headless smoke test for every Act I monster skill.
 *
 * <p>This test does not fake a complete world tick. It validates the inputs
 * consumed by the real Actioneer -> ServerSkillSystem chain and emits one
 * structured line per monster/skill, making missing wiring immediately
 * visible in CI or game.log.</p>
 */
class Act1MonsterSkillSmokeTest extends RiiabloTest {
  private static final Set<Integer> SUPPORTED = Set.of(
      0, 1, 3, 5, 7, 8, 22, 24, 27, 28, 67, 77, 83, 85, 86, 87, 90, 91, 95, 96, 97, 150);

  @Test
  void everyAct1MonsterSkillHasExecutableServerWiring() {
    Set<String> monsterIds = new LinkedHashSet<>();
    for (Levels.Entry level : Riiablo.files.Levels) {
      if (level.Act != 1) continue;
      addAll(monsterIds, level.mon);
      addAll(monsterIds, level.nmon);
      addAll(monsterIds, level.umon);
      addAll(monsterIds, level.cmon);
    }

    int monsterCount = 0;
    int skillCount = 0;
    int projectileCount = 0;
    Set<String> failures = new LinkedHashSet<>();
    for (String id : monsterIds) {
      MonStats.Entry monster = Riiablo.files.monstats.get(id);
      if (monster == null) {
        failures.add(id + ":missing_monstats");
        continue;
      }
      monsterCount++;
      // Resolve the same dynamic AI class used by ServerEntityFactory. A
      // GenericMonster fallback is valid, but a null/throwing constructor is
      // not; AI.findAI logs the fallback reason for game.log diagnostics.
      AI ai = AI.findAI(-1, monster.AI);
      if (ai == null) failures.add(id + ":ai_null:" + monster.AI);

      for (int slot = 0; slot < 8; slot++) {
        String skillName = skillName(monster, slot);
        if (skillName == null || skillName.isEmpty()) continue;
        skillCount++;
        Skills.Entry skill = Riiablo.files.skills.get(skillName);
        if (skill == null) {
          failures.add(id + "#" + (slot + 1) + ":missing_skill:" + skillName);
          continue;
        }
        int fn = skill.srvdofunc;
        boolean projectile = hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
            || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
            || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
            || hasText(skill.cltmissilec) || hasText(skill.cltmissiled);
        if (projectile) projectileCount++;
        if (!SUPPORTED.contains(fn)) failures.add(id + "#" + (slot + 1)
            + ":unsupported_srvDo:" + fn + ":" + skillName);
        if (requiresProjectile(fn) && !projectile) failures.add(id + "#" + (slot + 1)
            + ":projectile_missing:" + skillName);
        // Bestow (96) intentionally falls back to ZakarumHeal when calc1/2
        // are empty; this is the native D2MOO behaviour implemented by
        // Actioneer.resolveBestowPercentRange.
        if (fn == 86 && !hasText(skill.calc1)) failures.add(id + "#"
            + (slot + 1) + ":formula_missing:" + skillName);
        if ((fn == 87 || fn == 91) && !hasText(monster.spawn)) failures.add(id + "#"
            + (slot + 1) + ":spawn_missing:" + skillName);
        if (hasText(monster.spawn) && (fn == 87 || fn == 91)
            && Riiablo.files.monstats.get(monster.spawn) == null) {
          failures.add(id + "#" + (slot + 1) + ":spawn_row_missing:" + monster.spawn);
        }
        if (projectile) {
          for (String missileName : missileNames(skill)) {
            if (!hasText(missileName)) continue;
            Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
            if (missile == null) failures.add(id + "#" + (slot + 1)
                + ":missile_row_missing:" + missileName);
          }
        }
        final String rowKey = id + "#" + (slot + 1) + ":";
        System.out.println("[ACT1_SKILL_SMOKE] monster=" + id + " slot=" + (slot + 1)
            + " skill=" + skillName + " srvstfunc=" + skill.srvstfunc
            + " srvdofunc=" + fn + " projectile=" + projectile
            + " ai=" + monster.AI + " status="
            + (failures.stream().noneMatch(v -> v.startsWith(rowKey))
                ? "PASS" : "FAIL"));
      }
    }

    System.out.println("[ACT1_SKILL_SMOKE_SUMMARY] monsters=" + monsterCount
        + " skills=" + skillCount + " projectileSkills=" + projectileCount
        + " failures=" + failures);
    assertFalse(monsterIds.isEmpty(), "Act I level roster is empty");
    assertTrue(monsterCount > 0, "Act I monster rows are missing");
    assertTrue(skillCount > 0, "Act I monster skills are missing");
    assertTrue(failures.isEmpty(), "Act I skill wiring failures: " + failures);
  }

  private static boolean requiresProjectile(int fn) {
    // Fire Hit (83) is a direct melee/elemental hit despite its name.
    return fn == 3 || fn == 5 || fn == 8 || fn == 22 || fn == 24 || fn == 28
        || fn == 85 || fn == 95;
  }

  private static String[] missileNames(Skills.Entry skill) {
    return new String[] {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
        skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
        skill.cltmissilec, skill.cltmissiled};
  }

  private static void addAll(Set<String> out, String[] values) {
    if (values == null) return;
    for (String value : values) if (hasText(value)) out.add(value);
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

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
