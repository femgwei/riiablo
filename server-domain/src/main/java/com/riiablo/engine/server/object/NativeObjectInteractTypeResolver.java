package com.riiablo.engine.server.object;

import com.riiablo.codec.excel.Objects;

/** Resolves the persistent {@code D2ObjectDataStrc::InteractType} initialization. */
public final class NativeObjectInteractTypeResolver {
  public static final int LOCKED = 0x80;
  public static final int TRAP_TYPE_MASK = 0x7F;
  public static final int MIN_TRAP_TYPE = 1;
  public static final int MAX_TRAP_TYPE = 8;

  private NativeObjectInteractTypeResolver() {}

  /** D2Game InitFn 2 (urn), 3 (chest), and 57 (sparkly chest). */
  public static boolean supports(Objects.Entry object) {
    return object != null
        && (object.InitFn == 2 || object.InitFn == 3 || object.InitFn == 57);
  }

  /**
   * Reproduces the native initialization formula with a stable per-object seed.
   *
   * <p>D2Game consumes the object-region RNG stream. That stream is not present
   * in the DRLG export, so the same native rolls are derived from map/object
   * identity and remain stable across room entity recreation.</p>
   */
  public static int resolve(Objects.Entry object, int normalMonsterLevel,
      int mapSeed, int levelId, int classId, int x, int y) {
    if (!supports(object)) return 0;

    NativeRandom random = new NativeRandom(objectSeed(
        mapSeed, levelId, classId, x, y));
    int interactType = random.nextInt(100) < trapChance(normalMonsterLevel)
        ? MIN_TRAP_TYPE + random.nextInt(MAX_TRAP_TYPE)
        : 0;

    // InitFn 3 and 57 perform the native locked-chest roll after trap type.
    if (object.InitFn != 2 && object.Lockable
        && random.nextInt(100) < lockChance(normalMonsterLevel)) {
      interactType |= LOCKED;
    }
    return interactType;
  }

  /** Native {@code MonLvl[0] / 8 + 5} trapped-container chance. */
  public static int trapChance(int normalMonsterLevel) {
    return clampPercent(Math.max(0, normalMonsterLevel) / 8 + 5);
  }

  /** Native {@code MonLvl[0] / 2 + 8} locked-chest chance. */
  public static int lockChance(int normalMonsterLevel) {
    return clampPercent(Math.max(0, normalMonsterLevel) / 2 + 8);
  }

  public static int trapType(int interactType) {
    int type = interactType & TRAP_TYPE_MASK;
    return type >= MIN_TRAP_TYPE && type <= MAX_TRAP_TYPE ? type : 0;
  }

  public static boolean locked(int interactType) {
    return (interactType & LOCKED) != 0;
  }

  static int objectSeed(int seed, int levelId, int classId, int x, int y) {
    int hash = seed;
    hash = 31 * hash + levelId;
    hash = 31 * hash + classId;
    hash = 31 * hash + x;
    hash = 31 * hash + y;
    hash ^= hash >>> 16;
    return hash;
  }

  private static int clampPercent(int chance) {
    return Math.max(0, Math.min(chance, 100));
  }

  private static final class NativeRandom {
    private int state;

    NativeRandom(int seed) {
      state = seed == 0 ? 0x6D2B79F5 : seed;
    }

    int nextInt(int bound) {
      if (bound <= 1) return 0;
      int x = state;
      x ^= x << 13;
      x ^= x >>> 17;
      x ^= x << 5;
      state = x;
      return Math.floorMod(x, bound);
    }
  }
}
