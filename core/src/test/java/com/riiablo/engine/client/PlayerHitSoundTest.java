package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.riiablo.CharacterClass;
import org.junit.jupiter.api.Test;

class PlayerHitSoundTest {
  @Test
  void mapsEveryCharacterClassToItsGroupedHitSound() {
    assertEquals("amazon_hit_1", PlayerHitSound.soundFor(CharacterClass.AMAZON));
    assertEquals("sorceress_hit_1", PlayerHitSound.soundFor(CharacterClass.SORCERESS));
    assertEquals("necromancer_hit_1", PlayerHitSound.soundFor(CharacterClass.NECROMANCER));
    assertEquals("paladin_hit_1", PlayerHitSound.soundFor(CharacterClass.PALADIN));
    assertEquals("barbarian_hit_1", PlayerHitSound.soundFor(CharacterClass.BARBARIAN));
    assertEquals("druid_hit_1", PlayerHitSound.soundFor(CharacterClass.DRUID));
    assertEquals("assassin_hit_1", PlayerHitSound.soundFor(CharacterClass.ASSASSIN));
  }

  @Test
  void leavesUnknownClassUnmapped() {
    assertNull(PlayerHitSound.soundFor(null));
  }
}
