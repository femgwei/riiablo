package com.d2moo.common.drlg;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/** Native-parity tile animation routines from D2Common DrlgDrlgAnim.cpp. */
public final class DrlgDrlgAnim {
    private static final int TILE_FLAGS_LAVA = 0x100;
    private static final int MAPTILE_HIDDEN = 0x000008;
    private static final int MAPTILE_WALL_LAYER_MASK = 0x01C000;
    private static final int MAPTILE_WALL_LAYER_BIT = 14;
    private static final int MAX_TILE_ENTRIES = 40;

    private DrlgDrlgAnim() {}

    public static void initCache(D2DrlgStrc drlg, D2DrlgTileDataStrc tileData) {
        if (drlg == null || tileData == null) return;
        resetTileData(tileData);
        int style = 0;
        int sequence = 0;
        if (drlg.getActNo() == D2C_Acts.ACT_II) {
            sequence = 1;
        } else if (drlg.getActNo() == D2C_Acts.ACT_III) {
            style = 29;
            sequence = 12;
        } else if (drlg.getActNo() != D2C_Acts.ACT_I) {
            return;
        }
        Object[] entries = new Object[MAX_TILE_ENTRIES];
        int size = D2Cmp.getTiles(drlg.getTiles(), DrlgRoomTile.TILETYPE_FLOOR,
                style, sequence, entries, entries.length);
        if (size <= 0 || entries[0] == null) {
            D2Log.warning("DRLGANIM_InitCache: missing tile act=%d style=%d sequence=%d",
                    drlg.getActNo(), style, sequence);
            return;
        }
        DrlgRoomTile.initTileData(null, tileData, 0, 0, 0, entries[0]);
    }

