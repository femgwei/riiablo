package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class MonsterTest {
  @Test
  void storesScaledSecondaryAttackProfileAndClearsItOnReuse() {
    Monster monster = new Monster()
        .set(new MonStats.Entry(), new MonStats2.Entry())
        .setAttack2Profile(1, 2, 8);

    assertEquals(1, monster.attack2MinDamage);
    assertEquals(2, monster.attack2MaxDamage);
    assertEquals(8, monster.attack2ToHit);

    monster.set(new MonStats.Entry(), new MonStats2.Entry());
    assertEquals(0, monster.attack2MinDamage);
    assertEquals(0, monster.attack2MaxDamage);
    assertEquals(0, monster.attack2ToHit);
  }

  @Test
  void clampsInvalidSecondaryAttackRange() {
    Monster monster = new Monster()
        .set(new MonStats.Entry(), new MonStats2.Entry())
        .setAttack2Profile(3, 1, -4);

    assertEquals(3, monster.attack2MinDamage);
    assertEquals(3, monster.attack2MaxDamage);
    assertEquals(0, monster.attack2ToHit);
  }

  @Test
  void tracksResurrectionProvenanceAndClearsItOnReuse() {
    Monster monster = new Monster()
        .set(new MonStats.Entry(), new MonStats2.Entry())
        .setResurrected(42, false);

    assertTrue(monster.resurrected);
    assertEquals(42, monster.resurrectedBy);
    assertFalse(monster.playerRevive);

    monster.set(new MonStats.Entry(), new MonStats2.Entry());
    assertFalse(monster.resurrected);
    assertEquals(-1, monster.resurrectedBy);
    assertFalse(monster.playerRevive);
  }

  @Test
  void identifiesOnlyNativeAttackModesAsMelee() {
    assertTrue(Monster.isMeleeMode(Engine.Monster.MODE_A1));
    assertTrue(Monster.isMeleeMode(Engine.Monster.MODE_A2));
    assertFalse(Monster.isMeleeMode(Engine.Monster.MODE_S2));
    assertEquals("A1", Monster.modeName(Engine.Monster.MODE_A1));
    assertEquals("S2", Monster.modeName(Engine.Monster.MODE_S2));
  }
}
