package com.d2moo.common.drlg;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.d2cmp.D2TileData;
import com.d2moo.common.util.D2Log;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 从 D2DrlgStrc 导出各关卡的瓦片数据到 DrlgTileExporter。
 * 用于 riiablo 等消费方完全使用 D2MOO_JAVA 的 DRLG 结果。
 */
public final class DrlgExport {

    /** 层：floor */
    public static final int LAYER_FLOOR = 0;
    /** 层：wall */
    public static final int LAYER_WALL = 1;
    /** 层：shadow */
    public static final int LAYER_SHADOW = 2;

    /**
     * 导出指定关卡的已生成瓦片到 exporter。
     *
     * @param drlg     D2DrlgStrc（allocDrlg 之后，可先 initLevel 再导出）
     * @param levelId  D2 关卡 ID
     * @param exporter 接收每格瓦片的回调
     * @return 导出的瓦片数量（仅 floor 层计数）
     */
    public static int exportLevelTiles(D2DrlgStrc drlg, int levelId, DrlgTileExporter exporter) {
        if (drlg == null || exporter == null) return 0;
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (level == null) return 0;
        D2DrlgCoord levelCoord = level.getLevelCoords();
        if (levelCoord == null) return 0;
        int levelOriginX = levelCoord.getNPosX();
        int levelOriginY = levelCoord.getNPosY();

        ExportCounts counts = new ExportCounts();
        int rooms = 0;
        int initialized = 0;
        D2DrlgRoom room = level.getFirstRoomEx();
        while (room != null) {
            // initLevel builds RoomEx layout only.  Native Diablo streams a
            // RoomEx before consuming its tile grid; an offline exporter must
            // perform that same activation step explicitly.
            if (room.getTileGrid() == null) {
                DrlgActivate.initializeRoomEx(room);
                initialized++;
            }
            exportRoomTiles(room, levelId, levelOriginX, levelOriginY, exporter, counts);
            exportPresetSpecials(room, levelId, levelOriginX, levelOriginY, exporter, counts);
            rooms++;
            room = room.getDrlgRoomNext();
        }
        D2Log.debug("DRLG_EXPORT level=%d origin=(%d,%d) rooms=%d initialized=%d"
                        + " floorTiles=%d wallTiles=%d shadowTiles=%d"
                        + " inactiveWarpFloor=%d inactiveWarpWall=%d inactiveWarpShadow=%d",
                levelId, levelOriginX, levelOriginY, rooms, initialized,
                counts.floors, counts.walls, counts.shadows,
                counts.hiddenFloors, counts.hiddenWalls, counts.hiddenShadows);
        return counts.floors;
    }

    /**
     * Returns the union of the DT1 masks attached to every generated room in
     * a level. Outdoor preset rooms can extend the base outdoor mask, so a
     * renderer consuming {@link #exportLevelTiles} must use this union rather
     * than only the mask selected by {@code DRLGOUTDOORS_GenerateLevel}.
     *
     * <p>Call this after exporting the level. Export initializes streamed
     * rooms and tile-substitution may add further DT1 mask bits while doing so.</p>
     */
    public static int collectLevelDt1Mask(D2DrlgStrc drlg, int levelId) {
        if (drlg == null) return 0;
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (level == null) return 0;

        int mask = 0;
        D2DrlgRoom room = level.getFirstRoomEx();
        while (room != null) {
            mask |= room.getDt1Mask();
            room = room.getDrlgRoomNext();
        }
        return mask;
    }

