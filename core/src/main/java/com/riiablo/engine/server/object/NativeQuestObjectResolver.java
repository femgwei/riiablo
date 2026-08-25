package com.riiablo.engine.server.object;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

/** Identifies Act I quest objects whose lifecycle is owned by quest scripts. */
public final class NativeQuestObjectResolver {
  public enum Type {
    NONE(false, false, Engine.Object.MODE_NU),
    TOWER_TOME(true, true, Engine.Object.MODE_ON),
    CAIRN_STONE(false, true, Engine.Object.MODE_ON),
    CAIN_GIBBET(false, true, Engine.Object.MODE_S1),
    INIFUSS_TREE(false, true, Engine.Object.MODE_ON),
    HORADRIC_MALUS(false, true, Engine.Object.MODE_ON),
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
  public static final int LAST_CAIRN_STONE = 22;
  public static final int CAIN_GIBBET = 26;
  public static final int INIFUSS_TREE = 30;
  public static final int HORADRIC_MALUS = 108;

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
      default:
        // Countess room emitters share InitFn 47 and are registered with the
        // quest even though they are not ordinary loot containers.
        return object.InitFn == 47 ? Type.COUNTESS_CHEST : Type.NONE;
    }
  }
}
