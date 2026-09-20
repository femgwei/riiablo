package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;

class DT1sTest extends RiiabloTest {
  private static final String CAVE =
      "data\\global\\tiles\\Act1\\Caves\\Cavedr.dt1";
  private static final String STONES =
      "data\\global\\tiles\\Act1\\Outdoors\\Stones.dt1";

  @BeforeEach
  void disableTextureData() {
    DT1.loadData = false;
  }

  @AfterEach
  void restoreTextureData() {
    DT1.loadData = true;
  }

  @Test
  void resolvesCollidingWarpTilesFromTheirNativeDt1() {
    DT1 cave = DT1.loadFromFile(Riiablo.mpqs.resolve(CAVE));
    DT1 stones = DT1.loadFromFile(Riiablo.mpqs.resolve(STONES));
    DT1s tiles = new DT1s();
    tiles.add(stones, STONES);
    tiles.add(cave, CAVE);

    int defaultId = DT1.Tile.Index.create(Orientation.FLOOR, 24, 0);
    int litId = DT1.Tile.Index.create(Orientation.FLOOR, 24, 4);
    DT1.Tile defaultCave = tiles.get(CAVE, defaultId);
    DT1.Tile litCave = tiles.get(CAVE, litId);

    assertNotNull(defaultCave);
    assertNotNull(tiles.get(STONES, defaultId),
        "test requires the real style-24 collision that hid the cave entrance");
    assertNotNull(litCave);
    assertSame(litCave, tiles.getSibling(defaultCave, litId));

    TileGrid grid = new TileGrid(1, 1);
    grid.floorIds[0][0] = defaultId;
    grid.floorSourceFiles[0][0] = grid.registerSourceFile(CAVE);
    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[1];
    Act1MapBuilderD2MOD.applyTileGridLayers(grid, tiles, layers, 1, 1, 1, null);

    assertSame(defaultCave, layers[Map.FLOOR_OFFSET][0]);
  }
}
