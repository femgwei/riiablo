package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import com.riiablo.engine.server.combat.CombatSystem;
import net.mostlyoriginal.api.event.common.Event;

public class DamageEvent implements Event {
  public static final byte DIRECT = 0;
  public static final byte MELEE = 1;
  public static final byte MISSILE = 2;
  public static final byte REACTIVE = 3;

  @EntityId
  public int attacker;
  @EntityId
  public int victim;
  public float damage;
  /** Post-resistance physical portion of {@link #damage}. */
  public float physicalDamage;
  /** Post-resistance elemental channels carried by this hit packet. */
  public float fireDamage;
  public float lightningDamage;
  public float coldDamage;
  public float poisonDamage;
  /** Native hit path. Reactive curses only consume MELEE/MISSILE packets. */
  public byte kind;
  /** Optional sound key selected by the authoritative hit resolver. */
  public String hitSound;
  /** Incoming Missiles.txt ReturnFire gate used by Chilling Armor. */
  public boolean returnFire;
  /** Missile row already owns an elemental pCltHit presentation. */
  public boolean suppressElementalPresentation;

  public static DamageEvent obtain(int attacker, int victim, float damage) {
    return obtain(attacker, victim, damage, null);
  }

  public static DamageEvent obtain(int attacker, int victim, float damage, String hitSound) {
    DamageEvent event = new DamageEvent();
    event.attacker = attacker;
    event.victim = victim;
    event.damage = damage;
    event.physicalDamage = 0f;
    event.fireDamage = 0f;
    event.lightningDamage = 0f;
    event.coldDamage = 0f;
    event.poisonDamage = 0f;
    event.kind = DIRECT;
    event.hitSound = hitSound;
    event.returnFire = false;
    event.suppressElementalPresentation = false;
    return event;
  }

  public static DamageEvent obtainMelee(
      int attacker, int victim, float damage, float physicalDamage) {
    DamageEvent event = obtain(attacker, victim, damage);
    event.kind = MELEE;
    event.physicalDamage = Math.max(0f, physicalDamage);
    return event;
  }

  public static DamageEvent obtainMelee(int attacker, int victim, float damage,
      float physicalDamage, CombatSystem.CombatResult result) {
    return obtainMelee(attacker, victim, damage, physicalDamage)
        .withElementalDamage(result, 1f);
  }

  public static DamageEvent obtainMelee(int attacker, int victim, float damage,
      float physicalDamage, CombatSystem.CombatResult result, float elementalScale) {
    return obtainMelee(attacker, victim, damage, physicalDamage)
        .withElementalDamage(result, elementalScale);
  }

  public static DamageEvent obtainMissile(
      int attacker, int victim, float damage, float physicalDamage, String hitSound) {
    DamageEvent event = obtain(attacker, victim, damage, hitSound);
    event.kind = MISSILE;
    event.physicalDamage = Math.max(0f, physicalDamage);
    return event;
  }

  public static DamageEvent obtainMissile(int attacker, int victim, float damage,
      float physicalDamage, String hitSound, CombatSystem.CombatResult result) {
    return obtainMissile(attacker, victim, damage, physicalDamage, hitSound)
        .withElementalDamage(result, 1f);
  }

  public DamageEvent withReturnFire(boolean enabled) {
    returnFire = enabled;
    return this;
  }

  public DamageEvent suppressElementalPresentation(boolean suppress) {
    suppressElementalPresentation = suppress;
    return this;
  }

  /** Copies the resolved elemental channels into this presentation packet. */
  public DamageEvent withElementalDamage(CombatSystem.CombatResult result, float scale) {
    if (result == null) return this;
    float appliedScale = Math.max(0f, scale);
    fireDamage = Math.max(0f, result.elementalDamage[CombatSystem.DAMAGE_FIRE] * appliedScale);
    lightningDamage = Math.max(0f,
        result.elementalDamage[CombatSystem.DAMAGE_LIGHTNING] * appliedScale);
    coldDamage = Math.max(0f, result.elementalDamage[CombatSystem.DAMAGE_COLD] * appliedScale);
    poisonDamage = Math.max(0f,
        result.elementalDamage[CombatSystem.DAMAGE_POISON] * appliedScale);
    return this;
  }

  public static DamageEvent obtainReactive(
      int attacker, int victim, float damage, float physicalDamage) {
    DamageEvent event = obtain(attacker, victim, damage);
    event.kind = REACTIVE;
    event.physicalDamage = Math.max(0f, physicalDamage);
    return event;
  }

  public boolean isMelee() {
    return kind == MELEE;
  }

  public boolean isLeechableAttack() {
    return kind == MELEE || kind == MISSILE;
  }
}
