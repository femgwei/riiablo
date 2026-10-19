package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.combat.NativeOpenWoundsApplier;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;
import org.junit.jupiter.api.Test;

class MonsterOpenWoundsIntegrationTest extends RiiabloTest {
  @Test void actualMeleeMarkerProcsThroughZeroImmediateDamageThenExpiresAfter200Ticks() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      f.target.base().put(Stat.magic_damage_reduction,10000); f.target.reset();
      f.attack(); UnitState wounds=f.state(StateId.OPENWOUNDS);
      assertNotNull(wounds); assertEquals(-176,wounds.getStatContributionValue(Stat.hpregen));
      assertEquals(f.owner,wounds.sourceEntityId); assertEquals(200,wounds.duration);
      assertEquals(10000*256,f.hp());
      for(int i=0;i<200;i++) f.world.process();
      assertEquals(10000*256-200*176,f.hp()); assertNull(f.state(StateId.OPENWOUNDS));
      f.world.process(); assertEquals(10000*256-200*176,f.hp()); f.assertWire();
    }
  }

  @Test void actualMissileCollisionInstallsHalfRangedPlayerRateWithoutStatusComponent() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats=new MonStats.Entry();
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      f.target.attrs.base().put(Stat.normal_damage_reduction,1000); f.target.attrs.reset();
      f.world.getMapper(UnitStates.class).remove(f.victim);
      Missile p=f.spawn(MonsterMissileCreationCriticalIntegrationTest.row()); p.rngState=135;
      f.land();
      UnitState wounds=f.world.getMapper(UnitStates.class).get(f.victim).stateList.getState(StateId.OPENWOUNDS);
      assertNotNull(wounds); assertEquals(-32,wounds.getStatContributionValue(Stat.hpregen));
      assertEquals(10000*256,f.target.attrs.get(Stat.hitpoints).encodedValues());
    }
  }

  @Test void chanceZeroDoesNotDrawAndChance100StillDrawsOnceEvenOnRefresh() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      Monster source=f.world.getMapper(Monster.class).get(f.owner); source.rngState=1; source.nativeSeed=null;
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertNull(source.nativeSeed); assertNull(f.state(StateId.OPENWOUNDS));
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertEquals(1791398751,source.rngState);
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertEquals(791599131,source.rngState); assertEquals(747178749,source.nativeSeed.getNHighSeed());
    }
  }

  @Test void refreshPreservesFirstRateAndOwnerEvenWhenNewAttackerIsStrongerAndRanged() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      UnitState original=f.state(StateId.OPENWOUNDS); original.duration=10;
      int other=f.world.create(); f.world.getMapper(Monster.class).create(other);
      com.riiablo.attributes.Attributes attrs=MonsterModeMeleeIntegrationTest.attrs();
      attrs.base().put(Stat.level,99); attrs.base().put(Stat.item_openwounds,100); attrs.reset();
      f.world.getMapper(AttributesWrapper.class).create(other).attrs=attrs;
      NativeOpenWoundsApplier.apply(f.world,other,f.victim,true,1234);
      assertSame(original,f.state(StateId.OPENWOUNDS)); assertEquals(200,original.duration);
      assertEquals(f.owner,original.sourceEntityId); assertEquals(-176,original.getStatContributionValue(Stat.hpregen));
    }
  }

  @Test void mergedHealingAndPoisonDoNotDoubleCountWoundsAndPlayerSurvivesAtOneHp() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      f.target.base().put(Stat.hpregen,200); f.target.reset();
      f.world.getSystem(StateUpdater.class).applyStateExact(f.victim,StateId.POISON,2,1,f.owner,100/256f,0);
      f.world.process(); assertEquals(10000*256-76,f.hp());
      f.target.get(Stat.hitpoints).setEncoded(270);
      f.world.process(); assertEquals(256,f.hp()); f.assertWire();
    }
  }

  @Test void blockedMeleeCannotProcWounds() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      f.target.base().put(Stat.toblock,75); f.target.base().put(Stat.dexterity,1000); f.target.reset();
      // Attacker and defender both start at 1: each draws 51 independently.
      f.attack(1); assertNull(f.state(StateId.OPENWOUNDS)); assertEquals(10000*256,f.hp());
      assertEquals(1791398751,f.world.getMapper(Monster.class).get(f.owner).rngState);
      assertEquals(1791398751,f.world.getMapper(UnitRandomSeed.class).get(f.victim).low);
    }
  }

  @Test void chanceBoundaryUsesLowWordAndIncludesIndependentSourceStateStats() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      Monster source=f.world.getMapper(Monster.class).get(f.owner);
      f.source.base().put(Stat.item_openwounds,51); f.source.reset(); source.rngState=1;
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertNull(f.state(StateId.OPENWOUNDS));
      source.rngState=1; source.nativeSeed=null;
      f.world.getMapper(UnitStates.class).create(f.owner).init(f.owner).stateList
          .addState(StateId.MIGHT,100).setNativeModifier(Stat.item_openwounds,1);
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertNotNull(f.state(StateId.OPENWOUNDS));
    }
  }

  @Test void missedMeleeCannotProcWounds() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.base().put(Stat.tohit,0); f.source.reset();
      f.target.base().put(Stat.armorclass,100000); f.target.reset();
      // Seed 135 yields 65, above the minimum 5% chance.
      f.attack(135); assertNull(f.state(StateId.OPENWOUNDS)); assertEquals(10000*256,f.hp());
    }
  }

  @Test void pureTableMissileWithoutSourceEventFlagCannotProcWounds() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats=new MonStats.Entry();
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      com.riiablo.codec.excel.Missiles.Entry row=MonsterMissileCreationCriticalIntegrationTest.row();
      row.SrcDamage=0; row.MinDamage=row.MaxDamage=10;
      f.spawn(row); f.land();
      UnitStates states=f.world.getMapper(UnitStates.class).get(f.victim);
      assertTrue(states==null || !states.stateList.hasState(StateId.OPENWOUNDS));
      assertEquals(9990*256,f.target.attrs.get(Stat.hitpoints).encodedValues());
    }
  }

  @Test void snapshotOnlyUnitsNeitherRollNorInstallWounds() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.source.base().put(Stat.item_openwounds,100); f.source.reset();
      Monster monster=f.world.getMapper(Monster.class).get(f.owner); monster.rngState=1; monster.nativeSeed=null;
      f.world.getMapper(UnitStates.class).get(f.victim).snapshotOnly=true;
      NativeOpenWoundsApplier.apply(f.world,f.owner,f.victim,false,1234);
      assertNull(monster.nativeSeed); assertNull(f.state(StateId.OPENWOUNDS));
    }
  }

  @Test void legacyWoundsIngressUsesRawNativeRateInsteadOfLevelTimesTwo() {
    try (MonsterModeMeleeIntegrationTest.Fixture f = new MonsterModeMeleeIntegrationTest.Fixture("fire")) {
      f.world.getMapper(UnitStates.class).get(f.victim).stateList
          .addState(StateId.OPENWOUNDS,1,99,f.owner);
      f.world.process(); assertNull(f.state(StateId.OPENWOUNDS));
      assertEquals(10000*256-176,f.hp());
      f.world.process(); assertEquals(10000*256-176,f.hp());
    }
  }
}
