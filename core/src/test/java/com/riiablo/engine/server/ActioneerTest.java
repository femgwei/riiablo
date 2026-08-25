package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class ActioneerTest {
  @Test
  void nativeResurrectSkillMayExecuteAgainstDeadTarget() {
    Skills.Entry resurrect = new Skills.Entry();
    resurrect.srvdofunc = 97;
    assertTrue(Actioneer.allowsDeadTarget(resurrect));

    Skills.Entry attack = new Skills.Entry();
    attack.srvdofunc = 1;
    assertFalse(Actioneer.allowsDeadTarget(attack));
    assertFalse(Actioneer.allowsDeadTarget(null));
  }
}
