package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Validates the seven character skill ranges and their server data wiring. */
class CharacterSkillMatrixTest extends RiiabloTest {
  @Test
  void everyCharacterClassSkillHasConsistentData() {
    Set<String> failures = new LinkedHashSet<>();
    int rows = 0;
    int activeRows = 0;
    int projectileRows = 0;
    for (CharacterClass characterClass : CharacterClass.values()) {
      int classRows = 0;
      for (int id = characterClass.firstSpell; id < characterClass.lastSpell; id++) {
        Skills.Entry skill = Riiablo.files.skills.get(id);
        if (skill == null) {
          failures.add(characterClass + ":missing_skill=" + id);
          continue;
        }
        rows++;
        classRows++;
        boolean passiveOrAura = skill.passive || skill.aura;
        if (!passiveOrAura) activeRows++;
        if (skill.reqlevel < 0 || skill.reqlevel > 99) failures.add(skill.skill + ":reqlevel=" + skill.reqlevel);
        // Skills.txt encodes per-level mana deltas as negative values for
        // skills whose cost decreases or stays flat (e.g. Magic Arrow uses
        // -1).  The fixed-point shift is likewise allowed to be zero/negative;
        // only the base costs must be non-negative and finite.
        if (skill.startmana < 0 || skill.minmana < 0 || skill.mana < 0
            || skill.lvlmana < -4096 || skill.manashift < -16) {
          failures.add(skill.skill + ":invalid_mana");
        }
        boolean projectile = hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
            || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
            || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
            || hasText(skill.cltmissilec) || hasText(skill.cltmissiled);
        if (projectile) {
          projectileRows++;
          for (String missile : missileNames(skill)) {
            if (hasText(missile) && Riiablo.files.Missiles.get(missile) == null) {
              failures.add(skill.skill + ":missing_missile=" + missile);
            }
          }
        }
        CharacterClass dataClass = Skills.getClass(skill.charclass);
        // Skills.txt uses an empty class for shared/built-in skills; those are
        // valid in every tree and must not be mistaken for a class mismatch.
        if (dataClass != null && dataClass != characterClass) {
          failures.add(skill.skill + ":class=" + dataClass + ":expected=" + characterClass);
        }
        if (!passiveOrAura && skill.srvstfunc < 0 && skill.cltstfunc < 0) {
          failures.add(skill.skill + ":no_start_function");
        }
        System.out.println("[CHAR_SKILL_MATRIX] class=" + characterClass + " id=" + id
            + " skill=" + skill.skill + " reqlevel=" + skill.reqlevel
            + " mana=" + skill.startmana + "/" + skill.mana + "+" + skill.lvlmana
            + " srvDo=" + skill.srvdofunc + " cltDo=" + skill.cltdofunc
            + " projectile=" + projectile + " passive=" + passiveOrAura
            + " status=" + (failures.stream().noneMatch(v -> v.startsWith(skill.skill + ":")) ? "PASS" : "FAIL"));
      }
      System.out.println("[CHAR_SKILL_CLASS_SUMMARY] class=" + characterClass
          + " range=" + characterClass.firstSpell + ".." + (characterClass.lastSpell - 1)
          + " rows=" + classRows);
    }
    System.out.println("[CHAR_SKILL_MATRIX_SUMMARY] classes=" + CharacterClass.values().length
        + " rows=" + rows + " active=" + activeRows + " projectile=" + projectileRows
        + " failures=" + failures);
    assertTrue(rows > 0, "Character skill table is empty");
    assertTrue(failures.isEmpty(), "Character skill data failures: " + failures);
  }

  private static String[] missileNames(Skills.Entry skill) {
    return new String[] {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
        skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
        skill.cltmissilec, skill.cltmissiled};
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
