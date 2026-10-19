package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.MonsterMissilePhysicalIntegrationTest.Fixture;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.VitalsP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual creation -> collision -> raw HP -> requested/applied channels -> wire.
 * Synthetic rows isolate rules; real shaman table rows are independently checked.
 */
class MonsterMissileElementalIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"fire,10,11,8,0,0,0,2815", "ltng,10,11,8,10,50,0,127",
      "cold,10,11,8,10,-50,0,382", "mag,10,11,8,10,50,100,127",
      "fire,10,10,8,0,0,0,2560", "fire,11,10,8,0,0,0,2815",
      "fire,0,11,8,0,0,0,0", "fire,10,0,8,0,0,0,0",
      "fire,1,1,7,0,0,0,128", "fire,10,11,8,11,0,0,0",
      "fire,10,11,8,0,-200,0,5630", "fire,10,11,8,0,50,25,2111",
      "ltng,10,11,8,0,50,-25,703", "fire,5001,5001,8,0,50,0,640100"})
  void fractionalElementSurvivesLifeChannelsAndSynchronization(String element, int min, int max,
      int shift, int mdr, int resistance, int pierce, int expectedFixed) {
    try (Fixture f = new Fixture()) {
      f.source.base().put(pierceStat(element), pierce);
      f.source.reset();
      f.target.attrs.base().put(Stat.magic_damage_reduction, mdr);
      f.target.attrs.base().put(resistStat(element), resistance);
      f.target.attrs.reset();
      Missiles.Entry row = row(element, min, max);
      row.HitShift = shift;
      Missile projectile = f.spawn(row);
      assertNotNull(projectile.nativeElemental);
      f.land();
      assertEquals(10000 * 256 - expectedFixed, f.target.attrs.get(Stat.hitpoints).encodedValues());
      if (expectedFixed > 0) {
        float hp = expectedFixed / 256f;
        assertEquals(hp, f.probe.requested);
        assertEquals(0f, f.probe.physical);
        assertNotNull(f.probe.applied);
        assertEquals(hp, f.probe.applied.lifeLost);
        assertEquals("fire".equals(element) ? hp : 0f, f.probe.request.fireDamage);
        assertEquals("ltng".equals(element) ? hp : 0f, f.probe.request.lightningDamage);
        assertEquals("cold".equals(element) ? hp : 0f, f.probe.request.coldDamage);
        assertEquals("mag".equals(element) ? hp : 0f, f.probe.request.magicDamage);
        assertEquals(f.probe.request.fireDamage, f.probe.applied.fireDamage);
        assertEquals(f.probe.request.lightningDamage, f.probe.applied.lightningDamage);
        assertEquals(f.probe.request.coldDamage, f.probe.applied.coldDamage);
        assertEquals(f.probe.request.magicDamage, f.probe.applied.magicDamage);
      } else assertEquals(0, f.probe.events);
      assertWireHp(f);
    }
  }

  @ParameterizedTest
  @CsvSource({"10000,100,1,1433,10000,2558567", "9000,100,1,1433,9005.3984375,2303949",
      "9000,0,20,0,9010.99609375,2306815"})
  void percentThenFlatAbsorptionHealsBeforeDamageIncludingZeroDamage(float life, int percent,
      int flat, int damage, float beforeLoss, int finalFixed) {
    try (Fixture f = new Fixture()) {
      f.target.attrs.base().put(Stat.hitpoints, life);
      f.target.attrs.base().put(Stat.item_absorbfire_percent, percent);
      f.target.attrs.base().put(Stat.item_absorbfire, flat);
      f.target.attrs.reset();
      f.spawn(row("fire", 10, 11));
      f.land();
      assertEquals(finalFixed, f.target.attrs.get(Stat.hitpoints).encodedValues());
      if (damage > 0) {
        assertEquals(damage / 256f, f.probe.applied.damage);
        assertEquals(damage / 256f, f.probe.applied.lifeLost);
        assertEquals(beforeLoss, f.probe.applied.lifeBefore);
      } else assertEquals(0, f.probe.events);
      assertWireHp(f);
    }
  }

  @Test void coldDurationStillAppliesWhenMdrRemovesAllInstantDamage() {
    try (Fixture f = new Fixture(true)) {
      f.world.getMapper(UnitStates.class).create(f.victim).init(f.victim);
      f.target.attrs.base().put(Stat.magic_damage_reduction, 100);
      f.target.attrs.base().put(Stat.coldresist, 50);
      f.target.attrs.reset();
      Missiles.Entry row = row("cold", 10, 10);
      row.ELen = 100;
      f.spawn(row);
      f.land();
      UnitState cold = f.world.getMapper(UnitStates.class).get(f.victim).stateList.getState(StateId.COLD);
      assertNotNull(cold);
      assertEquals(50, cold.duration);
      assertEquals(0, f.probe.events);
    }
  }

  @Test void defenderStateMdrAndAbsorptionReachTheAuditedSnapshot() {
    try (Fixture f = new Fixture()) {
      UnitState state = f.world.getMapper(UnitStates.class).create(f.victim).init(f.victim)
          .stateList.addState(StateId.MIGHT, 100);
      state.setNativeModifier(Stat.magic_damage_reduction, 1);
      state.setNativeModifier(Stat.item_absorbfire_percent, 40);
      state.setNativeModifier(Stat.item_absorbfire, 1);
      f.spawn(row("fire", 10, 11));
      f.land();
      // (2815-256)=2559; absorb floor(2559*.4)=1023 then 256 => 1280 damage.
      assertEquals(5f, f.probe.applied.fireDamage);
      assertEquals(10000 * 256 - 1280, f.target.attrs.get(Stat.hitpoints).encodedValues());
    }
  }

  @Test void inFlightEndpointAndPierceSnapshotsAreNotReplacedByOwnerMutation() {
    try (Fixture f = new Fixture()) {
      f.source.base().put(Stat.passive_fire_pierce, 25);
      f.source.reset();
      f.target.attrs.base().put(Stat.fireresist, 50);
      f.target.attrs.reset();
      Missiles.Entry row = row("fire", 10, 11);
      f.spawn(row);
      row.EMin = row.Emax = 999;
      f.source.base().put(Stat.passive_fire_pierce, 999);
      f.source.base().put(Stat.firemindam, 999);
      f.source.base().put(Stat.firemaxdam, 999);
      f.source.reset();
      f.land();
      assertEquals(2111 / 256f, f.probe.applied.fireDamage);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"shafire1", "shafire2", "shafire3", "shafire4", "shafire5"})
  void realShamanTablePacketsHaveIndependentFixedEndpoints(String name) {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      assertNotNull(row);
      Missile projectile = f.spawn(row);
      assertNotNull(projectile.nativeElemental, name + " rate=" + row.DamageRate
          + " damageFunc=" + row.pSrvDmgFunc + " mastery=" + row.ApplyMastery);
      assertEquals(row.EMin << row.HitShift, projectile.nativeElemental.minimum);
      assertEquals(row.Emax << row.HitShift, projectile.nativeElemental.maximum);
      f.land();
      assertNotNull(f.probe.applied);
      assertEquals(((row.Emax << row.HitShift) - 1) / 256f, f.probe.applied.fireDamage);
    }
  }

  @Test void perLevelTableGrowthIsExpandedBeforeFractionalHitShift() {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = row("fire", 1, 2);
      row.HitShift = 7;
      row.MinELev = new int[] {1,2,3,4,5};
      row.MaxELev = new int[] {2,3,4,5,6};
      Missile p = new Missile().set(row, new Vector2(), 100);
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 30, 0));
      // Seven levels in band1, eight in band2, six each in band3/4, two in band5.
      assertEquals(76 * 128, p.nativeElemental.minimum);
      assertEquals(106 * 128, p.nativeElemental.maximum);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"source", "sourceSentinel", "sourceMissile", "skill", "formula", "physicalFormula", "mastery", "rate", "special", "player", "hireling"})
  void deferredPoliciesNeverSilentlyOptIn(String policy) {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = row("fire", 10, 11);
      switch (policy) {
        case "source": row.SrcDamage = 128; break;
        case "sourceSentinel": row.SrcDamage = -1; break;
        case "sourceMissile": row.SrcMissDmg = 1; break;
        case "skill": row.Skill = "Fire Bolt"; break;
        case "formula": row.EDmgSymPerCalc = "ln12"; break;
        case "physicalFormula": row.DmgSymPerCalc = "ln12"; break;
        case "mastery": row.ApplyMastery = true; break;
        case "rate": row.DamageRate = 1; break;
        case "special": row.pSrvDmgFunc = 2; break;
      }
      Missile p = new Missile().set(row, new Vector2(), 100);
      MissileDamageResolver.initialize(Riiablo.files, p, f.source,
          "player".equals(policy) ? null : f.monster, 0, 1, 0, null, !"hireling".equals(policy));
      assertNull(p.nativeElemental);
    }
  }

  @Test void rebuildAndPooledResetClearTheIndependentElementalPolicy() {
    try (Fixture f = new Fixture()) {
      int id = f.world.create();
      Missile p = f.world.getMapper(Missile.class).create(id).set(row("fire", 10, 11), new Vector2(), 100);
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0));
      assertNotNull(p.nativeElemental);
      assertTrue(MissileDamageResolver.initializeSkill(p, Riiablo.files.skills.get("Fire Bolt"), f.source, 1));
      assertNull(p.nativeElemental);
      p.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0));
      assertNotNull(p.nativeElemental);
      f.world.delete(id);
      f.land();
      assertNull(p.nativeElemental);
    }
  }

  private static Missiles.Entry row(String element, int min, int max) {
    Missiles.Entry row = new Missiles.Entry();
    row.Missile = "elemental-audit-fixture";
    row.EType = element; row.EMin = min; row.Emax = max;
    row.HitShift = 8; row.Range = 100;
    row.Collision = true; row.CollideType = 3; row.CollideKill = true;
    return row;
  }

  private static short resistStat(String element) {
    switch (element) {
      case "ltng": return Stat.lightresist;
      case "cold": return Stat.coldresist;
      case "mag": return Stat.magicresist;
      default: return Stat.fireresist;
    }
  }

  private static short pierceStat(String element) {
    switch (element) {
      case "ltng": return Stat.passive_ltng_pierce;
      case "cold": return Stat.passive_cold_pierce;
      case "mag": return Stat.passive_mag_pierce;
      default: return Stat.passive_fire_pierce;
    }
  }

  private static void assertWireHp(Fixture f) {
    FlatBufferBuilder buffer = new FlatBufferBuilder(64);
    buffer.finish(new VitalsSerializer().putData(buffer, f.target));
    VitalsP wire = VitalsP.getRootAsVitalsP(buffer.dataBuffer());
    AttributesWrapper client = new AttributesWrapper();
    client.attrs = Attributes.obtainLarge();
    VitalsSerializer.apply(client, wire);
    assertEquals(f.target.attrs.get(Stat.hitpoints).encodedValues(), client.attrs.get(Stat.hitpoints).encodedValues());
  }
}
