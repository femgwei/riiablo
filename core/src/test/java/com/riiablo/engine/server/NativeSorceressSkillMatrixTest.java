package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillBehavior;
import com.riiablo.engine.server.skill.NativeSkillBehaviorRegistry;
import org.junit.jupiter.api.Test;

/** Exact-ID/callback audit for the 1.10f Sorceress tree. */
class NativeSorceressSkillMatrixTest extends RiiabloTest {
  @Test
  void everySorceressRowHasAnExactNativeDeclaration() {
    assertEquals(30, NativeSkillBehaviorRegistry.sorceressSize());
    for (int id = CharacterClass.SORCERESS.firstSpell;
        id < CharacterClass.SORCERESS.lastSpell; id++) {
      final int skillId = id;
      Skills.Entry skill = Riiablo.files.skills.get(skillId);
      assertNotNull(skill, "missing Sorceress Skills.txt row " + skillId);
      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveSorceress(skill);
      assertNotNull(behavior,
          () -> "unregistered Sorceress row id=" + skillId + " name=" + skill.skill
              + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc);
      assertEquals(id, behavior.skillId);
      assertEquals(skill.srvstfunc, behavior.serverStartFunction);
      assertEquals(skill.srvdofunc, behavior.serverDoFunction);
    }
  }

  @Test
  void changedCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(62);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertEquals(null, NativeSkillBehaviorRegistry.resolveSorceress(skill));
    } finally {
      skill.srvdofunc = original;
    }
  }
}
