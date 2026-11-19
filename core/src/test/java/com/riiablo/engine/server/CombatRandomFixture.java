package com.riiablo.engine.server;

import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import java.lang.reflect.Field;
import java.util.function.IntUnaryOperator;

/** Explicit random inputs for legacy worlds which do not bind a server context. */
public final class CombatRandomFixture {
  private CombatRandomFixture() {}

  public static void bind(Object system, IntUnaryOperator draws) {
    replace(system, "combatSystemResolver", new CombatSystem(draws));
  }

  /** Select the real durability stream's next 4% branch, not libGDX's unrelated RNG. */
  public static void nextWeaponDurabilityLoss(Actioneer actioneer) {
    for (int seed = 1; seed < 10000; seed++) {
      if (new NativeRng(seed).nextInt(100) < ItemDurabilityManager.WEAPON_DURABILITY_CHANCE) {
        replace(actioneer, "itemDurabilityManagerResolver",
            new ItemDurabilityManager(new NativeRng(seed)));
        return;
      }
    }
    throw new AssertionError("No durability fixture seed found");
  }

  private static void replace(Object system, String name, Object value) {
    try {
      Field field = system.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(system, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Cannot bind fixture " + name, e);
    }
  }
}
