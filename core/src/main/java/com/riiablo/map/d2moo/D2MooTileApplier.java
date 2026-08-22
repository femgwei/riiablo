package com.riiablo.map.d2moo;

import com.badlogic.gdx.utils.IntMap;
import com.d2moo.common.drlg.DrlgTileExporter;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.DT1;

/**
 * 将 D2MOO_JAVA 导出的瓦片写入 riiablo 的 TileGrid。
 * 实现 DrlgTileExporter，供 DrlgExport.exportLevelTiles 回调。
 */
public final class D2MooTileApplier implements DrlgTileExporter {

    private final IntMap<TileGrid> levelIdToGrid = new IntMap<>();
    private int lastExportedFloorCount;
    private int callbackCount;
    private int ignoredLayerCount;
    private int missingGridCount;
    private int outOfBoundsCount;
    private int invalidTileCount;

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

    public int getCallbackCount() { return callbackCount; }
    public int getIgnoredLayerCount() { return ignoredLayerCount; }
    public int getMissingGridCount() { return missingGridCount; }
    public int getOutOfBoundsCount() { return outOfBoundsCount; }
    public int getInvalidTileCount() { return invalidTileCount; }

    public void resetLastExportedFloorCount() {
        lastExportedFloorCount = 0;
        callbackCount = 0;
        ignoredLayerCount = 0;
        missingGridCount = 0;
        outOfBoundsCount = 0;
        invalidTileCount = 0;
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId) {
        callbackCount++;
        if (layer != LAYER_FLOOR) {
            ignoredLayerCount++;
            return;
        }
        if (tileId < 0) {
            invalidTileCount++;
            return;
        }
        TileGrid grid = levelIdToGrid.get(levelId);
        if (grid == null) {
            missingGridCount++;
            return;
        }
        if (!grid.inBounds(tx, ty)) {
            outOfBoundsCount++;
            return;
        }
        int riiabloTileId = toRiiabloTileIndex(tileId);
        grid.floorIds[ty][tx] = riiabloTileId;
        lastExportedFloorCount++;
    }

    /** Decode D2MOO's (orientation, style, sequence) wire format and encode
     * riiablo's DT1.Tile.Index format. The two integer layouts must not be
     * shared directly. */
    public static int toRiiabloTileIndex(int d2mooTileId) {
        int orientation = (d2mooTileId >>> 24) & 0xFF;
        int style = (d2mooTileId >>> 12) & 0xFFF;
        int sequence = d2mooTileId & 0xFFF;
        return DT1.Tile.Index.create(orientation, style, sequence);
    }

    private static final int LAYER_FLOOR = 0;
}
