package com.riiablo.engine.server;

import com.riiablo.engine.Engine;

/**
 * The hard-coded player Whirlwind sequence from D2Common's
 * {@code gPlayerSequenceWhirlwind} table.  The sequence cursor has eight
 * points; each point selects a source frame and can emit a melee event.
 */
public final class NativeWhirlwindSequence {
  public static final byte EVENT_NONE = 0;
  public static final byte EVENT_MELEE_ATTACK = 1;

  private static final byte[] MODES = {
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1, Engine.Player.MODE_A1,
      Engine.Player.MODE_A1, Engine.Player.MODE_A1
  };
  private static final byte[] FRAMES = {0, 1, 2, 3, 3, 4, 5, 6};
  private static final byte[] EVENTS = {
      EVENT_NONE, EVENT_NONE, EVENT_NONE, EVENT_MELEE_ATTACK,
      EVENT_NONE, EVENT_NONE, EVENT_NONE, EVENT_MELEE_ATTACK
  };

  private NativeWhirlwindSequence() {}

  public static int frameCount() { return FRAMES.length; }

  public static byte mode(int point) { return MODES[check(point)]; }

  public static byte sourceFrame(int point) { return FRAMES[check(point)]; }

  public static byte event(int point) { return EVENTS[check(point)]; }

  private static int check(int point) {
    if (point < 0 || point >= FRAMES.length) throw new IndexOutOfBoundsException("point=" + point);
    return point;
  }
}
