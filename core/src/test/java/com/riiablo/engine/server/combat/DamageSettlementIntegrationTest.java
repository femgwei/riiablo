package com.riiablo.engine.server.combat;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageSettlementIntegrationTest extends RiiabloTest {
  @Test void defaultDirectSettlementSnapsBelowOneButPeriodicTicksKeepTheirOwnLifeFloor() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int direct = unit(world, 1.5f), periodic = unit(world, 1.5f);
      DamageSettlement.commit(world, DamageEvent.obtain(-1, direct, 1));
      DamageSettlement.commit(world, DamageEvent.obtainDamageOverTime(-1, periodic, 1));
      assertEquals(0f, life(world, direct));
      assertEquals(.5f, life(world, periodic));
    } finally { world.dispose(); }
  }
  @Test void defensiveCallbackPrecedesOneResourceWriteAndImmutablePresentation() {
    Probe probe = new Probe();
    World world = world(probe);
    try {
      int victim = unit(world, 20);
      probe.shield = 8;
      DamageEvent hit = DamageEvent.obtainMelee(-1, victim, 15, 15);
      DamageSettlement.prepare(world, hit);
      assertEquals(20f, life(world, victim));
      assertEquals(0, probe.presentations);
      assertEquals(7f, DamageSettlement.commit(world, hit));
      assertEquals(13f, life(world, victim));
      assertEquals(7f, probe.last.lifeLost);
      assertEquals(13f, probe.lifeObservedInPresentation);
      hit.damage = 100;
      assertEquals(7f, probe.last.damage, "presentation must not retain the mutable request");
      DamageSettlement.prepare(world, hit);
      DamageSettlement.commit(world, hit);
      assertEquals(1, probe.callbacks);
      assertEquals(1, probe.presentations);
      assertEquals(13f, life(world, victim));
    } finally { world.dispose(); }
  }

  @Test void absorbedHitAndCancelledFractionalHitDoNotCreateLifeLossOrDeath() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, .5f);
      probe.shield = 100;
      DamageEvent hit = DamageEvent.obtainMelee(-1, victim, 20, 20);
      assertEquals(0f, DamageSettlement.commit(world, hit, true));
      DamageSettlement.finishDeath(world, hit);
      assertEquals(.5f, life(world, victim));
      assertEquals(0f, probe.last.lifeLost);
      assertEquals(0, probe.deaths);
    } finally { world.dispose(); }
  }

  @Test void nativeFractionalSnapAndSourceSpecificDeathPhaseRunOnlyOnce() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 1.5f);
      DamageEvent hit = DamageEvent.obtainMissile(42, victim, 1, 1, null);
      DamageSettlement.commit(world, hit, true);
      assertEquals(0f, life(world, victim));
      assertEquals(0, probe.deaths, "caller must first resolve shatter/state side effects");
      DamageSettlement.finishDeath(world, hit);
      DamageSettlement.finishDeath(world, hit);
      assertEquals(1, probe.deaths);
      assertEquals(42, probe.killer);
      assertEquals(1, probe.presentations);
      DamageSettlement.commit(world, DamageEvent.obtain(42, victim, 1));
      assertEquals(1, probe.presentations, "dead targets must not replay impacts");
    } finally { world.dispose(); }
  }

  @Test void absorbCanHealBeforeTheLifeWriteWithoutChangingTheCallbackPhase() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 10);
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(victim).attrs;
      DamageEvent hit = DamageEvent.obtain(-1, victim, 12);
      hit.absorbedLife = 10;
      DamageSettlement.prepare(world, hit);
      DamageSettlement.commit(world, hit);
      assertEquals(10f, hit.lifeAbsorbed);
      assertEquals(10f, probe.last.lifeAbsorbed);
      assertEquals(8f, life(world, victim));
      assertEquals(20f, probe.last.lifeBefore);
      assertEquals(12f, probe.last.lifeLost);
    } finally { world.dispose(); }
  }

  @Test void zeroDamageAbsorbIsCommittedOnceAndRespectsDeathDelayAndSnapshot() {
    for (int scenario = 0; scenario < 4; scenario++) {
      Probe probe = new Probe(); World world = world(probe);
      try {
        int victim = unit(world, scenario == 3 ? 0 : 10);
        if (scenario == 1) {
          UnitStates states = world.getMapper(UnitStates.class).create(victim);
          states.stateList = new com.riiablo.engine.server.state.StateList(victim);
          states.stateList.addState(com.riiablo.engine.server.state.StateId.DEATH_DELAY, 0);
        }
        if (scenario == 2) world.getMapper(UnitStates.class).create(victim).snapshotOnly = true;
        DamageEvent hit = DamageEvent.obtain(-1, victim, 0);
        hit.absorbedLife = 20.5f;
        DamageSettlement.commit(world, hit);
        DamageSettlement.commit(world, hit);
        assertEquals(scenario == 0 ? 30.5f : scenario == 3 ? 0f : 10f, life(world, victim));
        assertEquals(scenario == 0 ? 20.5f : 0f, hit.lifeAbsorbed);
        assertEquals(scenario < 2 ? 1 : 0, probe.presentations);
      } finally { world.dispose(); }
    }
  }

  @Test void fullLifeAbsorbCannotBecomePostDamageHealingAndReentrantCommitDoesNotRepeatIt() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 100);
      probe.commitInCallback = true;
      DamageEvent hit = DamageEvent.obtain(-1, victim, 30);
      hit.absorbedLife = 20;
      DamageSettlement.commit(world, hit);
      DamageSettlement.commit(world, hit);
      assertEquals(70f, life(world, victim));
      assertEquals(0f, hit.lifeAbsorbed);
      assertEquals(30f, probe.last.lifeLost);
      assertEquals(1, probe.presentations);
    } finally { world.dispose(); }
  }

  @Test void clientSnapshotCannotReapplyAuthoritativeDamage() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 10);
      world.getMapper(UnitStates.class).create(victim).snapshotOnly = true;
      DamageEvent hit = DamageEvent.obtain(2, victim, 20);
      DamageSettlement.commit(world, hit);
      DamageSettlement.finishDeath(world, hit);
      assertEquals(10f, life(world, victim));
      assertEquals(0, probe.callbacks);
      assertEquals(0, probe.presentations);
      assertEquals(0, probe.deaths);
    } finally { world.dispose(); }
  }

  @Test void dotRetainsItsKindAndEnvironmentalDeathCreditsTheOwner() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 2);
      DamageEvent hit = DamageEvent.obtainDamageOverTime(3, victim, 5);
      DamageSettlement.commit(world, hit);
      DamageSettlement.finishDeath(world, hit, 9);
      assertEquals(DamageEvent.DAMAGE_OVER_TIME, probe.last.kind);
      assertEquals(2f, probe.last.lifeLost, "overkill is not actual life lost");
      assertEquals(9, probe.killer);
    } finally { world.dispose(); }
  }

  @Test void nestedLethalRequestsPublishOnceAndRevivalAllowsANewDeath() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 10);
      DamageEvent outer = DamageEvent.obtain(1, victim, 1);
      DamageSettlement.prepare(world, outer);
      DamageEvent nested = DamageEvent.obtain(2, victim, 20);
      DamageSettlement.commit(world, nested);
      DamageSettlement.finishDeath(world, nested);
      DamageSettlement.commit(world, outer);
      DamageSettlement.finishDeath(world, outer);
      assertEquals(1, probe.deaths);
      assertEquals(2, probe.killer);
      world.getMapper(AttributesWrapper.class).get(victim).attrs.get(Stat.hitpoints).set(10f);
      DamageEvent revivedHit = DamageEvent.obtain(3, victim, 20);
      DamageSettlement.commit(world, revivedHit);
      DamageSettlement.finishDeath(world, revivedHit);
      assertEquals(2, probe.deaths);
      assertEquals(3, probe.killer);
    } finally { world.dispose(); }
  }

  @Test void callerMayOwnDeathFromAFinisherAreaPacket() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 10);
      DamageEvent melee = DamageEvent.obtain(1, victim, 1);
      DamageSettlement.commit(world, melee);
      DamageEvent area = DamageEvent.obtain(1, victim, 20);
      DamageSettlement.commit(world, area);
      // Finisher helpers deliberately defer the primary target's death to melee.
      DamageSettlement.finishDeath(world, melee);
      DamageSettlement.finishDeath(world, area);
      assertEquals(1, probe.deaths);
    } finally { world.dispose(); }
  }

  @Test void defensiveCallbackCannotCommitTheSameRequestTwice() {
    Probe probe = new Probe(); World world = world(probe);
    try {
      int victim = unit(world, 10);
      probe.commitInCallback = true;
      DamageSettlement.commit(world, DamageEvent.obtain(1, victim, 3));
      assertEquals(7f, life(world, victim));
      assertEquals(1, probe.presentations);
    } finally { world.dispose(); }
  }

  private static World world(Probe probe) {
    return new World(new WorldConfigurationBuilder().with(new EventSystem(), probe).build());
  }
  private static int unit(World world, float hp) {
    int id = world.create();
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, 100);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return id;
  }
  private static float life(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed();
  }
  private static final class Probe extends BaseSystem {
    int callbacks, presentations, deaths, killer;
    float shield, lifeObservedInPresentation;
    boolean commitInCallback;
    DamageAppliedEvent last;
    @Subscribe public void before(DamageEvent event) {
      callbacks++;
      event.damage = Math.max(0, event.damage - shield);
      if (commitInCallback) DamageSettlement.commit(world, event);
    }
    @Subscribe public void after(DamageAppliedEvent event) {
      presentations++;
      last = event;
      lifeObservedInPresentation = life(world, event.victim);
    }
    @Subscribe public void death(DeathEvent event) { deaths++; killer = event.killer; }
    @Override protected void processSystem() {}
  }
}
