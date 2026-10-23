package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NamedRngStreamsTest {
  @Test void sameIdentityReplaysExactSequence() {
    NativeRng first = NamedRngStreams.open(0x110F, "combat.hit", 42L);
    NativeRng second = NamedRngStreams.open(0x110F, "combat.hit", 42L);
    for (int i = 0; i < 32; i++) assertEquals(first.nextInt(), second.nextInt());
  }

  @Test void streamNameAndContextIsolateConsumption() {
    NativeRng combat = NamedRngStreams.open(7, "combat.hit", 1L);
    NativeRng drops = NamedRngStreams.open(7, "drop.quality", 1L);
    NativeRng otherUnit = NamedRngStreams.open(7, "combat.hit", 2L);
    assertNotEquals(NamedRngStreams.seedFor(7, "combat.hit", 1L),
        NamedRngStreams.seedFor(7, "drop.quality", 1L));
    assertNotEquals(NamedRngStreams.seedFor(7, "combat.hit", 1L),
        NamedRngStreams.seedFor(7, "combat.hit", 2L));
    int untouched = drops.nextInt();
    combat.nextInt(); combat.nextInt();
    assertEquals(untouched, NamedRngStreams.open(7, "drop.quality", 1L).nextInt());
    assertNotEquals(combat.nextInt(), otherUnit.nextInt());
  }

  @Test void namesMustBeExplicit() {
    assertThrows(NullPointerException.class, () -> NamedRngStreams.open(1, null, 0));
    assertThrows(IllegalArgumentException.class, () -> NamedRngStreams.open(1, "  ", 0));
  }
}
