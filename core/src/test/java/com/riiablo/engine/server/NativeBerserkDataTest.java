package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class NativeBerserkDataTest extends RiiabloTest {
  @Test
  void nativeRowDrivesBerserkFormulas() {
    Skills.Entry s = Riiablo.files.skills.get("Berserk");
    assertNotNull(s);
    assertEquals(152, s.Id);
    assertEquals(39, s.srvstfunc);
    assertEquals(2, s.srvdofunc);
    assertEquals("A1", s.anim);
    assertEquals("berserk", s.aurastate);
    assertEquals("mag", s.EType);
    assertEquals(150, BarbarianSkills.calculateBerserkDamageBonus(s, 1, name -> 0));
    assertEquals(220, BarbarianSkills.calculateBerserkDamageBonus(s, 1,
        name -> "Howl".equals(name) ? 3 : "Shout".equals(name) ? 4 : 0));
    assertEquals(100, BarbarianSkills.getBerserkMagicConversion(s, 1, name -> 0));
    assertEquals(68, BarbarianSkills.getBerserkDuration(s, 1, name -> 0));
    assertEquals(33, BarbarianSkills.getBerserkDuration(s, 20, name -> 0));
  }
}
