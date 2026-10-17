package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import org.junit.jupiter.api.Test;

/** Golden check for the damage snapshot on a Scarab MonUMod 16 bolt. */
class ScarabChargedBoltDamageTest extends RiiabloTest {
  @Test
  void bugLightningUsesNativeFixedOnePointPerTwoLevels() {
    Missiles.Entry row = Riiablo.files.Missiles.get("buglightning");
    assertTrue(row != null);
    assertEquals("ltng", row.EType);
    assertEquals(256, row.EMin);
    assertEquals(256, row.Emax);
    assertEquals(0, row.HitShift);
    assertEquals(128, row.MinELev[0]);
    assertEquals(128, row.MaxELev[0]);

    for (int level = 1; level <= 20; level++) {
      Missile missile = new Missile();
      missile.missile = row;
      assertTrue(MissileDamageResolver.initialize(
          missile, Attributes.obtainStandard(), null, (byte) -1, level, 0));
      int expected = (level + 1) / 2;
      assertEquals(expected, statInt(missile, Stat.lightmindam), "min level " + level);
      assertEquals(expected, statInt(missile, Stat.lightmaxdam), "max level " + level);
    }
  }

  private static int statInt(Missile missile, short stat) {
    StatRef ref = missile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
