package com.d2moo.common.collision;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;
import com.d2moo.common.drlg.D2DrlgTileDataStrc;
import com.d2moo.common.util.D2Log;

/** Room collision grids aligned with D2Common's {@code D2Collision.cpp}. */
public final class D2CommonCollision {
    private static final int SUBTILES_PER_TILE = 5;
    private static final int MAPTILE_PRESET = 0x000002;
    private static final int MAPTILE_UNWALKABLE = 0x000040;
    private static final int MAPTILE_MISSILE_BARRIER = 0x000080;
    private static final int[][] CROSS = {{-1, 0}, {0, 0}, {1, 0}, {0, -1}, {0, 1}};

    private D2CommonCollision() {}

    /** D2Common {@code COLLISION_AllocRoomCollisionGrid}. */
    public static void allocRoomCollisionGrid(D2ActiveRoom room) {
        if (room == null) return;

        D2DrlgCoords coords = room.getCoords();
        int width = Math.max(1, coords.getNSubtileWidth());
        int height = Math.max(1, coords.getNSubtileHeight());
        long area = (long) width * height;
        if (area > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Room collision grid is too large");
        }

        D2DrlgGridStrc grid = new D2DrlgGridStrc(width, height);
        int[] rowOffsets = new int[height];
        for (int y = 0; y < height; y++) rowOffsets[y] = y * width;
        grid.setPCellsRowOffsets(rowOffsets);
        grid.setPCellsFlags(new int[(int) area]);
        room.setPCollisionGrid(grid);

        D2ActiveRoom[] adjacent = room.getPpRoomList();
        int count = Math.min(room.getNNumRooms(), adjacent != null ? adjacent.length : 0);
        boolean includedSelf = false;
        for (int i = 0; i < count; i++) {
            D2ActiveRoom source = adjacent[i];
            if (source == null) continue;
            includedSelf |= source == room;
            mergeRoomTiles(room, source);
        }
        // Synthetic callers may not yet have populated the native near-room list.
        if (!includedSelf) mergeRoomTiles(room, room);
        D2Log.debug("COLLISION_AllocRoomCollisionGrid: origin=("
                + coords.getNSubtileX() + "," + coords.getNSubtileY() + ")"
                + " size=" + width + "x" + height + " sources=" + count);
    }

    /** D2Common {@code COLLISION_FreeRoomCollisionGrid}. */
    public static void freeRoomCollisionGrid(D2ActiveRoom room) {
        if (room != null) {
            room.setPCollisionGrid(null);
            D2Log.debug("COLLISION_FreeRoomCollisionGrid: room=" + room.getNRoomId());
        }
    }

