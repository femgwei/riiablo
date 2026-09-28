package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
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
  protected ComponentMapper<Missile> mMissile;
  @com.artemis.annotations.Wire(name = "factory")
  protected EntityFactory factory;

  private final Vector2 direction = new Vector2(1f, 0f);

  @Subscribe
  public void onDamage(DamageEvent event) {
    if (event == null || event.damage < 0f
        || (event.kind != DamageEvent.MELEE && event.kind != DamageEvent.MISSILE)) return;
    if (!mPosition.has(event.victim)) return;
    // Skill missiles already run Missiles.txt pCltHitFunc/CltHitSubMissile.
    // Ordinary arrows have no elemental hit class and remain eligible here.
    if (event.suppressElementalPresentation) return;

    present(event, event.fireDamage, HITCLASS_FIRE, "fire", "impact_fire_1");
    present(event, event.lightningDamage, HITCLASS_LIGHTNING, "lightning", "impact_lightning_1");
    present(event, event.coldDamage, HITCLASS_COLD, "cold", "impact_cold_1");
    present(event, event.poisonDamage, HITCLASS_POISON, "poison", "impact_poison_1");
  }

  private void present(DamageEvent event, float damage, int hitClass,
      String element, String sound) {
    if (damage <= 0f) return;
    if (Riiablo.audio != null) Riiablo.audio.play(sound, true);
    Missiles.Entry visual = resolveImpactVisual(hitClass, element);
    if (visual == null || factory == null) {
      log.warn("[ELEMENTAL_HIT] class={} result=no_visual", hitClass);
      return;
    }
    Vector2 hit = mPosition.get(event.victim).position;
    direction.set(1f, 0f);
    if (mPosition.has(event.attacker)) {
      direction.set(hit).sub(mPosition.get(event.attacker).position);
      if (direction.isZero(0.0001f)) direction.set(1f, 0f);
      else direction.nor();
    }
    int id = factory instanceof ClientEntityFactory
        ? ((ClientEntityFactory) factory).createMissilePresentation(visual, direction, hit)
        : factory.createMissile(visual, direction, hit, -1);
    if (id == Engine.INVALID_ENTITY || !mMissile.has(id)) return;
    Missile missile = mMissile.get(id);
    missile.authoritative = false;
    missile.presentationOnly = true;
    missile.ownerId = -1;
    missile.persistent = false;
    missile.nativeLifetimeFrames =
        MissileImpactPresentationSystem.nativePresentationLifetimeFrames(visual);
    log.debug("[ELEMENTAL_HIT] attacker={} victim={} class={} visual={} entity={}",
        event.attacker, event.victim, hitClass, visual.Missile, id);
  }

  /** Resolves a native client hit child for the requested elemental HitClass. */
  private static Missiles.Entry resolveImpactVisual(int hitClass, String element) {
    if (Riiablo.files == null || Riiablo.files.Missiles == null) return null;
    Missiles.Entry childFallback = null;
    Missiles.Entry stationaryFallback = null;
    for (Missiles.Entry source : Riiablo.files.Missiles) {
      if (source == null || (source.HitClass != hitClass && !isElement(source, element))) continue;
      if (source.CltHitSubMissile != null) {
        for (String childName : source.CltHitSubMissile) {
          if (childName == null || childName.isEmpty()) continue;
          Missiles.Entry child = Riiablo.files.Missiles.get(childName);
          if (child == null) continue;
          if (child.Vel == 0) return child;
          if (childFallback == null) childFallback = child;
        }
      }
      if (stationaryFallback == null && source.Vel == 0) stationaryFallback = source;
    }
    return stationaryFallback != null ? stationaryFallback : childFallback;
  }

  private static boolean isElement(Missiles.Entry source, String element) {
    if (source == null || element == null || source.EType == null) return false;
    String type = source.EType;
    if ("lightning".equalsIgnoreCase(element)) {
      return "ltng".equalsIgnoreCase(type) || "lightning".equalsIgnoreCase(type);
    }
    if ("poison".equalsIgnoreCase(element)) {
      return "pois".equalsIgnoreCase(type) || "poison".equalsIgnoreCase(type);
    }
    return element.equalsIgnoreCase(type);
  }
}
