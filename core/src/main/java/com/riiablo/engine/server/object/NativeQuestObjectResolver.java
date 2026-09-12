package com.riiablo.engine.server.object;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

/** Identifies quest objects whose lifecycle is owned by native quest scripts. */
public final class NativeQuestObjectResolver {
  public enum Type {
    NONE(false, false, Engine.Object.MODE_NU),
    TOWER_TOME(true, true, Engine.Object.MODE_ON),
    CAIRN_STONE(false, true, Engine.Object.MODE_ON),
    CAIN_GIBBET(false, true, Engine.Object.MODE_S1),
    INIFUSS_TREE(false, true, Engine.Object.MODE_ON),
    HORADRIC_MALUS(false, true, Engine.Object.MODE_ON),
    TAINTED_SUN_ALTAR(false, true, Engine.Object.MODE_ON),
    HORADRIC_ORIFICE(false, true, Engine.Object.MODE_ON),
    ARCANE_SANCTUARY_TOME(false, true, Engine.Object.MODE_ON),
    GIDBINN_DECOY(false, true, Engine.Object.MODE_OP),
    KHALIM_CHEST(false, true, Engine.Object.MODE_ON),
    COMPELLING_ORB(false, false, Engine.Object.MODE_OP),
    DIABLO_SEAL(false, true, Engine.Object.MODE_ON),
    HELLFORGE(false, false, Engine.Object.MODE_ON),
    COUNTESS_CHEST(false, false, Engine.Object.MODE_NU);

    /** Safe without consulting a quest record (only the Countess tome). */
    public final boolean defaultActivation;
    /** Removes selection after an accepted operation until a quest resets it. */
    public final boolean oneShot;
    public final byte suggestedMode;

    Type(boolean defaultActivation, boolean oneShot, int suggestedMode) {
      this.defaultActivation = defaultActivation;
      this.oneShot = oneShot;
      this.suggestedMode = (byte) suggestedMode;
    }
  }

  public static final int TOWER_TOME = 8;
  public static final int FIRST_CAIRN_STONE = 17;
  // StoneTheta (22) uses the same native handler, although A1Q4's generated
  // five-stone solution contains only class ids 17..21.
  public static final int LAST_CAIRN_STONE = 22;
  public static final int CAIN_GIBBET = 26;
  public static final int INIFUSS_TREE = 30;
  public static final int HORADRIC_MALUS = 108;
  public static final int TAINTED_SUN_ALTAR = 149;
  public static final int HORADRIC_ORIFICE = 152;
  /** Objects.txt OBJECT_YET_ANOTHER_TOME / A2Q4 OperateFn 42. */
  public static final int ARCANE_SANCTUARY_TOME = 357;
  /** Objects.txt Gidbinn decoy / OperateFn 31. */
  public static final int GIDBINN_DECOY = 252;
  /** Native Khalim chest variants / OperateFn 57..59. */
  public static final int KHALIM_CHEST1 = 405;
  public static final int KHALIM_CHEST2 = 406;
  public static final int KHALIM_CHEST3 = 407;
  /** Travincal compelling orb (OperateFn 53). */
  public static final int COMPELLING_ORB = 404;
  /** Chaos Sanctuary seals / OperateFn 34..38. */
  public static final int FIRST_DIABLO_SEAL = 392;
  public static final int LAST_DIABLO_SEAL = 396;
  public static final int HELLFORGE = 376;

  private NativeQuestObjectResolver() {}

  public static Type resolve(Objects.Entry object) {
    if (object == null) return Type.NONE;
    int id = object.Id;
    if (id == TOWER_TOME) return Type.TOWER_TOME;
    if (id >= FIRST_CAIRN_STONE && id <= LAST_CAIRN_STONE) {
      return Type.CAIRN_STONE;
    }
    switch (id) {
      case CAIN_GIBBET: return Type.CAIN_GIBBET;
      case INIFUSS_TREE: return Type.INIFUSS_TREE;
      case HORADRIC_MALUS: return Type.HORADRIC_MALUS;
      case TAINTED_SUN_ALTAR: return Type.TAINTED_SUN_ALTAR;
      case HORADRIC_ORIFICE: return Type.HORADRIC_ORIFICE;
      case ARCANE_SANCTUARY_TOME: return Type.ARCANE_SANCTUARY_TOME;
      case GIDBINN_DECOY: return Type.GIDBINN_DECOY;
      case KHALIM_CHEST1: case KHALIM_CHEST2: case KHALIM_CHEST3:
        return Type.KHALIM_CHEST;
      case COMPELLING_ORB: return Type.COMPELLING_ORB;
      case FIRST_DIABLO_SEAL: case 393: case 394: case 395: case LAST_DIABLO_SEAL:
        return Type.DIABLO_SEAL;
      case HELLFORGE: return Type.HELLFORGE;
      default:
        // Countess room emitters share InitFn 47 and are registered with the
        // quest even though they are not ordinary loot containers.
        return object.InitFn == 47 ? Type.COUNTESS_CHEST : Type.NONE;
    }
  }
}
