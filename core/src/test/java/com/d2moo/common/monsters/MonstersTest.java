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

  @Test
  void classifiesNativeMonsterFlagsWithoutRuntimeUnits() {
    int flags = D2MonsterFlags.DEMON | D2MonsterFlags.BOSS | D2MonsterFlags.NPC;

    assertEquals(true, Monsters.isDemon(flags));
    assertEquals(false, Monsters.isUndead(flags));
    assertEquals(true, Monsters.isBoss(flags));
    assertEquals(false, Monsters.isPrimeEvil(flags));
    assertEquals(true, Monsters.canBeInTown(flags, false));
    assertEquals(true, Monsters.canBeInTown(0, true));
  }

  @Test
  void preservesNativeReadonlyLookupTables() {
    assertEquals(3371, D2MonsterLookupTables.hirelingDescriptionStringId(1));
    assertEquals(0, D2MonsterLookupTables.hirelingDescriptionStringId(99));
    assertEquals(15, D2MonsterLookupTables.value11052(61));
    assertEquals(7, D2MonsterLookupTables.value11053(59));
    assertEquals(60, D2MonsterLookupTables.value11054(7));
    assertEquals(-3, D2MonsterLookupTables.offsetX11055(25));
    assertEquals(-3, D2MonsterLookupTables.offsetY11055(24));
  }
}
