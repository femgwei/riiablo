package com.riiablo.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.SoundEnviron;

public enum Material {
  DIRT(new Color(0x4F7942FF)),
  WOOD(new Color(0x654321FF)),
  ISTONE(new Color(0x708090FF)),
  OSTONE(new Color(0xA9A9A9FF)),
  SAND(new Color(0xedc9afFF)),
  SNOW(new Color(0xfffafaFF)),
  ;

  final String name;
  final Color color;

  Material() {
    this(new Color(MathUtils.random.nextInt() | 0xFF));
  }

  Material(Color color) {
    this.name = name().toLowerCase();
    this.color = color;
  }

  Material(String name, Color color) {
    this.name = name;
    this.color = color;
  }

  @Override
  public String toString() {
    return name;
  }

  /** Selects the native footstep material from SoundEnviron and the DT1 material flags. */
  public static Material getMaterial(Levels.Entry level, DT1.Tile tile) {
    return getMaterial(defaultMaterialId(level), tile == null ? 0 : tile.materialFlags());
  }

  private static int defaultMaterialId(Levels.Entry level) {
    if (level == null || Riiablo.files == null || Riiablo.files.SoundEnviron == null) return 1;
    SoundEnviron.Entry environment = Riiablo.files.SoundEnviron.get(level.SoundEnv);
    return environment == null ? 1 : environment.Material_1;
  }

  /** Mirrors D2Client's material-bit priority; unrecognized flags use Material 1. */
  static Material getMaterial(int defaultMaterialId, int flags) {
    if ((flags & DT1.Tile.MATERIAL_DIRT) != 0) return DIRT;
    if ((flags & DT1.Tile.MATERIAL_ISTONE) != 0) return ISTONE;
    if ((flags & DT1.Tile.MATERIAL_OSTONE) != 0) return OSTONE;
    if ((flags & DT1.Tile.MATERIAL_SAND) != 0) return SAND;
    if ((flags & DT1.Tile.MATERIAL_WOOD) != 0) return WOOD;
    if ((flags & DT1.Tile.MATERIAL_SNOW) != 0) return SNOW;
    return fromId(defaultMaterialId);
  }

  private static Material fromId(int id) {
    switch (id) {
      case 2:  return ISTONE;
      case 3:  return OSTONE;
      case 4:  return SAND;
      case 5:  return SNOW;
      case 6:  return WOOD;
      default: return DIRT;
    }
  }
}