    public static void testLoadAnimatedRoomTiles(D2DrlgRoom room, D2DrlgGridStrc grid,
            D2DrlgGridStrc tileTypeGrid, int tileType, int killEdgeX, int killEdgeY) {
        if (room == null || grid == null || room.getTileGrid() == null
                || room.getTileGrid().getPTiles() == null) return;
        Object[] entries = new Object[MAX_TILE_ENTRIES];
        int height = room.getNTileHeight() + (killEdgeY == 0 ? 1 : 0);
        int width = room.getNTileWidth() + (killEdgeX == 0 ? 1 : 0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                D2C_PackedTileInformation info = new D2C_PackedTileInformation(
                        DrlgDrlgGrid.getGridEntry(grid, x, y));
                if (!info.isBShadow() && !info.isBIsWall() && !info.isBIsFloor()) continue;
                int type = tileTypeGrid != null
                        ? DrlgDrlgGrid.getGridEntry(tileTypeGrid, x, y) : tileType;
                int count = D2Cmp.getTiles(room.getTiles(), type,
                        info.getNTileStyle(), info.getNTileSequence(), entries, entries.length);
                if (count <= 0 || (D2Cmp.getTileFlags(entries[0]) & TILE_FLAGS_LAVA) == 0) continue;
                D2DrlgRoomTilesStrc tiles = room.getTileGrid().getPTiles();
                if (info.isBIsFloor()) {
                    tiles.setNFloors(tiles.getNFloors() + count - 1);
                } else if (info.isBIsWall()) {
                    tiles.setNWalls(tiles.getNWalls() + count - 1);
                } else {
                    tiles.setNRoofs(tiles.getNRoofs() + count - 1);
                }
                room.setFlags(room.getFlags() | D2DrlgRoomFlags.ANIMATED_FLOOR);
            }
        }
    }

    public static void animateTiles(D2DrlgRoom room) {
        if (room == null || room.getPpRoomsNear() == null) return;
        int nearCount = Math.min(room.getNRoomsNear(), room.getPpRoomsNear().length);
        for (int i = 0; i < nearCount; i++) {
            D2DrlgRoom near = room.getPpRoomsNear()[i];
            if (near == null || (near.getFlags() & D2DrlgRoomFlags.ANIMATED_FLOOR) == 0
                    || near.getTileGrid() == null) continue;
            for (D2DrlgAnimTileGridStrc animation = near.getTileGrid().getPAnimTiles();
                    animation != null; animation = animation.getPNext()) {
                D2DrlgTileDataStrc[] frames = animation.getPpMapTileData();
                if (frames == null || animation.getNFrames() <= 0) continue;
                setHidden(frameAt(frames, animation.getNCurrentFrame()), true);
                animation.setNCurrentFrame(Math.floorMod(
                        animation.getNCurrentFrame() + animation.getNAnimationSpeed(),
                        animation.getNFrames() << 8));
                setHidden(frameAt(frames, animation.getNCurrentFrame()), false);
            }
        }
    }

    public static void allocAnimationTileGrids(D2DrlgRoom room, int animationSpeed,
            D2DrlgGridStrc[] wallGrids, int walls,
            D2DrlgGridStrc[] floorGrids, int floors, D2DrlgGridStrc shadowGrid) {
        if (room == null || room.getTileGrid() == null
                || room.getTileGrid().getPTiles() == null) return;
        D2DrlgRoomTilesStrc tiles = room.getTileGrid().getPTiles();
        allocAnimationTileGrid(room, animationSpeed, tiles.getPWallTiles(),
                room.getTileGrid().getNWalls(), wallGrids, walls);
        allocAnimationTileGrid(room, animationSpeed, tiles.getPFloorTiles(),
                room.getTileGrid().getNFloors(), floorGrids, floors);
        allocAnimationTileGrid(room, animationSpeed, tiles.getPRoofTiles(),
                room.getTileGrid().getNShadows(),
                shadowGrid != null ? new D2DrlgGridStrc[] {shadowGrid} : null, 1);
    }

    public static void allocAnimationTileGrid(D2DrlgRoom room, int animationSpeed,
            D2DrlgTileDataStrc[] tiles, int tileCount, D2DrlgGridStrc grid, int unused) {
        allocAnimationTileGrid(room, animationSpeed, tiles, tileCount,
                grid != null ? new D2DrlgGridStrc[] {grid} : null, unused);
    }

    static void allocAnimationTileGrid(D2DrlgRoom room, int animationSpeed,
            D2DrlgTileDataStrc[] tiles, int tileCount,
            D2DrlgGridStrc[] grids, int gridCount) {
        if (room == null || room.getTileGrid() == null || tiles == null || grids == null) return;
        if (animationSpeed == 0) animationSpeed = 80;
        Object[] entries = new Object[MAX_TILE_ENTRIES];
        int count = Math.min(tileCount, tiles.length);
        for (int i = 0; i < count; i++) {
            D2DrlgTileDataStrc current = tiles[i];
            if (current == null || current.getPTile() == null
                    || (D2Cmp.getTileFlags(current.getPTile()) & TILE_FLAGS_LAVA) == 0) continue;
            int gridIndex = current.getNTileType() == DrlgRoomTile.TILETYPE_SHADOW
                    ? 0 : mapTileLayer(current.getDwFlags());
            if (gridIndex < 0 || gridIndex >= gridCount || gridIndex >= grids.length
                    || grids[gridIndex] == null) continue;
            int packed = DrlgDrlgGrid.getGridEntry(
                    grids[gridIndex], current.getNPosX(), current.getNPosY());
            D2C_PackedTileInformation info = new D2C_PackedTileInformation(packed);
            int frames = D2Cmp.getTiles(room.getTiles(), current.getNTileType(),
                    info.getNTileStyle(), info.getNTileSequence(), entries, entries.length);
            if (frames <= 0) continue;
            D2DrlgAnimTileGridStrc animation = D2Pool.callocStrcPool(memPool(room),
                    D2DrlgAnimTileGridStrc.class);
            if (animation == null) animation = new D2DrlgAnimTileGridStrc();
            animation.setPpMapTileData(new D2DrlgTileDataStrc[frames]);
            animation.setNFrames(frames);
            animation.setNAnimationSpeed(animationSpeed);
            animation.setPNext(room.getTileGrid().getPAnimTiles());
            room.getTileGrid().setPAnimTiles(animation);

            current.setPTile(findAnimatedTileFrame(entries, frames, 0,
                    info.getNTileStyle(), info.getNTileSequence()));
            animation.getPpMapTileData()[0] = current;
            int worldX = current.getNPosX() + room.getNTileXPos();
            int worldY = current.getNPosY() + room.getNTileYPos();
            for (int rarity = 1; rarity < frames; rarity++) {
                Object entry = findAnimatedTileFrame(entries, frames, rarity,
                        info.getNTileStyle(), info.getNTileSequence());
                D2DrlgTileDataStrc frame;
                if (current.getNTileType() == DrlgRoomTile.TILETYPE_FLOOR) {
                    frame = DrlgRoomTile.initFloorTileData(room, null, worldX, worldY, packed, entry);
                } else if (current.getNTileType() == DrlgRoomTile.TILETYPE_SHADOW) {
                    frame = DrlgRoomTile.initShadowTileData(room, null, worldX, worldY, packed, entry);
                } else {
                    frame = DrlgRoomTile.initWallTileData(room, null, worldX, worldY,
                            packed, entry, current.getNTileType());
                }
                animation.getPpMapTileData()[rarity] = frame;
                setHidden(frame, true);
            }
        }
    }

    static Object findAnimatedTileFrame(
            Object[] entries, int count, int rarity, int style, int sequence) {
        int size = Math.min(count, entries != null ? entries.length : 0);
        for (int i = 0; i < size; i++) {
            if (entries[i] != null && D2Cmp.getTileRarity(entries[i]) == rarity) return entries[i];
        }
        D2Log.warning("Animating tiles missing rarity=%d style=%d sequence=%d",
                rarity, style, sequence);
        return size > 0 ? entries[0] : null;
    }

    public static void updateFrameInAdjacentRooms(D2DrlgRoom first, D2DrlgRoom second) {
        if (second == null) return;
        int currentFrame = 0;
        if (first != null && first.getPpRoomsNear() != null) {
            int count = Math.min(first.getNRoomsNear(), first.getPpRoomsNear().length);
            for (int i = 0; i < count; i++) {
                D2DrlgRoom near = first.getPpRoomsNear()[i];
                if (near != null && near.getTileGrid() != null
                        && near.getTileGrid().getPAnimTiles() != null) {
                    currentFrame = near.getTileGrid().getPAnimTiles().getNCurrentFrame();
                    break;
                }
            }
        }
        if (second.getPpRoomsNear() == null) return;
        int count = Math.min(second.getNRoomsNear(), second.getPpRoomsNear().length);
        for (int i = 0; i < count; i++) {
            D2DrlgRoom near = second.getPpRoomsNear()[i];
            if (near == null || near.getTileGrid() == null) continue;
            for (D2DrlgAnimTileGridStrc animation = near.getTileGrid().getPAnimTiles();
                    animation != null; animation = animation.getPNext()) {
                animation.setNCurrentFrame(currentFrame);
            }
        }
    }

    private static D2DrlgTileDataStrc frameAt(D2DrlgTileDataStrc[] frames, int fixedFrame) {
        int index = fixedFrame >> 8;
        return index >= 0 && index < frames.length ? frames[index] : null;
    }

    private static void setHidden(D2DrlgTileDataStrc tile, boolean hidden) {
        if (tile == null) return;
        tile.setDwFlags(hidden
                ? tile.getDwFlags() | MAPTILE_HIDDEN
                : tile.getDwFlags() & ~MAPTILE_HIDDEN);
    }

    private static int mapTileLayer(int flags) {
        return ((flags & MAPTILE_WALL_LAYER_MASK) >>> MAPTILE_WALL_LAYER_BIT) - 1;
    }

    private static Object memPool(D2DrlgRoom room) {
        return room.getLevel() != null && room.getLevel().getDrlg() != null
                ? room.getLevel().getDrlg().getMempool() : null;
    }

    private static void resetTileData(D2DrlgTileDataStrc tile) {
        tile.setNWidth(0);
        tile.setNHeight(0);
        tile.setNPosX(0);
        tile.setNPosY(0);
        tile.setUnk0x10(0);
        tile.setDwFlags(0);
        tile.setPTile(null);
        tile.setNTileType(0);
        tile.setUnk0x20(null);
        tile.setUnk0x24(0);
        tile.setNRed((byte) 0);
        tile.setNGreen((byte) 0);
        tile.setNBlue((byte) 0);
        tile.setNIntensity((byte) 0);
        tile.setUnk0x2C(0);
    }
}
