package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaterialTest {
  @Test
  void mapsNativeMaterialIds() {
    assertEquals(Material.DIRT, Material.getMaterial(1, 0));
    assertEquals(Material.ISTONE, Material.getMaterial(2, 0));
    assertEquals(Material.OSTONE, Material.getMaterial(3, 0));
    assertEquals(Material.SAND, Material.getMaterial(4, 0));
    assertEquals(Material.SNOW, Material.getMaterial(5, 0));
    assertEquals(Material.WOOD, Material.getMaterial(6, 0));
  }

  @Test
  void tileFlagsOverrideSoundEnvironmentDefault() {
    assertEquals(Material.DIRT,
        Material.getMaterial(6, DT1.Tile.MATERIAL_DIRT));
    assertEquals(Material.ISTONE,
        Material.getMaterial(1, DT1.Tile.MATERIAL_ISTONE));
    assertEquals(Material.OSTONE,
        Material.getMaterial(1, DT1.Tile.MATERIAL_OSTONE));
    assertEquals(Material.SAND,
        Material.getMaterial(1, DT1.Tile.MATERIAL_SAND));
    assertEquals(Material.SNOW,
        Material.getMaterial(1, DT1.Tile.MATERIAL_SNOW));
    assertEquals(Material.WOOD,
        Material.getMaterial(1, DT1.Tile.MATERIAL_WOOD));
  }

  @Test
  void decodesCombinedDt1Flags() {
    assertEquals(Material.OSTONE,
        Material.getMaterial(1, DT1.Tile.MATERIAL_OTHER | DT1.Tile.MATERIAL_OSTONE));
    assertEquals(Material.SAND,
        Material.getMaterial(1, DT1.Tile.MATERIAL_OTHER | DT1.Tile.MATERIAL_SAND));
    assertEquals(Material.WOOD,
        Material.getMaterial(1, DT1.Tile.MATERIAL_OTHER | DT1.Tile.MATERIAL_WOOD));
  }

  @Test
  void readsSnowFromHighByteAndPreservesNativePriority() {
    assertEquals(Material.SNOW,
        Material.getMaterial(1, DT1.Tile.MATERIAL_SNOW));
    assertEquals(Material.WOOD,
        Material.getMaterial(1, DT1.Tile.MATERIAL_WOOD | DT1.Tile.MATERIAL_SNOW));
    assertEquals(Material.DIRT,
        Material.getMaterial(1, DT1.Tile.MATERIAL_DIRT | DT1.Tile.MATERIAL_WOOD));
  }

  @Test
  void unsupportedFlagsUseSoundEnvironmentDefault() {
    int unsupported = DT1.Tile.MATERIAL_OTHER
        | DT1.Tile.MATERIAL_WATER
        | DT1.Tile.MATERIAL_WOOD_OBJECT
        | DT1.Tile.MATERIAL_LAVA;
    assertEquals(Material.ISTONE, Material.getMaterial(2, unsupported));
    assertEquals(Material.DIRT, Material.getMaterial(0, unsupported));
  }
}
