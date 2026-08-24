package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.dungeon.Dungeon;

import java.util.Arrays;

/**
 * Drlg 传送门模块
 * 对应 C++ 文件：DrlgDrlgWarp.cpp
 */
public class DrlgDrlgWarp {
    private static final int MAPTILE_HIDDEN = 0x000008;
    private static final int[][] SPAWN_TILE_GROUP = {
        {1, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {1, 1},
        {0, 1}, {0, 1}, {0, 1}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}
    };

    /** Native DUNGEON_FindActSpawnLocation (the non-Ex, game-tile form). */
    public static D2ActiveRoom findActSpawnLocation(D2DrlgStrc drlg, int levelId,
            int tileIndex, int[] x, int[] y) {
        if (!hasXY(x, y) || drlg == null) return null;
        x[0] = -1; y[0] = -1;
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (level == null) return null;
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
        D2LevelDefBin def = DataTbls.getLevelDefRecord(levelId);
        if (def != null && def.getDwPosition() != 0) {
            if (tileIndex == 13) {
                D2DrlgRoom waypoint = getWaypointRoomExFromLevel(level, x, y);
                return initializeAndGet(waypoint);
            }
            int selected = selectTileInfo(level, tileIndex);
            if (selected >= 0) {
                D2DrlgTileInfoStrc info = level.getPTileInfo(selected);
                x[0] = info.getNPosX(); y[0] = info.getNPosY();
                return initializeAndGet(DrlgDrlg.getRoomExFromCoordinates(x[0], y[0], drlg, null, level));
            }
        }
        D2DrlgRoom room = getWaypointRoomExFromLevel(level, x, y);
        if (room == null) {
            int[] warps = getWarpIdArrayFromLevelId(drlg, levelId);
            for (D2DrlgRoom candidate = level.getFirstRoomEx(); candidate != null;
                    candidate = candidate.getDrlgRoomNext()) {
                for (int i = 0; i < 8; i++) {
                    if ((candidate.getFlags() & (D2DrlgRoomFlags.HAS_WARP_0 << i)) != 0
                            && warps != null && i < warps.length && warps[i] != -1) {
                        x[0] = candidate.getNTileXPos() + candidate.getNTileWidth() / 2;
                        y[0] = candidate.getNTileYPos() + candidate.getNTileHeight() / 2;
                        return initializeAndGet(candidate);
                    }
                }
            }
            room = DrlgDrlg.getRoomExFromLevelAndCoordinates(level,
                    level.getLevelCoords().getNPosX() + level.getLevelCoords().getNWidth() / 2 - 2,
                    level.getLevelCoords().getNPosY() + level.getLevelCoords().getNHeight() / 2 - 2);
            if (room == null) room = level.getFirstRoomEx();
            if (room != null) {
                x[0] = room.getNTileXPos() + room.getNTileWidth() / 2;
                y[0] = room.getNTileYPos() + room.getNTileHeight() / 2;
            }
        }
        return initializeAndGet(room);
    }

    /** Native DUNGEON_FindActSpawnLocationEx (subtile coordinates and footprint search). */
    public static D2ActiveRoom findActSpawnLocationEx(D2DrlgStrc drlg, int levelId,
            int tileIndex, int[] x, int[] y, int unitSize) {
        D2ActiveRoom room = findActSpawnLocation(drlg, levelId, tileIndex, x, y);
        if (room == null || !hasXY(x, y)) return room;
        x[0] = x[0] * 5 + 3;
        y[0] = y[0] * 5 + 3;
        findFreeSpawn(room, x, y, unitSize);
        D2ActiveRoom containing = room.getAct() != null
                ? Dungeon.findRoomBySubtileCoordinates(room.getAct(), x[0], y[0]) : null;
        return containing != null ? containing : room;
    }

