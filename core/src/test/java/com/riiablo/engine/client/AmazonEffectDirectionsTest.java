package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;

class AmazonEffectDirectionsTest extends RiiabloTest {
  @Test
  void shatterDeathUsesNativeIcebreakMissileByMonsterSize() {
    assertEquals("icebreaksmall", DeathHandler.shatterMissileName(1));
    assertEquals("icebreakmedium", DeathHandler.shatterMissileName(2));
    assertEquals("icebreaklarge", DeathHandler.shatterMissileName(3));
  }
  @Test
  void circularClientCallbacksUseMissileDirectionCount() {
    Missiles.Entry frostNova = Riiablo.files.Missiles.get("frostnova");
    assertNotNull(frostNova);
    assertEquals(16, frostNova.NumDirections);
    assertEquals(16, SkillCastHandler.nativeDirectionCount(frostNova, 64));

    Missiles.Entry unspecified = new Missiles.Entry();
    assertEquals(64, SkillCastHandler.nativeDirectionCount(unspecified, 64));
  }
}
