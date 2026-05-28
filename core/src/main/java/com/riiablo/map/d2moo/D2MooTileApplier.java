package com.riiablo.map.d2moo;

import com.badlogic.gdx.utils.IntMap;
import com.d2moo.common.drlg.DrlgTileExporter;
import com.riiablo.drlg.TileGrid;

/**
 * 将 D2MOO_JAVA 导出的瓦片写入 riiablo 的 TileGrid。
 * 实现 DrlgTileExporter，供 DrlgExport.exportLevelTiles 回调。
 */
public final class D2MooTileApplier implements DrlgTileExporter {

    private final IntMap<TileGrid> levelIdToGrid = new IntMap<>();
    private int lastExportedFloorCount;

    /** 注册 levelId -> TileGrid，仅写入 floor 层到 grid.floorIds。 */
    public void putGrid(int levelId, TileGrid grid) {
        levelIdToGrid.put(levelId, grid);
    }

    public void clearGrids() {
        levelIdToGrid.clear();
    }

    /** 上次 export 写入的 floor 瓦片数量（用于判断是否走 fallback）。 */
    public int getLastExportedFloorCount() {
        return lastExportedFloorCount;
    }

    public void resetLastExportedFloorCount() {
        lastExportedFloorCount = 0;
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId) {
        if (layer != LAYER_FLOOR) return;
        TileGrid grid = levelIdToGrid.get(levelId);
        if (grid == null) return;
        if (!grid.inBounds(tx, ty)) return;
        grid.floorIds[ty][tx] = tileId;
        lastExportedFloorCount++;
    }

    private static final int LAYER_FLOOR = 0;
}
