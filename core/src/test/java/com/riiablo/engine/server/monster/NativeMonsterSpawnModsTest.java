package com.riiablo.engine.server.monster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class NativeMonsterSpawnModsTest {
  @Test void mapsNormalMonsterBaseModsFromD2Game() {
    assertEquals(NativeMonsterSpawnMods.SELF_RESURRECT,
        NativeMonsterSpawnMods.forBaseId("reanimatedhorde1"));
    assertEquals(NativeMonsterSpawnMods.CHARGED_BOLTS,
        NativeMonsterSpawnMods.forBaseId("scarab1"));
    assertEquals(NativeMonsterSpawnMods.ICE_SHATTER_DEATH,
        NativeMonsterSpawnMods.forBaseId("frozenhorror1"));
    assertEquals(NativeMonsterSpawnMods.KILL_MINIONS_DEATH,
        NativeMonsterSpawnMods.forBaseId("boneprison4"));
    assertEquals(NativeMonsterSpawnMods.IGNORE_AC,
        NativeMonsterSpawnMods.forBaseId("brute2"));
    assertEquals(NativeMonsterSpawnMods.QUEST_MOD,
        NativeMonsterSpawnMods.forBaseId("duriel"));
  }

  @Test void specialDamageAndUnknownRowsStayBounded() {
    assertTrue(NativeMonsterSpawnMods.has("evilhole5",
        NativeMonsterSpawnMods.SPECIAL_DAMAGE));
    assertTrue(NativeMonsterSpawnMods.has("traplightning",
        NativeMonsterSpawnMods.SPECIAL_DAMAGE));
    assertEquals(NativeMonsterSpawnMods.NONE,
        NativeMonsterSpawnMods.forBaseId("ordinaryfallen"));
    assertEquals(NativeMonsterSpawnMods.NONE,
        NativeMonsterSpawnMods.forBaseId(null));
  }
}
