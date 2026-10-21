package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class MissileLoaderTest extends RiiabloTest {
  @Test void nativeMissileRatesFollowLifetimeAndDeathMaulerSkill() {
      com.riiablo.engine.server.component.Missile missile = new com.riiablo.engine.server.component.Missile();
      missile.missile = Riiablo.files.Missiles.get("megademoninferno");
      missile.skillId = 319; missile.nativeLifetimeFrames = 17;
      assertEquals((missile.missile.AnimLen << 8) / 17, MissileLoader.nativeFrameDelta(missile));
      missile.missile = Riiablo.files.Missiles.get("diablight");
      missile.skillId = 193; missile.nativeLifetimeFrames = 19;
      assertEquals((missile.missile.AnimLen << 8) / 19, MissileLoader.nativeFrameDelta(missile));
      missile.skillId = -1;
      assertEquals(missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256,
          MissileLoader.nativeFrameDelta(missile));
      missile.missile = Riiablo.files.Missiles.get("death mauler");
      missile.skillId = 308; missile.damageLevel = 3; missile.nativeLifetimeFrames = 50;
      assertEquals(96, MissileLoader.nativeFrameDelta(missile));
  }
}
