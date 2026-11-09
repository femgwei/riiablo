package com.riiablo.engine.server;

import com.riiablo.engine.Engine;

/** Player sequences 13/14 in D2Common/DataTbls/SequenceTbls.cpp. */
public final class NativePlayerLeapSequence {
  public static final int TAKEOFF = 5;
  public static final int FLIGHT = 10;
  public static final int LAND = 11;
  private NativePlayerLeapSequence() {}

  public static int frameCount(boolean attack, byte weapon) {
    if (!attack) return 15;
    if (weapon == Engine.WEAPON_BOW || weapon == Engine.WEAPON_1HT) return 25;
    if (weapon == Engine.WEAPON_1HS || weapon == Engine.WEAPON_XBW) return 28;
    if (weapon == Engine.WEAPON_STF) return 27;
    return 22;
  }

  public static byte mode(int point) {
    return point < 15 ? Engine.Player.MODE_S1 : Engine.Player.MODE_A1;
  }

  public static int sourceFrame(int point, byte weapon) {
    if (point < 15) return point;
    return point - 15 + (frameCount(true, weapon) == 22 ? 5 : 6);
  }

  public static int attackPoint(byte weapon) {
    if (weapon == Engine.WEAPON_1HS || weapon == Engine.WEAPON_XBW) return 18;
    if (weapon == Engine.WEAPON_STF) return 17;
    return 16;
  }
}
