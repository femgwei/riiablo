package com.d2moo.common.dungeon;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;

import java.util.Arrays;

/**
 * DUNGEON 房间和坐标管理模块
 * 对应 C++ 模块：DUNGEON
 * 
 * 注意：这是一个房间和坐标管理模块，负责房间的创建、删除和坐标转换
 * 当前实现提供基础框架和接口，实际逻辑需要后续实现
 */
public class Dungeon {
    /** D2Common #10008 wrapper around DRLGWARP_ToggleRoomTilesEnableFlag. */
    public static void toggleRoomTilesEnableFlag(D2ActiveRoom room, boolean enabled) {
        if (room != null) {
            com.d2moo.common.drlg.DrlgDrlgWarp.toggleRoomTilesEnableFlag(
                    room.getPDrlgRoom(), enabled);
        }
    }

    /** D2Common #10091 wrapper around DRLGWARP_UpdateWarpRoomSelect. */
    public static void updateWarpRoomSelect(D2ActiveRoom room, int levelId) {
        if (room != null) {
            com.d2moo.common.drlg.DrlgDrlgWarp.updateWarpRoomSelect(
                    room.getPDrlgRoom(), levelId);
        }
    }

    /** D2Common #10092 wrapper around DRLGWARP_UpdateWarpRoomDeselect. */
    public static void updateWarpRoomDeselect(D2ActiveRoom room, int levelId) {
        if (room != null) {
            com.d2moo.common.drlg.DrlgDrlgWarp.updateWarpRoomDeselect(
                    room.getPDrlgRoom(), levelId);
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
        
        return room;
    }

    private static void rebuildAdjacentRoomList(D2ActiveRoom room) {
        D2DrlgRoom drlgRoom = room != null ? room.getPDrlgRoom() : null;
        D2DrlgRoom[] nearRooms = drlgRoom != null ? drlgRoom.getPpRoomsNear() : null;
        int capacity = nearRooms != null
                ? Math.min(drlgRoom.getNRoomsNear(), nearRooms.length)
                : 0;
        D2ActiveRoom[] activeRooms = new D2ActiveRoom[capacity];
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            D2ActiveRoom active = nearRooms[i] != null ? nearRooms[i].getRoom() : null;
            if (active != null) activeRooms[count++] = active;
        }
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
