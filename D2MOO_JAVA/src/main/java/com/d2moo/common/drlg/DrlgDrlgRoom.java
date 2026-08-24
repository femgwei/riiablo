package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 房间管理模块
 * 对应 C++ 文件：DrlgDrlgRoom.cpp
 */
public class DrlgDrlgRoom {
    
    /**
     * D2Common.0x6FD771C0
     * 分配房间
     */
    public static D2DrlgRoom allocRoomEx(D2DrlgLevel level, int type) {
        D2DrlgRoom drlgRoom = D2Pool.callocStrcPool(level.getDrlg().getMempool(), D2DrlgRoom.class);
        if (drlgRoom == null) {
            D2Log.warning("Failed to allocate D2DrlgRoom from memory pool");
            return null;
        }
        
        drlgRoom.setLevel(level);
        drlgRoom.setType(type);
        drlgRoom.setRoomStatus(D2DrlgRoomStatus.COUNT); // fRoomStatus = 4
        
        // 初始化种子
        if (drlgRoom.getSeed() == null) {
            drlgRoom.setSeed(new D2Seed());
        }
        Seed.initLowSeed(drlgRoom.getSeed(), (int)Seed.rollRandomNumber(level.getSeed()));
        drlgRoom.setInitSeed((int)Seed.rollRandomNumber(drlgRoom.getSeed()));
        
        // 检查自动地图显示标志
        if ((level.getFlags() & D2DrlgLevelFlags.AUTOMAP_REVEAL) != 0) {
            drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.AUTOMAP_REVEAL);
        }
        
        if (type == D2DrlgType.MAZE.getValue()) {
            DrlgOutRoom.allocDrlgOutdoorRoom(drlgRoom);
        } else if (type == D2DrlgType.PRESET.getValue()) {
            DrlgPreset.allocPresetRoomData(drlgRoom);
        }
        
