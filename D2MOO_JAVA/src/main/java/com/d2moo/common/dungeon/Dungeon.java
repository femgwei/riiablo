package com.d2moo.common.dungeon;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.environment.D2DrlgEnvironment;
import com.d2moo.common.environment.Environment;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2ActCallback;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgFlags;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgTileDataStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2PresetUnit;
import com.d2moo.common.drlg.D2RoomCoordListStrc;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.drlg.DrlgActivate;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgDrlgAnim;
import com.d2moo.common.drlg.DrlgDrlgLogic;
import com.d2moo.common.drlg.DrlgDrlgRoom;
import com.d2moo.common.drlg.DrlgDrlgWarp;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

import java.util.Arrays;

/**
 * DUNGEON 房间和坐标管理模块
 * 对应 C++ 模块：DUNGEON
 * 
 * 注意：这是一个房间和坐标管理模块，负责房间的创建、删除和坐标转换
 * 当前实现提供基础框架和接口，实际逻辑需要后续实现
 */
public class Dungeon {
    private static final int ACTIVE_ROOM_FLAG_1 = 0x1;
    private static final int ACTIVE_ROOM_FLAG_2 = 0x2;
    private static final int ACTIVE_ROOM_FLAG_4 = 0x4;

    @FunctionalInterface
    public interface RoomCallback<T> {
        boolean visit(D2ActiveRoom room, T args);
    }

    /** D2Common #10038. Act numbers are zero-based. */
    public static D2DrlgAct allocAct(byte actNo, int initSeed, boolean client,
            Object game, byte difficulty, Object memPool, int townLevelId,
            Object autoMap, Object townAutoMap) {
        D2DrlgAct act = D2Pool.callocStrcPool(memPool, D2DrlgAct.class);
        if (act == null) return null;

        act.setInitSeed(initSeed);
        act.setClient(client);
        act.setAct(actNo);
        act.setPMemPool(memPool);
        if (!client) act.setTownId(townLevelId);

        D2DrlgStrc drlg = DrlgDrlg.allocDrlg(
                act, actNo, null, initSeed,
                client ? D2LevelIds.LEVEL_NONE : townLevelId,
                client ? D2DrlgFlags.ONCLIENT : 0,
                game, difficulty, autoMap, townAutoMap);
        act.setDrlg(drlg);
        act.setEnvironment(Environment.allocDrlgEnvironment(memPool));
        DrlgDrlgAnim.initCache(drlg, act.getTileData());
        return act;
    }

    /** D2Common #10039. Clears Java references after native-order release. */
    public static void freeAct(D2DrlgAct act) {
        if (act == null) return;
        Object memPool = act.getPMemPool();

        if (act.getDrlg() != null) {
            DrlgDrlg.freeDrlg(act.getDrlg());
            act.setDrlg(null);
        }

        D2ActiveRoom room = act.getRoom();
        while (room != null) {
            D2ActiveRoom next = room.getRoomNext();
            D2DrlgRoom drlgRoom = room.getPDrlgRoom();
            if (drlgRoom != null && drlgRoom.getRoom() == room) {
                drlgRoom.setRoom(null);
            }
            room.setPDrlgRoom(null);
            room.setPpRoomList(null);
            room.setNNumRooms(0);
            room.setRoomNext(null);
            room.setAct(null);
            D2Pool.freePool(memPool, room);
            room = next;
        }
        act.setRoom(null);

        Environment.freeDrlgEnvironment(memPool, act.getEnvironment());
        act.setEnvironment(null);
        act.setPfnActCallBack(null);
        act.setHasPendingRoomsUpdates(false);
        act.setHasPendingRoomDeletions(false);
        act.setHasPendingUnitListUpdates(false);
        D2Pool.freePool(memPool, act);
    }

    /** D2Common #10103. The callback runs only for newly allocated rooms. */
    public static void setActCallbackFunc(D2DrlgAct act, D2ActCallback callback) {
        if (act != null) act.setPfnActCallBack(callback);
    }

    public static Object getMemPoolFromAct(D2DrlgAct act) {
        return act != null ? act.getPMemPool() : null;
    }

    /** Java array form of D2Common #10030. The backing native-order array is returned. */
    public static D2DrlgTileDataStrc[] getFloorTilesFromRoom(
            D2ActiveRoom room, int[] floorCount) {
        D2DrlgRoomTilesStrc tiles = room != null ? room.getPRoomTiles() : null;
        setCount(floorCount, tiles != null ? tiles.getNFloors() : 0);
        return tiles != null ? tiles.getPFloorTiles() : null;
    }

    /** Java array form of D2Common #10031. The backing native-order array is returned. */
    public static D2DrlgTileDataStrc[] getWallTilesFromRoom(
            D2ActiveRoom room, int[] wallCount) {
        D2DrlgRoomTilesStrc tiles = room != null ? room.getPRoomTiles() : null;
        setCount(wallCount, tiles != null ? tiles.getNWalls() : 0);
        return tiles != null ? tiles.getPWallTiles() : null;
    }

    /** Java array form of D2Common #10032. The backing native-order array is returned. */
    public static D2DrlgTileDataStrc[] getRoofTilesFromRoom(
            D2ActiveRoom room, int[] roofCount) {
        D2DrlgRoomTilesStrc tiles = room != null ? room.getPRoomTiles() : null;
        setCount(roofCount, tiles != null ? tiles.getNRoofs() : 0);
        return tiles != null ? tiles.getPRoofTiles() : null;
    }

