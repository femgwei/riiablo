package com.riiablo.codec.excel;

/** Native TreasureClassEx.txt rows used by monster and object drops. */
@Excel.Binned
public class TreasureClassEx extends Excel<TreasureClassEx.Entry> {
  private static final String[] DIFFICULTY_SUFFIX = {"", " (N)", " (H)"};
  private static final String[] CHEST_VARIANT = {"A", "B", "C"};

  public static class Entry extends Excel.Entry {
    @Key
    @Column(format = "Treasure Class")
    public String TreasureClass;
    @Column(format = "group") public int Group;
    @Column(format = "level") public int Level;
    @Column public int Picks;
    @Column public int Unique;
    @Column public int Set;
    @Column public int Rare;
    @Column public int Magic;
    // Present on D2TCExShortStrc and populated by synthetic/item-type TCs or
    // per-token modifiers; vanilla TreasureClassEx.txt has no direct columns.
    public int Superior;
    public int Normal;
    @Column public int NoDrop;
    @Column(startIndex = 1, endIndex = 11, format = "Item%d")
    public String[] Item;
    @Column(startIndex = 1, endIndex = 11, format = "Prob%d")
    public int[] Prob;

    @Override
    public String toString() {
      return TreasureClass;
    }

    public int totalProbability() {
      int total = Math.max(0, NoDrop);
      return total + itemProbability();
    }

    public int itemProbability() {
      int total = 0;
      if (Prob != null) {
        for (int probability : Prob) total += Math.max(0, probability);
      }
      return total;
    }

    /** Returns one raw item/child-TC token, or {@code null} for NoDrop. */
    public String select(int randomValue) {
      int total = totalProbability();
      if (total <= 0) return null;
      int roll = Math.floorMod(randomValue, total);
      int cursor = Math.max(0, NoDrop);
      if (roll < cursor) return null;
      return selectItem(roll - cursor);
    }

    /** Returns one raw item/child-TC token using only the Item/Prob weights. */
    public String selectItem(int randomValue) {
      int total = itemProbability();
      if (total <= 0) return null;
      int roll = Math.floorMod(randomValue, total);
      int cursor = 0;
      if (Item == null || Prob == null) return null;
      int count = Math.min(Item.length, Prob.length);
      for (int i = 0; i < count; i++) {
        cursor += Math.max(0, Prob[i]);
        if (roll < cursor) {
          String item = Item[i];
          return item == null || item.isEmpty() ? null : item;
        }
      }
      return null;
    }
  }

  @Override
  protected void init() {
    for (Entry entry : this) {
      // DATATBLS_LoadTreasureClassExTxt changes zero picks to one.
      if (entry.Picks == 0) entry.Picks = 1;
    }
  }

  /** Mirrors DATATBLS_GetTreasureClassExRecordFromIdAndLevel. */
  public Entry getForLevel(int id, int level) {
    Entry selected = get(id);
    if (selected == null || level <= 0 || selected.Group == 0) return selected;
    for (int nextId = id + 1; ; nextId++) {
      Entry next = get(nextId);
      if (next == null || next.Group != selected.Group || next.Level > level) break;
      selected = next;
    }
    return selected;
  }

  /** Mirrors DATATBLS_GetTreasureClassExRecordFromActAndDifficulty. */
  public Entry getChest(int difficulty, int act, int tier) {
    int safeDifficulty = Math.max(0, Math.min(difficulty, 2));
    int safeAct = Math.max(0, Math.min(act, 4));
    int safeTier = Math.max(0, Math.min(tier, 2));
    String name = "Act " + (safeAct + 1) + DIFFICULTY_SUFFIX[safeDifficulty]
        + " Chest " + CHEST_VARIANT[safeTier];
    return get(name);
  }
}