    /**
     * Exports preset-unit lists created while RoomEx tile grids are
     * initialized. Coordinates are level-local subtiles, matching the game
     * entity coordinate system rather than the tile coordinates used by
     * {@link #exportLevelTiles}.
     */
    public static int exportLevelPresetUnits(D2DrlgStrc drlg, int levelId,
            DrlgPresetUnitExporter exporter) {
        if (drlg == null || exporter == null) return 0;
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (level == null || level.getLevelCoords() == null) return 0;

        int levelOriginX = level.getLevelCoords().getNPosX();
        int levelOriginY = level.getLevelCoords().getNPosY();
        int count = 0;
        for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
                room = room.getDrlgRoomNext()) {
            if (room.getTileGrid() == null) DrlgActivate.initializeRoomEx(room);
            int roomX = (room.getNTileXPos() - levelOriginX) * 5;
            int roomY = (room.getNTileYPos() - levelOriginY) * 5;
            for (D2PresetUnit unit = room.getPresetUnits(); unit != null;
                    unit = unit.getPNext()) {
                exporter.onPresetUnit(levelId, unit.getNUnitType(), unit.getNIndex(),
                    unit.getNMode(), roomX + unit.getNXpos(), roomY + unit.getNYpos(),
                    unit.isDs1Raw(), unit.isBSpawned());
                count++;
            }
        }
        D2Log.debug("DRLG_EXPORT_UNITS level=%d count=%d", levelId, count);
        return count;
    }

    private static void exportRoomTiles(D2DrlgRoom room, int levelId, int levelOriginX, int levelOriginY,
            DrlgTileExporter exporter, ExportCounts counts) {
        D2DrlgTileGrid grid = room.getTileGrid();
        if (grid == null) return;
        D2DrlgRoomTilesStrc tiles = grid.getPTiles();
        if (tiles == null) return;
        Set<D2DrlgTileDataStrc> inactiveWarpTiles = collectInactiveWarpTiles(room);

        // nTileXPos/nTileYPos and D2DrlgTileData.nPosX/nPosY are already
        // expressed in game-tile units. Do not convert through grid/subtile
        // units here; the C++ code adds the room origin and local tile offset.
        int roomBaseTx = room.getNTileXPos();
        int roomBaseTy = room.getNTileYPos();
        D2DrlgTileDataStrc[] pFloor = tiles.getPFloorTiles();
        if (pFloor != null) {
            for (D2DrlgTileDataStrc t : pFloor) {
                if (t == null) continue;
                if (inactiveWarpTiles.contains(t)) {
                    counts.hiddenFloors++;
                    continue;
                }
                int tx = roomBaseTx + t.getNPosX() - levelOriginX;
                int ty = roomBaseTy + t.getNPosY() - levelOriginY;
                int tileId = packTileId(t.getPTile());
                if (tileId < 0) continue;
                exporter.onTile(levelId, LAYER_FLOOR, tx, ty, tileId, t.getDwFlags());
                counts.floors++;
            }
        }
        D2DrlgTileDataStrc[] pWall = tiles.getPWallTiles();
        if (pWall != null) {
            for (D2DrlgTileDataStrc t : pWall) {
                if (t == null) continue;
                if (inactiveWarpTiles.contains(t)) {
                    counts.hiddenWalls++;
                    continue;
                }
                int tx = roomBaseTx + t.getNPosX() - levelOriginX;
                int ty = roomBaseTy + t.getNPosY() - levelOriginY;
                int tileId = packTileId(t.getPTile());
                if (tileId < 0) continue;
                exporter.onTile(levelId, LAYER_WALL, tx, ty, tileId, t.getDwFlags());
                counts.walls++;
            }
        }
        D2DrlgTileDataStrc[] pRoof = tiles.getPRoofTiles();
        if (pRoof != null) {
            for (D2DrlgTileDataStrc t : pRoof) {
                if (t == null) continue;
                if (inactiveWarpTiles.contains(t)) {
                    counts.hiddenShadows++;
                    continue;
                }
                int tx = roomBaseTx + t.getNPosX() - levelOriginX;
                int ty = roomBaseTy + t.getNPosY() - levelOriginY;
                int tileId = packTileId(t.getPTile());
                if (tileId < 0) continue;
                exporter.onTile(levelId, LAYER_SHADOW, tx, ty, tileId, t.getDwFlags());
                counts.shadows++;
            }
        }
    }

    /**
     * Native D2MOO keeps the currently selected warp graphics in unk0x10 and
     * the inactive (lit/alternate) graphics in unk0x0C. Both chains also live
     * in the flattened room arrays. Export only the selected chain; filtering
     * every MAPTILE_HIDDEN tile would incorrectly remove ordinary cave floors.
     */
    static Set<D2DrlgTileDataStrc> collectInactiveWarpTiles(D2DrlgRoom room) {
        Set<D2DrlgTileDataStrc> inactive =
                Collections.newSetFromMap(new IdentityHashMap<D2DrlgTileDataStrc, Boolean>());
        if (room == null) return inactive;
        for (D2RoomTile warp = room.getRoomTiles(); warp != null; warp = warp.getPNext()) {
            Object head = warp.getUnk0x0C();
            if (!(head instanceof D2DrlgTileDataStrc)) continue;
            D2DrlgTileDataStrc tile = (D2DrlgTileDataStrc) head;
            while (tile != null && inactive.add(tile)) {
                tile = tile.getUnk0x20();
            }
        }
        return inactive;
    }

    /**
     * Orientation 10/11 cells are logical warp markers, not ordinary rendered
     * wall tiles. Native room tiling consumes them while constructing warp
     * links, so they are absent from pWallTiles and must be exported from the
     * initialized preset grids explicitly.
     */
    private static void exportPresetSpecials(D2DrlgRoom room, int levelId,
            int levelOriginX, int levelOriginY, DrlgTileExporter exporter,
            ExportCounts counts) {
        if (!(room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) return;
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
        for (int layer = 0; layer < preset.getPWallGrid().length; layer++) {
            D2DrlgGridStrc walls = preset.getPWallGrid(layer);
            D2DrlgGridStrc types = preset.getPTileTypeGrid(layer);
            if (walls == null || types == null || walls.getPCellsFlags() == null
                    || types.getPCellsFlags() == null) continue;
            int width = Math.min(walls.getNWidth(), types.getNWidth());
            int height = Math.min(walls.getNHeight(), types.getNHeight());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int orientation = types.getFlag(x, y) & 0xFF;
                    if (orientation != DrlgRoomTile.TILETYPE_WALL_LEFT_EXIT
                            && orientation != DrlgRoomTile.TILETYPE_WALL_RIGHT_EXIT) continue;
                    D2C_PackedTileInformation info =
                        new D2C_PackedTileInformation(walls.getFlag(x, y));
                    int tileId = ((orientation & 0xFF) << 24)
                        | ((info.getNTileStyle() & 0xFFF) << 12)
                        | (info.getNTileSequence() & 0xFFF);
                    int tx = room.getNTileXPos() + x - levelOriginX;
                    int ty = room.getNTileYPos() + y - levelOriginY;
                    int flags = info.isBHidden() ? DrlgTileExporter.FLAG_HIDDEN : 0;
                    exporter.onTile(levelId, LAYER_WALL, tx, ty, tileId, flags);
                    counts.walls++;
                }
            }
        }
    }

    private static final class ExportCounts {
        int floors;
        int walls;
        int shadows;
        int hiddenFloors;
        int hiddenWalls;
        int hiddenShadows;
    }

    /**
     * 将 pTile（D2TileData 或 Object）打包为单一 tileId。
     * 格式：(orientation &lt;&lt; 24) | (style &lt;&lt; 12) | sequence，便于渲染端解码或直接使用。
     */
    public static int packTileId(Object pTile) {
        if (pTile == null) return -1;
        if (pTile instanceof D2TileData) {
            D2TileData d = (D2TileData) pTile;
            int ori = d.getNOrientation() & 0xFF;
            int style = d.getNTileId() & 0xFFF;
            int seq = d.getNSequence() & 0xFFF;
            return (ori << 24) | (style << 12) | seq;
        }
        int style = D2Cmp.getTileStyle(pTile) & 0xFFF;
        int seq = D2Cmp.getTileSequence(pTile) & 0xFFF;
        return (style << 12) | seq;
    }

    private DrlgExport() {}
}
