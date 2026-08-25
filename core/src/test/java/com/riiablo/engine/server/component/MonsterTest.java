package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
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
}
