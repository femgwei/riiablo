package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.server.combat.*;
import com.riiablo.engine.server.component.Monster;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeMeleeDamageReplayTest {
  private List<Map<String, Integer>> records(String resource) throws IOException {
    InputStream stream = getClass().getResourceAsStream("/native/" + resource);
    assertNotNull(stream);
    List<Map<String, Integer>> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String[] keys = reader.readLine().split(",");
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.split(",");
        assertEquals(keys.length, fields.length);
        Map<String, Integer> row = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
          String value = fields[i];
          row.put(keys[i], value.equals("True") ? 1 : value.equals("False") ? 0 : (int) Long.parseLong(value));
        }
        rows.add(row);
      }
    }
    assertFalse(rows.isEmpty(), "empty capture cannot establish parity");
    return rows;
  }

  private NativeUnitRandom random(Map<String, Integer> row, String prefix) {
    Monster monster = new Monster();
    monster.nativeSeed = new D2Seed(row.get(prefix + "seed_low"), row.get(prefix + "seed_high"));
    monster.rngState = monster.nativeSeed.getNLowSeed();
    return NativeUnitRandom.monster(monster, 0, row.get("observer_id"));
  }

  private void after(Map<String, Integer> row, String prefix, NativeUnitRandom random) {
    assertEquals(row.get(prefix + "seed_after_low").intValue(), random.state());
    assertEquals(row.get(prefix + "seed_after_high").intValue(), random.high());
  }

  @Test void repeatedNativeHitDecisionsIncludeSuccessesAndMisses() throws Exception {
    int hits = 0, misses = 0;
    for (Map<String, Integer> r : records("fallen-attack-hit-roll-repeat.csv")) {
      assertEquals(5, r.get("stages").intValue());
      int chance = NativeCombatMath.hitChance(r.get("effective_rating"), r.get("effective_defense"),
          r.get("attacker_level"), r.get("defender_level"));
      assertEquals(r.get("chance").intValue(), chance);
      NativeUnitRandom random = random(r, "");
      int roll = random.nextInt(100);
      assertEquals(r.get("roll").intValue(), roll);
      after(r, "", random);
      boolean hit = NativeAttackResolver.calculateHit(r.get("effective_rating"),
          r.get("effective_defense"), chance, roll, false).hit;
      assertEquals(r.get("hit_result") != 0, hit);
      if (hit) hits++; else misses++;
    }
    assertTrue(hits > 0 && misses > 0);
  }

  @Test void capturedBurnPlaceholderAdvancesBothSeedWordsDespiteReturningZero() throws Exception {
    for (Map<String, Integer> r : records("fallen-attack-intermediate-rng.csv")) {
      assertEquals(0x8F1AF, r.get("caller_rva").intValue());
      assertEquals(1, r.get("bound").intValue());
      assertEquals(0, r.get("value").intValue());
      NativeUnitRandom random = random(r, "");
      new NativeCombatRolls(random).consumeMeleeBurnPlaceholder();
      after(r, "", random);
    }
  }

  @Test void nativeMeleeResolverPreservesStreamFromHitThroughDamageAndMonsterCritical() throws Exception {
    Map<Integer, Map<String, Integer>> hits = new HashMap<>();
    for (Map<String, Integer> hit : records("fallen-attack-hit-roll-repeat.csv")) hits.put(hit.get("frame"), hit);
    for (Map<String, Integer> r : records("fallen-attack-physical-roll.csv")) {
      Map<String, Integer> hit = hits.get(r.get("frame"));
      assertNotNull(hit);
      NativeAttackData a = new NativeAttackData();
      a.level = hit.get("attacker_level"); a.attackRating = hit.get("effective_rating");
      a.strength = r.get("strength"); a.minDamage = r.get("min_damage"); a.maxDamage = r.get("max_damage");
      a.enhancedDamagePercent = r.get("enhanced_percent"); a.normalDamage = r.get("normal_damage");
      a.minimumDamagePercent = r.get("minimum_percent"); a.maximumDamagePercent = r.get("maximum_percent");
      a.fixedPointPhysical = true; a.nativeMonsterMeleeElements = true;
      a.monsterCriticalChance = r.get("critical_chance");
      NativeDefenseData d = new NativeDefenseData();
      d.isPlayer = true; d.level = hit.get("defender_level"); d.defense = hit.get("effective_defense");
      NativeUnitRandom random = random(hit, "");
      NativeDamagePacket result = new NativeAttackResolver(new NativeCombatRolls(random)).resolve(a, d);
      assertTrue(result.hit);
      assertEquals(hit.get("roll").intValue(), result.hitRoll);
      assertEquals(r.get("physical_fixed").intValue(), result.physicalDamage);
      assertEquals(r.get("hp_loss_fixed") / 256f, result.totalDamageHp(), 0f);
      after(r, "critical_", random);
    }
  }

  @Test void nativePhysicalAndCriticalDrawsReproduceSettlementAndHpLoss() throws Exception {
    for (Map<String, Integer> r : records("fallen-attack-physical-roll.csv")) {
      assertEquals(2, r.get("stages").intValue());
      assertEquals(2, r.get("critical_stages").intValue());
      assertEquals(0, r.get("base_fixed").intValue(), "fixture has no prefilled damage");
      NativeUnitRandom random = random(r, "");
      int raw = new NativeCombatRolls(random).physicalFixed(r.get("min_fixed"), r.get("max_fixed"));
      assertEquals(r.get("roll").intValue(), raw - r.get("min_fixed"));
      after(r, "", random);

      // Independently exercise the production endpoint/stat policy from the same checkpoint.
      NativeAttackData a = new NativeAttackData();
      a.strength = r.get("strength"); a.minDamage = r.get("min_damage"); a.maxDamage = r.get("max_damage");
      a.enhancedDamagePercent = r.get("enhanced_percent");
      a.minimumDamagePercent = r.get("minimum_percent"); a.maximumDamagePercent = r.get("maximum_percent");
      a.normalDamage = r.get("normal_damage"); a.weaponSourceDamage = r.get("source_scale");
      assertEquals(128, a.weaponSourceDamage, "recorded fixture uses unscaled source damage");
      NativeUnitRandom endpointRandom = random(r, "");
      assertEquals(raw, NativePhysicalDamageMath.roll(a, new NativeDefenseData(), new NativeCombatRolls(endpointRandom)));
      after(r, "", endpointRandom);

      // Native calls between physical and monster-critical draws are outside this checkpoint replay.
      NativeUnitRandom criticalRandom = random(r, "critical_");
      boolean critical = new NativeCombatRolls(criticalRandom).monsterCritical(r.get("critical_chance"));
      assertEquals(r.get("critical_roll") < r.get("critical_chance"), critical);
      after(r, "critical_", criticalRandom);
      if (critical) raw *= 2;
      int resistance = NativeMitigationMath.resistance(true, true, false, r.get("resistance"), 0, 0);
      int physical = NativeMitigationMath.reduce(raw, 256, resistance, r.get("flat_reduction"), 0);
      assertEquals(r.get("physical_fixed").intValue(), physical);
      assertEquals(r.get("total_fixed").intValue(), physical, "fixture has only physical damage");
      assertEquals(r.get("hp_loss_fixed").intValue(), physical);
    }
  }
}
