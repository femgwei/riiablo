package com.riiablo.engine.client.automap;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.monster.MonsterRank;

/** Pure classification and distant-pointer rules for enhanced Automap markers. */
public final class AutomapMarkerPolicy {
  public static final float POINTER_THRESHOLD = 180f;
  public static final float POINTER_RADIUS = 150f;

  private AutomapMarkerPolicy() {}

  public static int monsterType(int rank) {
    switch (rank) {
      case MonsterRank.CHAMPION: return AutomapIconType.CHAMPION;
      case MonsterRank.UNIQUE:
      case MonsterRank.SUPER_UNIQUE: return AutomapIconType.UNIQUE;
      case MonsterRank.MINION: return AutomapIconType.MINION;
      case MonsterRank.BOSS: return AutomapIconType.BOSS;
      default: return AutomapIconType.MONSTER;
    }
  }

  /** Native Automap excludes neutral presentation monsters and ambient critters. */
  public static boolean shouldDisplayMonster(
      MonStats.Entry monster, MonStats2.Entry visual, boolean npc) {
    if (monster == null) return false;
    if (npc) return true;
    if (monster.Align != 0 || !monster.killable) return false;
    if (visual != null && (visual.noMap || visual.critter)) return false;
    return !isDecorativeCreature(monster.Id)
        && !isDecorativeCreature(monster.BaseId)
        && !isDecorativeCreature(monster.Code);
  }

  static boolean isDecorativeCreature(String id) {
    if (id == null || id.isEmpty()) return false;
    String normalized = id.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals("chicken")
        || normalized.equals("frog")
        || normalized.equals("rat")
        || normalized.equals("smallbird")
        || normalized.equals("largebird")
        || normalized.equals("bird")
        || normalized.equals("bat")
        || normalized.equals("cow")
        || normalized.equals("camel")
        || normalized.equals("bunny")
        || normalized.equals("critter");
  }

  public static int objectType(Objects.Entry object) {
    if (object == null) return AutomapIconType.OBJECT;
    if (object.OpenWarp) return AutomapIconType.ENTRANCE;
    return isQuestCell(object.AutoMap) ? AutomapIconType.QUEST : AutomapIconType.OBJECT;
  }

  /** Ordinary AutoMap=0 scenery has no marker; explicit enhanced targets do. */
  public static boolean shouldDisplayObject(int nativeCell, int markerType) {
    return nativeCell >= 0 || markerType != AutomapIconType.OBJECT;
  }

  /** HackMap adds a blob only for closed, selectable container-style objects. */
  public static boolean shouldDisplayObject(int nativeCell, int markerType,
      Objects.Entry object, int mode, boolean hackMapEnabled) {
    return shouldDisplayObject(nativeCell, markerType)
        || (hackMapEnabled && isHackMapChest(object, mode));
  }

  static boolean isHackMapChest(Objects.Entry object, int mode) {
    if (object == null || mode != 0 || object.Selectable == null
        || object.Selectable.length == 0 || !object.Selectable[0]) return false;
    switch (object.OperateFn) {
      case 1:  // bed, grave, casket, sarcophagus
      case 3:  // basket, urn, rock pile
      case 4:  // chest/corpse containers
      case 5:  // barrel
      case 7:  // exploding barrel
      case 14: // loose boulder
      case 19: // armor stand
      case 20: // weapon rack
      case 33: // writ
      case 48: // trapped soul
      case 51: // stash
      case 68: // evil urn
        return true;
      default:
        return false;
    }
  }

  public static boolean isPointerTarget(int type) {
    return type == AutomapIconType.QUEST || type == AutomapIconType.ENTRANCE
        || type == AutomapIconType.EXIT;
  }

  /** Enhanced categories remain visible as a color overlay above a native cell. */
  public static boolean requiresColorOverlay(int type) {
    return type == AutomapIconType.CHAMPION || type == AutomapIconType.UNIQUE
        || type == AutomapIconType.MINION || type == AutomapIconType.BOSS
        || type == AutomapIconType.QUEST || type == AutomapIconType.ENTRANCE
        || type == AutomapIconType.EXIT;
  }

  /**
   * Compresses a distant target toward the source while retaining its direction.
   * Returns true when the output is a pointer rather than the target's real position.
   */
  public static boolean projectPointer(Vector2 source, Vector2 target, boolean enabled,
      Vector2 out) {
    return projectPointer(source, target, enabled, POINTER_THRESHOLD, POINTER_RADIUS, out);
  }

  public static boolean projectPointer(Vector2 source, Vector2 target, boolean enabled,
      float threshold, float radius, Vector2 out) {
    out.set(target);
    if (!enabled || source == null || target == null) return false;
    float distance = source.dst(target);
    if (distance <= threshold) return false;
    out.set(target).sub(source).setLength(radius).add(source);
    return true;
  }

  static boolean isQuestCell(int cell) {
    switch (cell) {
      case AutomapIconType.CAIN_CAGE:
      case AutomapIconType.MEPH_ORB:
      case AutomapIconType.DIABLO_SEAL:
      case AutomapIconType.INI_TREE:
      case AutomapIconType.CAIRN_STONE:
      case AutomapIconType.GIDBINN:
      case AutomapIconType.QUEST_HAMMER:
      case AutomapIconType.QUEST_CHEST:
      case AutomapIconType.ARCANE_PORTAL:
      case AutomapIconType.BOOK:
        return true;
      default:
        return false;
    }
  }
}
