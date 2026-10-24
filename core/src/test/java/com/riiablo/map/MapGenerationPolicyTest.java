package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapGenerationPolicyTest {
  @Test
  void nativeD2MooIsTheDefaultAndNullSafeChoice() {
    MapGenerationPolicy policy = MapGenerationPolicy.fromNativeToggle(null);
    assertEquals(MapGenerationPolicy.Mode.NATIVE_D2MOO, policy.mode());
    assertTrue(policy.usesNativeD2Moo());
    assertFalse(policy.usesLegacyCompatibility());
  }

  @Test
  void legacyBuilderRequiresAnExplicitCompatibilityOverride() {
    MapGenerationPolicy policy = MapGenerationPolicy.fromNativeToggle(false);
    assertEquals(MapGenerationPolicy.Mode.LEGACY_COMPATIBILITY, policy.mode());
    assertFalse(policy.usesNativeD2Moo());
    assertTrue(policy.usesLegacyCompatibility());
  }

  @Test
  void nativeExportOwnsCompatibilityPathAndCollisionLayers() {
    MapGenerationPolicy policy = MapGenerationPolicy.nativeDefault();
    assertFalse(policy.usesCompatibilityPath(true));
  }

  @Test
  void nativeSessionMayUseCompatibilityPathOnlyWithoutNativeExport() {
    MapGenerationPolicy policy = MapGenerationPolicy.nativeDefault();
    assertTrue(policy.usesCompatibilityPath(false));
  }

  @Test
  void explicitLegacyModeMayUseCompatibilityPathWithNativeExport() {
    MapGenerationPolicy policy = MapGenerationPolicy.fromNativeToggle(false);
    assertTrue(policy.usesCompatibilityPath(true));
  }

  @Test
  void nativeCollisionRequiresBothNativeModeAndAnAcceptedExport() {
    assertTrue(MapGenerationPolicy.nativeDefault().usesNativeCollision(true));
    assertFalse(MapGenerationPolicy.nativeDefault().usesNativeCollision(false));
    assertFalse(MapGenerationPolicy.fromNativeToggle(false).usesNativeCollision(true));
  }
}
