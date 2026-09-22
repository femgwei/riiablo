package com.riiablo.item;

import com.riiablo.CharacterClass;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.save.CharData;

/**
 * Client-side view of the native item usability checks.  This intentionally
 * reads the same item requirement stats that are shown in the tooltip, while
 * keeping the result independent from any particular inventory widget.
 */
public final class ItemRequirements {
  private ItemRequirements() {}

  public static final class Result {
    public final int requiredLevel;
    public final int requiredStrength;
    public final int requiredDexterity;
    public final boolean levelMet;
    public final boolean strengthMet;
    public final boolean dexterityMet;
    public final boolean classMet;

    private Result(int requiredLevel, int requiredStrength, int requiredDexterity,
        boolean levelMet, boolean strengthMet, boolean dexterityMet, boolean classMet) {
      this.requiredLevel = requiredLevel;
      this.requiredStrength = requiredStrength;
      this.requiredDexterity = requiredDexterity;
      this.levelMet = levelMet;
      this.strengthMet = strengthMet;
      this.dexterityMet = dexterityMet;
      this.classMet = classMet;
    }

    public boolean usable() {
      return levelMet && strengthMet && dexterityMet && classMet;
    }
  }

  public static Result check(Item item, CharData character) {
    if (item == null) return new Result(0, 0, 0, true, true, true, true);

    int level = 0;
    int strength = 0;
    int dexterity = 0;
    if (character != null && character.getStats() != null) {
      level = character.getStats().aggregate().getValue(Stat.level, character.level & 0xFF);
      strength = character.getStats().aggregate().getValue(Stat.strength, 0);
      dexterity = character.getStats().aggregate().getValue(Stat.dexterity, 0);
    }

    int requiredLevel = value(item, Stat.item_levelreq, item.base == null ? 0 : item.base.levelreq);
    int requiredStrength = value(item, Stat.reqstr, 0);
    int requiredDexterity = value(item, Stat.reqdex, 0);
    return new Result(
        requiredLevel,
        requiredStrength,
        requiredDexterity,
        level >= requiredLevel,
        strength >= requiredStrength,
        dexterity >= requiredDexterity,
        classMatches(item, character));
  }

  private static int value(Item item, short stat, int fallback) {
    if (item.attrs != null) {
      StatRef aggregate = item.attrs.get(stat);
      if (aggregate != null) return Math.max(0, aggregate.asInt());
      StatRef base = item.attrs.base().get(stat);
      if (base != null) return Math.max(0, base.asInt());
    }
    return Math.max(0, fallback);
  }

  /** Returns the class required by the base ItemTypes row, if one exists. */
  public static CharacterClass requiredClass(Item item) {
    if (item == null) return null;
    if (item.typeEntry != null && item.typeEntry.Class != null
        && !item.typeEntry.Class.trim().isEmpty()) {
      try {
        return CharacterClass.get(item.typeEntry.Class.trim());
      } catch (RuntimeException ignored) {
        // Fall through to the serialized classOnly field.
      }
    }
    int classOnly = item.classOnly & 0xFFFF;
    return item.classOnly >= 0 && classOnly < CharacterClass.values().length
        ? CharacterClass.get(classOnly) : null;
  }

  private static boolean classMatches(Item item, CharData character) {
    if (character == null || character.classId == null) return true;
    int classId = character.classId.id;

    if (item.classOnly != Item.NO_CLASS_ONLY) {
      int classOnly = item.classOnly & 0xFFFF;
      // The D2S field stores the character-class id, not a class bitset.
      if (classOnly != classId) return false;
    }

    CharacterClass required = requiredClass(item);
    return required == null || required.id == classId;
  }
}
