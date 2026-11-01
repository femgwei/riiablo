package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class MissileLoaderTest extends RiiabloTest {
  @Test void infernoUsesBothVisualVariantsWithoutChangingAuthoritativeRow() {
    ClientGameplayContext context = ClientGameplayContext.fromGlobals();
    com.riiablo.engine.server.component.Missile missile =
        new com.riiablo.engine.server.component.Missile();
    missile.missile = Riiablo.files.Missiles.get("infernoflame1");
    missile.skillId = 41;
    missile.nativeLifetimeFrames = 17;
    missile.presentationSeedKnown = true;
    java.util.Set<String> variants = new java.util.HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      missile.presentationSeed = seed;
      variants.add(MissileLoader.infernoVisualRow(context, missile).Missile);
      assertSame(MissileLoader.infernoVisualRow(context, missile),
          MissileLoader.infernoVisualRow(context, missile), "cold-load retries are stable");
    }
    assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
        "infernoflame1", "infernoflame2")), variants);
    assertEquals("infernoflame1", missile.missile.Missile);
    assertNull(missile.clientSeed, "selection does not consume another callback's RNG");
    assertEquals((missile.missile.AnimLen << 8) / 17,
        MissileLoader.nativeFrameDelta(context, missile));
  }

  @Test void nativeMissileRatesFollowLifetimeAndDeathMaulerSkill() {
      ClientGameplayContext context = ClientGameplayContext.fromGlobals();
      com.riiablo.engine.server.component.Missile missile = new com.riiablo.engine.server.component.Missile();
      missile.missile = Riiablo.files.Missiles.get("megademoninferno");
      missile.skillId = 319; missile.nativeLifetimeFrames = 17;
      assertEquals((missile.missile.AnimLen << 8) / 17, MissileLoader.nativeFrameDelta(context, missile));
      missile.missile = Riiablo.files.Missiles.get("diablight");
      missile.skillId = 193; missile.nativeLifetimeFrames = 19;
      assertEquals((missile.missile.AnimLen << 8) / 19, MissileLoader.nativeFrameDelta(context, missile));
      missile.skillId = -1;
      assertEquals(missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256,
          MissileLoader.nativeFrameDelta(context, missile));
      missile.missile = Riiablo.files.Missiles.get("death mauler");
      missile.skillId = 308; missile.damageLevel = 3; missile.nativeLifetimeFrames = 50;
      assertEquals(96, MissileLoader.nativeFrameDelta(context, missile));
  }
}