        return drlgRoom;
    }
    
    /**
     * D2Common.0x6FD77280
     * 重置房间（未命名函数）
     */
    public static void sub_6FD77280(D2DrlgRoom drlgRoom, boolean client, int flags) {
        drlgRoom.setRoom(null);
        drlgRoom.setOtherFlags(flags & 1);
        
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) != 0) {
            DrlgRoomTile.freeRoom(drlgRoom, !client);
        }
    }
    
    /**
     * D2Common.0x6FD772B0
     * 释放房间瓦片
     */
    public static void freeRoomTiles(Object memPool, D2DrlgRoom drlgRoom) {
        D2RoomTile roomTile = drlgRoom.getRoomTiles();
        while (roomTile != null) {
            D2RoomTile next = roomTile.getPNext();
            D2Pool.freePool(memPool, roomTile);
            roomTile = next;
        }
        drlgRoom.setRoomTiles(null);
    }
    
    /**
     * D2Common.0x6FD772F0
     * 释放房间
     */
    public static void freeRoomEx(D2DrlgRoom drlgRoom) {
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        freeRoomTiles(memPool, drlgRoom);
        
        if (drlgRoom.getPpRoomsNear() != null) {
            D2Pool.freePool(memPool, drlgRoom.getPpRoomsNear());
            drlgRoom.setNRoomsNear(0);
            drlgRoom.setPpRoomsNear(null);
        }
        
        if (drlgRoom.getType() == D2DrlgType.MAZE.getValue()) {
            DrlgOutRoom.freeDrlgOutdoorRoom(drlgRoom);
        } else if (drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
            DrlgPreset.freePresetRoomData(drlgRoom);
        }
        
        // 释放预设单位
        D2PresetUnit presetUnit = drlgRoom.getPresetUnits();
        while (presetUnit != null) {
            D2PresetUnit nextPresetUnit = presetUnit.getPNext();
            DrlgPreset.freePresetUnit(memPool, presetUnit);
            presetUnit = nextPresetUnit;
        }
        
        // 释放正交数据
        D2DrlgOrth drlgOrth = drlgRoom.getDrlgOrth();
        while (drlgOrth != null) {
            D2DrlgOrth nextDrlgOrth = drlgOrth.getPNext();
            
            // 处理双向链接的正交数据
            if (drlgOrth.isBInit()) {
                D2DrlgRoom currentRoomEx = drlgOrth.getPDrlgRoom();
                
                // 从当前房间的正交列表中移除
                D2DrlgOrth orth = drlgRoom.getDrlgOrth();
                D2DrlgOrth nextOrth = orth != null ? orth.getPNext() : null;
                
                if (orth != null && orth.isBInit() && orth.getPDrlgRoom() == currentRoomEx) {
                    drlgRoom.setDrlgOrth(nextOrth);
                    D2Pool.freePool(memPool, orth);
                } else {
                    while (nextOrth != null) {
                        if (nextOrth.isBInit() && nextOrth.getPDrlgRoom() == currentRoomEx) {
                            if (orth != null) {
                                orth.setPNext(nextOrth.getPNext());
                            }
                            D2Pool.freePool(memPool, nextOrth);
                            break;
                        }
                        orth = nextOrth;
                        nextOrth = nextOrth.getPNext();
                    }
                }
                
                // 从目标房间的正交列表中移除
                orth = currentRoomEx.getDrlgOrth();
                nextOrth = orth != null ? orth.getPNext() : null;
                
                if (orth != null && orth.isBInit() && orth.getPDrlgRoom() == drlgRoom) {
                    currentRoomEx.setDrlgOrth(nextOrth);
                    D2Pool.freePool(memPool, orth);
                } else {
                    while (nextOrth != null) {
                        if (nextOrth.isBInit() && nextOrth.getPDrlgRoom() == drlgRoom) {
                            if (orth != null) {
                                orth.setPNext(nextOrth.getPNext());
                            }
                            D2Pool.freePool(memPool, nextOrth);
                            break;
                        }
                        orth = nextOrth;
                        nextOrth = nextOrth.getPNext();
                    }
                }
            }
            
            drlgOrth = nextDrlgOrth;
        }
        
        // 释放剩余的正交数据
        drlgOrth = drlgRoom.getDrlgOrth();
        while (drlgOrth != null) {
            D2DrlgOrth nextDrlgOrth = drlgOrth.getPNext();
            D2Pool.freePool(memPool, drlgOrth);
            drlgOrth = nextDrlgOrth;
        }
        
        // 从关卡列表中移除
        D2DrlgRoom currentRoomEx = drlgRoom.getLevel().getFirstRoomEx();
        if (currentRoomEx == drlgRoom) {
            drlgRoom.getLevel().setFirstRoomEx(drlgRoom.getDrlgRoomNext());
            drlgRoom.getLevel().setRooms(drlgRoom.getLevel().getRooms() - 1);
        } else {
            D2DrlgRoom nextRoomEx = currentRoomEx.getDrlgRoomNext();
            while (nextRoomEx != null) {
                if (nextRoomEx == drlgRoom) {
                    currentRoomEx.setDrlgRoomNext(drlgRoom.getDrlgRoomNext());
                    drlgRoom.getLevel().setRooms(drlgRoom.getLevel().getRooms() - 1);
                    break;
                }
                currentRoomEx = nextRoomEx;
                nextRoomEx = currentRoomEx.getDrlgRoomNext();
            }
        }
        
        // 释放瓦片网格和坐标列表
        DrlgRoomTile.freeTileGrid(drlgRoom);
        DrlgDrlgLogic.freeDrlgCoordList(drlgRoom);
        
        // 释放房间本身
        D2Pool.freePool(memPool, drlgRoom);
    }
    
    /**
     * D2Common.0x6FD77930
     * 检查坐标是否在房间内
     */
    public static boolean areXYInsideCoordinates(D2DrlgCoord drlgCoord, int x, int y) {
        return x >= drlgCoord.getNPosX() 
            && y >= drlgCoord.getNPosY() 
            && x < drlgCoord.getNPosX() + drlgCoord.getNWidth() 
            && y < drlgCoord.getNPosY() + drlgCoord.getNHeight();
    }
    
    /**
     * D2Common.0x6FD77980
     * 检查坐标是否在房间内或边界上
     */
    public static boolean areXYInsideCoordinatesOrOnBorder(D2DrlgCoord drlgCoord, int x, int y) {
        return x >= drlgCoord.getNPosX() 
            && y >= drlgCoord.getNPosY() 
            && x <= drlgCoord.getNPosX() + drlgCoord.getNWidth() 
            && y <= drlgCoord.getNPosY() + drlgCoord.getNHeight();
    }
    
    /**
     * D2Common.0x6FD77910
     * 将房间添加到关卡
     */
    public static void addRoomExToLevel(D2DrlgLevel level, D2DrlgRoom drlgRoom) {
        drlgRoom.setDrlgRoomNext(level.getFirstRoomEx());
        level.setFirstRoomEx(drlgRoom);
        level.setRooms(level.getRooms() + 1);
    }
    
    /**
     * D2Common.0x6FD776B0
     * 比较两个正交结构，用于链表排序
     * 返回 true 表示 pDrlgOrth1 应该排在 pDrlgOrth2 之前
     */
    private static boolean sub_6FD776B0(D2DrlgOrth pDrlgOrth1, D2DrlgOrth pDrlgOrth2) {
        if (pDrlgOrth1 == null || pDrlgOrth2 == null) {
            return false;
        }
        
        if (pDrlgOrth1.getNDirection() <= pDrlgOrth2.getNDirection()) {
            if (pDrlgOrth1.getNDirection() == pDrlgOrth2.getNDirection()) {
                D2DrlgCoord pBox1 = pDrlgOrth1.getPBox();
                D2DrlgCoord pBox2 = pDrlgOrth2.getPBox();
                
                if (pBox1 == null || pBox2 == null) {
                    return false;
                }
                
                switch (pDrlgOrth2.getNDirection()) {
                    case 0: // 北
                        if (pBox1.getNPosY() <= pBox2.getNPosY()) {
                            return false;
                        }
                        break;
                    
                    case 1: // 东
                        if (pBox1.getNPosX() >= pBox2.getNPosX()) {
                            return false;
                        }
                        break;
                    
                    case 2: // 南
                        if (pBox1.getNPosY() >= pBox2.getNPosY()) {
                            return false;
                        }
                        break;
                    
                    case 3: // 西
                        if (pBox1.getNPosX() <= pBox2.getNPosX()) {
                            return false;
                        }
                        break;
                    
                    default:
                        return false;
                }
            } else {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD77600
     * 添加正交房间链接
     * 将新节点插入到有序链表中
     */
    public static void addOrth(D2DrlgOrth[] ppDrlgOrth, D2DrlgLevel pLevel, int nDirection, boolean bIsPreset) {
        if (ppDrlgOrth == null || pLevel == null || pLevel.getDrlg() == null) {
            return;
        }
        
        // 分配新的正交结构
        Object memPool = pLevel.getDrlg().getMempool();
        D2DrlgOrth pNew = D2Pool.callocStrcPool(memPool, D2DrlgOrth.class);
        if (pNew == null) {
            pNew = new D2DrlgOrth();
        }
        
        pNew.setPLevel(pLevel);
        pNew.setNDirection((byte)nDirection);
        pNew.setBPreset(bIsPreset);
        pNew.setBInit(false);
        pNew.setPBox(pLevel.getLevelCoords());
        
        // 如果链表为空，直接插入
        if (ppDrlgOrth[0] == null) {
            ppDrlgOrth[0] = pNew;
            return;
        }
        
        // 查找插入位置
        D2DrlgOrth pNext = ppDrlgOrth[0].getPNext();
        D2DrlgOrth pPrevious = ppDrlgOrth[0];
        
        if (pNext != null) {
            // 遍历链表查找插入位置
            while (pNext != null) {
                if (sub_6FD776B0(pNext, pNew)) {
                    break;
                }
                pPrevious = pNext;
                pNext = pNext.getPNext();
            }
        } else {
            // 只有一个节点，检查是否需要插入到前面
            if (sub_6FD776B0(ppDrlgOrth[0], pNew)) {
                pNew.setPNext(ppDrlgOrth[0]);
                ppDrlgOrth[0] = pNew;
                return;
            }
        }
        
        // 插入新节点
        pNew.setPNext(pNext);
        pPrevious.setPNext(pNew);
    }
    
    /**
     * D2Common.0x6FD77A10
     * 获取房间的关卡ID
     */
    public static int getLevelId(D2DrlgRoom drlgRoom) {
        return drlgRoom.getLevel().getLevelId();
    }

    /** D2Common.0x6FD779D0. */
    public static boolean checkLOSDraw(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return false;
        }
        if (drlgRoom.getType() == D2DrlgTypes.DRLGTYPE_MAZE) {
            return true;
        }
        return drlgRoom.getType() == D2DrlgTypes.DRLGTYPE_PRESET
                && (drlgRoom.getFlags() & D2DrlgRoomFlags.NO_LOS_DRAW) != 0;
    }

    /** D2Common.0x6FD77A20. */
    public static int getWarpDestinationLevel(D2DrlgRoom drlgRoom, int sourceLevel) {
        if (drlgRoom == null) {
            return 0;
        }
        int[] destinationLevel = new int[1];
        D2LvlWarpTxt[] warpRecord = new D2LvlWarpTxt[1];
        D2ActiveRoom destination = DrlgDrlgWarp.getDestinationRoom(
                drlgRoom, sourceLevel, destinationLevel, warpRecord);
        D2DrlgRoom destinationRoom = destination != null
                ? destination.getPDrlgRoom() : null;
        return destinationRoom != null && destinationRoom.getLevel() != null
                ? destinationRoom.getLevel().getLevelId() : 0;
    }

    /** D2Common.0x6FD77AB0. */
    public static int getLevelIdFromPopulatedRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getLevel() == null
                || (drlgRoom.getFlags() & D2DrlgRoomFlags.POPULATION_ZERO) != 0) {
            return 0;
        }
        return drlgRoom.getLevel().getLevelId();
    }

    /** D2Common.0x6FD77B20. */
    public static String getPickedLevelPrestFilePathFromRoomEx(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return null;
        }
        return drlgRoom.getType() == D2DrlgTypes.DRLGTYPE_PRESET
                ? DrlgPreset.getPickedLevelPrestFilePathFromRoomEx(drlgRoom)
                : "None";
    }

    /**
     * D2Common.0x6FD77B50. Compacts active near rooms into the caller-owned
     * array and clears the remaining native-capacity slots.
     */
    public static int reorderNearRoomList(
            D2DrlgRoom drlgRoom, D2ActiveRoom[] roomList) {
        if (drlgRoom == null || roomList == null) {
            return 0;
        }
        D2DrlgRoom[] nearRooms = drlgRoom.getPpRoomsNear();
        int nearCount = nearRooms != null
                ? Math.min(drlgRoom.getNRoomsNear(), nearRooms.length) : 0;
        int activeCount = 0;
        for (int i = 0; i < nearCount && activeCount < roomList.length; i++) {
            D2ActiveRoom activeRoom = nearRooms[i] != null ? nearRooms[i].getRoom() : null;
            if (activeRoom != null) {
                roomList[activeCount++] = activeRoom;
            }
        }
        for (int i = activeCount; i < roomList.length; i++) {
            roomList[i] = null;
        }
        return activeCount;
    }

    /** D2Common.0x6FD781A0. */
    public static void getRGBIntensityFromRoomEx(D2DrlgRoom drlgRoom,
            byte[] intensity, byte[] red, byte[] green, byte[] blue) {
        D2LevelDefBin levelDef = drlgRoom != null && drlgRoom.getLevel() != null
                ? DataTbls.getLevelDefRecord(drlgRoom.getLevel().getLevelId()) : null;
        setByteOutput(intensity, levelDef != null ? levelDef.getNIntensity() : 0);
        setByteOutput(red, levelDef != null ? levelDef.getNRed() : 0);
        setByteOutput(green, levelDef != null ? levelDef.getNGreen() : 0);
        setByteOutput(blue, levelDef != null ? levelDef.getNBlue() : 0);
    }

    private static void setByteOutput(byte[] output, int value) {
        if (output != null && output.length > 0) {
            output[0] = (byte) value;
        }
    }
    
    /**
     * D2Common.0x6FD77AF0
     * 检查房间是否有传送点
     */
    public static boolean hasWaypoint(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            D2Log.warning("DRLGROOM_HasWaypoint: drlgRoom is null");
            return false;
        }
        return checkWaypointFlags(drlgRoom) != 0;
    }
    
    /**
     * D2Common.0x6FD77A00
     * 检查传送点标志
     */
    public static int checkWaypointFlags(D2DrlgRoom drlgRoom) {
        return drlgRoom.getFlags() & (D2DrlgRoomFlags.HAS_WAYPOINT | D2DrlgRoomFlags.HAS_WAYPOINT_SMALL);
    }
    
    /**
     * D2Common.0x6FD77BB0
     * 初始化附近房间列表（被 DrlgActivate 依赖）
     */
    public static void sub_6FD77BB0(Object memPool, D2DrlgRoom drlgRoom) {
        D2DrlgRoom[] ppNearRooms = new D2DrlgRoom[30];
        int nRoomsNear = 0;
        
        // 查找附近的房间（距离小于6）
        D2DrlgRoom currentRoomEx = drlgRoom.getLevel().getFirstRoomEx();
        while (currentRoomEx != null) {
            int nX, nY;
            
            if (drlgRoom.getDrlgCoord().getNTileXPos() >= currentRoomEx.getDrlgCoord().getNTileXPos()) {
                nX = drlgRoom.getDrlgCoord().getNTileXPos() 
                    - currentRoomEx.getDrlgCoord().getNTileWidth() 
                    - currentRoomEx.getDrlgCoord().getNTileXPos();
            } else {
                nX = currentRoomEx.getDrlgCoord().getNTileXPos() 
                    - drlgRoom.getDrlgCoord().getNTileWidth() 
                    - drlgRoom.getDrlgCoord().getNTileXPos();
            }
            
            if (drlgRoom.getDrlgCoord().getNTileYPos() >= currentRoomEx.getDrlgCoord().getNTileYPos()) {
                nY = drlgRoom.getDrlgCoord().getNTileYPos() 
                    - currentRoomEx.getDrlgCoord().getNTileHeight() 
                    - currentRoomEx.getDrlgCoord().getNTileYPos();
            } else {
                nY = currentRoomEx.getDrlgCoord().getNTileYPos() 
                    - drlgRoom.getDrlgCoord().getNTileHeight() 
                    - drlgRoom.getDrlgCoord().getNTileYPos();
            }
            
            if (nX < 6 && nY < 6) {
                ppNearRooms[nRoomsNear] = currentRoomEx;
                nRoomsNear++;
            }
            
            currentRoomEx = currentRoomEx.getDrlgRoomNext();
        }
        
        // 排序房间列表
        sortRoomListByPosition(ppNearRooms, nRoomsNear);
        
        // 分配内存并复制
        D2DrlgRoom[] ppRoomsNear = D2Pool.callocArrayPool(memPool, D2DrlgRoom.class, nRoomsNear);
        for (int i = 0; i < nRoomsNear; ++i) {
            ppRoomsNear[i] = ppNearRooms[i];
        }
        
        drlgRoom.setPpRoomsNear(ppRoomsNear);
        drlgRoom.setNRoomsNear(nRoomsNear);
        
        // 处理传送门相关逻辑
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_WARP_MASK) != 0) {
            int nFlags = D2DrlgRoomFlags.HAS_WARP_0;
            int nWarpId = 0;
            
            do {
                if ((nFlags & drlgRoom.getFlags()) != 0) {
                    if (nWarpId >= 8) {
                        D2Log.warning("sub_6FD77BB0: nWarpId >= 8");
                    }
                    
                    int[] pSourceVisArray = getVisArrayFromLevelId(drlgRoom.getLevel().getDrlg(), 
                        drlgRoom.getLevel().getLevelId());
                    
                    D2DrlgLevel pLevel = DrlgDrlg.getLevel(drlgRoom.getLevel().getDrlg(), 
                        pSourceVisArray[nWarpId]);
                    
                    int[] pDestinationVisArray = getVisArrayFromLevelId(drlgRoom.getLevel().getDrlg(), 
                        pSourceVisArray[nWarpId]);
                    int nWarpDestination = DrlgDrlgWarp.getWarpDestinationFromArray(drlgRoom.getLevel(), (byte)nWarpId);
                    
                    if (pLevel.getFirstRoomEx() == null) {
                        DrlgDrlg.initLevel(pLevel);
                    }
                    
                    boolean bSkip = false;
                    if (nWarpDestination != -1) {
                        int nSourceVisCount = 0;
                        int nDestinationVisCount = 0;
                        
                        for (int i = 0; i < nWarpId; ++i) {
                            if (pSourceVisArray[i] == pSourceVisArray[nWarpId]) {
                                ++nSourceVisCount;
                            }
                        }
                        
                        for (int i = 0; i < 8; ++i) {
                            if (pDestinationVisArray[i] == drlgRoom.getLevel().getLevelId()) {
                                if (nSourceVisCount == nDestinationVisCount) {
                                    if (sub_6FD77F00(memPool, drlgRoom, nWarpId, pLevel.getFirstRoomEx(), i, nWarpDestination)) {
                                        bSkip = true;
                                    }
                                    break;
                                }
                                ++nDestinationVisCount;
                            }
                        }
                    }
                    
                    if (!bSkip) {
                        for (int i = 0; i < 8; ++i) {
                            if (pDestinationVisArray[i] == drlgRoom.getLevel().getLevelId() 
                                && sub_6FD77F00(memPool, drlgRoom, nWarpId, pLevel.getFirstRoomEx(), i, nWarpDestination)) {
                                break;
                            }
                        }
                    }
                }
                
                nFlags <<= 1;
                ++nWarpId;
            } while ((nFlags & D2DrlgRoomFlags.HAS_WARP_MASK) != 0);
        }
        
        // 检查是否连接到城镇关卡
        if (!DrlgDrlg.isTownLevel(drlgRoom.getLevel().getLevelId())) {
            for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
                if (DrlgDrlg.isTownLevel(drlgRoom.getPpRoomsNear()[i].getLevel().getLevelId())) {
                    drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.POPULATION_ZERO);
                    return;
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD77EB0
     * 按位置排序房间列表
     */
    public static void sortRoomListByPosition(D2DrlgRoom[] ppRoomList, int listSize) {
        for (int j = listSize - 1; j > 0; --j) {
            for (int i = 0; i < listSize - 1; ++i) {
                D2DrlgRoom drlgRoom1 = ppRoomList[i + 1];
                D2DrlgRoom drlgRoom2 = ppRoomList[i];
                
                if (drlgRoom2.getDrlgCoord().getNTileXPos() >= drlgRoom1.getDrlgCoord().getNTileXPos() + drlgRoom1.getDrlgCoord().getNTileWidth()
                    || drlgRoom2.getDrlgCoord().getNTileYPos() >= drlgRoom1.getDrlgCoord().getNTileYPos() + drlgRoom1.getDrlgCoord().getNTileHeight()) {
                    ppRoomList[i] = drlgRoom1;
                    ppRoomList[i + 1] = drlgRoom2;
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD77F00
     * 处理传送门房间连接（未命名函数）
     */
    private static boolean sub_6FD77F00(Object memPool, D2DrlgRoom drlgRoom1, int nWarpId, 
            D2DrlgRoom drlgRoom2, int nWarpFlag, int nDirection) {
        boolean bResult = false;
        
        while (drlgRoom2 != null) {
            if (((1 << (nWarpFlag + 4)) & drlgRoom2.getFlags()) != 0) {
                if (nDirection == -1) {
                    int nX, nY;
                    
                    if (drlgRoom1.getDrlgCoord().getNTileXPos() >= drlgRoom2.getDrlgCoord().getNTileXPos()) {
                        nX = drlgRoom1.getDrlgCoord().getNTileXPos() 
                            - drlgRoom2.getDrlgCoord().getNTileWidth() 
                            - drlgRoom2.getDrlgCoord().getNTileXPos();
                    } else {
                        nX = drlgRoom2.getDrlgCoord().getNTileXPos() 
                            - drlgRoom1.getDrlgCoord().getNTileWidth() 
                            - drlgRoom1.getDrlgCoord().getNTileXPos();
                    }
                    
                    if (drlgRoom1.getDrlgCoord().getNTileYPos() >= drlgRoom2.getDrlgCoord().getNTileYPos()) {
                        nY = drlgRoom1.getDrlgCoord().getNTileYPos() 
                            - drlgRoom2.getDrlgCoord().getNTileHeight() 
                            - drlgRoom2.getDrlgCoord().getNTileYPos();
                    } else {
                        nY = drlgRoom2.getDrlgCoord().getNTileYPos() 
                            - drlgRoom1.getDrlgCoord().getNTileHeight() 
                            - drlgRoom1.getDrlgCoord().getNTileYPos();
                    }
                    
                    if (nX < 6 && nY < 6) {
                        int nRoomsNear = drlgRoom1.getNRoomsNear() + 1;
                        D2DrlgRoom[] ppRoomsNear = D2Pool.callocArrayPool(memPool, D2DrlgRoom.class, nRoomsNear);
                        
                        // 复制现有房间
                        if (drlgRoom1.getPpRoomsNear() != null) {
                            for (int i = 0; i < drlgRoom1.getNRoomsNear(); ++i) {
                                ppRoomsNear[i] = drlgRoom1.getPpRoomsNear()[i];
                            }
                        }
                        
                        ppRoomsNear[nRoomsNear - 1] = drlgRoom2;
                        drlgRoom1.setPpRoomsNear(ppRoomsNear);
                        drlgRoom1.setNRoomsNear(nRoomsNear);
                        
                        sortRoomListByPosition(drlgRoom1.getPpRoomsNear(), drlgRoom1.getNRoomsNear());
                        
                        bResult = true;
                    }
                } else {
                    int nRoomsNear = drlgRoom1.getNRoomsNear() + 1;
                    D2DrlgRoom[] ppRoomsNear = D2Pool.callocArrayPool(memPool, D2DrlgRoom.class, nRoomsNear);
                    
                    // 复制现有房间
                    if (drlgRoom1.getPpRoomsNear() != null) {
                        for (int i = 0; i < drlgRoom1.getNRoomsNear(); ++i) {
                            ppRoomsNear[i] = drlgRoom1.getPpRoomsNear()[i];
                        }
                    }
                    
                    ppRoomsNear[nRoomsNear - 1] = drlgRoom2;
                    drlgRoom1.setPpRoomsNear(ppRoomsNear);
                    drlgRoom1.setNRoomsNear(nRoomsNear);
                    
                    sortRoomListByPosition(drlgRoom1.getPpRoomsNear(), drlgRoom1.getNRoomsNear());
                    
                    // 创建房间瓦片
                    D2RoomTile roomTile = D2Pool.callocStrcPool(memPool, D2RoomTile.class);
                    roomTile.setPDrlgRoom(drlgRoom2);
                    roomTile.setPLvlWarpTxtRecord(DrlgDrlgWarp.getLvlWarpTxtRecordFromWarpIdAndDirection(
                        drlgRoom1.getLevel(), (byte)nWarpId, 'b'));
                    roomTile.setBEnabled(true);
                    roomTile.setPNext(drlgRoom1.getRoomTiles());
                    drlgRoom1.setRoomTiles(roomTile);
                    
                    bResult = true;
                }
                
                break;
            }
            
            drlgRoom2 = drlgRoom2.getDrlgRoomNext();
        }
        
        return bResult;
    }
    
    /**
     * D2Common.0x6FD78190
     * 设置房间的 Room 对象
     */
    public static void setRoom(D2DrlgRoom drlgRoom, D2ActiveRoom room) {
        drlgRoom.setRoom(room);
    }
    
    /**
     * D2Common.0x6FD78230
     * 从房间获取 Drlg（被 DrlgActivate 依赖）
     */
    public static D2DrlgStrc getDrlgFromRoomEx(D2DrlgRoom room) {
        if (room == null) {
            D2Log.warning("DRLGROOM_GetDrlgFromRoomEx: room is null");
            return null;
        }
        if (room.getLevel() == null) {
            D2Log.warning("DRLGROOM_GetDrlgFromRoomEx: room.getLevel() is null");
            return null;
        }
        if (room.getLevel().getDrlg() == null) {
            D2Log.warning("DRLGROOM_GetDrlgFromRoomEx: room.getLevel().getDrlg() is null");
            return null;
        }
        return room.getLevel().getDrlg();
    }
    
    /**
     * D2Common.0x6FD781E0
     * 从关卡ID获取可见数组
     */
    public static int[] getVisArrayFromLevelId(D2DrlgStrc drlg, int levelId) {
        D2DrlgWarp warp = drlg.getWarp();
        while (warp != null) {
            if (warp.getNLevel() == levelId) {
                return warp.getNVis();
            }
            warp = warp.getPNext();
        }
        
        // 如果找不到，从数据表获取
        D2LevelDefBin levelDefRecord = DataTbls.getLevelDefRecord(levelId);
        return levelDefRecord != null ? levelDefRecord.getDwVis() : new int[8];
    }
    
    /**
     * D2Common.0x6FD780E0
     * 分配预设单位
     */
    public static D2PresetUnit allocPresetUnit(D2DrlgRoom drlgRoom, Object memPool, 
            int unitType, int index, int mode, int x, int y) {
        D2PresetUnit presetUnit = D2Pool.callocStrcPool(memPool, D2PresetUnit.class);
        
        presetUnit.setNUnitType(unitType);
        presetUnit.setNMode(mode);
        presetUnit.setNIndex(index);
        presetUnit.setNYpos(y);
        presetUnit.setNXpos(x);
        
        if (drlgRoom != null) {
            presetUnit.setPNext(drlgRoom.getPresetUnits());
            drlgRoom.setPresetUnits(presetUnit);
        } else {
            presetUnit.setPNext(null);
        }
        
        return presetUnit;
    }
    
    /**
     * D2Common.0x6FD78160
     * 获取预设单位
     */
    public static D2PresetUnit getPresetUnits(D2DrlgRoom drlgRoom) {
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.AUTOMAP_REVEAL) == 0) {
            if ((drlgRoom.getFlags() & D2DrlgRoomFlags.PRESET_UNITS_SPAWNED) != 0) {
                return null;
            }
            drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.PRESET_UNITS_SPAWNED);
        }
        return drlgRoom.getPresetUnits();
    }
}
