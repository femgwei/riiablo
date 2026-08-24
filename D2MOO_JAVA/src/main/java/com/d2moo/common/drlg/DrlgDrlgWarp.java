package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;

import java.util.Arrays;

/**
 * Drlg 传送门模块
 * 对应 C++ 文件：DrlgDrlgWarp.cpp
 */
public class DrlgDrlgWarp {
    private static final int MAPTILE_HIDDEN = 0x000008;
    
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
