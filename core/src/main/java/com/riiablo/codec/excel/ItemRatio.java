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
    boolean quest = base.quest != 0;
    boolean classSpecific = type != null && type.Class != null && !type.Class.isEmpty();
    Entry selected = null;
    int selectedVersion = -1;
    for (Entry entry : this) {
      if (entry.Uber != quest || entry.ClassSpecific != classSpecific
          || entry.Version > version || entry.Version < selectedVersion) continue;
      selected = entry;
      selectedVersion = entry.Version;
    }
    return selected;
  }
}
