package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import org.junit.jupiter.api.Test;

/** Verifies the Act III D2MOO MonPreset/SuperUniques index translation. */
class Act3NativeMonsterResolutionTest extends RiiabloTest {
  @Test
  void resolvesOrdinaryAct3MonPresetToLocalHcIdx() {
    String placement = null;
    int presetIndex = -1;
    MonStats.Entry expected = null;
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(3); i++) {
      String candidate = Riiablo.files.MonPreset.getPlace(3, i);
      MonStats.Entry monster = Riiablo.files.monstats.get(candidate);
      if (monster != null) {
        placement = candidate;
        presetIndex = i;
        expected = monster;
        break;
      }
    }
    assertNotNull(expected, "Act III MonPreset must contain an ordinary monster");

    Act3D2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act3D2MOOLayoutBridge.resolveNativeMonster(presetIndex);
    assertEquals(expected.hcIdx, resolved.monsterId,
        "ordinary placement must resolve through MonStats");
    assertEquals(-1, resolved.superUniqueId);
    assertEquals(null, resolved.superUniqueKey);
    assertTrue(placement != null);
  }

  @Test
  void preservesSuperUniqueForAct3MonPresetPlacement() {
    int presetIndex = -1;
    SuperUniques.Entry expectedUnique = null;
    MonStats.Entry expectedBase = null;
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(3); i++) {
      String placement = Riiablo.files.MonPreset.getPlace(3, i);
      SuperUniques.Entry unique = Riiablo.files.SuperUniques.get(placement);
      MonStats.Entry base = unique == null ? null
          : Riiablo.files.monstats.get(unique.MonClass);
      if (unique != null && base != null) {
        presetIndex = i;
        expectedUnique = unique;
        expectedBase = base;
        break;
      }
    }
    assertNotNull(expectedUnique, "Act III MonPreset must contain a SuperUnique placement");
    assertNotNull(expectedBase, "SuperUnique MonClass must resolve to MonStats");

    Act3D2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act3D2MOOLayoutBridge.resolveNativeMonster(presetIndex);
    assertEquals(expectedBase.hcIdx, resolved.monsterId);
    assertEquals(expectedUnique.hcIdx, resolved.superUniqueId);
    assertEquals(expectedUnique.Superunique, resolved.superUniqueKey);
  }

  @Test
  void resolvesEncodedSuperUniqueIndexWhenNoMonPresetRowExists() {
    SuperUniques.Entry expectedUnique = null;
    int encodedIndex = -1;
    MonStats.Entry expectedBase = null;
    for (SuperUniques.Entry unique : Riiablo.files.SuperUniques) {
      if (unique == null || unique.hcIdx < 0) continue;
      MonStats.Entry base = Riiablo.files.monstats.get(unique.MonClass);
      int candidate = Riiablo.files.monstats.size()
          + Riiablo.files.SuperUniques.size() + unique.hcIdx;
      if (base != null && Riiablo.files.monstats.get(candidate) == null) {
        expectedUnique = unique;
        expectedBase = base;
        encodedIndex = candidate;
        break;
      }
    }
    assertNotNull(expectedUnique, "an encoded SuperUnique index must be representable");
    assertNotNull(expectedBase);

    Act3D2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act3D2MOOLayoutBridge.resolveNativeMonster(encodedIndex);
    assertEquals(expectedBase.hcIdx, resolved.monsterId);
    assertEquals(expectedUnique.hcIdx, resolved.superUniqueId);
    assertEquals(expectedUnique.Superunique, resolved.superUniqueKey);
  }
}
