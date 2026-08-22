package com.d2moo.common.dungeon;

import com.d2moo.common.drlg.D2DrlgLevel;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.util.D2Log;

/**
 * DUNGEON 房间和坐标管理模块
 * 对应 C++ 模块：DUNGEON
 * 
 * 注意：这是一个房间和坐标管理模块，负责房间的创建、删除和坐标转换
 * 当前实现提供基础框架和接口，实际逻辑需要后续实现
 */
public class Dungeon {
    
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
        if (drlgRoom == null) {
            return;
        }
        
        // 获取房间所在的 Level
        D2DrlgLevel level = drlgRoom.getLevel();
        if (level == null) {
            D2Log.debug("DUNGEON_RemoveRoomFromAct: Room has no level, cannot remove from act");
            return;
        }
        
        // 获取 Level 的第一个房间（房间链表的头）
        D2DrlgRoom pFirstRoom = level.getFirstRoomEx();
        if (pFirstRoom == null) {
            D2Log.debug("DUNGEON_RemoveRoomFromAct: Level has no rooms, room already removed");
            return;
        }
        
        // 如果是要移除的房间就是第一个房间
        if (pFirstRoom == drlgRoom) {
            // 将第一个房间设置为下一个房间
            level.setFirstRoomEx(drlgRoom.getDrlgRoomNext());
            drlgRoom.setDrlgRoomNext(null);
            D2Log.debug("DUNGEON_RemoveRoomFromAct: Removed first room from level");
            return;
        }
        
        // 遍历房间链表，找到要移除的房间的前一个房间
        D2DrlgRoom pCurrentRoom = pFirstRoom;
        while (pCurrentRoom != null && pCurrentRoom.getDrlgRoomNext() != null) {
            if (pCurrentRoom.getDrlgRoomNext() == drlgRoom) {
                // 找到要移除的房间，更新链表
                pCurrentRoom.setDrlgRoomNext(drlgRoom.getDrlgRoomNext());
                drlgRoom.setDrlgRoomNext(null);
                D2Log.debug("DUNGEON_RemoveRoomFromAct: Removed room from level");
                return;
            }
            pCurrentRoom = pCurrentRoom.getDrlgRoomNext();
        }
        
        // 如果没找到房间，说明房间不在链表中
        D2Log.debug("DUNGEON_RemoveRoomFromAct: Room not found in level's room list");
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
     * 等距投影的瓦片尺寸常量（像素）
     * Diablo 2 使用等距投影，一个瓦片在屏幕上的尺寸
     */
    private static final int TILE_WIDTH_PIXELS = 32;   // 瓦片宽度（像素）
    private static final int TILE_HEIGHT_PIXELS = 16;  // 瓦片高度（像素）
    
    /**
     * 坐标转换：从游戏瓦片坐标转换为客户端坐标
     * 对应 C++ DUNGEON_GameTileToClientCoords
     * 
     * Diablo 2 的坐标系统：
     * - 游戏瓦片坐标：游戏逻辑使用的瓦片坐标
     * - 客户端坐标：客户端显示使用的坐标（考虑等距投影）
     * 
     * 等距投影公式：
     * - clientX = (tileX - tileY) * (tileWidth / 2)
     * - clientY = (tileX + tileY) * (tileHeight / 2)
     * 
     * 在 Diablo 2 中，等距投影使用以下变换：
     * - X 轴：从左上到右下的对角线方向
     * - Y 轴：从右上到左下的对角线方向
     * - 瓦片宽度通常是高度的两倍（32x16 像素）
     * 
     * @param x X坐标（输入输出参数，输入为游戏瓦片坐标，输出为客户端坐标）
     * @param y Y坐标（输入输出参数，输入为游戏瓦片坐标，输出为客户端坐标）
     */
    public static void gameTileToClientCoords(int[] x, int[] y) {
        if (x == null || y == null || x.length == 0 || y.length == 0) {
            return;
        }
        
        // 保存原始瓦片坐标
        int tileX = x[0];
        int tileY = y[0];
        
        // 等距投影变换
        // X 坐标：从左上到右下的对角线方向
        // Y 坐标：从右上到左下的对角线方向
        // 使用标准的等距投影公式
        int clientX = (tileX - tileY) * (TILE_WIDTH_PIXELS / 2);
        int clientY = (tileX + tileY) * (TILE_HEIGHT_PIXELS / 2);
        
        // 更新输出坐标
        x[0] = clientX;
        y[0] = clientY;
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
     * @param act Act 对象（可选，当前未使用）
     * @param drlgRoom Drlg 房间对象
     * @param coords 坐标对象
     * @param tiles 瓦片数据（可选）
     * @param seed 随机种子（可选，当前未使用）
     * @param flags 标志
     * @return 创建的活动房间对象，如果创建失败返回 null
     */
    public static D2ActiveRoom allocRoom(Object act, D2DrlgRoom drlgRoom, 
            com.d2moo.common.drlg.D2DrlgCoords coords, 
            com.d2moo.common.drlg.D2DrlgRoomTilesStrc tiles, 
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
        
        // 设置坐标
        if (coords != null) {
            // 从 coords 设置房间的瓦片坐标
            room.setNTileXPos(coords.getNTileXPos());
            room.setNTileYPos(coords.getNTileYPos());
            room.setNTileWidth(coords.getNTileWidth());
            room.setNTileHeight(coords.getNTileHeight());
        } else if (drlgRoom != null) {
            // 如果 coords 为 null，从 drlgRoom 获取坐标
            room.setNTileXPos(drlgRoom.getNTileXPos());
            room.setNTileYPos(drlgRoom.getNTileYPos());
            room.setNTileWidth(drlgRoom.getNTileWidth());
            room.setNTileHeight(drlgRoom.getNTileHeight());
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
}
