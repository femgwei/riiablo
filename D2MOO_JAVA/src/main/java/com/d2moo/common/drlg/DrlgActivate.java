package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;

/**
 * Drlg 激活模块
 * 对应 C++ 文件：DrlgActivate.cpp
 * 
 * 注意：本模块依赖以下其他模块的函数，需要先实现：
 * - DRLG_IsOnClient (D2DrlgDrlg)
 * - sub_6FD77BB0 (D2DrlgDrlgRoom) - 初始化附近房间列表
 * - DRLGROOMTILE_InitRoomGrids (D2DrlgRoomTile)
 * - DRLGROOMTILE_AddRoomMapTiles (D2DrlgRoomTile)
 * - DRLG_CreateRoomForRoomEx (D2DrlgDrlg)
 * - DRLGROOMTILE_LoadDT1FilesForRoom (D2DrlgRoomTile)
 * - DRLGPRESET_SpawnHardcodedPresetUnits (D2DrlgPreset)
 * - DRLGROOMTILE_FreeRoom (D2DrlgRoomTile)
 * - DRLG_GetLevel (D2DrlgDrlg)
 * - DRLG_GetRoomExFromCoordinates (D2DrlgDrlg)
 * - DRLG_IsTownLevel (D2DrlgDrlg)
 * - DRLGROOM_GetDrlgFromRoomEx (D2DrlgDrlgRoom)
 */
public class DrlgActivate {
    
    // 统计变量
    private static int gStatsClientFreedRooms;
    private static int gStatsClientAllocatedRooms;
    private static int gStatsFreedRooms;
    private static int gStatsAllocatedRooms;
    
    // 房间状态设置函数数组
    private static final RoomExStatusSetter[] gRoomExSetStatus = {
        DrlgActivate::roomExSetStatus_ClientInRoom,
        DrlgActivate::roomExSetStatus_ClientInSight,
        DrlgActivate::roomExSetStatus_ClientOutOfSight,
        DrlgActivate::roomExSetStatus_Untile
    };
    
    // 房间状态取消设置函数数组
    private static final RoomExStatusUnsetter[] gRoomExUnsetStatus = {
        DrlgActivate::roomExIdentifyRealStatus,
        DrlgActivate::roomExIdentifyRealStatus,
        DrlgActivate::roomExIdentifyRealStatus,
        DrlgActivate::roomExStatusUnset_Untile
    };
    
    /**
     * 房间状态设置函数接口
     */
    @FunctionalInterface
    private interface RoomExStatusSetter {
        void setStatus(D2DrlgRoom drlgRoom);
    }
    
    /**
     * 房间状态取消设置函数接口
     */
    @FunctionalInterface
    private interface RoomExStatusUnsetter {
        void unsetStatus(D2DrlgRoom drlgRoom);
    }
    
    /**
     * 辅助函数：将房间链接到状态列表
     */
    private static void roomExStatusLink(D2DrlgRoom statusRoomsListHead, D2DrlgRoom drlgRoom) {
        drlgRoom.setStatusNext(statusRoomsListHead);
        drlgRoom.setStatusPrev(statusRoomsListHead.getStatusPrev());
        
        statusRoomsListHead.getStatusPrev().setStatusNext(drlgRoom);
        statusRoomsListHead.setStatusPrev(drlgRoom);
    }
    
