package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class NativeSubmergeDataTest extends RiiabloTest {
  @Test
  void frogDemonRowsResolveNativeSubmergeAndEmergeSkills() {
    int rows = 0;
    for (MonStats.Entry monster : Riiablo.files.monstats) {
      if (!"FrogDemon".equalsIgnoreCase(monster.AI)) continue;
      rows++;
      Skills.Entry submerge = Riiablo.files.skills.get(monster.Skill1);
      Skills.Entry emerge = Riiablo.files.skills.get(monster.Skill2);
      assertNotNull(submerge, "missing FrogDemon Skill1 for " + monster.Id);
      assertNotNull(emerge, "missing FrogDemon Skill2 for " + monster.Id);
      assertEquals(51, submerge.srvstfunc, "Skill1 must use native Submerge");
      assertEquals(52, emerge.srvstfunc, "Skill2 must use native Emerge");
    }
    assertTrue(rows > 0, "MonStats must expose FrogDemon AI rows");
  }
}