    public static D2DrlgTileDataStrc getTileDataFromAct(D2DrlgAct act) {
        return act != null ? act.getTileData() : null;
    }

    public static D2PresetUnit getPresetUnitsFromRoom(D2ActiveRoom room) {
        return room != null ? DrlgDrlgRoom.getPresetUnits(room.getPDrlgRoom()) : null;
    }

    public static D2DrlgGridStrc getCollisionGridFromRoom(D2ActiveRoom room) {
        return room != null ? room.getPCollisionGrid() : null;
    }

    public static void setCollisionGridInRoom(
            D2ActiveRoom room, D2DrlgGridStrc collisionGrid) {
        if (room != null) {
            room.setPCollisionGrid(collisionGrid);
        }
    }

    /** D2Common #10063. Coordinates are native tile coordinates. */
    public static void setClientIsInSight(
            D2DrlgAct act, int levelId, int x, int y, D2ActiveRoom roomHint) {
        if (act == null || act.getDrlg() == null) return;
        DrlgActivate.setClientIsInSight(
                act.getDrlg(), levelId, x, y,
                roomHint != null ? roomHint.getPDrlgRoom() : null);
    }

    /** D2Common #10064. Coordinates are native tile coordinates. */
    public static void unsetClientIsInSight(
            D2DrlgAct act, int levelId, int x, int y, D2ActiveRoom roomHint) {
        if (act == null || act.getDrlg() == null) return;
        DrlgActivate.unsetClientIsInSight(
                act.getDrlg(), levelId, x, y,
                roomHint != null ? roomHint.getPDrlgRoom() : null);
    }

    /** D2Common #10062. */
    public static void changeClientRoom(D2ActiveRoom previousRoom, D2ActiveRoom newRoom) {
        DrlgActivate.changeClientRoom(
                previousRoom != null ? previousRoom.getPDrlgRoom() : null,
                newRoom != null ? newRoom.getPDrlgRoom() : null);
    }

    /** D2Common #10065. Streams the generated room containing the tile position. */
    public static D2ActiveRoom streamRoomAtCoords(D2DrlgAct act, int x, int y) {
        return act != null && act.getDrlg() != null
                ? DrlgActivate.streamRoomAtCoords(act.getDrlg(), x, y)
                : null;
    }

    /** D2Common #10069. Rooms containing a client take priority over sight-only rooms. */
    public static D2ActiveRoom getARoomInClientSight(D2DrlgAct act) {
        return act != null && act.getDrlg() != null
                ? DrlgActivate.getARoomInClientSight(act.getDrlg())
                : null;
    }

    /** D2Common #10070: advances the native status-list iteration. */
    public static D2ActiveRoom getARoomInSightButWithoutClient(
            D2DrlgAct act, D2ActiveRoom room) {
        return act != null && act.getDrlg() != null && room != null
                ? DrlgActivate.getARoomInSightButWithoutClient(
                        act.getDrlg(), room.getPDrlgRoom())
                : null;
    }

    public static void getRGBIntensityFromRoom(D2ActiveRoom room,
            byte[] intensity, byte[] red, byte[] green, byte[] blue) {
        DrlgDrlgRoom.getRGBIntensityFromRoomEx(
                room != null ? room.getPDrlgRoom() : null,
                intensity, red, green, blue);
    }

    /** D2Common #10071: server-side eligibility for releasing a streamed room. */
    public static boolean testRoomCanUnTile(D2DrlgAct act, D2ActiveRoom room) {
        if (act == null || room == null || act.isClient()) {
            return false;
        }
        return DrlgActivate.testRoomCanUnTile(room.getPDrlgRoom());
    }

    /** D2Common #10072 returns true for ROOMSTATUS_UNTILE or the COUNT sentinel. */
    public static boolean getRoomStatusFlags(D2ActiveRoom room) {
        return room != null
                && DrlgActivate.getRoomStatusFlags(room.getPDrlgRoom()) >= 3;
    }

    /** D2Common #10073. The native meaning of active-room flag bit 0 is unknown. */
    public static boolean areAllNearRoomsFlagged(D2ActiveRoom room) {
        if (room == null || room.getPDrlgRoom() == null
                || room.getNNumRooms() != room.getPDrlgRoom().getNRoomsNear()) {
            return false;
        }
        for (D2ActiveRoom nearRoom : getAdjacentRoomsListFromRoom(room)) {
            if (nearRoom == null || (nearRoom.getDwFlags() & ACTIVE_ROOM_FLAG_1) == 0) {
                return false;
            }
        }
        return true;
    }

    /** D2Common #10074. */
    public static boolean getActiveRoomFlag2(D2ActiveRoom room) {
        return room != null && (room.getDwFlags() & ACTIVE_ROOM_FLAG_2) != 0;
    }

    /** D2Common #10075. */
    public static void setActiveRoomFlag2(D2ActiveRoom room, boolean set) {
        if (room == null) {
            return;
        }
        room.setDwFlags(set
                ? room.getDwFlags() | ACTIVE_ROOM_FLAG_2
                : room.getDwFlags() & ~ACTIVE_ROOM_FLAG_2);
    }

    /** D2Common #10084. */
    public static boolean getActiveRoomFlag4(D2ActiveRoom room) {
        return room != null && (room.getDwFlags() & ACTIVE_ROOM_FLAG_4) != 0;
    }

    /** D2Common #10081: inactivity counter used before freeing room tiles. */
    public static int getTileCountFromRoom(D2ActiveRoom room) {
        if (room == null) {
            return 0;
        }
        if (room.getNNumClients() != 0) {
            room.setNTileCount(0);
            return 0;
        }
        room.setNTileCount(room.getNTileCount() + 1);
        return room.getNTileCount();
    }

