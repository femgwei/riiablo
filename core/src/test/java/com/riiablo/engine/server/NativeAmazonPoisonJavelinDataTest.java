package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AmazonSkills;
import org.junit.jupiter.api.Test;

/** Guards the native 1.10f rows used by the Amazon poison-javelin delay gate. */
public class NativeAmazonPoisonJavelinDataTest extends RiiabloTest {
  @Test
  void poisonJavelinsUseNativeRowsAndDistinctCastDelays() {
    Skills.Entry poison = Riiablo.files.skills.get("Poison Javelin");
    Skills.Entry plague = Riiablo.files.skills.get("Plague Javelin");
    assertNotNull(poison);
    assertNotNull(plague);
    assertTrue(AmazonSkills.isPoisonJavelin(poison));
    assertTrue(AmazonSkills.isPoisonJavelin(plague));
    assertEquals("pois", poison.EType);
    assertEquals("pois", plague.EType);

    NativeSkills.Entry poisonNative = Riiablo.files.NativeSkills.get(poison.Id);
    NativeSkills.Entry plagueNative = Riiablo.files.NativeSkills.get(plague.Id);
    assertNotNull(poisonNative);
    assertNotNull(plagueNative);
    Integer poisonDelay = poisonNative.integer("delay");
    Integer plagueDelay = plagueNative.integer("delay");
    assertNotNull(poisonDelay);
    assertNotNull(plagueDelay);
    assertTrue(poisonDelay > 0, "Poison Javelin must have a native cast delay");
    assertTrue(plagueDelay > poisonDelay,
        "Plague Javelin must retain its native long cast delay");
  }
}
