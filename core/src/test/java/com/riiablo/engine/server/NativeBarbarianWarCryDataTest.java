package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

class NativeBarbarianWarCryDataTest extends RiiabloTest {
  @Test
  void targetStatesAndServerFunctionsComeFromNativeSkillsData() {
    Skills.Entry howl = skill("Howl");
    assertEquals(130, howl.Id);
    assertEquals(22, howl.srvdofunc);
    assertEquals("terror", howl.auratargetstate);
    assertEquals(StateId.TERROR, BarbarianSkills.getWarCryStateId(howl, true));
    assertMissile(howl.srvmissilea, 1, 17, false, false);

    Skills.Entry taunt = skill("Taunt");
    assertEquals(137, taunt.Id);
    assertEquals(71, taunt.srvdofunc);
    assertEquals("taunt", taunt.auratargetstate);
    assertEquals(StateId.TAUNT, BarbarianSkills.getWarCryStateId(taunt, true));
    assertEquals("item_tohit_percent", taunt.aurastat[0]);
    assertEquals("damagepercent", taunt.aurastat[1]);

    Skills.Entry shout = skill("Shout");
    assertEquals(138, shout.Id);
    assertEquals(68, shout.srvdofunc);
    assertEquals("shout", shout.aurastate);
    assertEquals("shout", shout.auratargetstate);
    assertEquals(StateId.SHOUT, BarbarianSkills.getWarCryStateId(shout, false));
    assertMissile(shout.srvmissilea, 1, 18, false, true);

    Skills.Entry battleCry = skill("Battle Cry");
    assertEquals(146, battleCry.Id);
    assertEquals(68, battleCry.srvdofunc);
    assertEquals("battlecry", battleCry.auratargetstate);
    assertEquals(StateId.BATTLECRY, BarbarianSkills.getWarCryStateId(battleCry, true));
    assertMissile(battleCry.srvmissilea, 1, 21, false, false);
  }

  @Test
  void nativeFormulasDriveDurationsAndModifiers() {
    Skills.Entry howl = skill("Howl");
    assertEquals(24, BarbarianSkills.getHowlAiRange(howl, 1));
    assertEquals(29, BarbarianSkills.getHowlAiRange(howl, 2));
    assertEquals(75, BarbarianSkills.getHowlDuration(howl, 1));
    assertEquals(100, BarbarianSkills.getHowlDuration(howl, 2));
    assertEquals(false, BarbarianSkills.canHowlTarget(howl, 1, 1, 3));
    assertEquals(true, BarbarianSkills.canHowlTarget(howl, 1, 2, 3));

    Skills.Entry shout = skill("Shout");
    assertEquals(500, BarbarianSkills.getWarCryDuration(shout, 1, name -> 0));
    assertEquals(875, BarbarianSkills.getWarCryDuration(shout, 1,
        name -> "Battle Orders".equals(name) ? 2 : 1));

    Skills.Entry battleCry = skill("Battle Cry");
    assertEquals(300, BarbarianSkills.getWarCryDuration(battleCry, 1, name -> 0));
    assertEquals(360, BarbarianSkills.getWarCryDuration(battleCry, 2, name -> 0));
  }

  private static Skills.Entry skill(String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill, name);
    return skill;
  }

  private static void assertMissile(
      String name, int srvDo, int srvHit, boolean collideKill, boolean collideFriend) {
    Missiles.Entry missile = Riiablo.files.Missiles.get(name);
    assertNotNull(missile, name);
    assertEquals(srvDo, missile.pSrvDoFunc);
    assertEquals(srvHit, missile.pSrvHitFunc);
    assertEquals(collideKill, missile.CollideKill);
    assertEquals(collideFriend, missile.CollideFriend);
  }
}
