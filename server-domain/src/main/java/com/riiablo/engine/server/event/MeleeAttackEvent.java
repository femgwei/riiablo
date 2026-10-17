package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Authoritative melee attack-attempt event emitted after the hit/block roll.
 *
 * <p>D2Game dispatches {@code UNITEVENT_ATTACKEDINMELEE} even when the attack
 * misses or is blocked.  It is therefore deliberately separate from
 * {@link DamageEvent}, which represents a damage packet that reached the
 * defender.</p>
 */
public final class MeleeAttackEvent implements Event {
  public int attacker;
  public int victim;
  public boolean hit;
  public boolean blocked;

  public static MeleeAttackEvent obtain(
      int attacker, int victim, boolean hit, boolean blocked) {
    MeleeAttackEvent event = new MeleeAttackEvent();
    event.attacker = attacker;
    event.victim = victim;
    event.hit = hit;
    event.blocked = blocked;
    return event;
  }
}