    public static void toggleHasPortalFlag(D2ActiveRoom room, boolean reset) {
        if (room != null) {
            DrlgActivate.toggleHasPortalFlag(room.getPDrlgRoom(), reset);
        }
    }

    private static void setCount(int[] output, int count) {
        if (output != null && output.length > 0) {
            output[0] = count;
        }
    }

    /** D2Common #10008 wrapper around DRLGWARP_ToggleRoomTilesEnableFlag. */
    public static void toggleRoomTilesEnableFlag(D2ActiveRoom room, boolean enabled) {
        if (room != null) {
            DrlgDrlgWarp.toggleRoomTilesEnableFlag(room.getPDrlgRoom(), enabled);
        }
    }

    /** D2Common #10091 wrapper around DRLGWARP_UpdateWarpRoomSelect. */
    public static void updateWarpRoomSelect(D2ActiveRoom room, int levelId) {
        if (room != null) {
            DrlgDrlgWarp.updateWarpRoomSelect(room.getPDrlgRoom(), levelId);
        }
    }

    /** D2Common #10092 wrapper around DRLGWARP_UpdateWarpRoomDeselect. */
    public static void updateWarpRoomDeselect(D2ActiveRoom room, int levelId) {
        if (room != null) {
            DrlgDrlgWarp.updateWarpRoomDeselect(room.getPDrlgRoom(), levelId);
        }
    }
    
