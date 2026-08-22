package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.drlg.DrlgTileExporter;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.DT1;
import com.riiablo.map.Orientation;

/**
 * 将 D2MOO_JAVA 导出的瓦片写入 riiablo 的 TileGrid。
 * 实现 DrlgTileExporter，供 DrlgExport.exportLevelTiles 回调。
 */
public final class D2MooTileApplier implements DrlgTileExporter {

    private final IntMap<TileGrid> levelIdToGrid = new IntMap<>();
    private int exportedFloorCount;
    private int exportedWallCount;
    private int exportedShadowCount;
    private int callbackCount;
    private int ignoredLayerCount;
    private int missingGridCount;
    private int outOfBoundsCount;
    private int clippedBoundaryCount;
    private int clippedBoundaryFloorCount;
    private int invalidTileCount;
    private int duplicatePositionCount;
    private int duplicateShadowCount;
    private int wallLayerOverflowCount;
    private int nonFloorOrientationCount;
    private int nonWallOrientationCount;
    private int nonShadowOrientationCount;
    private int zeroTileIdCount;
    private final IntSet uniqueFloorIds = new IntSet();
    private final IntSet uniqueWallIds = new IntSet();
    private final IntSet uniqueShadowIds = new IntSet();

    /** Registers the destination for floor, wall/roof, and shadow callbacks. */
    public void putGrid(int levelId, TileGrid grid) {
        levelIdToGrid.put(levelId, grid);
    }

    public void clearGrids() {
        levelIdToGrid.clear();
    }

    /** 上次 export 写入的 floor 瓦片数量（用于判断是否走 fallback）。 */
    public int getLastExportedFloorCount() {
        return exportedFloorCount;
    }

    public int getExportedWallCount() { return exportedWallCount; }
    public int getExportedShadowCount() { return exportedShadowCount; }

    public int getCallbackCount() { return callbackCount; }
    public int getIgnoredLayerCount() { return ignoredLayerCount; }
    public int getMissingGridCount() { return missingGridCount; }
    public int getOutOfBoundsCount() { return outOfBoundsCount; }
    public int getClippedBoundaryCount() { return clippedBoundaryCount; }
    public int getClippedBoundaryFloorCount() { return clippedBoundaryFloorCount; }
    public int getInvalidTileCount() { return invalidTileCount; }
    public int getDuplicatePositionCount() { return duplicatePositionCount; }
    public int getDuplicateShadowCount() { return duplicateShadowCount; }
    public int getWallLayerOverflowCount() { return wallLayerOverflowCount; }
    public int getNonFloorOrientationCount() { return nonFloorOrientationCount; }
    public int getNonWallOrientationCount() { return nonWallOrientationCount; }
    public int getNonShadowOrientationCount() { return nonShadowOrientationCount; }
    public int getZeroTileIdCount() { return zeroTileIdCount; }
    public int getUniqueFloorIdCount() { return uniqueFloorIds.size; }
    public int getUniqueWallIdCount() { return uniqueWallIds.size; }
    public int getUniqueShadowIdCount() { return uniqueShadowIds.size; }

    public void resetLastExportedFloorCount() {
        exportedFloorCount = 0;
        exportedWallCount = 0;
        exportedShadowCount = 0;
        callbackCount = 0;
        ignoredLayerCount = 0;
        missingGridCount = 0;
        outOfBoundsCount = 0;
        clippedBoundaryCount = 0;
        clippedBoundaryFloorCount = 0;
        invalidTileCount = 0;
        duplicatePositionCount = 0;
        duplicateShadowCount = 0;
        wallLayerOverflowCount = 0;
        nonFloorOrientationCount = 0;
        nonWallOrientationCount = 0;
        nonShadowOrientationCount = 0;
        zeroTileIdCount = 0;
        uniqueFloorIds.clear();
        uniqueWallIds.clear();
        uniqueShadowIds.clear();
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId) {
        callbackCount++;
        if (layer < DrlgExport.LAYER_FLOOR || layer > DrlgExport.LAYER_SHADOW) {
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
            // Native RoomEx floor/wall grids have a shared +1 border. A tile
            // on the east/south edge belongs to the adjacent room; at the
            // outer level edge there is no adjacent destination, so clip it
            // without rejecting the otherwise valid native export.
            boolean sharedBoundary = tx >= 0 && ty >= 0
                && tx <= grid.width && ty <= grid.height
                && (tx == grid.width || ty == grid.height);
            if (sharedBoundary) {
                if (clippedBoundaryCount < 8 && Gdx.app != null) {
                    Gdx.app.log("D2MooTileApplier", String.format(
                        "D2MOO export clipped shared boundary: level=%d layer=%d pos=(%d,%d) grid=%dx%d tile=0x%08X",
                        levelId, layer, tx, ty, grid.width, grid.height, tileId));
                }
                clippedBoundaryCount++;
                if (layer == DrlgExport.LAYER_FLOOR) clippedBoundaryFloorCount++;
                return;
            }
            if (outOfBoundsCount < 8 && Gdx.app != null) {
                Gdx.app.log("D2MooTileApplier", String.format(
                    "D2MOO export out of bounds: level=%d layer=%d pos=(%d,%d) grid=%dx%d tile=0x%08X",
                    levelId, layer, tx, ty, grid.width, grid.height, tileId));
            }
            outOfBoundsCount++;
            return;
        }
        int riiabloTileId = toRiiabloTileIndex(tileId);
        int orientation = DT1.Tile.Index.orientation(riiabloTileId);
        switch (layer) {
            case DrlgExport.LAYER_FLOOR:
                applyFloor(grid, tx, ty, riiabloTileId, orientation);
                break;
            case DrlgExport.LAYER_WALL:
                applyWall(grid, tx, ty, riiabloTileId, orientation);
                break;
            case DrlgExport.LAYER_SHADOW:
                applyShadow(grid, tx, ty, riiabloTileId, orientation);
                break;
            default:
                throw new AssertionError("validated layer " + layer);
        }
    }

    private void applyFloor(TileGrid grid, int tx, int ty, int tileId, int orientation) {
        if (orientation != Orientation.FLOOR) nonFloorOrientationCount++;
        if (grid.floorIds[ty][tx] != -1) duplicatePositionCount++;
        if (tileId == 0) zeroTileIdCount++;
        grid.floorIds[ty][tx] = tileId;
        grid.exportedFloorCells[ty][tx] = true;
        uniqueFloorIds.add(tileId);
        exportedFloorCount++;
    }

    private void applyWall(TileGrid grid, int tx, int ty, int tileId, int orientation) {
        if (!isWallLayerOrientation(orientation)) nonWallOrientationCount++;
        for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
            if (grid.wallIds[slot][ty][tx] == -1) {
                grid.wallIds[slot][ty][tx] = tileId;
                uniqueWallIds.add(tileId);
                exportedWallCount++;
                return;
            }
        }
        wallLayerOverflowCount++;
    }

    private void applyShadow(TileGrid grid, int tx, int ty, int tileId, int orientation) {
        if (orientation != Orientation.SHADOW) nonShadowOrientationCount++;
        if (grid.shadowIds[ty][tx] != -1) duplicateShadowCount++;
        grid.shadowIds[ty][tx] = tileId;
        uniqueShadowIds.add(tileId);
        exportedShadowCount++;
    }

    private static boolean isWallLayerOrientation(int orientation) {
        return Orientation.isWall(orientation)
            || Orientation.isRoof(orientation)
            || Orientation.isSpecial(orientation);
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
}
