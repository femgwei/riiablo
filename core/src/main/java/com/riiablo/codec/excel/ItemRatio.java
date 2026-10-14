package com.riiablo.codec.excel;

/** Native ItemRatio.txt quality denominators. */
@Excel.Binned
public class ItemRatio extends Excel<ItemRatio.Entry> {
  public static class Entry extends Excel.Entry {
    @Key @Column public String Function;
    @Column(format = "Unique") public int Unique;
    @Column(format = "UniqueDivisor") public int UniqueDivisor;
    @Column(format = "UniqueMin") public int UniqueMin;
    @Column(format = "Rare") public int Rare;
    @Column(format = "RareDivisor") public int RareDivisor;
    @Column(format = "RareMin") public int RareMin;
    @Column(format = "Set") public int Set;
    @Column(format = "SetDivisor") public int SetDivisor;
    @Column(format = "SetMin") public int SetMin;
    @Column(format = "Magic") public int Magic;
    @Column(format = "MagicDivisor") public int MagicDivisor;
    @Column(format = "MagicMin") public int MagicMin;
    @Column(format = "HiQuality") public int HiQuality;
    @Column(format = "HiQualityDivisor") public int HiQualityDivisor;
    @Column(format = "Normal") public int Normal;
    @Column(format = "NormalDivisor") public int NormalDivisor;
    @Column(format = "Version") public int Version;
    @Column(format = "Uber") public boolean Uber;
    @Column(format = "Class Specific") public boolean ClassSpecific;
  }

  /** Mirrors DATATBLS_GetItemRatioTxtRecord's newest-compatible row choice. */
  public Entry get(ItemEntry base, ItemTypes.Entry type, int version) {
    if (base == null) return null;
    // ITEMS_GetQuestFromItemId (despite its name) selects upgraded equipment,
    // excluding quest items and missile potions, not the quest flag itself.
    boolean uber = (base instanceof Weapons.Entry || base instanceof Armor.Entry)
        && base.quest == 0 && !"mpot".equals(base.type) && base.code != null
        && (base.code.equals(base.ubercode) || base.code.equals(base.ultracode));
    boolean classSpecific = type != null && type.Class != null && !type.Class.isEmpty();
    Entry selected = null;
    int selectedVersion = -1;
    int selectedRow = -1;
    for (com.badlogic.gdx.utils.IntMap.Entry<Entry> row : entries.entries()) {
      Entry entry = row.value;
      if (entry.Uber != uber || entry.ClassSpecific != classSpecific
          || entry.Version > version || entry.Version < selectedVersion) continue;
      // IntMap iteration is not source order; native equal-version ties use
      // the last source row (the loader assigns numeric row ids).
      if (entry.Version == selectedVersion && row.key < selectedRow) continue;
      selected = entry;
      selectedVersion = entry.Version;
      selectedRow = row.key;
    }
    return selected;
  }
}