    /**
     * 从房间移除活动房间
     * 对应 C++ DUNGEON_RemoveRoomFromAct
     * 
     * 功能：
     * 1. 从房间链表中移除房间
     * 2. 更新相关引用
     * 3. 释放房间资源（如果需要）
     * 
     * @param drlgRoom Drlg 房间对象
     */
    public static void removeRoomFromAct(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getRoom() == null) return;
        D2DrlgAct act = drlgRoom.getRoom().getAct();
        if (act == null && drlgRoom.getLevel() != null
                && drlgRoom.getLevel().getDrlg() != null) {
            act = drlgRoom.getLevel().getDrlg().getAct();
        }
        removeRoomFromAct(act, drlgRoom.getRoom());
    }

    /** Exact active-room unlink semantics of D2Common {@code DUNGEON_RemoveRoomFromAct}. */
    public static void removeRoomFromAct(D2DrlgAct act, D2ActiveRoom room) {
        if (act == null || room == null) return;
        D2ActiveRoom previous = null;
        D2ActiveRoom current = act.getRoom();
        while (current != null && current != room) {
            previous = current;
            current = current.getRoomNext();
        }
        if (current == null) return;

        if (previous == null) {
            act.setRoom(current.getRoomNext());
        } else {
            previous.setRoomNext(current.getRoomNext());
        }
        for (D2ActiveRoom nearRoom : getAdjacentRoomsListFromRoom(current)) {
            if (nearRoom != null && nearRoom != current) {
                removeAdjacentRoom(nearRoom, current);
            }
        }
        current.setPpRoomList(null);
        current.setNNumRooms(0);
        current.setRoomNext(null);
        current.setAct(null);
        if (current.getPDrlgRoom() != null) current.getPDrlgRoom().setRoom(null);
    }
    
    /**
     * 从房间获取房间扩展对象
     * @param drlgRoom Drlg 房间对象
     * @return 房间扩展对象，如果不存在返回 null
     */
    public static D2ActiveRoom getRoomExFromRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return null;
        }
        
        // 返回房间的活动房间对象
        return drlgRoom.getRoom();
    }
    
    /**
     * 从活动房间获取 Drlg 房间对象
     * @param activeRoom 活动房间对象
     * @return Drlg 房间对象，如果不存在返回 null
     */
    public static D2DrlgRoom getDrlgRoomFromActiveRoom(D2ActiveRoom activeRoom) {
        if (activeRoom == null) {
            return null;
        }
        
        // 从活动房间获取关联的 Drlg 房间
        return activeRoom.getPDrlgRoom();
    }

    /** Native signature alias: active room to its DRLG room. */
    public static D2DrlgRoom getRoomExFromRoom(D2ActiveRoom activeRoom) {
        return activeRoom != null ? activeRoom.getPDrlgRoom() : null;
    }

    public static D2ActiveRoom getRoomFromAct(D2DrlgAct act) {
        return act != null ? act.getRoom() : null;
    }

    public static int getLevelIdFromRoom(D2ActiveRoom room) {
        return room != null ? DrlgDrlgRoom.getLevelId(room.getPDrlgRoom()) : 0;
    }

    public static int getWarpDestinationLevel(D2ActiveRoom room, int sourceLevel) {
        return room != null
                ? DrlgDrlgRoom.getWarpDestinationLevel(room.getPDrlgRoom(), sourceLevel)
                : 0;
    }

    public static int getLevelIdFromPopulatedRoom(D2ActiveRoom room) {
        return room != null
                ? DrlgDrlgRoom.getLevelIdFromPopulatedRoom(room.getPDrlgRoom()) : 0;
    }

    public static boolean hasWaypoint(D2ActiveRoom room) {
        return room != null && DrlgDrlgRoom.hasWaypoint(room.getPDrlgRoom());
    }

    public static String getPickedLevelPrestFilePathFromRoom(D2ActiveRoom room) {
        return room != null
                ? DrlgDrlgRoom.getPickedLevelPrestFilePathFromRoomEx(room.getPDrlgRoom())
                : null;
    }

    public static boolean checkLOSDraw(D2ActiveRoom room) {
        return room != null && DrlgDrlgRoom.checkLOSDraw(room.getPDrlgRoom());
    }

    public static D2DrlgEnvironment getEnvironmentFromAct(D2DrlgAct act) {
        return act != null ? act.getEnvironment() : null;
    }

    public static D2DrlgStrc getDrlgFromAct(D2DrlgAct act) {
        return act != null ? act.getDrlg() : null;
    }

    public static int getInitSeedFromAct(D2DrlgAct act) {
        return act != null ? act.getInitSeed() : 0;
    }

    public static boolean isTownLevelId(int levelId) {
        return DrlgDrlg.isTownLevel(levelId);
    }

    public static boolean isRoomInTown(D2ActiveRoom room) {
        return room != null && isTownLevelId(getLevelIdFromRoom(room));
    }

    public static int getTownLevelIdFromActNo(int act) {
        if (act < 0 || act >= D2LevelIds.TOWN_LEVEL_IDS.length) {
            throw new IllegalArgumentException("Invalid act number: " + act);
        }
        return D2LevelIds.TOWN_LEVEL_IDS[act];
    }

    public static int getTownLevelIdFromAct(D2DrlgAct act) {
        return act != null ? act.getTownId() : 0;
    }

    /** D2Common #10087: returns the native raw 0x80 outdoor-room flag. */
    public static int getOutdoorRoomFlag80(D2ActiveRoom room) {
        return room != null
                ? DrlgDrlgRoom.getOutdoorRoomFlag80(room.getPDrlgRoom())
                : 0;
    }

    /** D2Common #10090. The native query initializes an absent level as a side effect. */
    public static int getNumberOfPopulatedRoomsInLevel(D2DrlgAct act, int levelId) {
        return act != null && act.getDrlg() != null
                ? DrlgDrlg.getNumberOfPopulatedRoomsInLevel(act.getDrlg(), levelId)
                : 0;
    }

    /**
     * D2Common #10025 value-copy form. Native exposes 19 contiguous integers:
     * X coordinates [0..8], Y coordinates [9..17], then the populated count [18].
     */
    public static int[] getWarpCoordinatesFromRoom(D2ActiveRoom room) {
        D2DrlgRoom drlgRoom = room != null ? room.getPDrlgRoom() : null;
        D2DrlgLevel level = drlgRoom != null ? drlgRoom.getLevel() : null;
        if (level == null) return null;

        int[] coordinates = new int[19];
        int[] warpX = level.getNRoomCenterWarpX();
        int[] warpY = level.getNRoomCenterWarpY();
        if (warpX != null) {
            System.arraycopy(warpX, 0, coordinates, 0, Math.min(9, warpX.length));
        }
        if (warpY != null) {
            System.arraycopy(warpY, 0, coordinates, 9, Math.min(9, warpY.length));
        }
        coordinates[18] = level.getNRoomCoords();
        return coordinates;
    }

    /** D2Common #10047. */
    public static int getHoradricStaffTombLevelId(D2DrlgAct act) {
        return act != null
                ? DrlgDrlg.getHoradricStaffTombLevelId(act.getDrlg())
                : 0;
    }

    /** D2Common #10095: resolve a logical coordinate-list index across active neighbors. */
    public static int getRoomCoordListIndex(D2ActiveRoom room, int x, int y) {
        if (room == null) return 0;
        if (areSubtileCoordinatesInsideRoom(room.getCoords(), x, y)) {
            return DrlgDrlgLogic.getRoomCoordListIndex(room.getPDrlgRoom(), x, y);
        }
        for (D2ActiveRoom adjacent : getAdjacentRoomsListFromRoom(room)) {
            if (adjacent != null
                    && areSubtileCoordinatesInsideRoom(adjacent.getCoords(), x, y)) {
                return DrlgDrlgLogic.getRoomCoordListIndex(
                        adjacent.getPDrlgRoom(), x, y);
            }
        }
        return 0;
    }

    /** D2Common #10096: query this room's logical coordinate list at a subtile position. */
    public static D2RoomCoordListStrc getRoomCoordListAt(D2ActiveRoom room, int x, int y) {
        return room != null
                ? DrlgDrlgLogic.sub_6FD77110(room.getPDrlgRoom(), x, y)
                : null;
    }

    /** D2Common #10097: returns the head of this room's native-order coordinate-list chain. */
    public static D2RoomCoordListStrc getRoomCoordList(D2ActiveRoom room) {
        return room != null ? DrlgDrlgLogic.getRoomCoordList(room.getPDrlgRoom()) : null;
    }

    /** D2Common #10098: expands set portal bits in native portal-table order. */
    public static int[] getPortalLevelArrayFromPortalFlags(int flags) {
        int[] portalLevels = DataTbls.getPortalLevels();
        if (portalLevels.length > Integer.SIZE) {
            throw new IllegalStateException(
                    "Portal level table exceeds 32-bit native flag capacity: "
                            + portalLevels.length);
        }
        int count = 0;
        for (int i = 0; i < portalLevels.length; i++) {
            if ((flags & (1 << i)) != 0) count++;
        }
        int[] levels = new int[count];
        int levelIndex = 0;
        for (int i = 0; i < portalLevels.length; i++) {
            if ((flags & (1 << i)) != 0) levels[levelIndex++] = portalLevels[i];
        }
        return levels;
    }

    /** D2Common #10099: maps a portal-enabled level id back to its single native flag bit. */
    public static int getPortalFlagFromLevelId(int portalLevelId) {
        int[] portalLevels = DataTbls.getPortalLevels();
        int count = Math.min(portalLevels.length, Integer.SIZE);
        for (int i = 0; i < count; i++) {
            if (portalLevels[i] == portalLevelId) return 1 << i;
        }
        return 0;
    }

    /** Returns a value copy like native {@code DUNGEON_GetRoomCoordinates}. */
    public static D2DrlgCoords getRoomCoordinates(D2ActiveRoom room) {
        return room != null ? new D2DrlgCoords(room.getCoords()) : new D2DrlgCoords();
    }

    /** Returns only the populated prefix of the native adjacent-room array. */
    public static D2ActiveRoom[] getAdjacentRoomsListFromRoom(D2ActiveRoom room) {
        if (room == null) return new D2ActiveRoom[0];
        return Arrays.copyOf(room.getPpRoomList(), room.getNNumRooms());
    }

    public static D2ActiveRoom getAdjacentRoomByTileCoordinates(
            D2ActiveRoom room, int x, int y) {
        for (D2ActiveRoom adjacent : getAdjacentRoomsListFromRoom(room)) {
            if (areTileCoordinatesInsideRoom(adjacent, x, y)) return adjacent;
        }
        return null;
    }

    /** D2Common #10049: visits adjacent rooms in native array order until false. */
    public static <T> void callRoomCallback(
            D2ActiveRoom room, RoomCallback<T> callback, T args) {
        if (room == null || callback == null) return;
        for (D2ActiveRoom adjacent : getAdjacentRoomsListFromRoom(room)) {
            if (!callback.visit(adjacent, args)) break;
        }
    }

    /**
     * D2Common #10052 value-copy form. Array order is left, top, right, bottom.
     */
    public static int[] getRoomDrawRect(D2ActiveRoom room) {
        if (room == null) return new int[4];
        int[] x = new int[1];
        int[] y = new int[1];
        int[] rect = new int[4];
        gameToClientTileDrawPositionCoords(
                room.getNTileXPos(), room.getNTileYPos(), x, y);
        rect[1] = y[0];
        gameToClientTileDrawPositionCoords(
                room.getNTileXPos(), room.getNTileYPos() + room.getNTileHeight(), x, y);
        rect[0] = x[0];
        gameToClientTileDrawPositionCoords(
                room.getNTileXPos() + room.getNTileWidth(), room.getNTileYPos(), x, y);
        rect[2] = x[0];
        gameToClientTileDrawPositionCoords(
                room.getNTileXPos() + room.getNTileWidth(),
                room.getNTileYPos() + room.getNTileHeight(), x, y);
        rect[3] = y[0];
        return rect;
    }

    /** D2Common #10053 value-copy form. Array order is left, top, right, bottom. */
    public static int[] getSubtileRect(D2ActiveRoom room) {
        if (room == null) return new int[4];
        D2DrlgCoords coords = room.getCoords();
        return new int[] {
            coords.getNSubtileX(),
            coords.getNSubtileY(),
            coords.getNSubtileX() + coords.getNSubtileWidth(),
            coords.getNSubtileY() + coords.getNSubtileHeight()
        };
    }

    /** D2Common #10048, retained only for binary-behavior parity with the broken export. */
    public static int checkRoomsOverlappingBroken(
            D2ActiveRoom primary, D2ActiveRoom ignoredSecondary) {
        if (primary == null) return 0;
        int x = primary.getNTileXPos();
        int y = primary.getNTileYPos();
        int right = x + primary.getNTileWidth();
        int bottom = y + primary.getNTileHeight();
        if (right >= x && bottom >= y) {
            if (x == right) return 1;
            if (y == bottom) return 3;
            return 4;
        }
        return 0;
    }

    /** Native header helper: resolve a subtile position from a room and its adjacent rooms. */
    public static D2ActiveRoom getRoomAtPosition(D2ActiveRoom room, int x, int y) {
        if (room == null) return null;
        if (areSubtileCoordinatesInsideRoom(room.getCoords(), x, y)) return room;
        for (D2ActiveRoom adjacent : getAdjacentRoomsListFromRoom(room)) {
            if (adjacent != null
                    && areSubtileCoordinatesInsideRoom(adjacent.getCoords(), x, y)) {
                return adjacent;
            }
        }
        return null;
    }

    public static D2ActiveRoom findRoomByTileCoordinates(D2DrlgAct act, int x, int y) {
        for (D2ActiveRoom room = act != null ? act.getRoom() : null;
                room != null;
                room = room.getRoomNext()) {
            if (areTileCoordinatesInsideRoom(room, x, y)) return room;
        }
        return null;
    }

    public static D2ActiveRoom findRoomBySubtileCoordinates(D2DrlgAct act, int x, int y) {
        for (D2ActiveRoom room = act != null ? act.getRoom() : null;
                room != null;
                room = room.getRoomNext()) {
            if (areSubtileCoordinatesInsideRoom(room.getCoords(), x, y)) return room;
        }
        return null;
    }
    
    /**
     * 创建房间
     * 对应 C++ DUNGEON_CreateRoom
     * 
     * 功能：
     * 1. 分配活动房间对象
     * 2. 初始化房间数据
     * 3. 关联 DrlgRoom 和 ActiveRoom
     * 4. 初始化房间的基本属性（坐标、尺寸等）
     * 
     * @param drlgRoom Drlg 房间对象
     * @return 创建的活动房间对象，如果创建失败返回 null
     */
    public static D2ActiveRoom createRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return null;
        }
        
        // 检查房间是否已经有活动房间对象
        D2ActiveRoom existingRoom = drlgRoom.getRoom();
        if (existingRoom != null) {
            D2Log.debug("DUNGEON_CreateRoom: Room already has an active room object, returning existing");
            return existingRoom;
        }
        
        // 分配活动房间对象
        D2ActiveRoom activeRoom = new D2ActiveRoom();
        
        // 初始化房间数据
        // 1. 关联 DrlgRoom 和 ActiveRoom
        activeRoom.setPDrlgRoom(drlgRoom);
        drlgRoom.setRoom(activeRoom);
        
        // 2. 设置房间的基本属性
        activeRoom.setNTileXPos(drlgRoom.getNTileXPos());
        activeRoom.setNTileYPos(drlgRoom.getNTileYPos());
        activeRoom.setNTileWidth(drlgRoom.getNTileWidth());
        activeRoom.setNTileHeight(drlgRoom.getNTileHeight());
        
        // 3. 设置房间ID（可以使用房间的哈希值或其他唯一标识）
        // 注意：实际实现可能需要从 Level 或 Act 获取唯一的房间ID
        int roomId = (drlgRoom.getNTileXPos() << 16) | drlgRoom.getNTileYPos();
        activeRoom.setNRoomId(roomId);
        
        // 4. 初始化房间标志
        activeRoom.setDwFlags(0);
        
        // 5. 初始化单位列表和对象列表（占位符，实际需要从 DrlgRoom 获取）
        // activeRoom.setPUnits(...);
        // activeRoom.setPObjects(...);
        
        D2Log.debug("DUNGEON_CreateRoom: Created active room for drlgRoom at (" + 
                    drlgRoom.getNTileXPos() + ", " + drlgRoom.getNTileYPos() + ")");
        
        return activeRoom;
    }
    
    /**
     * 坐标转换：从世界坐标转换为瓦片坐标
     * 对应 C++ DUNGEON_WorldToTile
     * 
     * Diablo 2 的坐标系统：
     * - 世界坐标：像素坐标（每个瓦片是 5x5 像素）
     * - 瓦片坐标：瓦片网格坐标
     * 
     * @param worldX 世界X坐标
     * @param worldY 世界Y坐标
     * @return 瓦片坐标数组 [tileX, tileY]
     */
    public static int[] worldToTile(int worldX, int worldY) {
        // Diablo 2 中，每个瓦片是 5x5 像素
        // 瓦片坐标 = 世界坐标 / 5
        int tileX = worldX / 5;
        int tileY = worldY / 5;
        
        return new int[]{tileX, tileY};
    }
    
    /**
     * 坐标转换：从瓦片坐标转换为世界坐标
     * 对应 C++ DUNGEON_TileToWorld
     * 
     * @param tileX 瓦片X坐标
     * @param tileY 瓦片Y坐标
     * @return 世界坐标数组 [worldX, worldY]
     */
    public static int[] tileToWorld(int tileX, int tileY) {
        // Diablo 2 中，每个瓦片是 5x5 像素
        // 世界坐标 = 瓦片坐标 * 5
        int worldX = tileX * 5;
        int worldY = tileY * 5;
        
        return new int[]{worldX, worldY};
    }
    
    /**
     * 检查坐标是否在房间内
     * @param drlgRoom Drlg 房间对象
     * @param tileX 瓦片X坐标
     * @param tileY 瓦片Y坐标
     * @return 如果坐标在房间内返回 true，否则返回 false
     */
    public static boolean isCoordinateInRoom(D2DrlgRoom drlgRoom, int tileX, int tileY) {
        if (drlgRoom == null) {
            return false;
        }
        
        int roomX = drlgRoom.getNTileXPos();
        int roomY = drlgRoom.getNTileYPos();
        int roomWidth = drlgRoom.getNTileWidth();
        int roomHeight = drlgRoom.getNTileHeight();
        
        return tileX >= roomX && tileX < roomX + roomWidth &&
               tileY >= roomY && tileY < roomY + roomHeight;
    }
    
    /**
     * 获取房间的中心坐标
     * @param drlgRoom Drlg 房间对象
     * @return 中心坐标数组 [centerX, centerY]
     */
    public static int[] getRoomCenter(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return new int[]{0, 0};
        }
        
        int centerX = drlgRoom.getNTileXPos() + drlgRoom.getNTileWidth() / 2;
        int centerY = drlgRoom.getNTileYPos() + drlgRoom.getNTileHeight() / 2;
        
        return new int[]{centerX, centerY};
    }
    
    /**
     * 坐标转换：从游戏瓦片坐标转换为子瓦片坐标
     * 对应 C++ DUNGEON_GameTileToSubtileCoords
     * 
     * Diablo 2 的坐标系统：
     * - 游戏瓦片坐标：游戏逻辑使用的瓦片坐标
     * - 子瓦片坐标：更细粒度的坐标（每个游戏瓦片包含 5x5 个子瓦片）
     * 
     * @param x X坐标（输入输出参数，输入为游戏瓦片坐标，输出为子瓦片坐标）
     * @param y Y坐标（输入输出参数，输入为游戏瓦片坐标，输出为子瓦片坐标）
     */
    public static void gameTileToSubtileCoords(int[] x, int[] y) {
        if (x != null && x.length > 0) {
            x[0] = x[0] * 5; // 每个游戏瓦片 = 5 个子瓦片
        }
        if (y != null && y.length > 0) {
            y[0] = y[0] * 5;
        }
    }
    
    /**
     * 坐标转换：从游戏瓦片坐标转换为客户端坐标
     * 对应 C++ DUNGEON_GameTileToClientCoords
     * 
     * Diablo 2 的坐标系统：
     * - 游戏瓦片坐标：游戏逻辑使用的瓦片坐标
     * - 客户端坐标：客户端显示使用的坐标（考虑等距投影）
     * 
     * @param x X坐标（输入输出参数，输入为游戏瓦片坐标，输出为客户端坐标）
     * @param y Y坐标（输入输出参数，输入为游戏瓦片坐标，输出为客户端坐标）
     */
    public static void gameTileToClientCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int tileX = x[0];
        int tileY = y[0];
        x[0] = 80 * (tileX - tileY);
        y[0] = 40 * (tileX + tileY);
    }

    /** D2Common {@code DUNGEON_ClientToGameTileCoords}. */
    public static void clientToGameTileCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int clientX = x[0];
        int clientY = y[0];
        x[0] = (2 * clientY + clientX) / 160;
        y[0] = (2 * clientY - clientX) / 160;
    }

    /** D2Common {@code DUNGEON_GameSubtileToClientCoords}. */
    public static void gameSubtileToClientCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int subtileX = x[0];
        int subtileY = y[0];
        x[0] = 16 * (subtileX - subtileY);
        y[0] = 8 * (subtileX + subtileY);
    }

    /** D2Common {@code DUNGEON_ClientToGameSubtileCoords}. */
    public static void clientToGameSubtileCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int clientX = x[0];
        int clientY = y[0];
        x[0] = (2 * clientY + clientX) / 32;
        y[0] = (2 * clientY - clientX) / 32;
    }

    /** D2Common {@code DUNGEON_GameToClientCoords}. */
    public static void gameToClientCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int gameX = x[0];
        int gameY = y[0];
        x[0] = (gameX - gameY) / 2;
        y[0] = (gameX + gameY) / 4;
    }

    /** D2Common {@code DUNGEON_ClientToGameCoords}. */
    public static void clientToGameCoords(int[] x, int[] y) {
        if (!hasCoordinates(x, y)) return;
        int clientX = x[0];
        int clientY = y[0];
        x[0] = 2 * clientY + clientX;
        y[0] = 2 * clientY - clientX;
    }

    /** D2Common {@code DUNGEON_ClientTileDrawPositionToGameCoords}. */
    public static void clientTileDrawPositionToGameCoords(
            int clientX, int clientY, int[] gameX, int[] gameY) {
        if (!hasCoordinates(gameX, gameY)) return;
        gameX[0] = nativeDrawCoordinate(2 * clientY + clientX, 160);
        gameY[0] = nativeDrawCoordinate(2 * clientY - clientX, 160);
    }

    /** D2Common {@code DUNGEON_GameToClientTileDrawPositionCoords}. */
    public static void gameToClientTileDrawPositionCoords(
            int gameX, int gameY, int[] clientX, int[] clientY) {
        if (!hasCoordinates(clientX, clientY)) return;
        clientX[0] = 80 * (gameX - gameY) - 80;
        clientY[0] = 40 * (gameX + gameY) + 80;
    }

    /** D2Common {@code DUNGEON_ClientSubileDrawPositionToGameCoords}. */
    public static void clientSubtileDrawPositionToGameCoords(
            int clientX, int clientY, int[] gameX, int[] gameY) {
        if (!hasCoordinates(gameX, gameY)) return;
        gameX[0] = nativeDrawCoordinate(2 * clientY + clientX, 32);
        gameY[0] = nativeDrawCoordinate(2 * clientY - clientX, 32);
    }

    /** D2Common {@code DUNGEON_GameToClientSubtileDrawPositionCoords}. */
    public static void gameToClientSubtileDrawPositionCoords(
            int gameX, int gameY, int[] clientX, int[] clientY) {
        if (!hasCoordinates(clientX, clientY)) return;
        clientX[0] = 16 * (gameX - gameY) - 16;
        clientY[0] = 8 * (gameX + gameY) + 16;
    }

    private static int nativeDrawCoordinate(int value, int divisor) {
        return value >= 0 ? value / divisor : value / divisor - 1;
    }

    private static boolean hasCoordinates(int[] x, int[] y) {
        return x != null && y != null && x.length > 0 && y.length > 0;
    }

    /** D2Common {@code DUNGEON_DoRoomsTouchOrOverlap}; touching edges count as true. */
    public static boolean doRoomsTouchOrOverlap(D2ActiveRoom first, D2ActiveRoom second) {
        if (first == null || second == null) return false;
        return first.getNTileXPos() <= second.getNTileXPos() + second.getNTileWidth()
                && first.getNTileXPos() + first.getNTileWidth() >= second.getNTileXPos()
                && first.getNTileYPos() <= second.getNTileYPos() + second.getNTileHeight()
                && first.getNTileYPos() + first.getNTileHeight() >= second.getNTileYPos();
    }

    /** D2Common {@code DUNGEON_AreTileCoordinatesInsideRoom}; maximum edges are exclusive. */
    public static boolean areTileCoordinatesInsideRoom(D2ActiveRoom room, int x, int y) {
        if (room == null) return false;
        return x >= room.getNTileXPos()
                && x < room.getNTileXPos() + room.getNTileWidth()
                && y >= room.getNTileYPos()
                && y < room.getNTileYPos() + room.getNTileHeight();
    }

    /** D2Common {@code DUNGEON_AreSubtileCoordinatesInsideRoom}. */
    public static boolean areSubtileCoordinatesInsideRoom(
            com.d2moo.common.drlg.D2DrlgCoords coords, int x, int y) {
        if (coords == null) return false;
        return x >= coords.getNSubtileX()
                && x < coords.getNSubtileX() + coords.getNSubtileWidth()
                && y >= coords.getNSubtileY()
                && y < coords.getNSubtileY() + coords.getNSubtileHeight();
    }
    
    /**
     * 将游戏瓦片坐标转换为子瓦片坐标（修改传入的 D2DrlgCoords 对象）
     * @param coords 坐标对象（会被修改）
     */
    public static void gameTileToSubtileCoords(com.d2moo.common.drlg.D2DrlgCoords coords) {
        if (coords == null) {
            return;
        }
        
        coords.setNSubtileX(coords.getNTileXPos() * 5);
        coords.setNSubtileY(coords.getNTileYPos() * 5);
        coords.setNSubtileWidth(coords.getNTileWidth() * 5);
        coords.setNSubtileHeight(coords.getNTileHeight() * 5);
    }
    
    /**
     * 分配房间
     * 对应 C++ DUNGEON_AllocRoom
     * 
     * 功能：
     * 1. 创建活动房间对象
     * 2. 初始化房间数据（坐标、瓦片、标志等）
     * 3. 建立双向关联（DrlgRoom 和 ActiveRoom）
     * 4. 设置房间ID
     * 
     * @param act Act 对象；非空时将活动房间挂入 Act 房间链
     * @param drlgRoom Drlg 房间对象
     * @param coords 坐标对象
     * @param tiles 瓦片数据（可选）
     * @param seed 活动房间随机种子
     * @param flags 标志
     * @return 创建的活动房间对象，如果创建失败返回 null
     */
    public static D2ActiveRoom allocRoom(D2DrlgAct act, D2DrlgRoom drlgRoom,
            D2DrlgCoords coords,
            D2DrlgRoomTilesStrc tiles,
            int seed, int flags) {
        if (drlgRoom == null) {
            D2Log.debug("DUNGEON_AllocRoom: DrlgRoom is null, cannot allocate room");
            return null;
        }
        
        // 检查房间是否已经有活动房间对象
        D2ActiveRoom existingRoom = drlgRoom.getRoom();
        if (existingRoom != null) {
            D2Log.debug("DUNGEON_AllocRoom: Room already has an active room object, returning existing");
            return existingRoom;
        }
        
        // 创建活动房间对象
        D2ActiveRoom room = new D2ActiveRoom();
        
        // 建立双向关联
        room.setPDrlgRoom(drlgRoom);
        drlgRoom.setRoom(room);
        
        // Native memcpy preserves both tile and subtile coordinate systems.
        if (coords != null) {
            room.setCoords(coords);
        } else {
            room.setNTileXPos(drlgRoom.getNTileXPos());
            room.setNTileYPos(drlgRoom.getNTileYPos());
            room.setNTileWidth(drlgRoom.getNTileWidth());
            room.setNTileHeight(drlgRoom.getNTileHeight());
        }
        room.setPRoomTiles(tiles);
        D2Seed roomSeed = new D2Seed();
        Seed.initLowSeed(roomSeed, seed);
        room.setSeed(roomSeed);
        room.setAct(act);
        if (act != null) {
            room.setRoomNext(act.getRoom());
            act.setRoom(room);
            act.setHasPendingRoomsUpdates(true);
        }
        rebuildAdjacentRoomList(room);
        for (D2ActiveRoom adjacent : getAdjacentRoomsListFromRoom(room)) {
            if (adjacent != room) rebuildAdjacentRoomList(adjacent);
        }

        // 设置房间ID（可以使用房间的哈希值或其他唯一标识）
        int roomId = (room.getNTileXPos() << 16) | room.getNTileYPos();
        room.setNRoomId(roomId);
        
        // 设置标志
        room.setDwFlags(flags);
        
        // 注意：瓦片数据（tiles）和单位列表（pUnits）、对象列表（pObjects）
        // 可以在后续初始化，当前实现为基本框架
        
        D2Log.debug("DUNGEON_AllocRoom: Allocated active room for drlgRoom at (" + 
                    room.getNTileXPos() + ", " + room.getNTileYPos() + "), ID: " + roomId);

        D2ActCallback callback = act != null ? act.getPfnActCallBack() : null;
        if (callback != null) callback.onRoomAllocated(room);
        
        return room;
    }

    private static void rebuildAdjacentRoomList(D2ActiveRoom room) {
        D2DrlgRoom drlgRoom = room != null ? room.getPDrlgRoom() : null;
        D2DrlgRoom[] nearRooms = drlgRoom != null ? drlgRoom.getPpRoomsNear() : null;
        int capacity = nearRooms != null
                ? Math.min(drlgRoom.getNRoomsNear(), nearRooms.length)
                : 0;
        D2ActiveRoom[] activeRooms = new D2ActiveRoom[capacity];
        int count = DrlgDrlgRoom.reorderNearRoomList(drlgRoom, activeRooms);
        room.setPpRoomList(activeRooms);
        room.setNNumRooms(count);
    }

    private static void removeAdjacentRoom(D2ActiveRoom room, D2ActiveRoom removed) {
        D2ActiveRoom[] rooms = room.getPpRoomList();
        int count = room.getNNumRooms();
        for (int i = 0; i < count; i++) {
            if (rooms[i] == removed) {
                rooms[i] = rooms[count - 1];
                rooms[count - 1] = null;
                room.setNNumRooms(count - 1);
                return;
            }
        }
    }
}
