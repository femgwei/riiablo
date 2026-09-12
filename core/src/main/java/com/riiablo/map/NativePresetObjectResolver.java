package com.riiablo.map;

import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.engine.server.quest.Act2TombSelection;

/**
 * Resolves the object class carried by a D2MOO preset unit.
 *
 * <p>D2Common keeps DS1 object preset indices separate from Objects.txt class
 * ids.  D2Game then dispatches the reserved ids 573..582 to special spawn
 * routines.  Keeping this logic outside MapManager makes the bridge testable
 * without loading the game world.</p>
 */
public final class NativePresetObjectResolver {
  public enum Kind {
    ORDINARY,
    SKIP,
    SHRINE,
    SPECIAL_CHEST,
    PRESET_CHEST,
    ARCANE_SYMBOL
  }

  public static final class Resolution {
    public final int classId;
    public final Kind kind;

    private Resolution(int classId, Kind kind) {
      this.classId = classId;
      this.kind = kind;
    }

    public boolean shouldCreate() {
      return kind != Kind.SKIP && classId >= 0;
    }

    @Override
    public String toString() {
      return "Resolution{" + kind + ", classId=" + classId + '}';
    }
  }

  /**
   * Returns whether the preset provenance requires the authoritative object
   * interaction path in a multiplayer game.  Native D2Game special and
   * preset chests consume the object-region RNG and create shared ground
   * items, so allowing them to be opened locally would desynchronise peers.
   */
  public static boolean isNetworkObject(Kind kind) {
    return kind == Kind.SPECIAL_CHEST || kind == Kind.PRESET_CHEST;
  }

  private static final int INVALID_OBJECT = 573;
  private static final int FIRST_SPECIAL = 574;
  private static final int LAST_SPECIAL = 582;
  private static final int DESERT_SHRINE = 136;
  private static final int GENERIC_CHEST = 371;
  private static final int ARCANE_THING_FALLBACK = 307;
  private static final int TOWER_CELLAR_LEVEL_5 = 25;

  // D2Game::OBJECTS_SpawnPresetChest, Act I/default branch.
  private static final int[] ACT1_PRESET_CHESTS = {
      5, 6, 139, 140, 141, 144, 176, 177, 198, 240, 241, 242, 243
  };

  private NativePresetObjectResolver() {}

  /** Resolves an already class-resolved preset id (not an Obj.txt index). */
  public static Resolution resolve(int act, int levelId, int classId,
      int seed, int localX, int localY) {
    if (classId == INVALID_OBJECT) {
      return new Resolution(-1, Kind.SKIP);
    }
    if (classId >= FIRST_SPECIAL && classId <= LAST_SPECIAL) {
      switch (classId) {
        case 574: case 575: case 576: case 577: case 578: case 579:
          // D2Game::OBJECTS_SpawnShrine chooses the concrete shrine subtype
          // using the unit seed, but the renderable Objects.txt row is fixed.
          return new Resolution(DESERT_SHRINE, Kind.SHRINE);
        case 580:
          return new Resolution(resolveSpecialChest(levelId, seed, localX, localY),
              Kind.SPECIAL_CHEST);
        case 581:
          return new Resolution(resolvePresetChest(act, levelId, seed, localX, localY),
              Kind.PRESET_CHEST);
        case 582:
          // D2Game::ACT2Q6_GetObjectIdForArcaneThing allocates the six symbol
          // ids after excluding the staff tomb.  Preserve the fallback for
          // non-Act-II levels and for incomplete/headless table data.
          if (levelId >= D2LevelIds.LEVEL_TALRASHASTOMB1
              && levelId <= D2LevelIds.LEVEL_TALRASHASTOMB7) {
            int classIdForTomb = Act2TombSelection.forGameSeed(seed)
                .arcaneSymbolObjectFor(levelId);
            if (classIdForTomb >= 0) {
              return new Resolution(classIdForTomb, Kind.ARCANE_SYMBOL);
            }
            // The staff tomb deliberately has no Arcane Symbol.  Do not use
            // the generic fallback here: creating object 307 would duplicate
            // the native tomb layout and expose an invalid interaction.
            return new Resolution(-1, Kind.SKIP);
          }
          return new Resolution(ARCANE_THING_FALLBACK, Kind.ARCANE_SYMBOL);
        default:
          break;
      }
    }
    return new Resolution(classId, Kind.ORDINARY);
  }

  private static int resolveSpecialChest(int levelId, int seed, int x, int y) {
    // D2Game::OBJECTS_SpawnSpecialChest passes OBJECT_CHEST for Tower Cellar
    // level 5 and uses the normal preset-chest distribution elsewhere.
    return levelId == TOWER_CELLAR_LEVEL_5
        ? GENERIC_CHEST
        : resolvePresetChest(1, levelId, seed, x, y);
  }

  private static int resolvePresetChest(int act, int levelId, int seed, int x, int y) {
    // Act I is the active migration scope.  Other acts retain a safe generic
    // chest until their D2Game object-control seed is bridged.
    if (act != 1) return GENERIC_CHEST;
    int hash = seed;
    hash = 31 * hash + levelId;
    hash = 31 * hash + x;
    hash = 31 * hash + y;
    return ACT1_PRESET_CHESTS[(hash & 0x7FFFFFFF) % ACT1_PRESET_CHESTS.length];
  }
}
