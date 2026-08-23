package com.d2moo.common.monsters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MonstersTest {
  @Test
  void validatesNativeZeroBasedMonsterIds() {
    assertEquals(0, Monsters.validateMonsterId(0, 3));
    assertEquals(2, Monsters.validateMonsterId(2, 3));
    assertEquals(-1, Monsters.validateMonsterId(3, 3));
    assertEquals(-1, Monsters.validateMonsterId(-1, 3));
  }

  @Test
  void calculatesNativeHirelingExperience() {
    assertEquals(7_200, Monsters.getHirelingExperienceForNextLevel(3, 200));
  }

  @Test
  void capsNativeHirelingResurrectionCost() {
    assertEquals(750, Monsters.getHirelingResurrectionCost(10));
    assertEquals(50_000, Monsters.getHirelingResurrectionCost(100));
  }
}
