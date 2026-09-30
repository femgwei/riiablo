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
    private int duplicateWallCount;
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
    public int getDuplicateWallCount() { return duplicateWallCount; }
    public int getDuplicateShadowCount() { return duplicateShadowCount; }
    public int getWallLayerOverflowCount() { return wallLayerOverflowCount; }
    public int getNonFloorOrientationCount() { return nonFloorOrientationCount; }
    public int getNonWallOrientationCount() { return nonWallOrientationCount; }
    public int getNonShadowOrientationCount() { return nonShadowOrientationCount; }
    public int getZeroTileIdCount() { return zeroTileIdCount; }
    public int getUniqueFloorIdCount() { return uniqueFloorIds.size; }
    public int getUniqueWallIdCount() { return uniqueWallIds.size; }
    public int getUniqueShadowIdCount() { return uniqueShadowIds.size; }

    public int getBoundaryWallCount() { return boundaryWallCount; }

    private int boundaryWallCount;

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
        duplicateWallCount = 0;
        duplicateShadowCount = 0;
        wallLayerOverflowCount = 0;
        nonFloorOrientationCount = 0;
        nonWallOrientationCount = 0;
        nonShadowOrientationCount = 0;
        zeroTileIdCount = 0;
        uniqueFloorIds.clear();
        uniqueWallIds.clear();
        uniqueShadowIds.clear();
        boundaryWallCount = 0;
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId) {
        onTile(levelId, layer, tx, ty, tileId, 0);
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId, int flags) {
        onTile(levelId, layer, tx, ty, tileId, flags, null);
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId, int flags,
            String sourceFile) {
        onTile(levelId, layer, tx, ty, tileId, flags, sourceFile, -1, 0, 255, 0);
    }

    @Override
    public void onTile(int levelId, int layer, int tx, int ty, int tileId, int flags,
            String sourceFile, int logicalGroupId, int nativeStateFlags,
            int nativeAlpha, int fadeTick) {
        callbackCount++;
        if (layer < DrlgExport.LAYER_FLOOR || layer > DrlgExport.LAYER_SHADOW) {
            ignoredLayerCount++;
            return;
        }
        if (tileId < 0) {
            invalidTileCount++;
            return;
        }
        // MAPTILE_HIDDEN is also present on ordinary native cave floor records.
        // Do not drop floor/shadow tiles solely because this bit is set: the
        // selected/inactive warp chains have already been separated by
        // DrlgExport.collectInactiveWarpTiles(). Hidden wall markers remain
        // tagged below so warp registration can filter them without creating
        // a visible wall.
        TileGrid grid = levelIdToGrid.get(levelId);
        if (grid == null) {
            missingGridCount++;
            return;
        }
        if (!grid.inBounds(tx, ty)) {
            // Native RoomEx floor/wall grids have a shared +1 border. Floor
            // and shadow cells on that border are not renderable by the
            // rectangular Zone layer, but an ordinary wall still has a valid
            // render origin: its graphic extends back into the level. Keep
            // those edge walls in a side list instead of dropping them.
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
                if (layer == DrlgExport.LAYER_WALL) {
                    int riiabloTileId = toRiiabloTileIndex(tileId);
                    if (!Orientation.isSpecial(DT1.Tile.Index.orientation(riiabloTileId))) {
                        byte sourceIndex = grid.registerSourceFile(sourceFile);
                        int wallLayer = boundaryWallLayer(grid, tx, ty, riiabloTileId,
                            sourceIndex);
                        if (wallLayer >= 0) {
                            grid.boundaryWalls.add(new TileGrid.BoundaryWall(
                                wallLayer, tx, ty, riiabloTileId, sourceIndex,
                                (flags & DrlgTileExporter.FLAG_HIDDEN) != 0,
                                logicalGroupId));
                            boundaryWallCount++;
                            uniqueWallIds.add(riiabloTileId);
                        }
                    }
                }
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
                applyFloor(grid, tx, ty, riiabloTileId, orientation, sourceFile,
                    logicalGroupId);
                break;
            case DrlgExport.LAYER_WALL:
                applyWall(grid, tx, ty, riiabloTileId, orientation, flags, sourceFile,
                    logicalGroupId);
                break;
            case DrlgExport.LAYER_SHADOW:
                applyShadow(grid, tx, ty, riiabloTileId, orientation, sourceFile);
                break;
            default:
                throw new AssertionError("validated layer " + layer);
        }
    }

    private void applyFloor(TileGrid grid, int tx, int ty, int tileId, int orientation,
            String sourceFile, int logicalGroupId) {
        if (orientation != Orientation.FLOOR) nonFloorOrientationCount++;
        if (grid.floorIds[ty][tx] != -1) duplicatePositionCount++;
        if (tileId == 0) zeroTileIdCount++;
        grid.floorIds[ty][tx] = tileId;
        grid.floorSourceFiles[ty][tx] = grid.registerSourceFile(sourceFile);
        grid.floorLogicalGroups[ty][tx] = logicalGroupId;
        grid.exportedFloorCells[ty][tx] = true;
        uniqueFloorIds.add(tileId);
        exportedFloorCount++;
    }

    private void applyWall(TileGrid grid, int tx, int ty, int tileId, int orientation, int flags,
            String sourceFile, int logicalGroupId) {
        if (!isWallLayerOrientation(orientation)) nonWallOrientationCount++;
        byte sourceIndex = grid.registerSourceFile(sourceFile);
        // Adjacent native RoomEx grids share their boundary row/column and
        // may report the same wall more than once. Do not consume another of
        // riiablo's four wall slots for an identical tile.
        for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
            if (grid.wallIds[slot][ty][tx] == tileId
                    && grid.wallSourceFiles[slot][ty][tx] == sourceIndex) {
                grid.hiddenWallCells[slot][ty][tx] |=
                    (flags & DrlgTileExporter.FLAG_HIDDEN) != 0;
                if (grid.wallLogicalGroups[slot][ty][tx] < 0) {
                    grid.wallLogicalGroups[slot][ty][tx] = logicalGroupId;
                }
                duplicateWallCount++;
                return;
            }
        }
        for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
            if (grid.wallIds[slot][ty][tx] == -1) {
                grid.wallIds[slot][ty][tx] = tileId;
                grid.wallSourceFiles[slot][ty][tx] = sourceIndex;
                grid.wallLogicalGroups[slot][ty][tx] = logicalGroupId;
                grid.hiddenWallCells[slot][ty][tx] =
                    (flags & DrlgTileExporter.FLAG_HIDDEN) != 0;
                uniqueWallIds.add(tileId);
                exportedWallCount++;
                return;
            }
        }
        wallLayerOverflowCount++;
    }

    private void applyShadow(TileGrid grid, int tx, int ty, int tileId, int orientation,
            String sourceFile) {
        if (orientation != Orientation.SHADOW) {
            if (nonShadowOrientationCount < 16 && Gdx.app != null) {
                Gdx.app.log("D2MooTileApplier", String.format(
                    "D2MOO shadow slot referenced non-shadow DT1: pos=(%d,%d)"
                        + " orientation=%d(%s) style=%d sequence=%d tile=0x%08X",
                    tx, ty, orientation, Orientation.toString(orientation),
                    DT1.Tile.Index.mainIndex(tileId), DT1.Tile.Index.subIndex(tileId), tileId));
            }
            nonShadowOrientationCount++;
        }
        if (grid.shadowIds[ty][tx] != -1) duplicateShadowCount++;
        grid.shadowIds[ty][tx] = tileId;
        grid.shadowSourceFiles[ty][tx] = grid.registerSourceFile(sourceFile);
        uniqueShadowIds.add(tileId);
        exportedShadowCount++;
    }

    private static boolean isWallLayerOrientation(int orientation) {
        return Orientation.isWall(orientation)
            || Orientation.isRoof(orientation)
            || Orientation.isSpecial(orientation);
    }

    private static int boundaryWallLayer(TileGrid grid, int tx, int ty, int tileId,
            byte sourceIndex) {
        boolean[] used = new boolean[TileGrid.MAX_WALL_LAYERS];
        for (TileGrid.BoundaryWall wall : grid.boundaryWalls) {
            if (wall.x != tx || wall.y != ty) continue;
            if (wall.tileId == tileId && wall.sourceFile == sourceIndex) return -1;
            if (wall.layer >= 0 && wall.layer < used.length) used[wall.layer] = true;
        }
        for (int layer = 0; layer < used.length; layer++) if (!used[layer]) return layer;
        return -1;
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
