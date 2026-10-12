package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import org.junit.jupiter.api.Test;

/** Verifies Act II MonPreset and encoded SuperUnique translation. */
class Act2ArcaneNativeMonsterResolutionTest extends RiiabloTest {
  @Test
  void resolvesOrdinaryAct2MonPresetToLocalHcIdx() {
    int presetIndex = -1;
    MonStats.Entry expected = null;
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(2); i++) {
      String placement = Riiablo.files.MonPreset.getPlace(2, i);
      MonStats.Entry candidate = Riiablo.files.monstats.get(placement);
      if (candidate != null) {
        presetIndex = i;
        expected = candidate;
        break;
      }
    }
    assertNotNull(expected, "Act II MonPreset must contain an ordinary monster");

    Act2ArcaneD2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act2ArcaneD2MOOLayoutBridge.resolveNativeMonster(presetIndex, true);
    assertEquals(expected.hcIdx, resolved.monsterId);
    assertEquals(-1, resolved.superUniqueId);
  }

  @Test
  void preservesSuperUniqueForAct2MonPresetPlacement() {
    int presetIndex = -1;
    SuperUniques.Entry expectedUnique = null;
    MonStats.Entry expectedBase = null;
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(2); i++) {
      String placement = Riiablo.files.MonPreset.getPlace(2, i);
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
    assertNotNull(expectedUnique, "Act II MonPreset must contain a SuperUnique placement");
    assertNotNull(expectedBase);

    Act2ArcaneD2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act2ArcaneD2MOOLayoutBridge.resolveNativeMonster(presetIndex, true);
    assertEquals(expectedBase.hcIdx, resolved.monsterId);
    assertEquals(expectedUnique.hcIdx, resolved.superUniqueId);
    assertEquals(expectedUnique.Superunique, resolved.superUniqueKey);
  }

  @Test
  void resolvesEncodedSuperUniqueIndexWithoutMonPresetLookup() {
    SuperUniques.Entry expectedUnique = null;
    MonStats.Entry expectedBase = null;
    int encodedIndex = -1;
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

    Act2ArcaneD2MOOLayoutBridge.NativeMonsterResolution resolved =
        Act2ArcaneD2MOOLayoutBridge.resolveNativeMonster(encodedIndex, false);
    assertEquals(expectedBase.hcIdx, resolved.monsterId);
    assertEquals(expectedUnique.hcIdx, resolved.superUniqueId);
    assertEquals(expectedUnique.Superunique, resolved.superUniqueKey);
  }
}
