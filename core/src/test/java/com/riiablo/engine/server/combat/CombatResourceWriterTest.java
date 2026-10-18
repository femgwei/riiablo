package com.riiablo.engine.server.combat;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatResourceWriterTest extends RiiabloTest {
  @Test void migratedAbsorbEntrypointsPreserveWholeUnitsAndCap() throws Exception {
    String[] classes = {"com.riiablo.engine.server.AssassinTrapSystem",
        "com.riiablo.engine.server.StateUpdater",
        "com.riiablo.engine.server.object.NativeTrapFireSystem"};
    for (String name : classes) {
      java.lang.reflect.Method method = Class.forName(name).getDeclaredMethod(
          name.endsWith("StateUpdater") ? "applyElementalAbsorb" : "applyAbsorb",
          Attributes.class, int.class);
      method.setAccessible(true);
      for (int current : new int[] {0, 1, 80, 100, 120}) {
        Attributes attrs = attributes(current, 100);
        float expected = Math.max(0f, Math.min(30f, 100f - current));
        assertEquals(expected, (Float) method.invoke(null, attrs, 30));
        assertEquals(current + expected, attrs.get(Stat.hitpoints).asFixed());
      }
      assertEquals(0f, (Float) method.invoke(null, null, 30));
    }
  }

  @Test void missingMaximumAndNegativeRequestDoNotWrite() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 10);
    attrs.reset();
    assertEquals(0f, CombatResourceWriter.restore(attrs, Stat.hitpoints, Stat.maxhp, 30));
    attrs = attributes(10, 100);
    assertEquals(0f, CombatResourceWriter.restore(attrs, Stat.hitpoints, Stat.maxhp, -1));
    assertEquals(10f, attrs.get(Stat.hitpoints).asFixed());
  }

  private static Attributes attributes(int current, int maximum) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, current);
    attrs.base().put(Stat.maxhp, maximum);
    attrs.reset();
    return attrs;
  }
}
