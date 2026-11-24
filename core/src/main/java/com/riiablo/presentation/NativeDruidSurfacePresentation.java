package com.riiablo.presentation;

import com.riiablo.codec.excel.Missiles;

/** Audited 1.10f Fissure loops and Volcano intro/loop/outro clocks. */
final class NativeDruidSurfacePresentation {
  private NativeDruidSurfacePresentation() {}

  static boolean handles(Missiles.Entry row) {
    if (row == null) return false;
    return "erruption crack 1".equalsIgnoreCase(row.Missile)
        || "erruption smoke 1".equalsIgnoreCase(row.Missile)
        || "volcano".equalsIgnoreCase(row.Missile)
        || "volcano overlay fire".equalsIgnoreCase(row.Missile)
        || "volcano explosion".equalsIgnoreCase(row.Missile)
        || "volcano small fire".equalsIgnoreCase(row.Missile);
  }

  static int frame(Missiles.Entry row, int frames, int lifetime, int age) {
    if (frames <= 0) return 0;
    age = Math.max(0, age);
    if ("erruption crack 1".equalsIgnoreCase(row.Missile)
        || "erruption smoke 1".equalsIgnoreCase(row.Missile)) return age % frames;
    int start = row.SubStart, stop = row.SubStop;
    // SubStop is exclusive. Exit the resident loop early enough to play
    // every remaining cel before the native lifetime expires.
    int tail = Math.max(0, lifetime - frames);
    if (age >= tail + stop) return Math.min(frames - 1, Math.max(0, age - tail));
    if (stop > start && age >= stop) return start + (age - start) % (stop - start);
    return Math.min(frames - 1, age);
  }
}
