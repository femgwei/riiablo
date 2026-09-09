package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
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
  /** Native hit path. Reactive curses only consume MELEE/MISSILE packets. */
  public byte kind;
  /** Optional sound key selected by the authoritative hit resolver. */
  public String hitSound;

  public static DamageEvent obtain(int attacker, int victim, float damage) {
    return obtain(attacker, victim, damage, null);
  }

  public static DamageEvent obtain(int attacker, int victim, float damage, String hitSound) {
    DamageEvent event = new DamageEvent();
    event.attacker = attacker;
    event.victim = victim;
    event.damage = damage;
    event.physicalDamage = 0f;
    event.kind = DIRECT;
    event.hitSound = hitSound;
    return event;
  }

  public static DamageEvent obtainMelee(
      int attacker, int victim, float damage, float physicalDamage) {
    DamageEvent event = obtain(attacker, victim, damage);
    event.kind = MELEE;
    event.physicalDamage = Math.max(0f, physicalDamage);
    return event;
  }

  public static DamageEvent obtainMissile(
      int attacker, int victim, float damage, float physicalDamage, String hitSound) {
    DamageEvent event = obtain(attacker, victim, damage, hitSound);
    event.kind = MISSILE;
    event.physicalDamage = Math.max(0f, physicalDamage);
    return event;
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
