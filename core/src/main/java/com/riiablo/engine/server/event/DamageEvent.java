package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

public class DamageEvent implements Event {
  @EntityId
  public int attacker;
  @EntityId
  public int victim;
  public float damage;
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
    event.hitSound = hitSound;
    return event;
  }
}