    /**
     * 辅助函数：从状态列表取消链接房间
     */
    private static void roomExStatusUnlink(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getStatusPrev() != null && drlgRoom.getStatusNext() != null) {
            drlgRoom.getStatusPrev().setStatusNext(drlgRoom.getStatusNext());
            drlgRoom.getStatusNext().setStatusPrev(drlgRoom.getStatusPrev());
            drlgRoom.setStatusPrev(null);
            drlgRoom.setStatusNext(null);
        }
    }
    
    /**
     * 更新房间状态实现
     * 返回 true 如果状态改变
     */
    private static boolean updateRoomExStatusImpl(D2DrlgRoom drlgRoom, D2DrlgRoomStatus status) {
        // 注意：较低的值具有更高的优先级
        if (drlgRoom.getRoomStatus().getValue() > status.getValue()) {
            moveRoomExToStatus(drlgRoom, status);
            return true;
        }
        return false;
    }

    private static void moveRoomExToStatus(
            D2DrlgRoom drlgRoom, D2DrlgRoomStatus status) {
        roomExStatusUnlink(drlgRoom);
        if (status.getValue() < D2DrlgRoomStatus.COUNT.getValue()) {
            D2DrlgStrc drlg = drlgRoom.getLevel().getDrlg();
            roomExStatusLink(drlg.getStatusRoomsLists()[status.getValue()], drlgRoom);
        }
        drlgRoom.setRoomStatus(status);
    }
    
    /**
     * 查找第一个有引用计数的状态
     */
    private static D2DrlgRoomStatus roomExFindFirstStatusWithRefCount(D2DrlgRoom drlgRoom, D2DrlgRoomStatus maxStatus) {
        for (int firstNonEmptyListStatus = 0; 
             firstNonEmptyListStatus <= maxStatus.getValue() && firstNonEmptyListStatus < D2DrlgRoomStatus.COUNT.getValue(); 
             firstNonEmptyListStatus++) {
            if (drlgRoom.getRoomsInList()[firstNonEmptyListStatus] != 0) {
                return D2DrlgRoomStatus.fromValue(firstNonEmptyListStatus);
            }
        }
        return maxStatus;
    }
    
    /**
     * D2Common.0x6FD733D0
     * 设置房间状态：客户端在房间内
     */
    public static void roomExSetStatus_ClientInRoom(D2DrlgRoom drlgRoom) {
        updateRoomExStatusImpl(drlgRoom, D2DrlgRoomStatus.CLIENT_IN_ROOM);
    }
    
    /**
     * 辅助函数：初始化房间初始化超时
     */
    private static void initRoomsInitTimeout(D2DrlgStrc drlg) {
        drlg.setRoomsInitTimeout((byte)(2 * (DrlgDrlg.isOnClient(drlg) ? 0 : 1) + 5));
    }
    
    /**
     * 确保房间有 Room 对象
     */
    public static void roomEx_EnsureHasRoom(D2DrlgRoom drlgRoom, boolean initTimeoutCounter) {
        if (drlgRoom.getRoom() == null && (drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) == 0) {
            D2DrlgStrc drlg = drlgRoom.getLevel().getDrlg();
            if (drlgRoom.getNRoomsNear() == 0) {
                DrlgDrlgRoom.sub_6FD77BB0(drlg.getMempool(), drlgRoom);
            }
            
            DrlgRoomTile.initRoomGrids(drlgRoom);
            DrlgRoomTile.addRoomMapTiles(drlgRoom);
            DrlgDrlg.createRoomForRoomEx(drlg, drlgRoom);
            
            drlg.setRoomsInitSinceLastUpdate((byte)(drlg.getRoomsInitSinceLastUpdate() + 1));
            drlg.setAllocatedRooms(drlg.getAllocatedRooms() + 1);
            if (initTimeoutCounter) {
                initRoomsInitTimeout(drlg);
            }
        }
    }
    
    /**
     * D2Common.0x6FD73450
     * 设置房间状态：客户端在视野内
     */
    public static void roomExSetStatus_ClientInSight(D2DrlgRoom drlgRoom) {
        roomEx_EnsureHasRoom(drlgRoom, true);
        updateRoomExStatusImpl(drlgRoom, D2DrlgRoomStatus.CLIENT_IN_SIGHT);
    }
    
    /**
     * D2Common.0x6FD73550
     * 设置房间状态：客户端在视野外
     */
    public static void roomExSetStatus_ClientOutOfSight(D2DrlgRoom drlgRoom) {
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.TILELIB_LOADED) != 0
            && (drlgRoom.getType() != D2DrlgType.PRESET.getValue() 
                || (drlgRoom.getFlags() & D2DrlgRoomFlags.PRESET_UNITS_ADDED) != 0)) {
            
            if (updateRoomExStatusImpl(drlgRoom, D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT)
                && drlgRoom.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_SIGHT.getValue()] != 0) {
                roomExSetStatus_ClientInSight(drlgRoom);
            }
        }
    }
    
    /**
     * D2Common.0x6FD736F0
     * 设置房间状态：卸载
     */
    public static void roomExSetStatus_Untile(D2DrlgRoom drlgRoom) {
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.TILELIB_LOADED) == 0) {
            DrlgRoomTile.loadDT1FilesForRoom(drlgRoom);
        }
        
        if (drlgRoom.getType() == D2DrlgType.PRESET.getValue() 
            && (drlgRoom.getFlags() & D2DrlgRoomFlags.PRESET_UNITS_ADDED) == 0) {
            DrlgPreset.spawnHardcodedPresetUnits(drlgRoom);
        }
        updateRoomExStatusImpl(drlgRoom, D2DrlgRoomStatus.UNTILE);
    }
    
    /**
     * D2Common.0x6FD73790
     * 识别房间的真实状态
     */
    public static void roomExIdentifyRealStatus(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getRoomStatus().getValue() >= D2DrlgRoomStatus.COUNT.getValue() 
            || drlgRoom.getRoomsInList()[drlgRoom.getRoomStatus().getValue()] == 0) {
            D2DrlgRoomStatus firstStatusWithRefCount = roomExFindFirstStatusWithRefCount(
                drlgRoom, D2DrlgRoomStatus.COUNT);
            if (drlgRoom.getRoomStatus() != firstStatusWithRefCount) {
                // Status removal can move to a lower-priority (larger) value. The regular
                // update helper intentionally only promotes, so relink explicitly here.
                moveRoomExToStatus(drlgRoom, firstStatusWithRefCount);
            }
        }
    }
    
    /**
     * D2Common.0x6FD73880
     * 取消设置房间状态：卸载
     */
    public static void roomExStatusUnset_Untile(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getRoomStatus() != D2DrlgRoomStatus.COUNT) {
            roomExIdentifyRealStatus(drlgRoom);
            
            // 如果没有设置状态，可以卸载房间
            if (drlgRoom.getRoomStatus() == D2DrlgRoomStatus.COUNT) {
                if (DrlgDrlg.isOnClient(drlgRoom.getLevel().getDrlg())) {
                    DrlgRoomTile.freeRoom(drlgRoom, false);
                }
            }
        }
    }
    
    // 继续实现其他函数...
    // 由于代码较长，我将分多次创建
    
    /**
     * D2Common.0x6FD73EF0 (#10015)
     * 获取房间分配统计信息
     */
    public static void getRoomsAllocationStats(
            int[] outStatsClientAllocatedRooms,
            int[] outStatsClientFreedRooms,
            int[] outStatsAllocatedRooms,
            int[] outStatsFreedRooms) {
        outStatsClientAllocatedRooms[0] = gStatsClientAllocatedRooms;
        outStatsClientFreedRooms[0] = gStatsClientFreedRooms;
        outStatsAllocatedRooms[0] = gStatsAllocatedRooms;
        outStatsFreedRooms[0] = gStatsFreedRooms;
    }
    
    /**
     * D2Common.0x6FD740F0
     * 切换 HasPortal 标志
     */
    public static void toggleHasPortalFlag(D2DrlgRoom drlgRoom, boolean reset) {
        if (reset) {
            drlgRoom.setFlags(drlgRoom.getFlags() & ~D2DrlgRoomFlags.HASPORTAL);
        } else {
            drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.HASPORTAL);
        }
    }
    
    /**
     * D2Common.0x6FD73A30
     * 传播设置房间状态
     */
    public static void roomExPropagateSetStatus(Object memPool, D2DrlgRoom drlgRoom, byte status) {
        if (drlgRoom.getNRoomsNear() == 0) {
            DrlgDrlgRoom.sub_6FD77BB0(memPool, drlgRoom);
        }
        
        for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
            D2DrlgRoom nearRoom = drlgRoom.getPpRoomsNear()[i];
            if (status + 1 < D2DrlgRoomStatus.COUNT.getValue()) {
                roomExPropagateSetStatus(memPool, nearRoom, (byte)(status + 1));
            }
            
            if (nearRoom.getRoomStatus().getValue() >= status) {
                D2DrlgRoomStatus firstStatusWithRefCount = roomExFindFirstStatusWithRefCount(
                    nearRoom, D2DrlgRoomStatus.fromValue(status));
                if (firstStatusWithRefCount.getValue() == status) {
                    gRoomExSetStatus[status].setStatus(nearRoom);
                }
            }
            nearRoom.getRoomsInList()[status]++;
        }
    }
    
    /**
     * D2Common.0x6FD73BE0
     * 传播取消设置房间状态
     */
    public static void roomExPropagateUnsetStatus(D2DrlgRoom drlgRoom, byte status) {
        for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
            if (drlgRoom.getPpRoomsNear()[i] != null) {
                drlgRoom.getPpRoomsNear()[i].getRoomsInList()[status]--;
                
                gRoomExUnsetStatus[status].unsetStatus(drlgRoom.getPpRoomsNear()[i]);
                
                if (status + 1 < D2DrlgRoomStatus.COUNT.getValue()) {
                    roomExPropagateUnsetStatus(drlgRoom.getPpRoomsNear()[i], (byte)(status + 1));
                }
            }
        }
    }
    
    /**
     * 辅助函数：设置并传播房间状态
     */
    private static void roomSetAndPropagateStatus(D2DrlgRoom drlgRoom, D2DrlgRoomStatus status) {
        roomExPropagateSetStatus(drlgRoom.getLevel().getDrlg().getMempool(), drlgRoom, 
            (byte)(status.getValue() + 1));
        if (drlgRoom.getRoomStatus().getValue() >= status.getValue()) {
            D2DrlgRoomStatus firstStatusWithRefCount = roomExFindFirstStatusWithRefCount(drlgRoom, status);
            if (firstStatusWithRefCount == status) {
                gRoomExSetStatus[status.getValue()].setStatus(drlgRoom);
            }
        }
        drlgRoom.getRoomsInList()[status.getValue()]++;
    }
    
    /**
     * 辅助函数：取消设置并传播房间状态
     */
    private static void roomUnsetAndPropagateStatus(D2DrlgRoom drlgRoom, D2DrlgRoomStatus status) {
        if (drlgRoom.getRoomsInList()[status.getValue()] != 0) {
            drlgRoom.getRoomsInList()[status.getValue()]--;
            
            gRoomExUnsetStatus[status.getValue()].unsetStatus(drlgRoom);
            
            for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
                D2DrlgRoom nearRoom = drlgRoom.getPpRoomsNear()[i];
                if (nearRoom != null) {
                    nearRoom.getRoomsInList()[status.getValue() + 1]--;
                    gRoomExUnsetStatus[status.getValue() + 1].unsetStatus(nearRoom);
                    roomExPropagateUnsetStatus(nearRoom, (byte)(status.getValue() + 2));
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD739A0
     * 设置客户端在视野内
     */
    public static void setClientIsInSight(D2DrlgStrc drlg, int levelId, int x, int y, D2DrlgRoom drlgRoomHint) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (drlgRoomHint != null && drlgRoomHint.getLevel().getLevelId() != levelId) {
            drlgRoomHint = null;
        }
        D2DrlgRoom drlgRoom = DrlgDrlg.getRoomExFromCoordinates(x, y, drlg, drlgRoomHint, level);
        if (drlgRoom != null && drlgRoom.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_SIGHT.getValue()] == 0) {
            roomSetAndPropagateStatus(drlgRoom, D2DrlgRoomStatus.CLIENT_IN_SIGHT);
        }
    }
    
    /**
     * D2Common.0x6FD73B40
     * 取消设置客户端在视野内
     */
    public static void unsetClientIsInSight(D2DrlgStrc drlg, int levelId, int x, int y, D2DrlgRoom drlgRoomHint) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (drlgRoomHint != null && drlgRoomHint.getLevel().getLevelId() != levelId) {
            drlgRoomHint = null;
        }
        D2DrlgRoom drlgRoom = DrlgDrlg.getRoomExFromCoordinates(x, y, drlg, drlgRoomHint, level);
        if (drlgRoom != null) {
            roomUnsetAndPropagateStatus(drlgRoom, D2DrlgRoomStatus.CLIENT_IN_SIGHT);
        }
    }
    
    /**
     * D2Common.0x6FD73C40
     * 更改客户端房间
     */
    public static void changeClientRoom(D2DrlgRoom previousRoom, D2DrlgRoom newRoom) {
        if (previousRoom == newRoom) {
            return;
        }
        
        if (newRoom != null) {
            roomSetAndPropagateStatus(newRoom, D2DrlgRoomStatus.CLIENT_IN_ROOM);
        }
        
        if (previousRoom != null) {
            roomUnsetAndPropagateStatus(previousRoom, D2DrlgRoomStatus.CLIENT_IN_ROOM);
        }
    }
    
    /**
     * D2Common.0x6FD73CF0
     * 初始化房间
     */
    public static void initializeRoomEx(D2DrlgRoom drlgRoom) {
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.TILELIB_LOADED) == 0) {
            DrlgRoomTile.loadDT1FilesForRoom(drlgRoom);
        }
        
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.PRESET_UNITS_ADDED) == 0 
            && drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
            DrlgPreset.spawnHardcodedPresetUnits(drlgRoom);
        }
        
        roomEx_EnsureHasRoom(drlgRoom, false);
    }
    
    /**
     * D2Common.0x6FD73D80
     * 在坐标处流式加载房间
     */
    public static D2ActiveRoom streamRoomAtCoords(D2DrlgStrc drlg, int x, int y) {
        D2DrlgRoom drlgRoom = DrlgDrlg.getRoomExFromCoordinates(x, y, drlg, null, null);
        if (drlgRoom != null) {
            initializeRoomEx(drlgRoom);
            return drlgRoom.getRoom();
        }
        return null;
    }
    
    /**
     * D2Common.0x6FD73E30
     * 初始化房间状态列表
     */
    public static void initializeRoomExStatusLists(D2DrlgStrc drlg) {
        for (int status = 0; status < D2DrlgRoomStatus.COUNT.getValue(); ++status) {
            D2DrlgRoom drlgRoomStatusList = drlg.getStatusRoomsLists()[status];
            drlgRoomStatusList.setRoomStatus(D2DrlgRoomStatus.fromValue(status));
            drlgRoomStatusList.setStatusNext(drlgRoomStatusList);
            drlgRoomStatusList.setStatusPrev(drlgRoomStatusList);
        }
    }
    
    /**
     * 辅助函数：从状态列表获取第一个房间
     */
    private static D2DrlgRoom roomExStatusList_GetFirst(D2DrlgStrc drlg, D2DrlgRoomStatus status) {
        D2DrlgRoom statusListHead = drlg.getStatusRoomsLists()[status.getValue()];
        if (statusListHead != statusListHead.getStatusNext()) { // 如果不为空
            return statusListHead.getStatusNext();
        }
        return null;
    }
    
    /**
     * D2Common.0x6FD73E60
     * 获取客户端视野内的一个房间
     */
    public static D2ActiveRoom getARoomInClientSight(D2DrlgStrc drlg) {
        D2DrlgRoom drlgRoom = roomExStatusList_GetFirst(drlg, D2DrlgRoomStatus.CLIENT_IN_ROOM);
        if (drlgRoom != null) {
            return drlgRoom.getRoom();
        }
        drlgRoom = roomExStatusList_GetFirst(drlg, D2DrlgRoomStatus.CLIENT_IN_SIGHT);
        if (drlgRoom != null) {
            return drlgRoom.getRoom();
        }
        return null;
    }
    
    /**
     * D2Common.0x6FD73E90
     * 获取视野内但没有客户端的房间
     */
    public static D2ActiveRoom getARoomInSightButWithoutClient(D2DrlgStrc drlg, D2DrlgRoom drlgRoom) {
        D2DrlgRoom nextStatusRoom = drlgRoom.getStatusNext();
        if (nextStatusRoom != null) {
            if (drlgRoom.getRoomStatus() != D2DrlgRoomStatus.CLIENT_IN_ROOM 
                || nextStatusRoom != drlg.getStatusRoomsLists()[D2DrlgRoomStatus.CLIENT_IN_ROOM.getValue()]) {
                return nextStatusRoom.getRoom();
            }
            
            D2DrlgRoom firstRoomInSight = roomExStatusList_GetFirst(drlg, D2DrlgRoomStatus.CLIENT_IN_SIGHT);
            if (firstRoomInSight != null) {
                return firstRoomInSight.getRoom();
            }
        }
        
        return null;
    }
    
    /**
     * D2Common.0x6FD73F20 (#10003)
     * 更新房间激活状态
     */
    public static void update(D2DrlgStrc drlg) {
        if (drlg == null) return;

        boolean isOnClient = DrlgDrlg.isOnClient(drlg);
        
        if (isOnClient) {
            gStatsClientAllocatedRooms = drlg.getAllocatedRooms();
            gStatsClientFreedRooms = drlg.getFreedRooms();
        } else {
            gStatsAllocatedRooms = drlg.getAllocatedRooms();
            gStatsFreedRooms = drlg.getFreedRooms();
        }
        
        if (drlg.getRoomsInitSinceLastUpdate() > 1) {
            drlg.setRoomsInitSinceLastUpdate((byte)0);
            return;
        }
        
        // The native field is uint8_t. Preserve its wraparound instead of
        // allowing Java's signed byte representation to leak into the logic.
        int roomsInitTimeout = (Byte.toUnsignedInt(drlg.getRoomsInitTimeout()) - 1) & 0xFF;
        drlg.setRoomsInitTimeout((byte) roomsInitTimeout);
        
        if (roomsInitTimeout == 0) {
            initRoomsInitTimeout(drlg);

            D2DrlgRoom statusListHead = drlg.getStatusRoomsLists()[
                    D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT.getValue()];
            D2DrlgRoom cursor = drlg.getDrlgRoom();
            if (cursor == null
                    || cursor.getRoomStatus() != D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT) {
                cursor = statusListHead.getStatusNext();
            }

            if (cursor != null) {
                // The status list is circular. The reconstructed C++ source
                // spells this as a for-loop whose initial condition is false;
                // the native behavior requires processing the starting node
                // once before testing whether traversal wrapped around.
                D2DrlgRoom start = cursor;
                do {
                    if (cursor != statusListHead) {
                        roomEx_EnsureHasRoom(cursor, false);
                    }
                    if (drlg.getRoomsInitSinceLastUpdate() != 0) {
                        break;
                    }
                    cursor = cursor.getStatusNext();
                } while (cursor != null && cursor != start);

                drlg.setDrlgRoom(cursor);
                drlg.setRoomsInitSinceLastUpdate((byte)0);
            }
        }
    }
    
    /**
     * D2Common.0x6FD74060
     * 测试房间是否可以卸载
     */
    public static boolean testRoomCanUnTile(D2DrlgRoom drlgRoom) {
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.HASPORTAL) != 0) {
            return false;
        }
        
        // 断言：不能在客户端上卸载房间
        D2DrlgStrc drlg = DrlgDrlgRoom.getDrlgFromRoomEx(drlgRoom);
        if (drlg != null && DrlgDrlg.isOnClient(drlg)) {
            return false;
        }
        
        if (drlgRoom.getRoomStatus().getValue() <= 1) {
            return false;
        }
        
        // 检查城镇关卡或特殊关卡
        if (DrlgDrlg.isTownLevel(drlgRoom.getLevel().getLevelId()) 
            || drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_ROCKYSUMMIT) {
            for (D2DrlgRoom currentRoomEx = drlgRoom.getLevel().getFirstRoomEx(); 
                 currentRoomEx != null; 
                 currentRoomEx = currentRoomEx.getDrlgRoomNext()) {
                if (currentRoomEx.getRoomStatus().getValue() <= 1) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD74110
     * 获取房间状态标志
     */
    public static byte getRoomStatusFlags(D2DrlgRoom drlgRoom) {
        return (byte) drlgRoom.getRoomStatus().getValue();
    }
}
