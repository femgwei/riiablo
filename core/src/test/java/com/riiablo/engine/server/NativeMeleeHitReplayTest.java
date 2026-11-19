package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.server.combat.NativeAttackResolver;
import com.riiablo.engine.server.combat.NativeCombatMath;
import com.riiablo.engine.server.component.Monster;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeMeleeHitReplayTest {
  @Test void nativeEffectiveInputsAndBothSeedWordsReproduceHitDecision() throws Exception {
    InputStream stream = getClass().getResourceAsStream("/native/fallen-attack-hit-roll.csv");
    assertNotNull(stream);
    int samples = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      assertEquals("observer_id,target_id,stages,effective_rating,effective_defense,attacker_level,defender_level,ar_factor,chance,roll,seed_low,seed_high,seed_after_low,seed_after_high,hit_result", reader.readLine());
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.split(",");
        int[] v = new int[fields.length];
        for (int i = 0; i < v.length; i++) v[i] = (int) Long.parseLong(fields[i]);
        assertEquals(5, v[2], "complete native trace required");
        int chance = NativeCombatMath.hitChance(v[3], v[4], v[5], v[6]);
        assertEquals(v[8], chance);
        Monster monster = new Monster();
        monster.nativeSeed = new D2Seed(v[10], v[11]);
        monster.rngState = v[10];
        NativeUnitRandom random = NativeUnitRandom.monster(monster, 0, v[0]);
        int roll = random.nextInt(100);
        assertEquals(v[9], roll);
        assertEquals(v[12], random.state(), "post-draw low word");
        assertEquals(v[13], random.high(), "post-draw high word");
        assertEquals(v[14] != 0,
            NativeAttackResolver.calculateHit(v[3], v[4], chance, roll, false).hit);
        samples++;
      }
    }
    assertTrue(samples > 0, "empty recordings are not parity evidence");
  }
}
