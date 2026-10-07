package com.riiablo.item;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MagicAffix;
import com.riiablo.codec.excel.SetItems;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.UniqueItems;
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

    int requiredLevel = requiredLevel(item, character == null ? null : character.classId);
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

  /** Mirrors D2Common's ITEMS_GetRequiredLevel for item data represented by {@link Item}. */
  static int requiredLevel(Item item, CharacterClass characterClass) {
    if (item == null) return 0;

    int required = qualityRequiredLevel(item, characterClass);
    if (item.base != null) required = Math.max(required, item.base.levelreq);
    for (Item socket : item.sockets) {
      required = Math.max(required, requiredLevel(socket, characterClass));
    }
    required = Math.max(required, skillRequiredLevel(item, characterClass));

    // Riiablo keeps Items.txt's levelreq in the base stat list for legacy UI
    // consumers. Native STAT_ITEM_LEVELREQ is only the serialized modifier,
    // so read it from property lists instead of adding that base copy twice.
    required += serializedLevelAdjustment(item);
    return Math.max(0, required);
  }

  private static int qualityRequiredLevel(Item item, CharacterClass characterClass) {
    if (item.quality == null || Riiablo.files == null) return 0;
    switch (item.quality) {
      case MAGIC:
        return Math.max(
            affixRequiredLevel(Riiablo.files.MagicPrefix.get(
                item.qualityId & Item.MAGIC_AFFIX_MASK), characterClass),
            affixRequiredLevel(Riiablo.files.MagicSuffix.get(
                item.qualityId >>> Item.MAGIC_AFFIX_SIZE), characterClass));
      case RARE:
      case CRAFTED:
        if (!(item.qualityData instanceof RareQualityData)) return 0;
        RareQualityData rare = (RareQualityData) item.qualityData;
        int required = 0;
        int craftBonus = item.quality == Quality.CRAFTED ? 10 : 0;
        for (int i = 0; i < RareQualityData.NUM_AFFIXES; i++) {
          MagicAffix prefix = Riiablo.files.MagicPrefix.get(rare.prefixes[i]);
          MagicAffix suffix = Riiablo.files.MagicSuffix.get(rare.suffixes[i]);
          if (prefix != null) {
            required = Math.max(required, affixRequiredLevel(prefix, characterClass));
            if (item.quality == Quality.CRAFTED) craftBonus += 3;
          }
          if (suffix != null) {
            required = Math.max(required, affixRequiredLevel(suffix, characterClass));
            if (item.quality == Quality.CRAFTED) craftBonus += 3;
          }
        }
        return item.quality == Quality.CRAFTED
            ? Math.min(98, required + craftBonus) : required;
      case UNIQUE:
        return item.qualityData instanceof UniqueItems.Entry
            ? Math.max(0, ((UniqueItems.Entry) item.qualityData).lvl_req) : 0;
      case SET:
        return item.qualityData instanceof SetItems.Entry
            ? Math.max(0, ((SetItems.Entry) item.qualityData).lvl_req) : 0;
      default:
        return 0;
    }
  }

  private static int affixRequiredLevel(MagicAffix affix, CharacterClass characterClass) {
    if (affix == null) return 0;
    if (characterClass != null && affix._class != null && !affix._class.isEmpty()) {
      try {
        if (CharacterClass.get(affix._class.toLowerCase()).id == characterClass.id) {
          return Math.max(0, affix.classlevelreq);
        }
      } catch (RuntimeException ignored) {
        // Invalid class data falls back to the general affix requirement.
      }
    }
    return Math.max(0, affix.levelreq);
  }

  private static int skillRequiredLevel(Item item, CharacterClass characterClass) {
    if (item.attrs == null || Riiablo.files == null || Riiablo.files.skills == null) return 0;
    int required = 0;
    for (int i = 0; i < item.attrs.list().numLists(); i++) {
      StatListRef list = item.attrs.list(i);
      for (StatRef stat : list) {
        if (stat.id() != Stat.item_singleskill && stat.id() != Stat.item_nonclassskill) continue;
        Skills.Entry skill = Riiablo.files.skills.get(stat.param0());
        if (skill == null) continue;
        int skillRequirement = skill.reqlevel;
        if (stat.id() == Stat.item_nonclassskill
            && (characterClass == null
                || Skills.getClassId(skill.charclass) != characterClass.id)) {
          skillRequirement += 6;
        }
        required = Math.max(required, skillRequirement);
      }
    }
    return required;
  }

  private static int serializedLevelAdjustment(Item item) {
    if (item.attrs == null) return 0;
    int adjustment = 0;
    for (int i = 0; i < item.attrs.list().numLists(); i++) {
      for (StatRef stat : item.attrs.list(i)) {
        if (stat.id() == Stat.item_levelreq) adjustment += stat.asInt();
      }
    }
    return adjustment;
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
