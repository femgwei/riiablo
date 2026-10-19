package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable presentation fact, emitted only after defensive callbacks and life loss. */
public final class DamageAppliedEvent implements Event {
  public final int attacker, victim, missileId;
  public final byte kind;
  public final float damage, lifeLost, lifeBefore, lifeAfter;
  /** Resolved physical channel in HP, not the packet's raw 8.8 integer. */
  public final float physicalDamage;
  public final float fireDamage, lightningDamage, coldDamage, magicDamage, poisonDamage;
  public final String hitSound;
  public final boolean suppressElementalPresentation, suppressMonsterHitSound;

  public DamageAppliedEvent(DamageEvent hit, float lifeBefore, float lifeAfter) {
    attacker = hit.attacker;
    victim = hit.victim;
    missileId = hit.missileId;
    kind = hit.kind;
    damage = Math.max(0f, hit.damage);
    this.lifeBefore = lifeBefore;
    this.lifeAfter = lifeAfter;
    lifeLost = Math.max(0f, lifeBefore - lifeAfter);
    physicalDamage = hit.physicalDamage;
    fireDamage = hit.fireDamage;
    lightningDamage = hit.lightningDamage;
    coldDamage = hit.coldDamage;
    magicDamage = hit.magicDamage;
    poisonDamage = hit.poisonDamage;
    hitSound = hit.hitSound;
    suppressElementalPresentation = hit.suppressElementalPresentation;
    suppressMonsterHitSound = hit.suppressMonsterHitSound;
  }
}
