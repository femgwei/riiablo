package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.MonsterMissilePhysicalIntegrationTest.Fixture;
import com.riiablo.engine.server.combat.NativeMissileElementalDamage;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.VitalsP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Creation -> immutable multichannel packet -> collision -> states -> raw HP / wire. */
class MonsterMissileSourceIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"64,384,640,639", "128,768,1280,1279", "192,1152,1920,1919"})
  void tablePlusSourceScalesAsOneFixedPacket(int scale, int min, int max, int rolled) {
    try (Fixture f = new Fixture(true)) {
      f.source.base().put(Stat.firemindam, 2); f.source.base().put(Stat.firemaxdam, 3);
      f.source.base().put(Stat.mindamage, 0); f.source.base().put(Stat.maxdamage, 0); f.source.reset();
      Missile missile = f.spawn(row("fire", scale));
      assertEquals(min, missile.nativeElemental.minimum(1));
      assertEquals(max, missile.nativeElemental.maximum(1));
      f.land();
      assertEquals(10000 * 256 - rolled, hp(f));
      assertEquals(rolled / 256f, f.probe.applied.lifeLost);
      assertWire(f);
    }
  }

  @Test
  void allSourceChannelsCoexistWithPhysicalAndPoisonIsNotAnImmediateHit() {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      short[] mins = {Stat.firemindam, Stat.lightmindam, Stat.coldmindam, Stat.magicmindam};
      short[] maxs = {Stat.firemaxdam, Stat.lightmaxdam, Stat.coldmaxdam, Stat.magicmaxdam};
      for (int i = 0; i < mins.length; i++) {
        f.source.base().put(mins[i], 2); f.source.base().put(maxs[i], 3);
      }
      f.source.base().put(Stat.poisonmindam, 256); f.source.base().put(Stat.poisonmaxdam, 512);
      f.source.base().put(Stat.poisonlength, 100); f.source.base().put(Stat.poison_count, 4);
      f.source.base().put(Stat.coldlength, 20); f.source.reset();
      Missile p = f.spawn(row("fire", 64));
      assertEquals(10, p.nativeElemental.coldLength);
      assertEquals(100, p.nativeElemental.poisonLength);
      assertEquals(2, p.damage.get(Stat.poison_count).asInt());
      f.land();
      // Physical source 10..11 -> 1280..1408; fire=(table256..512+source512..768)/2.
      int immediate = 1407 + 639 + 383 * 3;
      assertEquals(10000 * 256 - immediate, hp(f));
      assertEquals(1407 / 256f, f.probe.physical);
      assertEquals(immediate / 256f, f.probe.applied.lifeLost);
      UnitState poison = states.getState(StateId.POISON);
      assertNotNull(poison); assertEquals(255 / 256f, poison.exactDamagePerFrame);
      assertEquals(50, poison.duration);
      assertEquals(10, states.getState(StateId.COLD).duration);
      assertWire(f);
      for (int tick = 1; tick <= 50; tick++) {
        f.land(); assertEquals(10000 * 256 - immediate - 255 * tick, hp(f)); assertWire(f);
      }
      assertNull(states.getState(StateId.POISON));
    }
  }

  @ParameterizedTest
  @CsvSource({"64,0,265,190", "128,0,531,63", "192,0,797,47",
      "64,30,265,70", "128,30,531,70", "192,30,797,70"})
  void poisonTableAndRawSourceRatesKeepIndependentLengthRules(
      int scale, int override, int expectedRate, int length) {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      f.source.base().put(Stat.mindamage, 0); f.source.base().put(Stat.maxdamage, 0);
      f.source.base().put(Stat.poisonmindam, 10); f.source.base().put(Stat.poisonmaxdam, 20);
      f.source.base().put(Stat.poisonlength, 150); f.source.base().put(Stat.poison_count, 3);
      f.source.base().put(Stat.skill_poison_override_length, override); f.source.reset();
      Missiles.Entry row = row("pois", scale); row.ELen = 40;
      f.spawn(row); f.land();
      assertEquals(10000 * 256, hp(f)); assertEquals(0, f.probe.events);
      UnitState poison = states.getState(StateId.POISON);
      assertNotNull(poison); assertEquals(length, poison.duration);
      assertEquals(expectedRate / 256f, poison.exactDamagePerFrame);
      f.land(); assertEquals(10000 * 256 - expectedRate, hp(f)); assertWire(f);
    }
  }

  @Test
  void sourceOnlyPacketStillInstallsColdWhenDamageIsFullyReducedAndPoisonResistsSeparately() {
    try (Fixture f = new Fixture(true)) {
      StateList states = states(f);
      f.source.base().put(Stat.mindamage, 0); f.source.base().put(Stat.maxdamage, 0);
      f.source.base().put(Stat.coldmindam, 2); f.source.base().put(Stat.coldmaxdam, 3);
      f.source.base().put(Stat.coldlength, 40);
      f.source.base().put(Stat.poisonmindam, 256); f.source.base().put(Stat.poisonmaxdam, 512);
      f.source.base().put(Stat.poisonlength, 100); f.source.base().put(Stat.poison_count, 4);
      f.source.base().put(Stat.passive_pois_pierce, 25); f.source.reset();
      f.target.attrs.base().put(Stat.magic_damage_reduction, 999);
      f.target.attrs.base().put(Stat.coldresist, 50);
      f.target.attrs.base().put(Stat.poisonresist, 50);
      f.target.attrs.base().put(Stat.item_poisonlengthresist, 50); f.target.attrs.reset();
      f.spawn(row("", 64)); f.land();
      assertEquals(10000 * 256, hp(f));
      assertEquals(10, states.getState(StateId.COLD).duration);
      UnitState poison = states.getState(StateId.POISON);
      assertEquals(37, poison.duration); // 100 / (4*64/128) * 75%
      assertEquals(191 / 256f, poison.exactDamagePerFrame);
      f.land(); assertEquals(10000 * 256 - 191, hp(f));
    }
  }

  @Test
  void sourceStateLayersAndFlightSnapshotAreCapturedExactlyOnce() {
    try (Fixture f = new Fixture(true)) {
      f.source.base().put(Stat.mindamage, 0); f.source.base().put(Stat.maxdamage, 0);
      f.source.base().put(Stat.firemindam, 1); f.source.base().put(Stat.firemaxdam, 1); f.source.reset();
      StateList states = f.world.getMapper(UnitStates.class).create(f.owner).init(f.owner).stateList;
      UnitState enchant = states.addState(StateId.ENCHANT, 100, 1, f.owner);
      enchant.setNativeModifier(Stat.firemindam, 2); enchant.setNativeModifier(Stat.firemaxdam, 3);
      Missile p = f.spawn(row("fire", 64));
      assertEquals(512, p.nativeElemental.minimum(1)); assertEquals(768, p.nativeElemental.maximum(1));
      f.source.base().put(Stat.firemindam, 999); f.source.base().put(Stat.firemaxdam, 999); f.source.reset();
      enchant.setNativeModifier(Stat.firemindam, 999); enchant.setNativeModifier(Stat.firemaxdam, 999);
      f.land(); assertEquals(10000 * 256 - 767, hp(f)); assertWire(f);
    }
  }

  @Test
  void mixedPolicyRemainsImmutableAcrossRebuildAndActualPoolReset() {
    try (Fixture f = new Fixture()) {
      Missile p = f.spawn(row("fire", 64)); NativeMissileElementalDamage first = p.nativeElemental;
      assertNotNull(first);
      assertTrue(MissileDamageResolver.initializeSkill(p, Riiablo.files.skills.get("Fire Bolt"), f.source, 1));
      assertNull(p.nativeElemental);
      p.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0));
      assertNotSame(first, p.nativeElemental);
      f.land(); f.land(); assertNull(p.nativeElemental);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"skill", "missileSkill", "sentinel", "oversize", "origin", "damageFormula",
      "elementFormula", "mastery", "rate", "callback", "unknown", "venom", "monsterMode",
      "burning", "stun", "stamina", "burningState", "player", "mercenary"})
  void unreviewedSourcePoliciesDoNotOptIn(String policy) {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = row("fire", 64);
      StateList states = null;
      switch (policy) {
        case "skill": row.Skill = "Fire Bolt"; break;
        case "missileSkill": row.MissileSkill = true; break;
        case "sentinel": row.SrcDamage = -1; break;
        case "oversize": row.SrcDamage = 256; break;
        case "origin": row.SrcMissDmg = 1; break;
        case "damageFormula": row.DmgSymPerCalc = "ln12"; break;
        case "elementFormula": row.EDmgSymPerCalc = "ln12"; break;
        case "mastery": row.ApplyMastery = true; break;
        case "rate": row.DamageRate = 1; break;
        case "callback": row.pSrvDmgFunc = 2; break;
        case "unknown": row.EType = "burn"; break;
        case "venom": states = new StateList(f.owner); states.addState(StateId.VENOMCLAWS, 100); break;
        case "burning": f.source.base().put(Stat.burningmin, 1); f.source.reset(); break;
        case "stun": f.source.base().put(Stat.stunlength, 1); f.source.reset(); break;
        case "stamina": f.source.base().put(Stat.stamdrainmindam, 1); f.source.reset(); break;
        case "burningState":
          states = new StateList(f.owner);
          states.addState(StateId.ENCHANT, 100).setNativeModifier(Stat.burningmax, 1); break;
        case "monsterMode":
          f.monster.monstats = new MonStats.Entry();
          f.monster.monstats.El1Mode = "A1"; f.monster.monstats.El1Pct = new int[] {100, 100, 100}; break;
      }
      Missile p = new Missile().set(row, new Vector2(), 100);
      MissileDamageResolver.initialize(Riiablo.files, p, f.source,
          "player".equals(policy) ? null : f.monster, Riiablo.files.MonMode.index("A1"), 1, 0,
          states, !"mercenary".equals(policy));
      assertNull(p.nativeElemental);
    }
  }

  @Test
  void installedOrdinarySourceRowsHaveAnExplicitCreationPolicy() {
    try (Fixture f = new Fixture()) {
      f.monster.monstats = new MonStats.Entry();
      int checked = 0;
      StringBuilder names = new StringBuilder();
      for (Missiles.Entry row : Riiablo.files.Missiles) {
        if (row.SrcDamage <= 0 || row.SrcDamage > 255 || row.SrcMissDmg != 0
            || row.pSrvDmgFunc != 0 || row.DamageRate != 0 || row.ApplyMastery
            || text(row.Skill) || row.MissileSkill || text(row.DmgSymPerCalc) || text(row.EDmgSymPerCalc)
            || text(row.EType) && !java.util.Arrays.asList("fire", "ltng", "cold", "pois", "mag").contains(row.EType)) continue;
        Missile p = new Missile().set(row, new Vector2(), Math.max(1, row.Range));
        assertTrue(MissileDamageResolver.initialize(Riiablo.files, p, f.source, f.monster, 0, 1, 0));
        assertNotNull(p.nativeElemental, row.Missile);
        names.append(row.Missile).append(' '); checked++;
      }
      assertTrue(checked > 0);
      System.out.println("Ordinary source rows (" + checked + "): " + names);
    }
  }

  private static boolean text(String value) { return value != null && !value.trim().isEmpty(); }
  private static Missiles.Entry row(String type, int scale) {
    Missiles.Entry row = new Missiles.Entry(); row.Missile = "source-mixture-audit";
    row.EType = type; row.EMin = 1; row.Emax = 2; row.HitShift = 8; row.SrcDamage = scale;
    row.Range = 100; row.Collision = true; row.CollideType = 3; row.CollideKill = true;
    return row;
  }
  private static StateList states(Fixture f) {
    return f.world.getMapper(UnitStates.class).create(f.victim).init(f.victim).stateList;
  }
  private static int hp(Fixture f) { return f.target.attrs.get(Stat.hitpoints).encodedValues(); }
  private static void assertWire(Fixture f) {
    FlatBufferBuilder buffer = new FlatBufferBuilder(64);
    buffer.finish(new VitalsSerializer().putData(buffer, f.target));
    AttributesWrapper client = new AttributesWrapper(); client.attrs = Attributes.obtainLarge();
    VitalsSerializer.apply(client, VitalsP.getRootAsVitalsP(buffer.dataBuffer()));
    assertEquals(hp(f), client.attrs.get(Stat.hitpoints).encodedValues());
  }
}
