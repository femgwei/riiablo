package com.riiablo.engine.server.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCombatRollsTest {
  @Test
  void rangesAndGuaranteedRollsKeepTheirDifferentDrawContracts() {
    List<Integer> bounds = new ArrayList<>();
    NativeCombatRolls rolls = new NativeCombatRolls(bound -> { bounds.add(bound); return bound - 1; });
    assertEquals(5, rolls.inclusive(5, 5));
    assertEquals(5, rolls.missileDamage(5, 5));
    assertEquals(9, rolls.missileDamage(5, 10));
    assertEquals(10, rolls.inclusive(5, 10));
    assertFalse(rolls.percent(0));
    assertTrue(rolls.percent(100));
    assertEquals(99, rolls.hitRoll());
    assertEquals(java.util.Arrays.asList(1, 5, 6, 100, 100), bounds);
  }

  @Test
  void passivePriorityAndGuaranteesDoNotConsumeExtraDraws() {
    List<Integer> bounds = new ArrayList<>();
    NativeCombatRolls rolls = new NativeCombatRolls(bound -> { bounds.add(bound); return 74; });
    assertEquals(NativeCombatConstants.DEFENSE_NONE, rolls.passiveDefense(0, true, 100, 100, 0, 100));
    assertEquals(NativeCombatConstants.DEFENSE_EVADE, rolls.passiveDefense(0, true, 100, 100, 100, 100));
    assertEquals(NativeCombatConstants.DEFENSE_WEAPON_BLOCK, rolls.passiveDefense(0, false, 100, 100, 100, 100));
    assertTrue(bounds.isEmpty());
    assertEquals(NativeCombatConstants.DEFENSE_DODGE, rolls.passiveDefense(0, false, 99, 100, 100, 1));
    assertEquals(java.util.Arrays.asList(100, 100), bounds);
  }

  @Test
  void seededSnapshotsMatchFrozenHostPassiveResolverAndRandomState() {
    Random inputs = new Random(518014L);
    for (int i = 0; i < 4000; i++) {
      int type = inputs.nextInt(4);
      boolean moving = inputs.nextBoolean();
      int dodge = inputs.nextInt(160) - 20, avoid = inputs.nextInt(160) - 20;
      int evade = inputs.nextInt(160) - 20, weapon = inputs.nextInt(160) - 20;
      long seed = inputs.nextLong();
      Random old = new Random(seed), migrated = new Random(seed);
      List<Integer> oldBounds = new ArrayList<>(), newBounds = new ArrayList<>();
      IntUnaryOperator oldRng = bound -> { oldBounds.add(bound); return old.nextInt(bound); };
      NativeCombatRolls rolls = new NativeCombatRolls(bound -> { newBounds.add(bound); return migrated.nextInt(bound); });
      assertEquals(frozen(type, moving, dodge, avoid, evade, weapon, oldRng),
          rolls.passiveDefense(type, moving, dodge, avoid, evade, weapon));
      assertEquals(oldBounds, newBounds);
      assertEquals(old.nextLong(), migrated.nextLong());
    }
  }

  // Frozen from 5ed47c6 DefenseCalculator's snapshot overload (not its legacy API).
  private static int frozen(int type, boolean moving, int dodge, int avoid,
      int evade, int weapon, IntUnaryOperator rng) {
    if (moving) {
      if (evade > 0 && passive(evade, rng)) return NativeCombatConstants.DEFENSE_EVADE;
      return NativeCombatConstants.DEFENSE_NONE;
    }
    if (weapon > 0 && passive(weapon, rng)) return NativeCombatConstants.DEFENSE_WEAPON_BLOCK;
    if (type == 0 && dodge > 0 && passive(dodge, rng)) return NativeCombatConstants.DEFENSE_DODGE;
    if (type == 1 && avoid > 0 && passive(avoid, rng)) return NativeCombatConstants.DEFENSE_AVOID;
    return NativeCombatConstants.DEFENSE_NONE;
  }

  private static boolean passive(int chance, IntUnaryOperator rng) {
    if (chance >= 100) return true;
    return rng.applyAsInt(100) < Math.min(Math.max(chance, 0), 75);
  }
}
