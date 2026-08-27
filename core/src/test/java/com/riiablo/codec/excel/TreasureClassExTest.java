package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.item.TreasureClassResolver;
import org.junit.jupiter.api.Test;

class TreasureClassExTest extends RiiabloTest {
  @Test
  void selectsRawWeightedEntriesIncludingNoDrop() {
    TreasureClassEx.Entry entry = new TreasureClassEx.Entry();
    entry.NoDrop = 2;
    entry.Item = new String[] {"hp1", "mp1"};
    entry.Prob = new int[] {3, 5};

    assertEquals(10, entry.totalProbability());
    assertNull(entry.select(0));
    assertNull(entry.select(1));
    assertEquals("hp1", entry.select(2));
    assertEquals("hp1", entry.select(4));
    assertEquals("mp1", entry.select(5));
    assertEquals("mp1", entry.select(9));
    assertNull(entry.select(10));
    assertEquals("hp1", entry.selectItem(0));
    assertEquals("mp1", entry.selectItem(3));
  }

  @Test
  void loadsNativeActOneChestClasses() {
    TreasureClassEx table = Riiablo.files.TreasureClassEx;
    TreasureClassEx.Entry normalA = table.getChest(Riiablo.NORMAL, Riiablo.ACT1, 0);
    TreasureClassEx.Entry normalC = table.getChest(Riiablo.NORMAL, Riiablo.ACT1, 2);
    TreasureClassEx.Entry nightmareA = table.getChest(Riiablo.NIGHTMARE, Riiablo.ACT1, 0);

    assertNotNull(normalA);
    assertNotNull(normalC);
    assertNotNull(nightmareA);
    assertEquals("Act 1 Chest A", normalA.TreasureClass);
    assertEquals("Act 1 Chest C", normalC.TreasureClass);
    assertEquals("Act 1 (N) Chest A", nightmareA.TreasureClass);
    assertTrue(normalA.totalProbability() > 0);
  }

  @Test
  void expandsActualActOneChestClassToLeafTokens() {
    TreasureClassEx table = Riiablo.files.TreasureClassEx;
    List<TreasureClassResolver.Drop> drops = new TreasureClassResolver(table)
        .resolve("Act 1 Chest A", 1, bound -> bound - 1);

    assertFalse(drops.isEmpty());
    for (TreasureClassResolver.Drop drop : drops) {
      assertNull(table.get(TreasureClassResolver.baseToken(drop.token)),
          "unexpanded child TC " + drop.token);
    }
  }

  @Test
  void loadsNativeActOneFallenNoDropWeights() {
    TreasureClassEx.Entry fallen = Riiablo.files.TreasureClassEx.get("Act 1 H2H A");

    assertNotNull(fallen);
    assertEquals(1, fallen.Picks);
    assertEquals(100, fallen.NoDrop);
    assertEquals(60, fallen.itemProbability());
    assertEquals(160, fallen.totalProbability());

    TreasureClassResolver resolver = new TreasureClassResolver(Riiablo.files.TreasureClassEx);
    assertTrue(resolver.resolve("Act 1 H2H A", 0, bound -> 99).isEmpty());
    List<TreasureClassResolver.Drop> firstDrop =
        resolver.resolve("Act 1 H2H A", 0, bound -> 100);
    assertEquals(1, firstDrop.size());
    assertEquals("gld", TreasureClassResolver.baseToken(firstDrop.get(0).token));
  }
}