    private static boolean hasXY(int[] x, int[] y) { return x != null && y != null && x.length > 0 && y.length > 0; }
    private static D2ActiveRoom initializeAndGet(D2DrlgRoom room) {
        if (room == null) return null;
        DrlgActivate.initializeRoomEx(room);
        return room.getRoom();
    }
    private static int selectTileInfo(D2DrlgLevel level, int tileIndex) {
        if (tileIndex < 0 || tileIndex >= SPAWN_TILE_GROUP.length) return -1;
        int matches = 0;
        for (int i = 0; i < level.getNTileInfo(); i++) {
            D2DrlgTileInfoStrc info = level.getPTileInfo(i);
            if (info != null && (info.getNTileIndex() == tileIndex ||
                    (SPAWN_TILE_GROUP[tileIndex][0] != 0 && info.getNTileIndex() >= 0
                            && info.getNTileIndex() < SPAWN_TILE_GROUP.length
                            && SPAWN_TILE_GROUP[info.getNTileIndex()][1] == SPAWN_TILE_GROUP[tileIndex][1]))) matches++;
        }
        if (matches == 0) return -1;
        int pick = Math.max(0, Math.min(matches - 1,
                Math.floorMod(level.getSeed() != null ? level.getSeed().getNLowSeed() : 0, matches)));
        for (int i = 0; i < level.getNTileInfo(); i++) {
            D2DrlgTileInfoStrc info = level.getPTileInfo(i);
            if (info != null && (info.getNTileIndex() == tileIndex ||
                    (SPAWN_TILE_GROUP[tileIndex][0] != 0 && info.getNTileIndex() >= 0
                            && info.getNTileIndex() < SPAWN_TILE_GROUP.length
                            && SPAWN_TILE_GROUP[info.getNTileIndex()][1] == SPAWN_TILE_GROUP[tileIndex][1])) && pick-- == 0) return i;
        }
        return -1;
    }
    private static void findFreeSpawn(D2ActiveRoom room, int[] x, int[] y, int unitSize) {
        if (room == null || room.getPCollisionGrid() == null) return;
        int mask = D2Collision.COLLIDE_MASK_SPAWN;
        for (int radius = 0; radius <= 8; radius++) {
            for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                int cx = x[0] + dx, cy = y[0] + dy;
                if (free(room.getPCollisionGrid(), room, cx, cy, unitSize, mask)) { x[0] = cx; y[0] = cy; return; }
            }
        }
    }
    private static boolean free(D2DrlgGridStrc grid, D2ActiveRoom room, int cx, int cy, int size, int mask) {
        int width = size == D2Collision.UNIT_SIZE_BIG ? 3 : 1;
        int height = width;
        int left = cx - width / 2, bottom = cy - height / 2;
        int minX = room.getCoords().getNSubtileX();
        int minY = room.getCoords().getNSubtileY();
        int maxX = minX + room.getCoords().getNSubtileWidth();
        int maxY = minY + room.getCoords().getNSubtileHeight();
        if (size == D2Collision.UNIT_SIZE_SMALL
                && (cx - 1 < minX || cy - 1 < minY || cx + 1 >= maxX || cy + 1 >= maxY)) return false;
        if (left < minX || bottom < minY || left + width > maxX || bottom + height > maxY) return false;
        if (size == D2Collision.UNIT_SIZE_SMALL) {
            int[][] p = {{0,0},{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] q : p) if ((grid.getFlag(cx + q[0] - room.getCoords().getNSubtileX(), cy + q[1] - room.getCoords().getNSubtileY()) & mask) != 0) return false;
            return true;
        }
        for (int yy = bottom; yy < bottom + height; yy++) for (int xx = left; xx < left + width; xx++)
            if ((grid.getFlag(xx - room.getCoords().getNSubtileX(), yy - room.getCoords().getNSubtileY()) & mask) != 0) return false;
        return true;
    }
    
    /**
     * D2Common.0x6FD78780
     * 获取目标房间
     * 
     * 功能：
     * 1. 遍历源房间的瓦片列表
     * 2. 查找匹配源关卡ID的传送点记录
     * 3. 在目标房间中查找对应的传送点
     * 4. 返回目标房间的活动房间对象
     */
    public static D2ActiveRoom getDestinationRoom(D2DrlgRoom drlgRoom, int sourceLevel, 
            int[] destinationLevel, D2LvlWarpTxt[] ppLvlWarpTxtRecord) {
        if (drlgRoom == null || destinationLevel == null || destinationLevel.length == 0 
                || ppLvlWarpTxtRecord == null || ppLvlWarpTxtRecord.length == 0) {
            return null;
        }
        
        D2RoomTile sourceRoomTile = drlgRoom.getRoomTiles();
        
        while (sourceRoomTile != null) {
            // 获取传送点记录
            Object warpTxtRecordObj = sourceRoomTile.getPLvlWarpTxtRecord();
            if (warpTxtRecordObj instanceof D2LvlWarpTxt) {
                D2LvlWarpTxt sourceWarpTxt = (D2LvlWarpTxt) warpTxtRecordObj;
                
                // 检查是否匹配源关卡ID
                if (sourceWarpTxt.getDwLevelId() == sourceLevel) {
                    D2DrlgRoom destinationDrlgRoom = sourceRoomTile.getPDrlgRoom();
                    if (destinationDrlgRoom != null) {
                        // 在目标房间中查找对应的传送点
                        D2RoomTile destinationRoomTile = destinationDrlgRoom.getRoomTiles();
                        while (destinationRoomTile != null) {
                            Object destWarpTxtRecordObj = destinationRoomTile.getPLvlWarpTxtRecord();
                            if (destWarpTxtRecordObj instanceof D2LvlWarpTxt) {
                                D2LvlWarpTxt destWarpTxt = (D2LvlWarpTxt) destWarpTxtRecordObj;
                                
                                // 检查是否匹配源房间
                                if (destinationRoomTile.getPDrlgRoom() == drlgRoom) {
                                    destinationLevel[0] = destWarpTxt.getDwLevelId();
                                    ppLvlWarpTxtRecord[0] = destWarpTxt;
                                    
                                    // 如果目标房间还没有激活，初始化它
                                    if (destinationDrlgRoom.getRoom() == null) {
                                        DrlgActivate.initializeRoomEx(destinationDrlgRoom);
                                    }
                                    
                                    return destinationDrlgRoom.getRoom();
                                }
                            }
                            destinationRoomTile = destinationRoomTile.getPNext();
                        }
                    }
                }
            }
            sourceRoomTile = sourceRoomTile.getPNext();
        }
        
        return null;
    }
    
    /**
     * D2Common.0x6FD787F0
     * 切换房间瓦片启用标志
     */
    public static void toggleRoomTilesEnableFlag(D2DrlgRoom drlgRoom, boolean enabled) {
        if (drlgRoom == null) {
            return;
        }
        D2RoomTile roomTile = drlgRoom.getRoomTiles();
        while (roomTile != null) {
            roomTile.setBEnabled(enabled);
            roomTile = roomTile.getPNext();
        }
    }
    
    /**
     * D2Common.0x6FD78D10
     * 从数组获取传送门目标
     * 
     * 功能：
     * 1. 遍历 Drlg 的传送门链表
     * 2. 查找匹配关卡ID的传送门记录
     * 3. 从传送门数组中获取指定索引的目标关卡ID
     */
    public static int getWarpDestinationFromArray(D2DrlgLevel level, byte arrayId) {
        if (level == null || level.getDrlg() == null) {
            return -1;
        }
        
        D2DrlgWarp warp = level.getDrlg().getWarp();
        while (warp != null) {
            if (warp.getNLevel() == level.getLevelId()) {
                if (arrayId >= 0 && arrayId < 8) {
                    int[] nWarpArray = warp.getNWarp();
                    if (nWarpArray != null && arrayId < nWarpArray.length) {
                        return nWarpArray[arrayId];
                    }
                }
            }
            warp = warp.getPNext();
        }
        
        return -1;
    }
    
    /**
     * D2Common.0x6FD78D80
     * 从传送门ID和方向获取 LvlWarpTxt 记录
     */
    public static D2LvlWarpTxt getLvlWarpTxtRecordFromWarpIdAndDirection(D2DrlgLevel level, 
            byte warpId, char direction) {
        if (level == null) {
            return null;
        }
        // Native DRLGWARP_GetLvlWarpTxtRecordFromWarpIdAndDirection resolves
        // the level's Warp slot first. LvlWarp.txt is keyed by that warp
        // definition id, not by the source level that owns the entrance room.
        int lvlWarpId = getWarpDestinationFromArray(level, warpId);
        if (lvlWarpId < 0) {
            return null;
        }
        return DataTbls.getLvlWarpTxtRecordFromLevelIdAndDirection(
            lvlWarpId, direction);
    }

    /**
     * D2Common.0x6FD78810
     * Selects the matching warp's alternate tile chain.
     */
    public static void updateWarpRoomSelect(D2DrlgRoom drlgRoom, int levelId) {
        if (drlgRoom == null) {
            return;
        }
        for (D2RoomTile roomTile = drlgRoom.getRoomTiles(); roomTile != null;
                roomTile = roomTile.getPNext()) {
            D2LvlWarpTxt warp = roomTile.getPLvlWarpTxtRecord();
            if (warp == null || warp.getDwLevelId() != levelId
                    || roomTile.getUnk0x0C() == null) {
                continue;
            }
            setTileChainHidden(roomTile.getUnk0x0C(), false);
            setTileChainHidden(roomTile.getUnk0x10(), true);
        }
    }

    /**
     * D2Common.0x6FD78870
     * Restores the matching warp's default tile chain.
     */
    public static void updateWarpRoomDeselect(D2DrlgRoom drlgRoom, int levelId) {
        if (drlgRoom == null) {
            return;
        }
        for (D2RoomTile roomTile = drlgRoom.getRoomTiles(); roomTile != null;
                roomTile = roomTile.getPNext()) {
            D2LvlWarpTxt warp = roomTile.getPLvlWarpTxtRecord();
            if (warp == null || warp.getDwLevelId() != levelId
                    || roomTile.getUnk0x10() == null) {
                continue;
            }
            setTileChainHidden(roomTile.getUnk0x10(), false);
            setTileChainHidden(roomTile.getUnk0x0C(), true);
        }
    }
    
    /**
     * D2Common.0x6FD78CC0
     * 从关卡ID获取传送门ID数组
     */
    public static int[] getWarpIdArrayFromLevelId(D2DrlgStrc drlg, int levelId) {
        D2DrlgWarp warp = drlg.getWarp();
        while (warp != null) {
            if (warp.getNLevel() == levelId) {
                return warp.getNWarp();
            }
            warp = warp.getPNext();
        }
        
        // 从数据表获取关卡定义记录
        com.d2moo.common.datatbls.D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef != null) {
            return levelDef.getDwWarp();
        }
        int[] noWarps = new int[8];
        Arrays.fill(noWarps, -1);
        return noWarps;
    }
    
    /**
     * D2Common.0x6FD78C10
     * 从关卡获取传送点房间
     * 
     * 功能：
     * 1. 遍历关卡的所有房间
     * 2. 查找带有传送点标志的房间（HAS_WAYPOINT 或 HAS_WAYPOINT_SMALL）
     * 3. 返回传送点房间的坐标（房间中心）
     */
    public static D2DrlgRoom getWaypointRoomExFromLevel(D2DrlgLevel level, int[] x, int[] y) {
        if (level == null || x == null || x.length == 0 || y == null || y.length == 0) {
            if (x != null && x.length > 0) x[0] = -1;
            if (y != null && y.length > 0) y[0] = -1;
            return null;
        }
        
        // 初始化返回值
        x[0] = -1;
        y[0] = -1;
        
        D2DrlgRoom roomEx = level.getFirstRoomEx();
        while (roomEx != null) {
            // 检查是否有传送点标志
            if ((roomEx.getFlags() & (D2DrlgRoomFlags.HAS_WAYPOINT | D2DrlgRoomFlags.HAS_WAYPOINT_SMALL)) != 0) {
                // 计算房间中心坐标
                x[0] = roomEx.getNTileXPos() + roomEx.getNTileWidth() / 2;
                y[0] = roomEx.getNTileYPos() + roomEx.getNTileHeight() / 2;
                return roomEx;
            }
            roomEx = roomEx.getDrlgRoomNext();
        }
        
        return null;
    }

    /** Native unit lookup expressed using its only required field, dwClassId. */
    public static D2LvlWarpTxt getLvlWarpTxtRecordFromClassId(
            D2DrlgRoom drlgRoom, int classId) {
        if (drlgRoom == null) {
            return null;
        }
        for (D2RoomTile roomTile = drlgRoom.getRoomTiles(); roomTile != null;
                roomTile = roomTile.getPNext()) {
            D2LvlWarpTxt warp = roomTile.getPLvlWarpTxtRecord();
            if (warp != null && warp.getDwLevelId() == classId && roomTile.isBEnabled()) {
                return warp;
            }
        }
        return null;
    }

    private static void setTileChainHidden(D2DrlgTileDataStrc tile, boolean hidden) {
        while (tile != null) {
            tile.setDwFlags(hidden
                    ? tile.getDwFlags() | MAPTILE_HIDDEN
                    : tile.getDwFlags() & ~MAPTILE_HIDDEN);
            tile = tile.getUnk0x20();
        }
    }
}
