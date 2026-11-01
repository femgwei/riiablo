package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import org.junit.jupiter.api.Test;

class GroundFireReplicaClockTest {
  @Test
  void replicaWithoutPersistentServerClockUsesNativeRowRange() {
    Missile missile = groundFire();
    missile.damageLevel = 3;
    missile.nativeFrame = 30;
    assertEquals(126, NativeGroundFirePresentation.remainingFrames(missile));
  }

  @Test
  void explicitExtendedLifetimeOverridesRowRange() {
    Missile missile = groundFire();
    missile.nativeFrame = 140;
    missile.nativeLifetimeFrames = 300;
    assertEquals(160, NativeGroundFirePresentation.remainingFrames(missile));
    missile.nativeFrame = 301;
    assertEquals(0, NativeGroundFirePresentation.remainingFrames(missile));
  }

  private static Missile groundFire() {
    Missile missile = new Missile();
    missile.authoritative = false;
    missile.missile = new Missiles.Entry();
    missile.missile.pCltDoFunc = 5;
    missile.missile.Range = 150;
    missile.missile.LevRange = 2;
    return missile;
  }
}
