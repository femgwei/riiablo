package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.DamageSettlementState;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * Authoritative life-loss boundary. Mirrors SUnitDmg's unit-event, resource-write
 * and death phases without mixing hit rolls, action modes or client effects.
 * Absorb belongs to this boundary, after leech and before life loss. Callers
 * retain source-specific state/shatter ordering between phases.
 */
public final class DamageSettlement {
  private DamageSettlement() {}

  /** Mutable defensive callbacks; neither clients nor sound systems consume this phase. */
  public static void prepare(World world, DamageEvent hit) {
    if (hit.prepared) return;
    hit.prepared = true;
    if (hit.victim < 0) {
      hit.rejected = true;
      hit.damage = 0f;
      return;
    }
    UnitStates states = world.getMapper(UnitStates.class).get(hit.victim);
    if (states != null && states.snapshotOnly) {
      hit.rejected = true;
      hit.damage = 0f;
      return;
    }
    StatRef life = life(world, hit.victim);
    if (life == null || life.asFixed() <= 0f) {
      hit.rejected = true;
      hit.damage = 0f;
      return;
    }
    world.getMapper(DamageSettlementState.class).create(hit.victim).deathPublished = false;
    EventSystem events = world.getSystem(EventSystem.class);
    if (events != null) events.dispatch(hit);
  }

  public static float commit(World world, DamageEvent hit) {
    return commit(world, hit, hit.kind != DamageEvent.DAMAGE_OVER_TIME);
  }

  /** The native direct/missile paths can snap a positive-damage survivor below one HP. */
  public static float commit(World world, DamageEvent hit, boolean snapBelowOne) {
    if (hit.committed) return hit.appliedDamage;
    prepare(world, hit);
    // A defensive callback may have committed the same request reentrantly.
    if (hit.committed) return hit.appliedDamage;
    hit.committed = true;
    if (hit.rejected) return 0f;
    StatRef life = life(world, hit.victim);
    // A nested reactive callback may have killed the target already.
    if (life == null || life.asFixed() <= 0f) return 0f;
    float before = life.asFixed();
    float damage = Float.isFinite(hit.damage) ? Math.max(0f, hit.damage) : 0f;
    hit.damage = damage;
    LeechSettlement.apply(world, hit, before);
    UnitStates states = world.getMapper(UnitStates.class).get(hit.victim);
    if (Float.isFinite(hit.absorbedLife) && hit.absorbedLife > 0f
        && (states == null || states.stateList == null
            || !states.stateList.hasState(StateId.DEATH_DELAY))) {
      AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(hit.victim);
      hit.lifeAbsorbed = CombatResourceWriter.restore(
          wrapper.attrs, Stat.hitpoints, Stat.maxhp, hit.absorbedLife);
    }
    before = life.asFixed();
    if (damage > 0f) life.sub(damage);
    if (life.asFixed() < 0f || (snapBelowOne && damage > 0f && life.asFixed() < 1f)) {
      life.set(0f);
    }
    hit.appliedDamage = damage;
    LeechSettlement.drainResources(world, hit);
    hit.lethal = life.asFixed() <= 0f;
    EventSystem events = world.getSystem(EventSystem.class);
    if (events != null) events.dispatch(new DamageAppliedEvent(hit, before, life.asFixed()));
    return damage;
  }

  /** Invoke after source-specific lethal effects (e.g. cold shatter), before leaving the hit. */
  public static void finishDeath(World world, DamageEvent hit) {
    finishDeath(world, hit, hit.attacker);
  }

  /** Some environmental carriers credit their owner rather than the carrier entity. */
  public static void finishDeath(World world, DamageEvent hit, int killer) {
    if (!hit.committed || hit.rejected || hit.deathPublished) return;
    StatRef life = life(world, hit.victim);
    if (life == null || life.asFixed() > 0f) return;
    hit.deathPublished = true;
    DamageSettlementState state = world.getMapper(DamageSettlementState.class).create(hit.victim);
    if (state.deathPublished) return;
    state.deathPublished = true;
    EventSystem events = world.getSystem(EventSystem.class);
    if (events != null) events.dispatch(DeathEvent.obtain(killer, hit.victim));
  }

  private static StatRef life(World world, int entityId) {
    if (entityId < 0) return null;
    AttributesWrapper attributes = world.getMapper(AttributesWrapper.class).get(entityId);
    return attributes == null || attributes.attrs == null ? null
        : attributes.attrs.get(Stat.hitpoints, StatRef.obtain());
  }
}
