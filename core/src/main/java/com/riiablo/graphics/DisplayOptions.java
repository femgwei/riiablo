package com.riiablo.graphics;

import com.riiablo.Client;

/** Shared display option semantics used by the video options menu. */
public final class DisplayOptions {
  private DisplayOptions() {}

  public static byte normalizeFpsMode(byte mode) {
    return mode < Client.FPS_NONE || mode > Client.FPS_MAX ? Client.FPS_NONE : mode;
  }

  public static byte nextFpsMode(byte mode) {
    return (byte) ((normalizeFpsMode(mode) + 1) % (Client.FPS_MAX + 1));
  }

  public static String fpsModeLabel(byte mode) {
    switch (normalizeFpsMode(mode)) {
      case Client.FPS_TOPLEFT: return "TOP LEFT";
      case Client.FPS_TOPRIGHT: return "TOP RIGHT";
      case Client.FPS_BOTTOMLEFT: return "BOTTOM LEFT";
      case Client.FPS_BOTTOMRIGHT: return "BOTTOM RIGHT";
      case Client.FPS_NONE:
      default: return "OFF";
    }
  }
}
