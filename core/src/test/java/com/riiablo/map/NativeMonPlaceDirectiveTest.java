package com.riiablo.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonPlaceDirectiveTest {
  @Test void regionalFallenVariantsMatchMonsterRegion() {
    assertEquals("fallen1", Act1MapBuilderD2MOD.resolveActOnePlacement("place_fallen", 3));
    assertEquals("fallen2", Act1MapBuilderD2MOD.resolveActOnePlacement("place_fallen", 6));
    assertEquals("fallen3", Act1MapBuilderD2MOD.resolveActOnePlacement("place_fallen", 12));
    assertEquals("fallenshaman2", Act1MapBuilderD2MOD.resolveActOnePlacement("place_fallenshaman", 7));
    assertEquals("fallenshaman3", Act1MapBuilderD2MOD.resolveActOnePlacement("place_fallenshaman", 16));
    assertEquals("bloodraven", Act1MapBuilderD2MOD.resolveActOnePlacement("place_bloodraven", 17));
  }

  @Test void nativeDefaultDirectivesDoNotSpawnButPacksMustBeHandled() {
    assertTrue(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_group100"));
    assertTrue(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_nothing"));
    assertFalse(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_champion"));
    assertFalse(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_unique_pack"));
    assertFalse(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_fallen"));
    assertFalse(Act1MapBuilderD2MOD.isNativeNonSpawningPlacement("place_unknown"));
  }
}
