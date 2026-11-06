package com.riiablo.presentation;

import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.event.MissileImpactEvent;
import org.junit.jupiter.api.Test;

class MissileTargetOverlayTest extends RiiabloTest {
  @Test void holyBoltOnlyShowsHealingForAuthoritativeHeal() {
    MissileImpactPresentationSystem system = system();
    int holyBolt = Riiablo.files.Missiles.get("holybolt").Id;
    system.onMissileImpact(MissileImpactEvent.obtain(-1, holyBolt, 2, 3, Vector2.Zero));
    verifyNoInteractions(system.overlays);
    system.onMissileImpact(MissileImpactEvent.obtain(-1, holyBolt, 2, 3, Vector2.Zero)
        .withTargetOverlay("healing"));
    verify(system.overlays).set(3, "healing");
    system.onMissileImpact(MissileImpactEvent.obtain(-1, holyBolt, 2, -1, Vector2.Zero)
        .withTargetOverlay("healing"));
    verifyNoMoreInteractions(system.overlays);
  }

  @Test void explicitTargetOverlayOverridesNativeHitTenFallback() {
    MissileImpactPresentationSystem system = system();
    system.onMissileImpact(MissileImpactEvent.obtain(-1, 177, 2, 3, Vector2.Zero)
        .withTargetOverlay("healing"));
    verify(system.overlays).set(3, "healing");
    verifyNoMoreInteractions(system.overlays);
    system.onMissileImpact(MissileImpactEvent.obtain(-1, 177, 2, 4, Vector2.Zero));
    verify(system.overlays).set(4, "fingermagespiderexplode");
    verifyNoMoreInteractions(system.overlays);
  }

  private static MissileImpactPresentationSystem system() {
    MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    system.clientContext = ClientGameplayContext.fromGlobals();
    system.overlays = mock(OverlayManager.class);
    return system;
  }
}
