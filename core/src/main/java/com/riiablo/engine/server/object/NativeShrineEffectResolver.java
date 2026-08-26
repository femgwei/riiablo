package com.riiablo.engine.server.object;

import com.riiablo.codec.excel.Shrines;

/**
 * Classifies a row from {@code Shrines.txt} using the native D2Game shrine
 * dispatch table.  This class deliberately has no gameplay dependencies: the
 * object layer publishes the result and the future portal, item, monster and
 * combat systems can consume it independently.
 */
public final class NativeShrineEffectResolver {
  public static final int UNKNOWN = 0;
  public static final int BASIC_HEALTH_MANA = 1;
  public static final int PORTAL = 2;
  public static final int GEM = 3;
  public static final int STORM = 4;
  public static final int MONSTER = 5;
  public static final int EXPLODING = 6;
  public static final int POISON = 7;
  public static final int COMBAT_BUFF = 8;
  public static final int DEFENSIVE_BUFF = 9;
  public static final int SKILL_BUFF = 10;
  public static final int STAMINA = 11;

  private NativeShrineEffectResolver() {}

  /** Resolves a table row without applying any effect. */
  public static Effect resolve(Shrines.Entry shrine) {
    if (shrine == null) return Effect.unknown();
    return new Effect(shrine.Code, kindForCode(shrine.Code), shrine.EffectClass,
        shrine.Arg0, shrine.Arg1, Math.max(0, shrine.DurationInFrames));
  }

  /** Maps the {@code gpShrineTable} code in D2Game's ObjMode.cpp. */
  public static int kindForCode(int code) {
    switch (code) {
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
        return BASIC_HEALTH_MANA;
      case 6:
      case 8:
      case 9:
      case 10:
      case 11:
      case 13:
      case 15:
        return DEFENSIVE_BUFF;
      case 7:
        return COMBAT_BUFF;
      case 12:
        return SKILL_BUFF;
      case 14:
        return STAMINA;
      case 17:
        return PORTAL;
      case 18:
        return GEM;
      case 19:
        return STORM;
      case 20:
        return MONSTER;
      case 21:
        return EXPLODING;
      case 22:
        return POISON;
      default:
        // Code 16 is the native Enirhs easter egg and code 23 is the table
        // terminator; neither has a supported effect bridge yet.
        return UNKNOWN;
    }
  }

  /** Immutable, event-friendly description of one native shrine effect. */
  public static final class Effect {
    public final int code;
    public final int kind;
    public final int effectClass;
    public final int arg0;
    public final int arg1;
    public final int durationFrames;

    private Effect(int code, int kind, int effectClass, int arg0, int arg1,
        int durationFrames) {
      this.code = code;
      this.kind = kind;
      this.effectClass = effectClass;
      this.arg0 = arg0;
      this.arg1 = arg1;
      this.durationFrames = durationFrames;
    }

    private static Effect unknown() {
      return new Effect(0, UNKNOWN, 0, 0, 0, 0);
    }
  }
}
