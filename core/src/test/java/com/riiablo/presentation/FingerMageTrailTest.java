package com.riiablo.presentation;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerMageTrailTest extends com.riiablo.RiiabloTest {
  @Test void nativeHitTenAttachesSparkleToVictimNotGround() {
    MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    system.overlays = org.mockito.Mockito.mock(OverlayManager.class);
    system.onMissileImpact(com.riiablo.engine.server.event.MissileImpactEvent.obtain(
        1, 177, 2, 3, com.badlogic.gdx.math.Vector2.Zero));
    org.mockito.Mockito.verify(system.overlays).set(3, "fingermagespiderexplode");
    system.onMissileImpact(com.riiablo.engine.server.event.MissileImpactEvent.obtain(
        1, 177, 2, -1, com.badlogic.gdx.math.Vector2.Zero));
    org.mockito.Mockito.verifyNoMoreInteractions(system.overlays);
  }

  @Test void trailIsFiniteAndCannotSeekOrDealDamage() {
    Missiles.Entry row = new Missiles.Entry();
    row.pCltDoFunc = 15;
    row.CltParam = new int[] {3, 20, 5, 20, 2};
    Missile trail = new Missile();
    trail.missile = row;
    trail.authoritative = true;
    trail.homing = true;
    trail.range = 80;
    trail.targetId = 5;
    MissileImpactPresentationSystem.configureFingerMageTrail(trail, row);
    assertFalse(trail.authoritative);
    assertTrue(trail.presentationOnly);
    assertFalse(trail.homing);
    assertEquals(-1, trail.targetId);
    assertEquals(0, trail.range);
    assertEquals(20, trail.nativeLifetimeFrames);
  }
}
