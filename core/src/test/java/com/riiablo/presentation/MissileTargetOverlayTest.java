package com.riiablo.presentation;

import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.event.MissileImpactEvent;
import org.junit.jupiter.api.Test;

class MissileTargetOverlayTest extends RiiabloTest {
  private com.artemis.World world;

  @org.junit.jupiter.api.AfterEach void disposeWorld() {
    if (world != null) world.dispose();
  }

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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"CorpseCycler", "VineCycler"})
  void recyclerProgressOverlayTargetsOwnerWithoutCreatingAnImpactChild(String skillName) {
    MissileImpactPresentationSystem system = system();
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillName);
    com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    int owner = 7;
    system.onMissileImpact(MissileImpactEvent.obtain(-1, row.Id, owner, owner, Vector2.Zero)
        .withTargetOverlay(row.ProgOverlay));
    verify(system.overlays).set(owner, row.ProgOverlay);
    verifyNoMoreInteractions(system.overlays);
    verifyNoInteractions(system.factory);
  }

  private MissileImpactPresentationSystem system() {
    MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class);
    // The impact handler also consults optional presentation systems. Bind a
    // real World instead of calling it on an unattached BaseSystem.
    world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), factory,
            new OverlayManager(), system).build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    system.overlays = mock(OverlayManager.class);
    // Ignore BaseSystem binding/initialization; assertions cover only calls
    // caused by the incoming impact/progress event.
    clearInvocations(factory);
    return system;
  }
}