    /**
     * D2Common {@code D2Common_COLLISION_FirstFn_6FD41000}: remove the old
     * DT1 5x5 flags and optionally apply a replacement tile entry.
     */
    public static void firstFn(
            D2ActiveRoom activeRoom, D2DrlgTileDataStrc tileData, Object replacementTile) {
        if (activeRoom == null || tileData == null || activeRoom.getPCollisionGrid() == null) return;

        int worldX = (activeRoom.getCoords().getNTileXPos() + tileData.getNPosX())
                * SUBTILES_PER_TILE;
        int worldY = (activeRoom.getCoords().getNTileYPos() + tileData.getNPosY())
                * SUBTILES_PER_TILE;
        D2ActiveRoom target = getRoomBySubtileCoordinates(activeRoom, worldX, worldY);
        if (target == null || target.getPCollisionGrid() == null) return;

        applyTileFlags(target, worldX, worldY, tileData.getPTile(), false);
        if (replacementTile != null) applyTileFlags(target, worldX, worldY, replacementTile, true);
        D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: sourceRoom="
                + activeRoom.getNRoomId() + " targetRoom=" + target.getNRoomId()
                + " worldSubtile=(" + worldX + "," + worldY + ") replacement="
                + (replacementTile != null));
    }

    /** Finds the active near room containing a world-subtile coordinate. */
    public static D2ActiveRoom getRoomBySubtileCoordinates(
            D2ActiveRoom roomHint, int worldX, int worldY) {
        if (roomHint == null) return null;
        if (contains(roomHint, worldX, worldY)) return roomHint;

        D2ActiveRoom[] rooms = roomHint.getPpRoomList();
        int count = Math.min(roomHint.getNNumRooms(), rooms != null ? rooms.length : 0);
        for (int i = 0; i < count; i++) {
            D2ActiveRoom room = rooms[i];
            if (room != null && contains(room, worldX, worldY)) return room;
        }
        return null;
    }

    public static int checkMask(D2ActiveRoom room, int worldX, int worldY, int mask) {
        D2ActiveRoom target = getRoomBySubtileCoordinates(room, worldX, worldY);
        if (target == null || target.getPCollisionGrid() == null) {
            return D2Collision.COLLIDE_MASK_INVALID;
        }
        return getLocalFlag(target, worldX, worldY) & mask & D2Collision.COLLIDE_ALL_MASK;
    }

    public static void setMask(D2ActiveRoom room, int worldX, int worldY, int mask) {
        alterPoint(room, worldX, worldY, mask, true);
    }

    public static void resetMask(D2ActiveRoom room, int worldX, int worldY, int mask) {
        alterPoint(room, worldX, worldY, mask, false);
    }

    public static int checkMaskWithSizeXY(
            D2ActiveRoom room, int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX <= 1 && sizeY <= 1) return checkMask(room, x, y, mask);
        if (sizeX <= 0 || sizeY <= 0) return D2Collision.COLLIDE_MASK_INVALID;
        return checkBox(room, D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask);
    }

    public static void setMaskWithSizeXY(
            D2ActiveRoom room, int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX == 1 && sizeY == 1) {
            setMask(room, x, y, mask);
        } else if (sizeX > 0 && sizeY > 0) {
            alterBox(room, D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask, true);
        }
    }

    public static void resetMaskWithSizeXY(
            D2ActiveRoom room, int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX == 1 && sizeY == 1) {
            resetMask(room, x, y, mask);
        } else if (sizeX > 0 && sizeY > 0) {
            alterBox(room, D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask, false);
        }
    }

    public static int checkMaskWithSize(
            D2ActiveRoom room, int x, int y, int unitSize, int mask) {
        switch (unitSize) {
            case D2Collision.UNIT_SIZE_NONE:
            case D2Collision.UNIT_SIZE_POINT:
                return checkMask(room, x, y, mask);
            case D2Collision.UNIT_SIZE_SMALL:
                return checkCross(room, x, y, mask);
            case D2Collision.UNIT_SIZE_BIG:
                return checkBox(room, D2Collision.createBoundingBox(x, y, 3, 3), mask);
            default:
                return D2Collision.COLLIDE_ALL_MASK;
        }
    }

    public static int checkMaskWithPattern(
            D2ActiveRoom room, int x, int y, int pattern, int mask) {
        switch (pattern) {
            case D2Collision.PATTERN_NONE:
                return checkMask(room, x, y, mask);
            case D2Collision.PATTERN_SMALL_UNIT_PRESENCE:
            case D2Collision.PATTERN_SMALL_PET_PRESENCE:
            case D2Collision.PATTERN_SMALL_NO_PRESENCE:
                return checkCross(room, x, y, mask);
            case D2Collision.PATTERN_BIG_UNIT_PRESENCE:
            case D2Collision.PATTERN_BIG_PET_PRESENCE:
                return checkBox(room, D2Collision.createBoundingBox(x, y, 3, 3), mask);
            default:
                return D2Collision.COLLIDE_ALL_MASK;
        }
    }

    public static void setMaskWithSize(
            D2ActiveRoom room, int x, int y, int unitSize, int mask) {
        alterSize(room, x, y, unitSize, mask, true);
    }

    public static void resetMaskWithSize(
            D2ActiveRoom room, int x, int y, int unitSize, int mask) {
        alterSize(room, x, y, unitSize, mask, false);
    }

    public static void setMaskWithPattern(
            D2ActiveRoom room, int x, int y, int pattern, int mask) {
        alterPattern(room, x, y, pattern, mask, true);
    }

    public static void resetMaskWithPattern(
            D2ActiveRoom room, int x, int y, int pattern, int mask) {
        alterPattern(room, x, y, pattern, mask, false);
    }

    private static void mergeRoomTiles(D2ActiveRoom target, D2ActiveRoom source) {
        D2DrlgRoomTilesStrc tiles = source.getPRoomTiles();
        if (tiles == null) return;
        mergeTiles(target, source, tiles.getPFloorTiles(), tiles.getNFloors());
        mergeTiles(target, source, tiles.getPWallTiles(), tiles.getNWalls());
        mergeTiles(target, source, tiles.getPRoofTiles(), tiles.getNRoofs());
    }

    private static void mergeTiles(D2ActiveRoom target, D2ActiveRoom source,
            D2DrlgTileDataStrc[] tiles, int count) {
        int limit = Math.min(Math.max(count, 0), tiles != null ? tiles.length : 0);
        for (int i = 0; i < limit; i++) {
            D2DrlgTileDataStrc tile = tiles[i];
            if (tile == null) continue;
            int worldX = (source.getCoords().getNTileXPos() + tile.getNPosX())
                    * SUBTILES_PER_TILE;
            int worldY = (source.getCoords().getNTileYPos() + tile.getNPosY())
                    * SUBTILES_PER_TILE;
            applyTileFlags(target, worldX, worldY, tile.getPTile(), true);

            int mapFlags = 0;
            if ((tile.getDwFlags() & MAPTILE_PRESET) != 0) mapFlags |= D2Collision.COLLIDE_PRESET;
            if ((tile.getDwFlags() & MAPTILE_UNWALKABLE) != 0) mapFlags |= D2Collision.COLLIDE_WALL;
            if ((tile.getDwFlags() & MAPTILE_MISSILE_BARRIER) != 0) {
                mapFlags |= D2Collision.COLLIDE_MISSILE_BARRIER;
            }
            if (mapFlags != 0) applyUniformFlags(target, worldX, worldY, mapFlags);
        }
    }

    private static void applyTileFlags(
            D2ActiveRoom target, int worldX, int worldY, Object tile, boolean add) {
        byte[] flags = D2Cmp.getTileFlagArray(tile);
        if (flags == null || flags.length < 25) return;
        D2DrlgCoords coords = target.getCoords();
        D2DrlgGridStrc grid = target.getPCollisionGrid();
        if (grid == null) return;

        for (int dy = 0; dy < SUBTILES_PER_TILE; dy++) {
            int localY = worldY + dy - coords.getNSubtileY();
            if (localY < 0 || localY >= grid.getNHeight()) continue;
            for (int dx = 0; dx < SUBTILES_PER_TILE; dx++) {
                int localX = worldX + dx - coords.getNSubtileX();
                if (localX < 0 || localX >= grid.getNWidth()) continue;
                int flag = flags[(SUBTILES_PER_TILE - 1 - dy) * SUBTILES_PER_TILE + dx] & 0xFF;
                int old = grid.getFlag(localX, localY);
                grid.setFlag(localX, localY, add
                        ? D2Collision.setMask(old, flag)
                        : D2Collision.resetMask(old, flag));
            }
        }
    }

    private static void applyUniformFlags(
            D2ActiveRoom target, int worldX, int worldY, int flags) {
        D2DrlgCoords coords = target.getCoords();
        D2DrlgGridStrc grid = target.getPCollisionGrid();
        for (int dy = 0; dy < SUBTILES_PER_TILE; dy++) {
            int localY = worldY + dy - coords.getNSubtileY();
            if (localY < 0 || localY >= grid.getNHeight()) continue;
            for (int dx = 0; dx < SUBTILES_PER_TILE; dx++) {
                int localX = worldX + dx - coords.getNSubtileX();
                if (localX >= 0 && localX < grid.getNWidth()) {
                    grid.setFlag(localX, localY,
                            D2Collision.setMask(grid.getFlag(localX, localY), flags));
                }
            }
        }
    }

    private static int checkCross(D2ActiveRoom room, int x, int y, int mask) {
        int result = 0;
        for (int[] offset : CROSS) result |= checkMask(room, x + offset[0], y + offset[1], mask);
        return result & D2Collision.COLLIDE_ALL_MASK;
    }

    private static int checkBox(D2ActiveRoom room, D2Collision.BoundingBox box, int mask) {
        int result = 0;
        for (int y = box.bottom; y <= box.top; y++) {
            for (int x = box.left; x <= box.right; x++) result |= checkMask(room, x, y, mask);
        }
        return result & D2Collision.COLLIDE_ALL_MASK;
    }

    private static void alterSize(
            D2ActiveRoom room, int x, int y, int unitSize, int mask, boolean set) {
        switch (unitSize) {
            case D2Collision.UNIT_SIZE_POINT:
                alterPoint(room, x, y, mask, set);
                break;
            case D2Collision.UNIT_SIZE_SMALL:
                alterCross(room, x, y, mask, set);
                break;
            case D2Collision.UNIT_SIZE_BIG:
                alterBox(room, D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                break;
            default:
                break;
        }
    }

    private static void alterPattern(
            D2ActiveRoom room, int x, int y, int pattern, int mask, boolean set) {
        switch (pattern) {
            case D2Collision.PATTERN_SMALL_UNIT_PRESENCE:
                alterCross(room, x, y, mask, set);
                if (mask != 0) alterPoint(room, x, y, D2Collision.COLLIDE_NO_PATH, set);
                break;
            case D2Collision.PATTERN_BIG_UNIT_PRESENCE:
                alterBox(room, D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                if (mask != 0) alterCross(room, x, y, D2Collision.COLLIDE_NO_PATH, set);
                break;
            case D2Collision.PATTERN_SMALL_PET_PRESENCE:
                alterCross(room, x, y, mask, set);
                if (mask != 0) alterPoint(room, x, y, D2Collision.COLLIDE_PET, set);
                break;
            case D2Collision.PATTERN_BIG_PET_PRESENCE:
                alterBox(room, D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                if (mask != 0) alterCross(room, x, y, D2Collision.COLLIDE_PET, set);
                break;
            case D2Collision.PATTERN_SMALL_NO_PRESENCE:
                alterCross(room, x, y, mask, set);
                break;
            default:
                break;
        }
    }

    private static void alterCross(
            D2ActiveRoom room, int x, int y, int mask, boolean set) {
        for (int[] offset : CROSS) alterPoint(room, x + offset[0], y + offset[1], mask, set);
    }

    private static void alterBox(
            D2ActiveRoom room, D2Collision.BoundingBox box, int mask, boolean set) {
        for (int y = box.bottom; y <= box.top; y++) {
            for (int x = box.left; x <= box.right; x++) alterPoint(room, x, y, mask, set);
        }
    }

    private static void alterPoint(
            D2ActiveRoom room, int worldX, int worldY, int mask, boolean set) {
        D2ActiveRoom target = getRoomBySubtileCoordinates(room, worldX, worldY);
        if (target == null || target.getPCollisionGrid() == null) return;
        D2DrlgCoords coords = target.getCoords();
        D2DrlgGridStrc grid = target.getPCollisionGrid();
        int x = worldX - coords.getNSubtileX();
        int y = worldY - coords.getNSubtileY();
        int old = grid.getFlag(x, y);
        grid.setFlag(x, y, set
                ? D2Collision.setMask(old, mask)
                : D2Collision.resetMask(old, mask));
    }

    private static int getLocalFlag(D2ActiveRoom room, int worldX, int worldY) {
        return room.getPCollisionGrid().getFlag(
                worldX - room.getCoords().getNSubtileX(),
                worldY - room.getCoords().getNSubtileY());
    }

    private static boolean contains(D2ActiveRoom room, int worldX, int worldY) {
        D2DrlgCoords coords = room.getCoords();
        return worldX >= coords.getNSubtileX() && worldY >= coords.getNSubtileY()
                && (long) worldX < (long) coords.getNSubtileX() + coords.getNSubtileWidth()
                && (long) worldY < (long) coords.getNSubtileY() + coords.getNSubtileHeight();
    }
}
