package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Presents the generic elemental flash carried by weapon hit packets. */
public class ElementalHitPresentationSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ElementalHitPresentationSystem.class);

  private static final int HITCLASS_FIRE = 32;
  private static final int HITCLASS_COLD = 48;
  private static final int HITCLASS_LIGHTNING = 64;
  private static final int HITCLASS_POISON = 80;

  protected ComponentMapper<Position> mPosition;
  protected OverlayManager overlays;

  @Subscribe
  public void onDamage(DamageEvent event) {
    if (event == null || event.damage < 0f
        || (event.kind != DamageEvent.MELEE && event.kind != DamageEvent.MISSILE)) return;
    if (!mPosition.has(event.victim)) return;
    // Skill missiles already run Missiles.txt pCltHitFunc/CltHitSubMissile.
    // Ordinary arrows have no elemental hit class and remain eligible here.
    if (event.suppressElementalPresentation) return;

    // D2's generic weapon-fire impact is the common fire_explode overlay
    // (FireExplode.dcc).  fire_hit is the shorter unit-state flash and does
    // not match the native weapon impact presentation.
    present(event, event.fireDamage, HITCLASS_FIRE, "impact_fire_1", "fire_explode");
    present(event, event.lightningDamage, HITCLASS_LIGHTNING,
        "impact_lightning_1", "lightning");
    present(event, event.coldDamage, HITCLASS_COLD, "impact_cold_1", "ice_explode");
    present(event, event.poisonDamage, HITCLASS_POISON, "impact_poison_1", "poisonhit");
  }

  private void present(DamageEvent event, float damage, int hitClass,
      String sound, String overlayId) {
    if (damage <= 0f) return;
    if (Riiablo.audio != null) Riiablo.audio.play(sound, true);
    if (overlays == null) {
      log.warn("[ELEMENTAL_HIT] class={} result=no_overlay_manager", hitClass);
      return;
    }
    overlays.set(event.victim, overlayId);
    log.debug("[ELEMENTAL_HIT] attacker={} victim={} class={} overlay={}",
        event.attacker, event.victim, hitClass, overlayId);
  }
}
