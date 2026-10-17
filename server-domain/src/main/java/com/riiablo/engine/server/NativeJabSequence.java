package com.riiablo.engine.server;

import com.riiablo.engine.Engine;

/**
 * The hard-coded player Jab sequence from D2Common/DataTbls/SequenceTbls.cpp.
 * The sequence cursor is separate from the current COF frame: each point
 * selects a mode and source frame, and the MELEE_ATTACK points produce the
 * three authoritative hit events.
 */
public final class NativeJabSequence {
  static final byte EVENT_NONE = 0;
  static final byte EVENT_MELEE_ATTACK = 1;

  private static final byte[] HTH_MODES = {
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1
  };
  private static final byte[] HTH_FRAMES = {
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
  };
  private static final byte[] HTH_EVENTS = {
      0, 0, 0, 0, 0, 0, 0, 0, EVENT_MELEE_ATTACK, 0, 0, 0, 0
  };

  // D2MOO calls this record Jab_BOW; it is the 1HT player weapon class.
  private static final byte[] ONE_HAND_THRUST_MODES = {
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A2, Engine.Player.MODE_A2,
      Engine.Player.MODE_A2, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A2, Engine.Player.MODE_A2,
      Engine.Player.MODE_A2, Engine.Player.MODE_A2, Engine.Player.MODE_A2
  };
  private static final byte[] ONE_HAND_THRUST_FRAMES = {
      5, 6, 8, 9, 10, 11, 13, 6, 8, 9, 10, 11, 13, 6, 8, 9, 10, 13
  };
  private static final byte[] ONE_HAND_THRUST_EVENTS = {
      0, 0, 0, EVENT_MELEE_ATTACK, 0, 0, 0, 0, 0,
      EVENT_MELEE_ATTACK, 0, 0, 0, 0, 0, EVENT_MELEE_ATTACK, 0, 0
  };

  // D2MOO calls this record Jab_1HS; it is the 2HT player weapon class.
  private static final byte[] TWO_HAND_THRUST_MODES = {
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A2, Engine.Player.MODE_A2,
      Engine.Player.MODE_A2, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A2, Engine.Player.MODE_A2,
      Engine.Player.MODE_A2, Engine.Player.MODE_A2, Engine.Player.MODE_A2,
      Engine.Player.MODE_A2, Engine.Player.MODE_A2, Engine.Player.MODE_A2
  };
  private static final byte[] TWO_HAND_THRUST_FRAMES = {
      2, 7, 9, 10, 12, 13, 15, 4, 6, 9, 12, 13, 15, 4, 6, 9, 10, 11, 13, 15
  };
  private static final byte[] TWO_HAND_THRUST_EVENTS = {
      0, 0, 0, EVENT_MELEE_ATTACK, 0, 0, 0,
      0, 0, 0, EVENT_MELEE_ATTACK, 0, 0,
      0, 0, 0, EVENT_MELEE_ATTACK, 0, 0, 0, 0
  };

  private NativeJabSequence() {}

  public static int frameCount(byte weaponClass) {
    return data(weaponClass).modes.length;
  }

  public static byte mode(byte weaponClass, int frame) {
    return data(weaponClass).modes[frame];
  }

  public static byte sourceFrame(byte weaponClass, int frame) {
    return data(weaponClass).frames[frame];
  }

  public static byte event(byte weaponClass, int frame) {
    return data(weaponClass).events[frame];
  }

  private static Data data(byte weaponClass) {
    if (weaponClass == Engine.WEAPON_HTH) {
      return new Data(HTH_MODES, HTH_FRAMES, HTH_EVENTS);
    }
    if (weaponClass == Engine.WEAPON_2HT
        || weaponClass == Engine.WEAPON_2HS
        || weaponClass == Engine.WEAPON_HT2) {
      return new Data(TWO_HAND_THRUST_MODES, TWO_HAND_THRUST_FRAMES, TWO_HAND_THRUST_EVENTS);
    }
    return new Data(ONE_HAND_THRUST_MODES, ONE_HAND_THRUST_FRAMES, ONE_HAND_THRUST_EVENTS);
  }

  private static final class Data {
    final byte[] modes;
    final byte[] frames;
    final byte[] events;

    Data(byte[] modes, byte[] frames, byte[] events) {
      this.modes = modes;
      this.frames = frames;
      this.events = events;
    }
  }
}
