package com.riiablo.map;

/**
 * Selects the world-generation implementation for one map session.
 *
 * <p>The native D2MOO bridge is the authoritative production path. The
 * legacy builder remains available only as an explicit compatibility mode for
 * tools and historical comparisons; world rules must not infer the mode from
 * individual map classes.</p>
 */
public final class MapGenerationPolicy {
  public enum Mode {
    NATIVE_D2MOO,
    LEGACY_COMPATIBILITY
  }

  private final Mode mode;

  private MapGenerationPolicy(Mode mode) {
    this.mode = mode;
  }

  /** Returns the native D2MOO policy used when no compatibility override exists. */
  public static MapGenerationPolicy nativeDefault() {
    return new MapGenerationPolicy(Mode.NATIVE_D2MOO);
  }

  /** Resolves the old boolean toggle without making it the world-rule owner. */
  public static MapGenerationPolicy fromNativeToggle(Boolean useNative) {
    return Boolean.FALSE.equals(useNative)
        ? new MapGenerationPolicy(Mode.LEGACY_COMPATIBILITY)
        : nativeDefault();
  }

  public Mode mode() { return mode; }

  public boolean usesNativeD2Moo() { return mode == Mode.NATIVE_D2MOO; }

  public boolean usesLegacyCompatibility() { return mode == Mode.LEGACY_COMPATIBILITY; }

  /**
   * Returns whether the compatibility straight-line path generator may run.
   *
   * <p>Native outdoor exports own both the path floor rasterization and the
   * collision footprint.  A native session must therefore not let the legacy
   * path pass run after an export was accepted.  The legacy mode remains an
   * explicit escape hatch, including when a native export is available.</p>
   */
  public boolean usesCompatibilityPath(boolean hasNativeOutdoorExport) {
    return usesLegacyCompatibility() || !hasNativeOutdoorExport;
  }

  @Override
  public String toString() { return mode.name(); }
}
