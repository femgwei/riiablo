package com.riiablo.item;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.Weapons;

/** Native 1.10f weapon-speed calculation and tooltip description lookup. */
final class NativeWeaponSpeed {
  private NativeWeaponSpeed() {}

  private static final String[] DESCRIPTION_KEYS = {
      "WeaponAttackFastest",
      "WeaponAttackVeryFast",
      "WeaponAttackFast",
      "WeaponAttackNormal",
      "WeaponAttackSlow",
      "WeaponAttackVerySlow",
      "WeaponAttackSlowest",
  };

  /**
   * D2Client's 1.10f table for native attack lengths 10 through 27. Each row
   * contains the five character/weapon animation groups selected below.
   */
  private static final byte[][] SPEED_BUCKETS = {
      {1, 1, 1, 1, 1}, // 10
      {1, 1, 1, 1, 1}, // 11
      {1, 1, 1, 1, 1}, // 12
      {1, 1, 2, 1, 1}, // 13
      {2, 1, 2, 2, 1}, // 14
      {2, 1, 2, 2, 2}, // 15
      {2, 2, 3, 2, 2}, // 16
      {3, 2, 3, 3, 2}, // 17
      {3, 2, 3, 3, 3}, // 18
      {3, 2, 4, 3, 3}, // 19
      {4, 3, 4, 4, 3}, // 20
      {4, 3, 4, 4, 4}, // 21
      {4, 3, 5, 4, 4}, // 22
      {5, 4, 5, 5, 4}, // 23
      {5, 4, 5, 5, 5}, // 24
      {5, 4, 5, 5, 5}, // 25
      {5, 5, 5, 5, 5}, // 26
      {5, 5, 5, 5, 5}, // 27
  };

  /** Normal-weapon and bow/crossbow column offsets, indexed by player class. */
  private static final byte[][] CLASS_OFFSETS = {
      {0, 2}, // Amazon
      {1, 4}, // Sorceress
      {1, 4}, // Necromancer
      {0, 3}, // Paladin
      {0, 3}, // Barbarian
      {1, 4}, // Druid
      {0, 3}, // Assassin
  };

  static String descriptionKey(Item item, CharacterClass characterClass) {
    if (item == null || characterClass == null || item.attrs == null
        || !(item.base instanceof Weapons.Entry)) {
      return DESCRIPTION_KEYS[5];
    }

    Weapons.Entry weapon = item.getBase();
    int attackLength = 45;
    if (Riiablo.anim != null && weapon.wclass != null) {
      com.riiablo.codec.excel.PlrType.Entry playerType = Riiablo.files == null
          || Riiablo.files.PlrType == null ? null : Riiablo.files.PlrType.get(characterClass.id);
      String token = playerType == null ? characterClass.shortName : playerType.Token;
      String cof = token.toUpperCase(java.util.Locale.ROOT)
          + "A1" + weapon.wclass.toUpperCase(java.util.Locale.ROOT);
      D2.Entry animation = Riiablo.anim.getEntry(cof);
      if (animation != null) {
        StatRef fasterAttackRate = item.attrs.get(Stat.item_fasterattackrate);
        int weaponIas = fasterAttackRate == null ? 0 : fasterAttackRate.asInt();
        attackLength = attackLength(
            animation.framesPerDir, animation.speed, weapon.speed, weaponIas);
      }
    }

    boolean bowOrCrossbow = item.type != null
        && (item.type.is(Type.BOW) || item.type.is(Type.XBOW));
    return descriptionKey(characterClass.id, bowOrCrossbow, attackLength);
  }

  /** Mirrors D2Common.10878 (ITEMS_GetWeaponAttackSpeed). */
  static int attackLength(
      int animationFrames, int animationSpeed, int weaponSpeed, int weaponIas) {
    int scaledAnimationSpeed = animationSpeed * (100 - weaponSpeed + weaponIas) / 100;
    if (animationFrames <= 0 || scaledAnimationSpeed <= 0) return 45;
    return (animationFrames << 8) / scaledAnimationSpeed;
  }

  /** Mirrors the seven-description D2Client 1.10f tooltip lookup. */
  static String descriptionKey(int classId, boolean bowOrCrossbow, int attackLength) {
    if (attackLength < 10) return DESCRIPTION_KEYS[1];
    if (attackLength >= 28 || classId < 0 || classId >= CLASS_OFFSETS.length) {
      return DESCRIPTION_KEYS[5];
    }

    int column = CLASS_OFFSETS[classId][bowOrCrossbow ? 1 : 0];
    return DESCRIPTION_KEYS[SPEED_BUCKETS[attackLength - 10][column]];
  }
}
