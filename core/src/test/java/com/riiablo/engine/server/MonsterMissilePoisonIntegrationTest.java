package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.MathUtils;
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
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.VitalsP;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real collision -> state -> periodic settlement -> raw HP and Vitals round-trip.
 * Tick scheduling is the host contract, not an original executable capture.
 */
class MonsterMissilePoisonIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"1,2,8,0,0,0,511,4", "1,2,8,50,0,0,255,4",
      "1,2,8,0,50,0,511,2", "1,2,8,50,50,25,383,3",
      "1,2,8,50,50,-25,127,1", "1,1,7,0,0,0,128,4",
      "2,1,8,0,0,0,511,4", "0,2,8,0,0,0,0,4"})
  void rateSurvivesEveryTickWithoutAnImmediateHitOrDurationDivision(int min, int max, int shift,
      int resistance, int lengthResistance, int pierce, int expectedRate, int expectedLength) {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      f.source.base().put(Stat.passive_pois_pierce, pierce); f.source.reset();
      f.target.attrs.base().put(Stat.poisonresist, resistance);
      f.target.attrs.base().put(Stat.item_poisonlengthresist, lengthResistance);
      f.target.attrs.base().put(Stat.magic_damage_reduction, 999);
      f.target.attrs.reset();
      Missiles.Entry row = row(min, max, 4); row.HitShift = shift;
      assertNotNull(f.spawn(row).nativeElemental);
      f.land();
      assertEquals(10000 * 256, hp(f));
      assertEquals(0, f.probe.events);
      UnitState poison = states.getState(StateId.POISON);
      if (expectedRate == 0) { assertNull(poison); return; }
      assertNotNull(poison);
      assertEquals(expectedRate / 256f, poison.exactDamagePerFrame);
      assertEquals(expectedLength, poison.duration);
      for (int tick = 1; tick <= expectedLength; tick++) {
        f.land();
        assertEquals(10000 * 256 - expectedRate * tick, hp(f));
        assertEquals(tick, f.probe.events);
        assertEquals(DamageEvent.DAMAGE_OVER_TIME, f.probe.request.kind);
        assertEquals(expectedRate / 256f, f.probe.requested);
        assertEquals(expectedRate / 256f, f.probe.applied.lifeLost);
        assertEquals(f.owner, f.probe.request.attacker);
        assertWire(f);
      }
      assertNull(states.getState(StateId.POISON));
      f.land();
      assertEquals(expectedLength, f.probe.events);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"shrine", "zeroLength", "immune", "miss", "block", "noStates"})
  void rejectedPoisonNeverBecomesInstantDamage(String scenario) {
    try (Fixture f = new Fixture(true)) {
      StateList states = "noStates".equals(scenario) ? null : states(f);
      Missiles.Entry row = row(1, 2, "zeroLength".equals(scenario) ? 0 : 4);
      if ("shrine".equals(scenario)) states.addState(StateId.SHRINE_RESIST_POISON, 100);
      if ("immune".equals(scenario)) {
        // Player poison cap is 95; use a monster target to test actual immunity.
        f.world.getMapper(Player.class).remove(f.victim);
        f.world.getMapper(Monster.class).create(f.victim).set(f.monster.monstats, f.monster.monstats2);
        f.monster.converted = true; // friendly monster attacks evil monster; not an alignment rejection
        f.target.attrs.base().put(Stat.poisonresist, 100); f.target.attrs.reset();
      }
      if ("block".equals(scenario)) {
        row.MinDamage = row.MaxDamage = 1;
        f.target.attrs.base().put(Stat.toblock, 100);
        f.target.attrs.base().put(Stat.dexterity, 200); f.target.attrs.reset();
      }
      if ("miss".equals(scenario)) {
        row.ToHit = true;
        MathUtils.random = new Random(0) {
          @Override public int nextInt(int bound) { return bound - 1; }
        };
      }
      f.spawn(row); f.land();
      for (int i = 0; i < 6; i++) f.land();
      assertEquals(10000 * 256, hp(f));
      assertEquals(0, f.probe.events);
      if (states != null) assertFalse(states.hasState(StateId.POISON));
    }
  }

  @Test void nonimmuneMonsterControlProvesTheImmunityFixtureActuallyCollides() {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      f.world.getMapper(Player.class).remove(f.victim);
      f.world.getMapper(Monster.class).create(f.victim).set(f.monster.monstats, f.monster.monstats2);
      f.monster.converted = true;
      f.target.attrs.base().put(Stat.poisonresist, 99); f.target.attrs.reset();
      f.spawn(row(1, 2, 4)); f.land();
      UnitState poison = states.getState(StateId.POISON);
      assertNotNull(poison);
      assertEquals(5 / 256f, poison.exactDamagePerFrame);
      assertEquals(4, poison.duration);
      f.land();
      assertEquals(10000 * 256 - 5, hp(f));
    }
  }

  @Test void mixedPhysicalImpactDoesNotCarryThePoisonRateAsAnInstantElement() {
    try (Fixture f = new Fixture(true)) {
      states(f);
      Missiles.Entry row = row(1, 2, 2);
      row.MinDamage = row.MaxDamage = 1;
      f.spawn(row); f.land();
      assertEquals(10000 * 256 - 256, hp(f));
      assertEquals(DamageEvent.MISSILE, f.probe.request.kind);
      assertEquals(1f, f.probe.requested);
      assertEquals(1f, f.probe.physical);
      assertEquals(0f, f.probe.request.poisonDamage);
      assertEquals(0f, f.probe.applied.poisonDamage);
      f.land();
      assertEquals(10000 * 256 - 256 - 511, hp(f));
    }
  }

  @Test void activeLengthResistanceAndFrozenSourceEndpointsReachTheNewPolicy() {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      states.addState(StateId.MIGHT, 100).setNativeModifier(Stat.item_poisonlengthresist, 50);
      f.source.base().put(Stat.passive_pois_pierce, 25); f.source.reset();
      f.target.attrs.base().put(Stat.poisonresist, 50); f.target.attrs.reset();
      Missiles.Entry row = row(1, 2, 4);
      f.spawn(row);
      row.EMin = row.Emax = row.ELen = 999;
      f.source.base().put(Stat.passive_pois_pierce, 999); f.source.reset();
      f.land();
      UnitState p = states.getState(StateId.POISON);
      assertNotNull(p);
      assertEquals(383 / 256f, p.exactDamagePerFrame);
      assertEquals(3, p.duration);
    }
  }

  @Test void weakerRateDoesNotRefreshAndEqualRateCanShortenExistingPoison() {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      f.spawn(row(2, 2, 10)); f.land();
      f.spawn(row(1, 1, 20)); f.land(); // existing state ticks before the new collision
      assertEquals(2f, states.getState(StateId.POISON).exactDamagePerFrame);
      assertEquals(9, states.getState(StateId.POISON).duration);
      f.spawn(row(2, 2, 2)); f.land();
      assertEquals(2, states.getState(StateId.POISON).duration);
      f.land(); f.land();
      assertFalse(states.hasState(StateId.POISON));
      assertEquals(10000 * 256 - 4 * 512, hp(f));
    }
  }

  @Test void realTableOnlyPoisonRowsRetainTheirNativeRateEndpoints() {
    try (Fixture f = new Fixture()) {
      int checked = 0;
      StringBuilder rows = new StringBuilder();
      StringBuilder checkedRows = new StringBuilder();
      for (Missiles.Entry row : Riiablo.files.Missiles) {
        if (!"pois".equalsIgnoreCase(row.EType)) continue;
        rows.append(row.Missile).append(" src=").append(row.SrcDamage)
            .append(" func=").append(row.pSrvDmgFunc).append(" rate=").append(row.DamageRate)
            .append(" skill=").append(row.Skill).append(';');
        if (row.SrcDamage != 0 || row.SrcMissDmg != 0 || row.pSrvDmgFunc != 0
            || row.DamageRate != 0 || row.ApplyMastery || row.MissileSkill
            || text(row.Skill) || text(row.DmgSymPerCalc) || text(row.EDmgSymPerCalc)) continue;
        Missile p = new Missile().set(row, new Vector2(), 100);
        assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0), row.Missile);
        assertNotNull(p.nativeElemental, row.Missile);
        assertEquals(row.EMin << row.HitShift, p.nativeElemental.minimum, row.Missile);
        assertEquals(row.Emax << row.HitShift, p.nativeElemental.maximum, row.Missile);
        assertEquals(row.ELen, p.nativeElemental.poisonLength, row.Missile);
        checkedRows.append(row.Missile).append(';');
        checked++;
      }
      assertTrue(checked > 0, "No table-only poison rows: " + rows);
      System.out.println("Audited table-only poison rows (" + checked + "): " + checkedRows);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"source", "sourceSentinel", "sourceMissile", "skill", "formula",
      "physicalFormula", "mastery", "rate", "special", "player", "hireling"})
  void sourceSkillAndSpecialPoisonPoliciesStayOptedOut(String policy) {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = row(1, 2, 4);
      switch (policy) {
        case "source": row.SrcDamage = 128; break;
        case "sourceSentinel": row.SrcDamage = -1; break;
        case "sourceMissile": row.SrcMissDmg = 1; break;
        case "skill": row.Skill = "Poison Nova"; break;
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

  @Test void poisonPolicyIsClearedOnRebuildAndActualComponentRecycling() {
    try (Fixture f = new Fixture()) {
      Missile p = f.spawn(row(1, 2, 4));
      assertEquals(4, p.nativeElemental.poisonLength);
      assertTrue(MissileDamageResolver.initializeSkill(p, Riiablo.files.skills.get("Fire Bolt"), f.source, 1));
      assertNull(p.nativeElemental);
      p.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0));
      assertNotNull(p.nativeElemental);
      f.land(); // CollideKill deletes and resets the actual pooled component.
      f.land();
      assertNull(p.nativeElemental);
    }
  }

  private static StateList states(Fixture f) {
    return f.world.getMapper(UnitStates.class).create(f.victim).init(f.victim).stateList;
  }
  private static boolean text(String value) { return value != null && !value.trim().isEmpty(); }
  private static int hp(Fixture f) { return f.target.attrs.get(Stat.hitpoints).encodedValues(); }
  private static Missiles.Entry row(int min, int max, int length) {
    Missiles.Entry row = new Missiles.Entry();
    row.Missile = "poison-rate-audit-fixture";
    row.EType = "pois"; row.EMin = min; row.Emax = max; row.ELen = length;
    row.HitShift = 8; row.Range = 100;
    row.Collision = true; row.CollideType = 3; row.CollideKill = true;
    return row;
  }
  private static void assertWire(Fixture f) {
    FlatBufferBuilder buffer = new FlatBufferBuilder(64);
    buffer.finish(new VitalsSerializer().putData(buffer, f.target));
    AttributesWrapper client = new AttributesWrapper(); client.attrs = Attributes.obtainLarge();
    VitalsSerializer.apply(client, VitalsP.getRootAsVitalsP(buffer.dataBuffer()));
    assertEquals(hp(f), client.attrs.get(Stat.hitpoints).encodedValues());
  }
}
