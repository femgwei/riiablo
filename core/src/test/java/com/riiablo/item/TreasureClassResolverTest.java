package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.riiablo.codec.excel.TreasureClassEx;
import org.junit.jupiter.api.Test;

class TreasureClassResolverTest {
  @Test
  void expandsChildClassesAndConsumesNoDropPicks() {
    TestTable table = new TestTable();
    table.add(0, entry("root", 3, 1, new String[] {"child"}, new int[] {2}));
    TreasureClassEx.Entry child = entry("child", 1, 0,
        new String[] {"hp1", "mp1"}, new int[] {1, 1});
    child.Magic = 100;
    table.add(1, child);

    SequenceRandom random = new SequenceRandom(0, 1, 1, 2, 0);
    List<TreasureClassResolver.Drop> drops =
        new TreasureClassResolver(table).resolve("root", 0, random);

    assertEquals(2, drops.size());
    assertEquals("mp1", drops.get(0).token);
    assertEquals("hp1", drops.get(1).token);
    assertEquals(100, drops.get(0).Magic);
  }

  @Test
  void negativePicksSelectProbabilitySlotsInOrderWithoutNoDrop() {
    TestTable table = new TestTable();
    table.add(0, entry("sequence", -3, 99,
        new String[] {"a", "b", "c"}, new int[] {1, 1, 1}));

    List<TreasureClassResolver.Drop> drops =
        new TreasureClassResolver(table).resolve("sequence", 0, bound -> {
          throw new AssertionError("negative Picks must not use the random source");
        });

    assertEquals(3, drops.size());
    assertEquals("a", drops.get(0).token);
    assertEquals("b", drops.get(1).token);
    assertEquals("c", drops.get(2).token);
  }

  @Test
  void inheritsStrongestNativeQualityModifiersAndCapsLeafDrops() {
    TestTable table = new TestTable();
    TreasureClassEx.Entry root = entry("root", 10, 0,
        new String[] {"child"}, new int[] {1});
    root.Unique = 500;
    root.Magic = 0;
    table.add(0, root);
    TreasureClassEx.Entry child = entry("child", 1, 0,
        new String[] {"rin"}, new int[] {1});
    child.Unique = 300;
    child.Magic = 200;
    table.add(1, child);

    List<TreasureClassResolver.Drop> drops =
        new TreasureClassResolver(table).resolve("root", 0, bound -> 0, 2);

    assertEquals(2, drops.size());
    assertEquals(500, drops.get(0).Unique);
    assertEquals(200, drops.get(0).Magic);
  }

  @Test
  void recognizesQuotedChildClassBeforeCommaModifiers() {
    TestTable table = new TestTable();
    table.add(0, entry("root", 1, 0,
        new String[] {"\"child,cm=500\""}, new int[] {1}));
    table.add(1, entry("child", 1, 0, new String[] {"gld,mul=2048"}, new int[] {1}));

    List<TreasureClassResolver.Drop> drops =
        new TreasureClassResolver(table).resolve("root", 0, bound -> 0);

    assertEquals(1, drops.size());
    assertEquals("gld,mul=2048", drops.get(0).token);
  }

  @Test
  void cyclicClassesStopAtNativeStackDepth() {
    TestTable table = new TestTable();
    table.add(0, entry("a", 1, 0, new String[] {"b"}, new int[] {1}));
    table.add(1, entry("b", 1, 0, new String[] {"a"}, new int[] {1}));

    List<TreasureClassResolver.Drop> drops =
        new TreasureClassResolver(table).resolve("a", 0, bound -> 0);

    assertTrue(drops.isEmpty());
  }

  @Test
  void appliesNativePartyAndRemotePlayerNoDropWeighting() {
    TreasureClassResolver.PlayerContext solo =
        new TreasureClassResolver.PlayerContext(1, 1);
    TreasureClassResolver.PlayerContext twoUnpartied =
        new TreasureClassResolver.PlayerContext(2, 1);
    TreasureClassResolver.PlayerContext threeUnpartied =
        new TreasureClassResolver.PlayerContext(3, 1);
    TreasureClassResolver.PlayerContext twoPartyMembers =
        new TreasureClassResolver.PlayerContext(2, 2);
    TreasureClassResolver.PlayerContext cappedAtSpawn =
        new TreasureClassResolver.PlayerContext(8, 1, 1);

    assertEquals(1, solo.effectivePlayerCount());
    assertEquals(1, twoUnpartied.effectivePlayerCount());
    assertEquals(2, threeUnpartied.effectivePlayerCount());
    assertEquals(2, twoPartyMembers.effectivePlayerCount());
    assertEquals(1, cappedAtSpawn.effectivePlayerCount());
    assertEquals(100, TreasureClassResolver.adjustedNoDrop(100, 100, 1));
    assertEquals(33, TreasureClassResolver.adjustedNoDrop(100, 100, 2));
    assertEquals(14, TreasureClassResolver.adjustedNoDrop(100, 100, 3));
  }

  @Test
  void usesNativeMonsterRootTcLevelPolicy() {
    assertEquals(0, TreasureClassResolver.nativeMonsterRootLevel(0, 12, false, false));
    assertEquals(12, TreasureClassResolver.nativeMonsterRootLevel(1, 12, false, false));
    assertEquals(12, TreasureClassResolver.nativeMonsterRootLevel(2, 12, false, false));
    assertEquals(0, TreasureClassResolver.nativeMonsterRootLevel(1, 12, true, false));
    assertEquals(0, TreasureClassResolver.nativeMonsterRootLevel(1, 12, false, true));
  }

  @Test
  void multiplayerContextChangesPositivePickNoDropBoundary() {
    TestTable table = new TestTable();
    table.add(0, entry("root", 1, 100, new String[] {"hp1"}, new int[] {100}));
    TreasureClassResolver resolver = new TreasureClassResolver(table);

    List<TreasureClassResolver.Drop> solo = resolver.resolve(
        "root", 0, bound -> 50, 6, TreasureClassResolver.PlayerContext.SINGLE_PLAYER);
    List<TreasureClassResolver.Drop> party = resolver.resolve(
        "root", 0, bound -> 50, 6, new TreasureClassResolver.PlayerContext(2, 2));

    assertTrue(solo.isEmpty());
    assertEquals(1, party.size());
    assertEquals("hp1", party.get(0).token);
  }

  private static TreasureClassEx.Entry entry(String name, int picks, int noDrop,
      String[] items, int[] probabilities) {
    TreasureClassEx.Entry entry = new TreasureClassEx.Entry();
    entry.TreasureClass = name;
    entry.Picks = picks;
    entry.NoDrop = noDrop;
    entry.Item = items;
    entry.Prob = probabilities;
    return entry;
  }

  private static final class TestTable extends TreasureClassEx {
    private final Map<String, Integer> ids = new HashMap<>();

    void add(int id, Entry entry) {
      put(id, entry);
      ids.put(entry.TreasureClass, id);
    }

    @Override
    public Entry get(String id) {
      Integer index = ids.get(id);
      return index == null ? null : get(index);
    }

    @Override
    public int index(String id) {
      Integer index = ids.get(id);
      return index == null ? -1 : index;
    }
  }

  private static final class SequenceRandom implements TreasureClassResolver.RandomSource {
    private final int[] values;
    private int index;

    SequenceRandom(int... values) {
      this.values = values;
    }

    @Override
    public int nextInt(int bound) {
      return values[index++];
    }
  }
}
