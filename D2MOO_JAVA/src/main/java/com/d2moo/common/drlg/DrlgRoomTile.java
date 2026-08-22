package com.d2moo.common.drlg;

import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;
import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.dungeon.Dungeon;

/**
 * Drlg 房间瓦片模块
 * 对应 C++ 文件：DrlgRoomTile.cpp
 * 
 * 注意：本模块依赖以下其他模块的函数，需要先实现：
 * - SEED 相关函数（随机数生成）
 * - D2CMP 相关函数（瓦片库管理）
 * - DATATBLS 相关函数（数据表查询）
 * - DUNGEON 相关函数（房间管理）
 * - DRLGOUTPLACE, DRLGOUTROOM, DRLGPRESET 相关函数
 */
public class DrlgRoomTile {
    
    // 瓦片类型常量
    public static final int TILETYPE_FLOOR = 0;
    public static final int TILETYPE_WALL_LEFT = 1;
    public static final int TILETYPE_WALL_RIGHT = 2;
    public static final int TILETYPE_WALL_TOP_CORNER_RIGHT = 3;
    public static final int TILETYPE_WALL_TOP_CORNER_LEFT = 4;
    public static final int TILETYPE_WALL_TOP_RIGHT = 5;
    public static final int TILETYPE_WALL_BOTTOM_LEFT = 6;
    public static final int TILETYPE_ROOF = 7;  // 屋顶类型
    public static final int TILETYPE_WALL_BOTTOM_RIGHT = 7;
    public static final int TILETYPE_SHADOW = 8;
    public static final int TILETYPE_WALL_LEFT_EXIT = 9;
    public static final int TILETYPE_WALL_RIGHT_EXIT = 10;
    public static final int TILETYPE_WALL_LEFT_DOOR = 11;
    public static final int TILETYPE_WALL_RIGHT_DOOR = 12;
    public static final int TILETYPE_TREE = 13;
    
    // 瓦片大小常量
    public static final int DRLGROOMTILE_TILES_SIZE = 8;
    public static final int DRLGROOMTILE_SUBTILES_SIZE = DRLGROOMTILE_TILES_SIZE * 5;
    
    /**
     * D2Common.0x6FD89FA0
     * 初始化房间网格
     * 被 DrlgActivate 依赖
     */
    public static void initRoomGrids(D2DrlgRoom drlgRoom) {
        // 初始化种子
        if (drlgRoom.getSeed() == null) {
            drlgRoom.setSeed(new D2Seed());
        }
        Seed.initLowSeed(drlgRoom.getSeed(), drlgRoom.getInitSeed());
        
        if (drlgRoom.getType() == D2DrlgType.MAZE.getValue()) {
            // 初始化户外房间网格
            DrlgOutPlace.initOutdoorRoomGrids(drlgRoom);
            // DrlgOutPlace.initOutdoorRoomGrids(drlgRoom);
        } else if (drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
            // 初始化预设房间网格
            DrlgPreset.initPresetRoomGrids(drlgRoom);
            // DrlgPreset.initPresetRoomGrids(drlgRoom);
        }
    }
    
    /**
     * D2Common.0x6FD89FD0
     * 添加房间地图瓦片
     * 被 DrlgActivate 依赖
     */
    public static void addRoomMapTiles(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getType() == D2DrlgType.MAZE.getValue()) {
            // 初始化户外房间
            DrlgOutRoom.initializeDrlgOutdoorRoom(drlgRoom);
            // DrlgOutRoom.initializeDrlgOutdoorRoom(drlgRoom);
        } else if (drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
            // 添加预设房间地图瓦片
            DrlgPreset.addPresetRoomMapTiles(drlgRoom);
            // DrlgPreset.addPresetRoomMapTiles(drlgRoom);
        }
        
        drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.HAS_ROOM);
    }
    
    /**
     * D2Common.0x6FD8A380
     * 为房间加载 DT1 文件
     * 被 DrlgActivate 依赖
     */
    public static void loadDT1FilesForRoom(D2DrlgRoom drlgRoom) {
        // 从数据表获取关卡类型记录
        com.d2moo.common.datatbls.D2LevelTypesTxt levelTypesTxt = DataTbls.getLevelTypesTxtRecord(
            drlgRoom.getLevel().getLevelType()
        );
        if (levelTypesTxt == null) {
            D2Log.warning("Failed to get level types txt record for level type: " + drlgRoom.getLevel().getLevelType());
            return;
        }
        
        int dwDT1Mask = drlgRoom.getDt1Mask();
        Object archive = drlgRoom.getLevel().getDrlg().getArchive();
        // 遍历 DT1 掩码位，加载对应的瓦片库文件
        // 注意：szFile 数组最多 6 个元素，但 DT1Mask 是 32 位
        int maxFiles = Math.min(levelTypesTxt.getSzFile().length, 32);
        for (int nFileIndex = 0; nFileIndex < maxFiles && dwDT1Mask != 0; ++nFileIndex, dwDT1Mask >>= 1) {
            if ((dwDT1Mask & 1) != 0) {
                String fileName = levelTypesTxt.getSzFile(nFileIndex);
                if (fileName != null && !fileName.isEmpty()) {
                    // 使用 D2CMP 模块加载瓦片库
                    // 加载瓦片库（使用已实现的函数）
                    D2Cmp.loadTileLibrarySlot(archive, drlgRoom.getTiles(), fileName);
                }
            }
        }
        
        // 加载默认的 DT1 文件
        String blankPath = "DATA\\GLOBAL\\Tiles\\Act1\\Outdoors\\Blank.dt1";
        D2Cmp.loadTileLibrarySlot(archive, drlgRoom.getTiles(), blankPath);
        
        String invisWalPath = "DATA\\GLOBAL\\Tiles\\Act1\\Barracks\\InvisWal.dt1";
        D2Cmp.loadTileLibrarySlot(archive, drlgRoom.getTiles(), invisWalPath);
        
        String warpPath = "DATA\\GLOBAL\\Tiles\\Act1\\Barracks\\Warp.dt1";
        D2Cmp.loadTileLibrarySlot(archive, drlgRoom.getTiles(), warpPath);
        
        drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.TILELIB_LOADED);
    }
    
    /**
     * D2Common.0x6FD8A2E0
     * 释放房间
     * 被 DrlgActivate 依赖
     */
    public static void freeRoom(D2DrlgRoom drlgRoom, boolean keepRoom) {
        if (!keepRoom && drlgRoom.getRoom() != null) {
            // 使用 DUNGEON 模块移除房间
            Dungeon.removeRoomFromAct(drlgRoom);
            // Dungeon.removeRoomFromAct(drlgRoom.getLevel().getDrlg().getAct(), drlgRoom.getRoom());
        }
        
        drlgRoom.setRoom(null);
        
        // 释放坐标列表
        DrlgDrlgLogic.freeDrlgCoordList(drlgRoom);
        
        if ((drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) != 0) {
            drlgRoom.setFlags(drlgRoom.getFlags() ^ D2DrlgRoomFlags.HAS_ROOM);
            drlgRoom.getLevel().getDrlg().setFreedRooms(
                drlgRoom.getLevel().getDrlg().getFreedRooms() + 1
            );
            
            freeTileGrid(drlgRoom);
            
            if (drlgRoom.getType() == D2DrlgType.MAZE.getValue()) {
                // 释放户外房间数据
                DrlgOutRoom.freeDrlgOutdoorRoomData(drlgRoom);
                // DrlgOutRoom.freeDrlgOutdoorRoomData(drlgRoom);
            } else if (drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
                // 释放预设房间网格
                DrlgPreset.freeDrlgGridsFromPresetRoom(drlgRoom);
                // DrlgPreset.freeDrlgGridsFromPresetRoom(drlgRoom);
            }
            
            // 清理房间瓦片列表
            D2RoomTile roomTile = drlgRoom.getRoomTiles();
            while (roomTile != null) {
                roomTile.setUnk0x0C(null);
                roomTile.setUnk0x10(null);
                roomTile = roomTile.getPNext();
            }
            
            if (keepRoom) {
                drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.ROOM_FREED_SRV);
            }
        }
    }
    
    /**
     * D2Common.0x6FD8A1D0
     * 释放瓦片网格
     */
    public static void freeTileGrid(D2DrlgRoom drlgRoom) {
        D2DrlgTileGrid tileGrid = drlgRoom.getTileGrid();
        if (tileGrid == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 释放墙壁瓦片数组
        if (tileGrid.getPTiles() != null && tileGrid.getPTiles().getPWallTiles() != null) {
            D2Pool.freePool(memPool, tileGrid.getPTiles().getPWallTiles());
            tileGrid.getPTiles().setPWallTiles(null);
        }
        
        // 释放地板瓦片数组
        if (tileGrid.getPTiles() != null && tileGrid.getPTiles().getPFloorTiles() != null) {
            D2Pool.freePool(memPool, tileGrid.getPTiles().getPFloorTiles());
            tileGrid.getPTiles().setPFloorTiles(null);
        }
        
        // 释放屋顶瓦片数组
        if (tileGrid.getPTiles() != null && tileGrid.getPTiles().getPRoofTiles() != null) {
            D2Pool.freePool(memPool, tileGrid.getPTiles().getPRoofTiles());
            tileGrid.getPTiles().setPRoofTiles(null);
        }
        
        // 释放瓦片链接列表
        D2DrlgTileLinkStrc pTileLink = tileGrid.getPMapLinks();
        while (pTileLink != null) {
            D2DrlgTileLinkStrc pNextTileLink = pTileLink.getPNext();
            D2Pool.freePool(memPool, pTileLink);
            pTileLink = pNextTileLink;
        }
        tileGrid.setPMapLinks(null);
        
        // 释放动画瓦片网格链表
        D2DrlgAnimTileGridStrc pAnimTileGrid = tileGrid.getPAnimTiles();
        while (pAnimTileGrid != null) {
            D2DrlgAnimTileGridStrc pNextAnimTileGrid = pAnimTileGrid.getPNext();
            // 释放动画瓦片数据数组
            if (pAnimTileGrid.getPpMapTileData() != null) {
                D2Pool.freePool(memPool, pAnimTileGrid.getPpMapTileData());
            }
            D2Pool.freePool(memPool, pAnimTileGrid);
            pAnimTileGrid = pNextAnimTileGrid;
        }
        tileGrid.setPAnimTiles(null);
        
        // 释放瓦片网格本身
        D2Pool.freePool(memPool, tileGrid);
        drlgRoom.setTileGrid(null);
    }
    
    /**
     * D2Common.0x6FD8A010
     * 分配瓦片网格
     */
    public static void allocTileGrid(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getTileGrid() == null) {
            Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
            D2DrlgTileGrid tileGrid = D2Pool.callocStrcPool(memPool, D2DrlgTileGrid.class);
            if (tileGrid == null) {
                tileGrid = new D2DrlgTileGrid();
            }
            drlgRoom.setTileGrid(tileGrid);
        }
    }
    
    /**
     * D2Common.0x6FD8A050
     * 分配瓦片数据
     */
    public static void allocTileData(D2DrlgRoom drlgRoom) {
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        D2DrlgTileGrid tileGrid = drlgRoom.getTileGrid();
        
        if (tileGrid == null) {
            return;
        }
        
        D2DrlgRoomTilesStrc roomTiles = tileGrid.getPTiles();
        
        // C++ 原始代码：
        // pRoomTiles->pFloorTiles = (D2DrlgTileDataStrc*)D2_CALLOC_POOL(
        //     pMemPool, sizeof(D2DrlgTileDataStrc) * pRoomTiles->nFloors);
        // 
        // Java 实现：使用类型安全的数组分配方法，无需强制类型转换
        if (roomTiles.getNFloors() > 0) {
            roomTiles.setPFloorTiles(allocTileDataArray(memPool, roomTiles.getNFloors()));
        }
        
        if (roomTiles.getNWalls() > 0) {
            roomTiles.setPWallTiles(allocTileDataArray(memPool, roomTiles.getNWalls()));
        }
        
        if (roomTiles.getNRoofs() > 0 && roomTiles.getPRoofTiles() == null) {
            roomTiles.setPRoofTiles(allocTileDataArray(memPool, roomTiles.getNRoofs()));
        }
    }

    /**
     * D2_CALLOC_POOL returns contiguous, zeroed C structs.  A Java object
     * array only zeroes its references, so each structure must be constructed
     * explicitly before the translated pointer arithmetic indexes it.
     */
    private static D2DrlgTileDataStrc[] allocTileDataArray(Object memPool, int length) {
        D2DrlgTileDataStrc[] result =
                D2Pool.callocArrayPool(memPool, D2DrlgTileDataStrc.class, length);
        for (int i = 0; i < result.length; i++) {
            result[i] = new D2DrlgTileDataStrc();
        }
        return result;
    }
    
    /**
     * D2Common.0x6FD89E30
     * 统计所有瓦片类型
     */
    public static void countAllTileTypes(D2DrlgRoom drlgRoom, D2DrlgGridStrc pTileInfoGrid, 
            boolean checkCoordinatesValidity, boolean killEdgeX, boolean killEdgeY) {
        if (pTileInfoGrid == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        int nTileCountX = drlgRoom.getNTileWidth() + (killEdgeX ? 0 : 1);
        int nTileCountY = drlgRoom.getNTileHeight() + (killEdgeY ? 0 : 1);
        
        D2DrlgRoomTilesStrc roomTiles = drlgRoom.getTileGrid().getPTiles();
        
        for (int nY = 0; nY < nTileCountY; ++nY) {
            for (int nX = 0; nX < nTileCountX; ++nX) {
                int flags = DrlgDrlgGrid.getGridEntry(pTileInfoGrid, nX, nY);
                D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(flags);
                
                if (tileInfo.isBIsWall()) {
                    roomTiles.setNWalls(roomTiles.getNWalls() + 1);
                }
                
                if (tileInfo.isBIsFloor() || 
                    (checkCoordinatesValidity && DrlgDrlgRoom.areXYInsideCoordinates(
                        drlgRoom.getDrlgCoord(), 
                        nX + drlgRoom.getNTileXPos(), 
                        nY + drlgRoom.getNTileYPos()))) {
                    roomTiles.setNFloors(roomTiles.getNFloors() + 1);
                }
                
                if (tileInfo.isBShadow()) {
                    roomTiles.setNRoofs(roomTiles.getNRoofs() + 1);
                }
            }
        }
        
        // int nTileCountX = drlgRoom.getNTileWidth() + (killEdgeX ? 0 : 1);
        // int nTileCountY = drlgRoom.getNTileHeight() + (killEdgeY ? 0 : 1);
        
        // for (int nY = 0; nY < nTileCountY; ++nY) {
        //     for (int nX = 0; nX < nTileCountX; ++nX) {
        //         int flags = DRLGGRID_GetGridFlagsPointer(pTileInfoGrid, nX, nY);
        //         D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(flags);
        //         
        //         if (tileInfo.isBIsWall()) {
        //             tileGrid.getPTiles().setNWalls(tileGrid.getPTiles().getNWalls() + 1);
        //         }
        //         
        //         if (tileInfo.isBIsFloor() || 
        //             (checkCoordinatesValidity && DrlgDrlgRoom.areXYInsideCoordinates(
        //                 drlgRoom.getDrlgCoord(), 
        //                 nX + drlgRoom.getNTileXPos(), 
        //                 nY + drlgRoom.getNTileYPos()))) {
        //             tileGrid.getPTiles().setNFloors(tileGrid.getPTiles().getNFloors() + 1);
        //         }
        //         
        //         if (tileInfo.isBShadow()) {
        //             tileGrid.getPTiles().setNRoofs(tileGrid.getPTiles().getNRoofs() + 1);
        //         }
        //     }
        // }
    }
    
    /**
     * D2Common.0x6FD89F00
     * 统计墙壁传送门瓦片
     */
    public static void countWallWarpTiles(D2DrlgRoom drlgRoom, D2DrlgGridStrc pTileInfoGrid, 
            D2DrlgGridStrc pTileTypeGrid, boolean killEdgeX, boolean killEdgeY) {
        if (pTileInfoGrid == null || pTileTypeGrid == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        int nTileCountX = drlgRoom.getNTileWidth() + (killEdgeX ? 0 : 1);
        int nTileCountY = drlgRoom.getNTileHeight() + (killEdgeY ? 0 : 1);
        
        D2DrlgRoomTilesStrc roomTiles = drlgRoom.getTileGrid().getPTiles();
        
        for (int nY = 0; nY < nTileCountY; ++nY) {
            for (int nX = 0; nX < nTileCountX; ++nX) {
                int nTileType = DrlgDrlgGrid.getGridEntry(pTileTypeGrid, nX, nY);
                
                switch (nTileType) {
                case TILETYPE_WALL_TOP_CORNER_RIGHT:
                    roomTiles.setNWalls(roomTiles.getNWalls() + 1);
                    break;
                case TILETYPE_WALL_LEFT_EXIT:
                case TILETYPE_WALL_RIGHT_EXIT:
                    int flags = DrlgDrlgGrid.getGridEntry(pTileInfoGrid, nX, nY);
                    D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(flags);
                    if (tileInfo.isBHidden()) {
                        roomTiles.setNFloors(roomTiles.getNFloors() + 6);
                    } else {
                        roomTiles.setNWalls(roomTiles.getNWalls() + 1);
                    }
                    break;
                }
            }
        }
        
        // int nTileCountX = drlgRoom.getNTileWidth() + (killEdgeX ? 0 : 1);
        // int nTileCountY = drlgRoom.getNTileHeight() + (killEdgeY ? 0 : 1);
        
        // for (int nY = 0; nY < nTileCountY; ++nY) {
        //     for (int nX = 0; nX < nTileCountX; ++nX) {
        //         int nTileType = DRLGGRID_GetGridEntry(pTileTypeGrid, nX, nY);
        //         
        //         switch (nTileType) {
        //         case TILETYPE_WALL_TOP_CORNER_RIGHT:
        //             tileGrid.getPTiles().setNWalls(tileGrid.getPTiles().getNWalls() + 1);
        //             break;
        //         case TILETYPE_WALL_LEFT_EXIT:
        //         case TILETYPE_WALL_RIGHT_EXIT:
        //             int flags = DRLGGRID_GetGridEntry(pTileInfoGrid, nX, nY);
        //             D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(flags);
        //             if (tileInfo.isBHidden()) {
        //                 tileGrid.getPTiles().setNFloors(tileGrid.getPTiles().getNFloors() + 6);
        //             } else {
        //                 tileGrid.getPTiles().setNWalls(tileGrid.getPTiles().getNWalls() + 1);
        //             }
        //             break;
        //         }
        //     }
        // }
    }
    
    /**
     * D2Common.0x6FD8A1B0 (#10017)
     * 从房间获取阴影数量
     */
    public static int getNumberOfShadowsFromRoom(D2ActiveRoom room) {
        if (room == null) {
            return 0;
        }
        
        // 使用 DUNGEON 模块获取房间扩展对象
        D2DrlgRoom drlgRoom = null;
        if (room != null) {
            // 从 D2ActiveRoom 获取关联的 D2DrlgRoom
            drlgRoom = com.d2moo.common.dungeon.Dungeon.getDrlgRoomFromActiveRoom(room);
        }
        if (drlgRoom != null && drlgRoom.getTileGrid() != null) {
            return drlgRoom.getTileGrid().getNShadows();
        }
        return 0;
    }
    
    /**
     * D2Common.0x6FD88860
     * 获取瓦片缓存（从瓦片库中随机选择一个瓦片）
     * @param drlgRoom 房间
     * @param nType 瓦片类型
     * @param nPackedTileInformation 打包的瓦片信息
     * @return 瓦片库条目，如果找不到则返回默认瓦片
     */
    public static Object getTileCache(D2DrlgRoom drlgRoom, int nType, int nPackedTileInformation) {
        if (drlgRoom == null || drlgRoom.getTiles() == null) {
            return null;
        }
        
        Object[] ppTileLibraryEntries = new Object[40];
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        int nStyle = tileInfo.getNTileStyle();
        int nSequence = tileInfo.getNTileSequence();
        
        int nEntries = D2Cmp.getTiles(drlgRoom.getTiles(), nType, nStyle, nSequence, ppTileLibraryEntries, ppTileLibraryEntries.length);
        
        if (nEntries > 0) {
            // 计算总稀有度
            int nMax = 0;
            for (int i = 0; i < nEntries; ++i) {
                if (ppTileLibraryEntries[i] == null) {
                    D2Log.warning("DRLGROOMTILE_GetTileCache: phTileArray[ii] is null");
                    continue;
                }
                nMax += D2Cmp.getTileRarity(ppTileLibraryEntries[i]);
            }
            
            // 根据稀有度随机选择
            int nId = 0;
            int nRand = Seed.rollLimitedRandomNumber(drlgRoom.getSeed(), nMax) + 1;
            if (nMax > 0) {
                while (nEntries > 1 && nRand > 0) {
                    nRand -= D2Cmp.getTileRarity(ppTileLibraryEntries[nId]);
                    ++nId;
                }
                
                if (nId > 0) {
                    --nId;
                }
            }
            
            return ppTileLibraryEntries[nId];
        } else {
            // 如果找不到，使用默认的 WALL_LEFT_EXIT 类型
            if (D2Cmp.getTiles(drlgRoom.getTiles(), TILETYPE_WALL_LEFT_EXIT, 0, 0, ppTileLibraryEntries, ppTileLibraryEntries.length) == 0) {
                D2Log.warning("DRLGROOMTILE_GetTileCache: nSize is 0");
            }
            return ppTileLibraryEntries[0];
        }
    }
    
    /**
     * D2Common.0x6FD88FD0
     * 初始化瓦片阴影
     * @param drlgRoom 房间
     * @param nX X坐标（游戏瓦片坐标）
     * @param nY Y坐标（游戏瓦片坐标）
     * @param nPackedTileInformation 打包的瓦片信息
     */
    public static void initTileShadow(D2DrlgRoom drlgRoom, int nX, int nY, int nPackedTileInformation) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        Object pTileLibraryEntry = getTileCache(drlgRoom, TILETYPE_SHADOW, nPackedTileInformation);
        initShadowTileData(drlgRoom, null, nX, nY, nPackedTileInformation, pTileLibraryEntry);
    }
    
    /**
     * D2Common.0x6FD88F10
     * 初始化阴影瓦片数据（辅助函数）
     * @param drlgRoom 房间
     * @param ppTileData 瓦片数据指针的指针（用于链接）
     * @param nX X坐标
     * @param nY Y坐标
     * @param nPackedTileInformation 打包的瓦片信息
     * @param pTileLibraryEntry 瓦片库条目
     * @return 创建的瓦片数据
     */
    private static D2DrlgTileDataStrc initShadowTileData(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc[] ppTileData,
            int nX, int nY, int nPackedTileInformation, Object pTileLibraryEntry) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null 
                || drlgRoom.getTileGrid().getPTiles() == null) {
            return null;
        }
        
        D2DrlgRoomTilesStrc roomTiles = drlgRoom.getTileGrid().getPTiles();
        if (roomTiles.getPRoofTiles() == null 
                || drlgRoom.getTileGrid().getNShadows() >= roomTiles.getNRoofs()) {
            return null;
        }
        
        D2DrlgTileDataStrc[] pRoofTiles = roomTiles.getPRoofTiles();
        int nIndex = drlgRoom.getTileGrid().getNShadows();
        D2DrlgTileDataStrc pTileData = pRoofTiles[nIndex];
        
        if (ppTileData != null && ppTileData.length > 0) {
            pTileData.setUnk0x20(ppTileData[0]);
            ppTileData[0] = pTileData;
        } else {
            pTileData.setUnk0x20(null);
        }
        
        drlgRoom.getTileGrid().setNShadows(drlgRoom.getTileGrid().getNShadows() + 1);
        
        initTileDataDefaults(drlgRoom, pTileData, nX, nY, nPackedTileInformation, TILETYPE_SHADOW, pTileLibraryEntry);
        
        return pTileData;
    }
    
    /**
     * D2Common.0x6FD8A050 (辅助函数)
     * 初始化瓦片数据默认值
     * @param drlgRoom 房间
     * @param pTileData 瓦片数据
     * @param nX X坐标
     * @param nY Y坐标
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nTileType 瓦片类型
     * @param pTileLibraryEntry 瓦片库条目
     */
    private static void initTileDataDefaults(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc pTileData,
            int nX, int nY, int nPackedTileInformation, int nTileType, Object pTileLibraryEntry) {
        if (pTileData == null) {
            return;
        }
        
        pTileData.setPTile(pTileLibraryEntry);
        pTileData.setNTileType(nTileType);
        pTileData.setDwFlags(0); // MAPTILE_FLAGS_NONE
        pTileData.setUnk0x24(0);
        pTileData.setNRed((byte)0xFF);
        pTileData.setNGreen((byte)0xFF);
        pTileData.setNBlue((byte)0xFF);
        
        if (drlgRoom != null) {
            pTileData.setNPosX(nX - drlgRoom.getNTileXPos());
            pTileData.setNPosY(nY - drlgRoom.getNTileYPos());
            
            // 将游戏瓦片坐标转换为客户端坐标
            int nPosX = nX;
            int nPosY = nY + 1;
            int[] posXArray = new int[]{nPosX};
            int[] posYArray = new int[]{nPosY};
            com.d2moo.common.dungeon.Dungeon.gameTileToClientCoords(posXArray, posYArray);
            
            // 设置瓦片的宽度和高度（使用客户端坐标）
            // DRLGROOMTILE_SUBTILES_SIZE = DRLGROOMTILE_TILES_SIZE * 5 = 8 * 5 = 40
            pTileData.setNWidth(posXArray[0]);
            pTileData.setNHeight(posYArray[0] + DRLGROOMTILE_SUBTILES_SIZE);
        }
        
        initializeTileDataFlags(drlgRoom, pTileData, nPackedTileInformation, nTileType, nX, nY);
    }
    
    /**
     * D2Common.0x6FD88AC0
     * 初始化瓦片数据标志
     * @param drlgRoom 房间
     * @param pTileData 瓦片数据
     * @param nTileFlags 瓦片标志
     * @param nType 瓦片类型
     * @param nX X坐标
     * @param nY Y坐标
     */
    private static void initializeTileDataFlags(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc pTileData,
            int nTileFlags, int nType, int nX, int nY) {
        if (pTileData == null) {
            return;
        }
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nTileFlags);
        
        // 处理门类型的预设单位
        if (drlgRoom != null && (nType == TILETYPE_WALL_RIGHT_DOOR || nType == TILETYPE_WALL_LEFT_DOOR)) {
            addTilePresetUnits(drlgRoom, pTileData, nTileFlags, nX, nY, nType);
        }
        
        // 设置墙壁层标志
        if (nType != TILETYPE_SHADOW) {
            int nWallLayer = tileInfo.getNWallLayer();
            pTileData.setDwFlags(pTileData.getDwFlags() | ((nWallLayer + 1) << 0)); // MAPTILE_WALL_LAYER_BIT = 0
        }
        
        // 设置树标志
        if (nType == TILETYPE_TREE) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x1); // MAPTILE_TREES
        } else if (nType == TILETYPE_WALL_RIGHT_EXIT || nType == TILETYPE_WALL_LEFT_EXIT 
                || nType == TILETYPE_WALL_RIGHT_DOOR || nType == TILETYPE_WALL_LEFT_DOOR) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x2); // MAPTILE_WALL_EXIT
        }
        
        // 设置其他标志
        if (tileInfo.isBLayerAbove()) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x1); // MAPTILE_UNK_0x1
        }
        
        if (tileInfo.isBLinkage()) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x4 | 0x2); // MAPTILE_FLOOR_LINKER_PATH | MAPTILE_WALL_EXIT
        }
        
        if (tileInfo.isBUnwalkable()) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x8); // MAPTILE_UNWALKABLE
        }
        
        if (tileInfo.isBFillLOS()) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x10); // MAPTILE_FILL_LOS
        }
        
        if (tileInfo.isBEnclosed()) {
            pTileData.setDwFlags(pTileData.getDwFlags() | 0x1); // MAPTILE_TREES
        }
        
        if (!tileInfo.isBHidden()) {
            pTileData.setDwFlags(pTileData.getDwFlags() & ~0x20); // MAPTILE_HIDDEN
        }
    }
    
    /**
     * D2Common.0x6FD88E30
     * 初始化瓦片数据
     * @param drlgRoom 房间
     * @param pTileData 瓦片数据
     * @param nX X坐标
     * @param nY Y坐标
     * @param nPackedTileInformation 打包的瓦片信息
     * @param pTileLibraryEntry 瓦片库条目
     */
    public static void initTileData(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc pTileData,
            int nX, int nY, int nPackedTileInformation, Object pTileLibraryEntry) {
        initTileDataDefaults(drlgRoom, pTileData, nX, nY, nPackedTileInformation, TILETYPE_FLOOR, pTileLibraryEntry);
    }
    
    /**
     * D2Common.0x6FD8A1B0
     * 重新分配屋顶瓦片网格
     * @param pMemPool 内存池
     * @param pTileGrid 瓦片网格
     * @param nAdditionalRoofs 额外的屋顶数量
     */
    public static void reallocRoofTileGrid(Object pMemPool, D2DrlgTileGrid pTileGrid, int nAdditionalRoofs) {
        if (pTileGrid == null || pTileGrid.getPTiles() == null || nAdditionalRoofs <= 0) {
            return;
        }
        
        D2DrlgRoomTilesStrc roomTiles = pTileGrid.getPTiles();
        int nCurrentRoofs = roomTiles.getNRoofs();
        int nNewSize = nAdditionalRoofs + nCurrentRoofs;
        
        // 重新分配数组
        D2DrlgTileDataStrc[] pNewRoofTiles = D2Pool.reallocArrayPool(pMemPool, 
                roomTiles.getPRoofTiles(), D2DrlgTileDataStrc.class, nNewSize);
        
        if (pNewRoofTiles == null) {
            D2Log.warning("DRLGROOMTILE_ReallocRoofTileGrid: Failed to reallocate roof tiles");
            return;
        }
        for (int i = 0; i < pNewRoofTiles.length; i++) {
            if (pNewRoofTiles[i] == null) {
                pNewRoofTiles[i] = new D2DrlgTileDataStrc();
            }
        }
        
        roomTiles.setPRoofTiles(pNewRoofTiles);
        
        // 链接屋顶瓦片（unk0x20 字段）
        int nCounter = 0;
        while (nCounter < pTileGrid.getNShadows() - 1) {
            if (nCounter + 1 < pNewRoofTiles.length) {
                pNewRoofTiles[nCounter].setUnk0x20(pNewRoofTiles[nCounter + 1]);
            }
            ++nCounter;
        }
        
        if (nCounter < pNewRoofTiles.length) {
            pNewRoofTiles[nCounter].setUnk0x20(null);
        }
        
        roomTiles.setNRoofs(nNewSize);
    }
    
    /**
     * D2Common.0x6FD88E60
     * 初始化地板瓦片数据
     * @param drlgRoom 房间
     * @param ppTileData 瓦片数据指针的指针（用于链接）
     * @param nX X坐标
     * @param nY Y坐标
     * @param nPackedTileInformation 打包的瓦片信息
     * @param pTileLibraryEntry 瓦片库条目
     * @return 创建的瓦片数据
     */
    public static D2DrlgTileDataStrc initFloorTileData(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc[] ppTileData,
            int nX, int nY, int nPackedTileInformation, Object pTileLibraryEntry) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null 
                || drlgRoom.getTileGrid().getPTiles() == null) {
            return null;
        }
        
        D2DrlgTileGrid tileGrid = drlgRoom.getTileGrid();
        D2DrlgRoomTilesStrc roomTiles = tileGrid.getPTiles();
        if (roomTiles.getPFloorTiles() == null 
                || tileGrid.getNFloors() >= roomTiles.getNFloors()) {
            return null;
        }
        
        D2DrlgTileDataStrc[] pFloorTiles = roomTiles.getPFloorTiles();
        int nIndex = tileGrid.getNFloors();
        D2DrlgTileDataStrc pTileData = pFloorTiles[nIndex];
        
        if (ppTileData != null && ppTileData.length > 0) {
            pTileData.setUnk0x20(ppTileData[0]);
            ppTileData[0] = pTileData;
        } else {
            pTileData.setUnk0x20(null);
        }
        
        tileGrid.setNFloors(tileGrid.getNFloors() + 1);
        
        initTileData(drlgRoom, pTileData, nX, nY, nPackedTileInformation, pTileLibraryEntry);
        
        return pTileData;
    }
    
    /**
     * D2Common.0x6FD889C0
     * 初始化墙壁瓦片数据
     * @param drlgRoom 房间
     * @param ppTileData 瓦片数据指针的指针（用于链接）
     * @param nX X坐标
     * @param nY Y坐标
     * @param nPackedTileInformation 打包的瓦片信息
     * @param pTileLibraryEntry 瓦片库条目
     * @param nTileType 瓦片类型
     * @return 创建的瓦片数据
     */
    public static D2DrlgTileDataStrc initWallTileData(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc[] ppTileData,
            int nX, int nY, int nPackedTileInformation, Object pTileLibraryEntry, int nTileType) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null 
                || drlgRoom.getTileGrid().getPTiles() == null) {
            return null;
        }
        
        D2DrlgTileGrid tileGrid = drlgRoom.getTileGrid();
        D2DrlgRoomTilesStrc roomTiles = tileGrid.getPTiles();
        if (roomTiles.getPWallTiles() == null 
                || tileGrid.getNWalls() >= roomTiles.getNWalls()) {
            return null;
        }
        
        D2DrlgTileDataStrc[] pWallTiles = roomTiles.getPWallTiles();
        int nIndex = tileGrid.getNWalls();
        D2DrlgTileDataStrc pTileData = pWallTiles[nIndex];
        
        if (ppTileData != null && ppTileData.length > 0) {
            pTileData.setUnk0x20(ppTileData[0]);
            ppTileData[0] = pTileData;
        } else {
            pTileData.setUnk0x20(null);
        }
        
        tileGrid.setNWalls(tileGrid.getNWalls() + 1);
        
        initTileDataDefaults(drlgRoom, pTileData, nX, nY, nPackedTileInformation, nTileType, pTileLibraryEntry);
        
        // 如果是右上角墙壁，还需要创建左上角墙壁
        if (nTileType == TILETYPE_WALL_TOP_CORNER_RIGHT) {
            Object pLeftCornerTile = getTileCache(drlgRoom, TILETYPE_WALL_TOP_CORNER_LEFT, nPackedTileInformation);
            D2DrlgTileDataStrc pLeftTileData = initWallTileData(drlgRoom, ppTileData, nX, nY, 
                    nPackedTileInformation, pLeftCornerTile, TILETYPE_WALL_TOP_CORNER_LEFT);
            if (pLeftTileData != null) {
                initializeTileDataFlags(drlgRoom, pLeftTileData, nPackedTileInformation, 
                        TILETYPE_WALL_TOP_CORNER_LEFT, nX, nY);
            }
        }
        
        return pTileData;
    }
    
    /**
     * D2Common.0x6FD89000
     * 加载并初始化房间瓦片
     * @param drlgRoom 房间
     * @param pTilePackedInfoGrid 打包的瓦片信息网格
     * @param pTileTypeGrid 瓦片类型网格（可为 null）
     * @param bFillBlanks 是否填充空白
     * @param bKillEdgeX 是否移除X边缘
     * @param bKillEdgeY 是否移除Y边缘
     */
    public static void loadInitRoomTiles(D2DrlgRoom drlgRoom, D2DrlgGridStrc pTilePackedInfoGrid,
            D2DrlgGridStrc pTileTypeGrid, boolean bFillBlanks, boolean bKillEdgeX, boolean bKillEdgeY) {
        if (drlgRoom == null || pTilePackedInfoGrid == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        Object pMemPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        int nTileCountX = drlgRoom.getNTileWidth() + (bKillEdgeX ? 0 : 1);
        int nTileCountY = drlgRoom.getNTileHeight() + (bKillEdgeY ? 0 : 1);
        
        int nRoomX = drlgRoom.getNTileXPos();
        int nRoomY = drlgRoom.getNTileYPos();
        
        for (int nTileOffsetY = 0; nTileOffsetY < nTileCountY; ++nTileOffsetY) {
            for (int nTileOffsetX = 0; nTileOffsetX < nTileCountX; ++nTileOffsetX) {
                int nPackedTileInformation = DrlgDrlgGrid.getGridEntry(pTilePackedInfoGrid, nTileOffsetX, nTileOffsetY);
                D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
                int nTileStyle = tileInfo.getNTileStyle();
                int nTileSequence = tileInfo.getNTileSequence();
                
                int nTileType = pTileTypeGrid != null 
                        ? DrlgDrlgGrid.getGridEntry(pTileTypeGrid, nTileOffsetX, nTileOffsetY) 
                        : TILETYPE_FLOOR;
                
                // 跳过某些类型的瓦片
                if ((nTileType == TILETYPE_WALL_LEFT_EXIT || nTileType == TILETYPE_WALL_RIGHT_EXIT) 
                        && nTileStyle >= 8) {
                    continue;
                }
                
                // 特殊处理：风格30的地板瓦片
                if (nTileType == TILETYPE_FLOOR && nTileStyle == 30 
                        && (nTileSequence == 0 || nTileSequence == 1)) {
                    tileInfo.setBHidden(true);
                    nPackedTileInformation = tileInfo.getNPackedValue();
                }
                
                int nTilePosX = nRoomX + nTileOffsetX;
                int nTilePosY = nRoomY + nTileOffsetY;
                
                // 处理隐藏瓦片（门、传送门等）
                if (tileInfo.isBHidden()) {
                    boolean bTileAdded = false;
                    switch (nTileType) {
                        case TILETYPE_WALL_LEFT_DOOR:
                        case TILETYPE_WALL_RIGHT_DOOR: {
                            int nLevelId = drlgRoom.getLevel().getLevelId();
                            // 某些关卡忽略门（Act5 路障关卡）
                            // LEVEL_ID_ACT5_BARRICADE_1 在 C++ 中是一个特殊关卡ID，对应 Act5 路障关卡
                            // 在 Java 中，我们使用 LEVEL_BLOODYFOOTHILLS 作为替代（Act5 第一个户外关卡）
                            if (nLevelId == D2LevelIds.LEVEL_BLOODYFOOTHILLS 
                                    || nLevelId == D2LevelIds.LEVEL_ARREATPLATEAU 
                                    || nLevelId == D2LevelIds.LEVEL_TUNDRAWASTELANDS) {
                                break;
                            }
                            addTilePresetUnits(drlgRoom, null, nPackedTileInformation, nTilePosX, nTilePosY, nTileType);
                            bTileAdded = true;
                            break;
                        }
                        case TILETYPE_WALL_LEFT_EXIT:
                        case TILETYPE_WALL_RIGHT_EXIT:
                            if (nTileStyle < 8) {
                                addWarp(drlgRoom, nTilePosX, nTilePosY, nPackedTileInformation, nTileType);
                                loadFloorWarpTiles(drlgRoom, nTilePosX, nTilePosY, nPackedTileInformation, nTileType);
                            }
                            bTileAdded = true;
                            break;
                        default:
                            break;
                    }
                    if (bTileAdded) {
                        continue;
                    }
                }
                
                // 处理 LOS 瓦片（链接瓦片）
                boolean bTileAdded = false;
                if (tileInfo.isBLOS()) {
                    bTileAdded = true;
                    if (tileInfo.isBIsFloor()) {
                        if (nTileStyle == 30 && (nTileSequence == 0 || nTileSequence == 1)) {
                            tileInfo.setBLayerAbove(false);
                            nPackedTileInformation = tileInfo.getNPackedValue();
                        }
                        
                        // 处理链接瓦片
                        D2DrlgRoom[] ppLinkedRoom = new D2DrlgRoom[1];
                        D2DrlgTileDataStrc pLinkedTileData = getLinkedTileData(drlgRoom, true, 
                                nPackedTileInformation, nTilePosX, nTilePosY, ppLinkedRoom);
                        if (pLinkedTileData != null && ppLinkedRoom[0] != null) {
                            linkedTileDataManager(pMemPool, drlgRoom, ppLinkedRoom[0], pLinkedTileData, 
                                    TILETYPE_FLOOR, nPackedTileInformation, nTilePosX, nTilePosY);
                        } else {
                            getCreateLinkedTileData(pMemPool, drlgRoom, TILETYPE_FLOOR, 
                                    nPackedTileInformation, nTilePosX, nTilePosY);
                        }
                    } else if (tileInfo.isBIsWall()) {
                        // 处理墙壁链接瓦片
                        D2DrlgRoom[] ppLinkedRoom = new D2DrlgRoom[1];
                        D2DrlgTileDataStrc pLinkedTileData = getLinkedTileData(drlgRoom, false, 
                                nPackedTileInformation, nTilePosX, nTilePosY, ppLinkedRoom);
                        if (pLinkedTileData != null && ppLinkedRoom[0] != null) {
                            linkedTileDataManager(pMemPool, drlgRoom, ppLinkedRoom[0], pLinkedTileData, 
                                    nTileType, nPackedTileInformation, nTilePosX, nTilePosY);
                        } else {
                            getCreateLinkedTileData(pMemPool, drlgRoom, nTileType, 
                                    nPackedTileInformation, nTilePosX, nTilePosY);
                        }
                    } else if (tileInfo.isBShadow() && !tileInfo.isBHidden()) {
                        // 处理阴影链接瓦片
                        getCreateLinkedTileData(pMemPool, drlgRoom, TILETYPE_SHADOW, 
                                nPackedTileInformation, nTilePosX, nTilePosY);
                    } else {
                        bTileAdded = false;
                    }
                }
                
                // 处理普通瓦片
                if (!bTileAdded) {
                    if (tileInfo.isBIsFloor()) {
                        Object pTileCache = getTileCache(drlgRoom, TILETYPE_FLOOR, nPackedTileInformation);
                        initFloorTileData(drlgRoom, null, nTilePosX, nTilePosY, nPackedTileInformation, pTileCache);
                    } else if (bFillBlanks && DrlgDrlgRoom.areXYInsideCoordinates(
                            drlgRoom.getDrlgCoord(), nTilePosX, nTilePosY)) {
                        // 填充空白区域
                        D2C_PackedTileInformation cachedTileInfo = new D2C_PackedTileInformation(0);
                        cachedTileInfo.setNTileStyle(30);
                        int nLevelId = drlgRoom.getLevel().getLevelId();
                        cachedTileInfo.setNTileSequence(nLevelId == D2LevelIds.LEVEL_ARCANESANCTUARY ? 1 : 0);
                        Object pTileCache = getTileCache(drlgRoom, 0, cachedTileInfo.getNPackedValue());
                        
                        D2C_PackedTileInformation arcaneFloorTileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
                        arcaneFloorTileInfo.setBLayerAbove(false);
                        arcaneFloorTileInfo.setBHidden(true);
                        initFloorTileData(drlgRoom, null, nTilePosX, nTilePosY, 
                                arcaneFloorTileInfo.getNPackedValue(), pTileCache);
                    }
                    
                    if (tileInfo.isBIsWall()) {
                        Object pTileCache = getTileCache(drlgRoom, nTileType, nPackedTileInformation);
                        D2DrlgTileDataStrc pWallTileData = initWallTileData(drlgRoom, null, nTilePosX, nTilePosY, 
                                nPackedTileInformation, pTileCache, nTileType);
                        if (nTileType == TILETYPE_WALL_RIGHT_EXIT || nTileType == TILETYPE_WALL_LEFT_EXIT) {
                            loadWallWarpTiles(drlgRoom, pWallTileData, nPackedTileInformation, nTileType);
                        }
                    }
                    
                    if (tileInfo.isBShadow()) {
                        Object pTileCache = getTileCache(drlgRoom, TILETYPE_SHADOW, nPackedTileInformation);
                        initShadowTileData(drlgRoom, null, nTilePosX, nTilePosY, nPackedTileInformation, pTileCache);
                    }
                }
            }
        }
    }
    
    // 瓦片标志常量
    private static final int MAPTILE_HASPRESETUNITS = 0x000020; // 瓦片已有预设单位
    private static final int MAPTILE_HIDDEN = 0x000008; // 隐藏瓦片（用于传送门等）
    
    // 房间标志常量（使用 D2DrlgRoomFlags 中定义的常量）
    // DRLGROOMFLAG_POPULATION_ZERO 在 D2DrlgRoomFlags 中定义为 POPULATION_ZERO = 0x00800000
    
    /**
     * D2Common.0x6FD88A40
     * 添加瓦片预设单位
     * 根据瓦片类型和风格，在特定位置生成门、塔等预设单位
     * @param drlgRoom 房间
     * @param pTileData 瓦片数据（可为 null）
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nX X坐标（游戏瓦片坐标）
     * @param nY Y坐标（游戏瓦片坐标）
     * @param nTileType 瓦片类型（当 pTileData 为 null 时使用）
     */
    public static void addTilePresetUnits(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc pTileData,
            int nPackedTileInformation, int nX, int nY, int nTileType) {
        if (drlgRoom == null || drlgRoom.getLevel() == null) {
            return;
        }
        
        // 关卡ID到数组范围的映射
        final int[][] stru_6FDD0DA8 = {
            {D2LevelIds.LEVEL_BARRACKS, 0, 3},
            {D2LevelIds.LEVEL_JAILLVL1, 0, 3},
            {D2LevelIds.LEVEL_JAILLVL2, 0, 3},
            {D2LevelIds.LEVEL_JAILLVL3, 0, 3},
            {D2LevelIds.LEVEL_MONASTERYGATE, 4, 6},
            {D2LevelIds.LEVEL_OUTERCLOISTER, 4, 6},
            {D2LevelIds.LEVEL_INNERCLOISTER, 5, 9},
            {D2LevelIds.LEVEL_CATHEDRAL, 5, 9},
            {D2LevelIds.LEVEL_CATACOMBSLVL1, 10, 11},
            {D2LevelIds.LEVEL_CATACOMBSLVL2, 10, 11},
            {D2LevelIds.LEVEL_CATACOMBSLVL3, 10, 11},
            {D2LevelIds.LEVEL_CATACOMBSLVL4, 10, 12},
            {D2LevelIds.LEVEL_HAREMLVL2, 13, 14},
            {D2LevelIds.LEVEL_PALACECELLARLVL1, 15, 18},
            {D2LevelIds.LEVEL_PALACECELLARLVL2, 15, 18},
            {D2LevelIds.LEVEL_PALACECELLARLVL3, 15, 18},
            {D2LevelIds.LEVEL_STONYTOMBLEV1, 19, 20},
            {D2LevelIds.LEVEL_HALLSOFTHEDEADLVL1, 19, 20},
            {D2LevelIds.LEVEL_HALLSOFTHEDEADLEV2, 19, 20},
            {D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV1, 19, 20},
            {D2LevelIds.LEVEL_STONYTOMBLEV2, 19, 20},
            {D2LevelIds.LEVEL_HALLSOFTHEDEADLEV3, 19, 20},
            {D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB1, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB2, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB3, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB4, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB5, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB6, 19, 20},
            {D2LevelIds.LEVEL_TALRASHASTOMB7, 19, 20},
            {D2LevelIds.LEVEL_MAGGOTLAIRLEV1, 21, 22},
            {D2LevelIds.LEVEL_MAGGOTLAIRLEV2, 21, 22},
            {D2LevelIds.LEVEL_MAGGOTLAIRLEV3, 21, 22},
            {D2LevelIds.LEVEL_HARROGATH, 23, 24},
            {D2LevelIds.LEVEL_BLOODYFOOTHILLS, 24, 33}, // LEVEL_ID_ACT5_BARRICADE_1 的替代
            {D2LevelIds.LEVEL_ARREATPLATEAU, 24, 33},
            {D2LevelIds.LEVEL_TUNDRAWASTELANDS, 24, 33},
        };
        
        // 预设单位配置数组
        final int[][] stru_6FDD0F68 = {
            // {nTileStyle, nTileSequence, bIsRightWallWithDoor(1=true,0=false), nUnitId, nUnitType, nX, nY}
            {7, 0, 1, D2ObjectIds.OBJECT_DOORGATERIGHT, D2UnitTypes.UNIT_OBJECT, 5, 0},
            {7, 0, 0, D2ObjectIds.OBJECT_DOORGATELEFT, D2UnitTypes.UNIT_OBJECT, 0, 5},
            {5, 0, 1, D2ObjectIds.OBJECT_DOORWOODENRIGHT, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {5, 0, 0, D2ObjectIds.OBJECT_DOORWOODENLEFT, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {6, 0, 1, D2ObjectIds.OBJECT_DOORMONASTERYDOUBLERIGHT, D2UnitTypes.UNIT_OBJECT, 5, -2},
            {4, 0, 1, D2ObjectIds.OBJECT_DOORCOURTYARDRIGHT, D2UnitTypes.UNIT_OBJECT, 1, 2},
            {4, 0, 0, D2ObjectIds.OBJECT_DOORCOURTYARDLEFT, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {4, 3, 1, D2ObjectIds.OBJECT_DOORCATHEDRALDOUBLE, D2UnitTypes.UNIT_OBJECT, 1, 0},
            {1, 2, 0, D2ObjectIds.OBJECT_DOORCATHEDRALLEFT, D2UnitTypes.UNIT_OBJECT, 0, 3},
            {1, 2, 1, D2ObjectIds.OBJECT_DOORCATHEDRALRIGHT, D2UnitTypes.UNIT_OBJECT, 3, 0},
            {0, 0, 1, D2ObjectIds.OBJECT_DOORWOODENRIGHT, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {0, 0, 0, D2ObjectIds.OBJECT_DOORWOODENLEFT2, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {2, 0, 1, D2ObjectIds.OBJECT_ANDARIELS_DOOR, D2UnitTypes.UNIT_OBJECT, 5, 0},
            {0, 1, 1, D2ObjectIds.OBJECT_IRONGRATEDOORRIGHT, D2UnitTypes.UNIT_OBJECT, 2, 0},
            {0, 1, 0, D2ObjectIds.OBJECT_IRONGRATEDOORLEFT, D2UnitTypes.UNIT_OBJECT, 0, 2},
            {5, 0, 1, D2ObjectIds.OBJECT_WOODENGRATEDOORRIGHT, D2UnitTypes.UNIT_OBJECT, 2, 0},
            {4, 0, 0, D2ObjectIds.OBJECT_WOODENGRATEDOORLEFT, D2UnitTypes.UNIT_OBJECT, 0, 2},
            {0, 0, 1, D2ObjectIds.OBJECT_WOODENDOORRIGHT, D2UnitTypes.UNIT_OBJECT, 2, 0},
            {0, 0, 0, D2ObjectIds.OBJECT_WOODENDOORLEFT, D2UnitTypes.UNIT_OBJECT, 0, 2},
            {2, 4, 1, D2ObjectIds.OBJECT_TOMBDOORRIGHT, D2UnitTypes.UNIT_OBJECT, 1, 0},
            {2, 1, 0, D2ObjectIds.OBJECT_TOMBDOORLEFT, D2UnitTypes.UNIT_OBJECT, 0, 2},
            {0, 1, 1, D2ObjectIds.OBJECT_SLIMEDOOR1, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {0, 1, 0, D2ObjectIds.OBJECT_SLIMEDOOR2, D2UnitTypes.UNIT_OBJECT, 0, 0},
            {3, 3, 0, D2ObjectIds.OBJECT_HARROGATH_TOWN_MAIN_GATE, D2UnitTypes.UNIT_OBJECT, -2, 4},
            
            // 怪物类型（路障、监狱门等）
            {2, 1, 0, D2MonsterIds.MONSTER_BARRICADETOWER, D2UnitTypes.UNIT_MONSTER, 1, 2},
            {2, 1, 1, D2MonsterIds.MONSTER_BARRICADETOWER, D2UnitTypes.UNIT_MONSTER, 2, 1},
            {2, 6, 0, D2MonsterIds.MONSTER_BARRICADETOWER, D2UnitTypes.UNIT_MONSTER, 1, 1},
            {2, 2, 0, D2MonsterIds.MONSTER_BARRICADEDOOR2, D2UnitTypes.UNIT_MONSTER, 0, 1},
            {2, 3, 1, D2MonsterIds.MONSTER_BARRICADEDOOR1, D2UnitTypes.UNIT_MONSTER, 1, 0},
            {26, 0, 0, D2MonsterIds.MONSTER_PRISONDOOR, D2UnitTypes.UNIT_MONSTER, 0, 1},
            {2, 4, 1, D2MonsterIds.MONSTER_BARRICADEWALL1, D2UnitTypes.UNIT_MONSTER, 0, 0},
            {2, 4, 0, D2MonsterIds.MONSTER_BARRICADEWALL2, D2UnitTypes.UNIT_MONSTER, 0, 0},
            
            // 永久城镇传送门
            {29, 0, 1, D2ObjectIds.OBJECT_PERMANENT_TOWN_PORTAL, D2UnitTypes.UNIT_OBJECT, 2, 0},
            {29, 0, 0, D2ObjectIds.OBJECT_PERMANENT_TOWN_PORTAL, D2UnitTypes.UNIT_OBJECT, 0, 2},
        };
        
        // 检查瓦片数据是否已有预设单位
        boolean bIsRightWallWithDoor = false;
        if (pTileData != null) {
            if ((pTileData.getDwFlags() & MAPTILE_HASPRESETUNITS) != 0) {
                return; // 已有预设单位，直接返回
            }
            bIsRightWallWithDoor = (pTileData.getNTileType() == TILETYPE_WALL_RIGHT_DOOR);
        } else {
            bIsRightWallWithDoor = (nTileType == TILETYPE_WALL_RIGHT_DOOR);
        }
        
        int nLevelId = drlgRoom.getLevel().getLevelId();
        
        // 查找匹配的关卡ID
        for (int[] levelEntry : stru_6FDD0DA8) {
            if (levelEntry[0] == nLevelId) {
                D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
                int nTileStyle = tileInfo.getNTileStyle();
                int nTileSequence = tileInfo.getNTileSequence();
                
                int nArrayStartId = levelEntry[1];
                int nArrayEndId = levelEntry[2];
                
                // 在数组范围内查找匹配的瓦片风格和序列
                for (int j = nArrayStartId; j <= nArrayEndId && j < stru_6FDD0F68.length; ++j) {
                    int[] presetEntry = stru_6FDD0F68[j];
                    if (presetEntry[0] == nTileStyle && presetEntry[1] == nTileSequence 
                            && (presetEntry[2] == 1) == bIsRightWallWithDoor) {
                        
                        // 计算相对位置
                        int nPosX = nX - drlgRoom.getNTileXPos();
                        int nPosY = nY - drlgRoom.getNTileYPos();
                        
                        // 转换为子瓦片坐标
                        int[] xArray = {nPosX};
                        int[] yArray = {nPosY};
                        com.d2moo.common.dungeon.Dungeon.gameTileToSubtileCoords(xArray, yArray);
                        nPosX = xArray[0];
                        nPosY = yArray[0];
                        
                        // 添加偏移
                        nPosX += presetEntry[5];
                        nPosY += presetEntry[6];
                        
                        // 检查坐标是否在房间范围内
                        if (nPosX >= 0 && nPosY >= 0 
                                && nPosX < 5 * drlgRoom.getNTileWidth() 
                                && nPosY < 5 * drlgRoom.getNTileHeight()) {
                            
                            int nUnitId = presetEntry[3];
                            int nUnitType = presetEntry[4];
                            Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
                            
                            // 根据单位类型分配预设单位
                            switch (nUnitType) {
                                case D2UnitTypes.UNIT_MONSTER:
                                    // 分配怪物预设单位
                                    // 验证怪物ID是否有效
                                    if (com.d2moo.common.monsters.Monsters.validateMonsterId(nUnitId)) {
                                        DrlgDrlgRoom.allocPresetUnit(drlgRoom, memPool, 
                                                D2UnitTypes.UNIT_MONSTER, nUnitId, 
                                                D2MonModes.MONMODE_NEUTRAL, nPosX, nPosY);
                                    } else {
                                        D2Log.debug("DRLGROOMTILE_AddTilePresetUnits: Invalid monster ID: " + nUnitId + ", skipping preset unit");
                                    }
                                    break;
                                    
                                case D2UnitTypes.UNIT_OBJECT:
                                    // 对于某些对象ID（91-92），有33%的概率不生成
                                    if (nUnitId < 91 || nUnitId > 92 
                                            || Seed.rollLimitedRandomNumber(drlgRoom.getSeed(), 3) != 0) {
                                        DrlgDrlgRoom.allocPresetUnit(drlgRoom, memPool, 
                                                D2UnitTypes.UNIT_OBJECT, nUnitId, 
                                                D2ObjModes.OBJMODE_NEUTRAL, nPosX, nPosY);
                                    }
                                    break;
                                    
                                default:
                                    DrlgDrlgRoom.allocPresetUnit(drlgRoom, memPool, 
                                            nUnitType, nUnitId, 0, nPosX, nPosY);
                                    break;
                            }
                            
                            // 标记瓦片数据已有预设单位
                            if (pTileData != null) {
                                pTileData.setDwFlags(pTileData.getDwFlags() | MAPTILE_HASPRESETUNITS);
                            }
                        }
                        return; // 找到匹配后直接返回
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD89190
     * 添加传送门
     * @param drlgRoom 房间
     * @param nX X坐标（游戏瓦片坐标）
     * @param nY Y坐标（游戏瓦片坐标）
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nTileType 瓦片类型
     * @return 如果成功添加传送门返回 true，否则返回 false
     */
    public static boolean addWarp(D2DrlgRoom drlgRoom, int nX, int nY, 
            int nPackedTileInformation, int nTileType) {
        if (drlgRoom == null || drlgRoom.getLevel() == null) {
            return false;
        }
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        int nTileStyle = tileInfo.getNTileStyle();
        
        // 获取传送门文本记录
        char direction = (nTileType == TILETYPE_WALL_RIGHT_EXIT) ? 'r' : 'l';
        D2LvlWarpTxt pLvlWarpTxtRecord = DrlgDrlgWarp.getLvlWarpTxtRecordFromWarpIdAndDirection(
                drlgRoom.getLevel(), (byte)nTileStyle, direction);
        
        if (pLvlWarpTxtRecord != null) {
            // 计算相对位置
            int nPosX = nX - drlgRoom.getNTileXPos();
            int nPosY = nY - drlgRoom.getNTileYPos();
            
            // 检查是否在房间边界内
            if (nPosX != drlgRoom.getNTileWidth() && nPosY != drlgRoom.getNTileHeight()) {
                // 转换为子瓦片坐标
                int[] posArray = {nPosX, nPosY};
                        int[] xArray = {nPosX};
                        int[] yArray = {nPosY};
                        com.d2moo.common.dungeon.Dungeon.gameTileToSubtileCoords(xArray, yArray);
                        nPosX = xArray[0];
                        nPosY = yArray[0];
                nPosX = posArray[0];
                nPosY = posArray[1];
                
                // 添加偏移
                nPosX += pLvlWarpTxtRecord.getDwOffsetX();
                nPosY += pLvlWarpTxtRecord.getDwOffsetY();
                
                // 分配预设单位（UNIT_TILE 类型）
                Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
                DrlgDrlgRoom.allocPresetUnit(drlgRoom, memPool, 
                        D2UnitTypes.UNIT_TILE, pLvlWarpTxtRecord.getDwLevelId(), 
                        D2ObjModes.OBJMODE_NEUTRAL, nPosX, nPosY);
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 辅助函数：查找目标传送门瓦片
     * @param drlgRoom 房间
     * @param tileInfo 瓦片信息
     * @return 找到的传送门瓦片，如果未找到返回 null
     */
    private static D2RoomTile findDestinationWarpTile(D2DrlgRoom drlgRoom, 
            D2C_PackedTileInformation tileInfo) {
        if (drlgRoom == null || drlgRoom.getLevel() == null) {
            return null;
        }
        
        int nDestinationLevelId = DrlgDrlgWarp.getWarpDestinationFromArray(
                drlgRoom.getLevel(), (byte)tileInfo.getNTileStyle());
        
        if (nDestinationLevelId < 0) {
            return null;
        }
        
        D2RoomTile pWarpTile = drlgRoom.getRoomTiles();
        while (pWarpTile != null) {
            D2LvlWarpTxt pLvlWarpTxtRecord = (D2LvlWarpTxt) pWarpTile.getPLvlWarpTxtRecord();
            if (pLvlWarpTxtRecord != null && pLvlWarpTxtRecord.getDwLevelId() == nDestinationLevelId) {
                return pWarpTile;
            }
            pWarpTile = pWarpTile.getPNext();
        }
        
        return null;
    }
    
    /**
     * 辅助函数：更新并获取传送门文本记录
     * @param pWarpTile 传送门瓦片
     * @param nTileType 瓦片类型
     * @return 更新后的传送门文本记录
     */
    private static D2LvlWarpTxt updateAndGetLvlWarpTxtRecord(D2RoomTile pWarpTile, int nTileType) {
        if (pWarpTile == null) {
            return null;
        }
        
        D2LvlWarpTxt pLvlWarpTxtRecord = (D2LvlWarpTxt) pWarpTile.getPLvlWarpTxtRecord();
        if (pLvlWarpTxtRecord == null) {
            return null;
        }
        
        // 如果方向不是 'b'（双向），则根据瓦片类型更新方向
        String szDirection = pLvlWarpTxtRecord.getSzDirection();
        if (szDirection == null || szDirection.length() == 0 || szDirection.charAt(0) != 'b') {
            char direction = (nTileType == TILETYPE_WALL_RIGHT_EXIT) ? 'r' : 'l';
            
            if (szDirection == null || szDirection.length() == 0 || szDirection.charAt(0) != direction) {
                // 获取新的传送门文本记录
                D2LvlWarpTxt newRecord = DataTbls.getLvlWarpTxtRecordFromLevelIdAndDirection(
                        pLvlWarpTxtRecord.getDwLevelId(), direction);
                if (newRecord != null) {
                    pWarpTile.setPLvlWarpTxtRecord(newRecord);
                    return newRecord;
                }
            }
        }
        
        return pLvlWarpTxtRecord;
    }
    
    /**
     * D2Common.0x6FD89410
     * 加载墙壁传送门瓦片
     * @param drlgRoom 房间
     * @param pTileData 瓦片数据
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nTileType 瓦片类型
     */
    public static void loadWallWarpTiles(D2DrlgRoom drlgRoom, D2DrlgTileDataStrc pTileData,
            int nPackedTileInformation, int nTileType) {
        if (drlgRoom == null || pTileData == null) {
            return;
        }
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        
        // 查找目标传送门瓦片
        D2RoomTile pWarpTile = findDestinationWarpTile(drlgRoom, tileInfo);
        
        if (pWarpTile == null) {
            D2Log.warning("DRLGROOMTILE_LoadWallWarpTiles: Warp tile not found");
            return;
        }
        
        // 如果序列不是 0 或 4，或者成功添加传送门
        int nTileSequence = tileInfo.getNTileSequence();
        int nTilePosX = drlgRoom.getNTileXPos() + pTileData.getNPosX();
        int nTilePosY = pTileData.getNPosY() + drlgRoom.getNTileYPos();
        
        if (nTileSequence != 0 && nTileSequence != 4 
                || addWarp(drlgRoom, nTilePosX, nTilePosY, nPackedTileInformation, nTileType)) {
            
            // 链接瓦片数据
            pTileData.setUnk0x20((D2DrlgTileDataStrc) pWarpTile.getUnk0x10());
            pWarpTile.setUnk0x10(pTileData);
            
            // 更新并获取传送门文本记录
            D2LvlWarpTxt pWarpDef = updateAndGetLvlWarpTxtRecord(pWarpTile, nTileType);
            if (pWarpDef == null) {
                D2Log.warning("DRLGROOMTILE_LoadWallWarpTiles: Warp definition not found");
                return;
            }
            
            // 如果有光照版本，创建隐藏的墙壁瓦片
            if (pWarpDef.getDwLitVersion() != 0) {
                tileInfo.setNTileSequence(tileInfo.getNTileSequence() | pWarpDef.getDwTiles());
                
                Object pTileCache = getTileCache(drlgRoom, nTileType, tileInfo.getNPackedValue());
                D2DrlgTileDataStrc pDrlgTileData = initWallTileData(drlgRoom, 
                        (D2DrlgTileDataStrc[]) new Object[]{pWarpTile.getUnk0x0C()}, 
                        nTilePosX, nTilePosY, tileInfo.getNPackedValue(), pTileCache, nTileType);
                
                if (pDrlgTileData != null) {
                    pDrlgTileData.setDwFlags(pDrlgTileData.getDwFlags() | MAPTILE_HIDDEN);
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD89590
     * 加载地板传送门瓦片
     * @param drlgRoom 房间
     * @param nX X坐标（游戏瓦片坐标）
     * @param nY Y坐标（游戏瓦片坐标）
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nTileType 瓦片类型
     */
    public static void loadFloorWarpTiles(D2DrlgRoom drlgRoom, int nX, int nY,
            int nPackedTileInformation, int nTileType) {
        if (drlgRoom == null) {
            return;
        }
        
        // 传送门瓦片偏移数组
        final int[][] gWarpTileOffsets = {
            {0, 0},
            {1, 0},
            {0, 1},
            {1, 1}
        };
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        
        // 设置房间标志
        drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.POPULATION_ZERO);
        
        // 查找目标传送门瓦片
        D2RoomTile pWarpTile = findDestinationWarpTile(drlgRoom, tileInfo);
        
        if (pWarpTile != null) {
            // 更新并获取传送门文本记录
            D2LvlWarpTxt pWarpDef = updateAndGetLvlWarpTxtRecord(pWarpTile, nTileType);
            
            if (pWarpDef != null && pWarpDef.getDwLitVersion() != 0) {
                // 为每个目标瓦片序列创建隐藏的地板瓦片
                for (int nDestinationTileSequence = 0; nDestinationTileSequence < 4; ++nDestinationTileSequence) {
                    D2C_PackedTileInformation nDestinationTileInfo = new D2C_PackedTileInformation(0);
                    // 使用序列作为风格引用（用于链接瓦片以显示/隐藏）
                    nDestinationTileInfo.setNTileStyle(tileInfo.getNTileSequence());
                    nDestinationTileInfo.setNTileSequence(nDestinationTileSequence | 4);
                    
                    Object pTileCache = getTileCache(drlgRoom, TILETYPE_FLOOR, 
                            nDestinationTileInfo.getNPackedValue());
                    
                    int nDestinationTilePosX = nX - 1 + gWarpTileOffsets[nDestinationTileSequence][0];
                    int nDestinationTilePosY = nY - 1 + gWarpTileOffsets[nDestinationTileSequence][1];
                    
                    D2DrlgTileDataStrc[] ppTileData = new D2DrlgTileDataStrc[1];
                    ppTileData[0] = (D2DrlgTileDataStrc) pWarpTile.getUnk0x0C();
                    
                    D2DrlgTileDataStrc pDestTileData = initFloorTileData(drlgRoom, ppTileData,
                            nDestinationTilePosX, nDestinationTilePosY, 
                            nDestinationTileInfo.getNPackedValue(), pTileCache);
                    
                    if (pDestTileData != null) {
                        pDestTileData.setDwFlags(pDestTileData.getDwFlags() | MAPTILE_HIDDEN);
                    }
                }
                
                // 链接所有匹配的地板瓦片
                if (drlgRoom.getTileGrid() != null && drlgRoom.getTileGrid().getPTiles() != null) {
                    D2DrlgTileDataStrc[] pFloorTiles = drlgRoom.getTileGrid().getPTiles().getPFloorTiles();
                    if (pFloorTiles != null) {
                        for (int i = 0; i < drlgRoom.getTileGrid().getNFloors(); ++i) {
                            D2DrlgTileDataStrc pFloorTileData = pFloorTiles[i];
                            if (pFloorTileData != null) {
                                // 使用 D2CMP 函数获取瓦片风格和序列
                                Object pTile = pFloorTileData.getPTile();
                                if (pTile != null) {
                                    // 从瓦片库条目中获取风格和序列
                                    int nTileStyle = D2Cmp.getTileStyle(pTile);
                                    int nTileSequence = D2Cmp.getTileSequence(pTile);
                                    
                                    // 检查瓦片风格和序列是否匹配（如果需要）
                                    // 当前实现：直接链接瓦片数据
                                    
                                    // 链接瓦片数据
                                    pFloorTileData.setUnk0x20((D2DrlgTileDataStrc) pWarpTile.getUnk0x10());
                                    pWarpTile.setUnk0x10(pFloorTileData);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 瓦片标志辅助函数
    private static final int MAPTILE_WALL_LAYER_BIT = 14;
    private static final int MAPTILE_WALL_LAYER_MASK = 0x1C000; // 3 bits value indicating the wall layer + 1
    
    /**
     * 检查瓦片是否有墙壁层标志
     */
    private static boolean hasMapTileLayer(int dwFlags) {
        return (dwFlags & MAPTILE_WALL_LAYER_MASK) != 0;
    }
    
    /**
     * 获取瓦片的墙壁层值
     */
    private static int getMapTileLayer(int dwFlags) {
        return ((dwFlags & MAPTILE_WALL_LAYER_MASK) >> MAPTILE_WALL_LAYER_BIT) - 1;
    }
    
    /**
     * 检查瓦片类型是否为带门的墙壁
     */
    private static boolean tileTypeIsAWallWithDoor(int nTileType) {
        return nTileType == TILETYPE_WALL_LEFT_DOOR || nTileType == TILETYPE_WALL_RIGHT_DOOR;
    }
    
    /**
     * D2Common.0x6FD897E0
     * 获取链接瓦片数据
     * @param drlgRoom 房间
     * @param bFloor 是否为地板
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nX X坐标
     * @param nY Y坐标
     * @param ppDrlgRoom 输出参数：找到的相邻房间
     * @return 找到的瓦片数据，如果未找到返回 null
     */
    public static D2DrlgTileDataStrc getLinkedTileData(D2DrlgRoom drlgRoom, boolean bFloor,
            int nPackedTileInformation, int nX, int nY, D2DrlgRoom[] ppDrlgRoom) {
        if (drlgRoom == null || ppDrlgRoom == null) {
            return null;
        }
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        
        // 遍历相邻房间
        if (drlgRoom.getPpRoomsNear() != null) {
            for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
                D2DrlgRoom pNearRoomEx = drlgRoom.getPpRoomsNear()[i];
                
                if (pNearRoomEx != drlgRoom && pNearRoomEx.getTileGrid() != null 
                        && DrlgDrlgRoom.areXYInsideCoordinatesOrOnBorder(
                                pNearRoomEx.getDrlgCoord(), nX, nY)) {
                    
                    // 遍历瓦片链接
                    D2DrlgTileLinkStrc pTileLink = pNearRoomEx.getTileGrid().getPMapLinks();
                    while (pTileLink != null) {
                        // 检查是否为地板或非地板
                        if ((!pTileLink.isBFloor() && !bFloor) || (pTileLink.isBFloor() && bFloor)) {
                            // 遍历链接的瓦片数据
                            D2DrlgTileDataStrc pTileData = pTileLink.getPMapTile();
                            while (pTileData != null) {
                                // 检查坐标是否匹配
                                if (pNearRoomEx.getNTileXPos() + pTileData.getNPosX() == nX 
                                        && pNearRoomEx.getNTileYPos() + pTileData.getNPosY() == nY) {
                                    
                                    // 检查瓦片类型和阴影标志
                                    if (pTileData.getNTileType() != TILETYPE_WALL_TOP_CORNER_LEFT 
                                            && (pTileData.getNTileType() == TILETYPE_SHADOW 
                                                    || !tileInfo.isBShadow())) {
                                        
                                        // 检查墙壁层
                                        if (!hasMapTileLayer(pTileData.getDwFlags()) 
                                                || getMapTileLayer(pTileData.getDwFlags()) == tileInfo.getNWallLayer()) {
                                            ppDrlgRoom[0] = pNearRoomEx;
                                            return pTileData;
                                        }
                                    }
                                }
                                
                                pTileData = pTileData.getUnk0x20();
                            }
                        }
                        
                        pTileLink = pTileLink.getPNext();
                    }
                }
            }
        }
        
        ppDrlgRoom[0] = null;
        return null;
    }
    
    /**
     * D2Common.0x6FD89930
     * 添加链接瓦片数据
     * @param pMemPool 内存池
     * @param drlgRoom 房间
     * @param nTileType 瓦片类型
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nX X坐标
     * @param nY Y坐标
     */
    public static void addLinkedTileData(Object pMemPool, D2DrlgRoom drlgRoom, int nTileType,
            int nPackedTileInformation, int nX, int nY) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        // 检查是否为出口类型，或者坐标在房间内
        if ((nTileType != TILETYPE_WALL_LEFT_EXIT && nTileType != TILETYPE_WALL_RIGHT_EXIT) 
                || DrlgDrlgRoom.areXYInsideCoordinates(drlgRoom.getDrlgCoord(), nX, nY)) {
            
            // 查找或创建瓦片链接
            D2DrlgTileLinkStrc pCurLink = drlgRoom.getTileGrid().getPMapLinks();
            while (pCurLink != null) {
                // 检查是否为地板类型匹配
                if ((pCurLink.isBFloor() && nTileType == TILETYPE_FLOOR) 
                        || (nTileType != TILETYPE_FLOOR)) {
                    break; // 使用当前链接
                }
                pCurLink = pCurLink.getPNext();
            }
            
            // 如果没有找到合适的链接，创建新的
            if (pCurLink == null) {
                D2DrlgTileLinkStrc pTileLink = D2Pool.callocStrcPool(pMemPool, D2DrlgTileLinkStrc.class);
                if (pTileLink == null) {
                    pTileLink = new D2DrlgTileLinkStrc();
                }
                
                pTileLink.setBFloor(nTileType == TILETYPE_FLOOR);
                pTileLink.setPMapTile(null);
                pTileLink.setPNext(drlgRoom.getTileGrid().getPMapLinks());
                drlgRoom.getTileGrid().setPMapLinks(pTileLink);
                pCurLink = pTileLink;
            }
            
            // 获取瓦片缓存
            Object pTileCache = getTileCache(drlgRoom, nTileType, nPackedTileInformation);
            
            // 根据瓦片类型初始化瓦片数据
            D2DrlgTileDataStrc[] ppTileData = new D2DrlgTileDataStrc[1];
            ppTileData[0] = pCurLink.getPMapTile();
            
            switch (nTileType) {
                case TILETYPE_FLOOR:
                    initFloorTileData(drlgRoom, ppTileData, nX, nY, nPackedTileInformation, pTileCache);
                    break;
                    
                case TILETYPE_SHADOW:
                    initShadowTileData(drlgRoom, ppTileData, nX, nY, nPackedTileInformation, pTileCache);
                    break;
                    
                default:
                    initWallTileData(drlgRoom, ppTileData, nX, nY, nPackedTileInformation, pTileCache, nTileType);
                    // 如果是出口类型，加载墙壁传送门瓦片
                    if (nTileType == TILETYPE_WALL_LEFT_EXIT || nTileType == TILETYPE_WALL_RIGHT_EXIT) {
                        if (ppTileData[0] != null) {
                            loadWallWarpTiles(drlgRoom, ppTileData[0], nPackedTileInformation, nTileType);
                        }
                    }
                    break;
            }
            
            // 更新链接的瓦片数据
            pCurLink.setPMapTile(ppTileData[0]);
        }
    }
    
    /**
     * D2Common.0x6FD89AF0
     * 链接瓦片数据管理器
     * 管理两个房间之间的链接瓦片数据，处理瓦片类型重映射和标志设置
     * @param pMemPool 内存池
     * @param drlgRoom1 第一个房间
     * @param drlgRoom2 第二个房间
     * @param pTileData 瓦片数据
     * @param nTileType 瓦片类型
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nX X坐标
     * @param nY Y坐标
     */
    public static void linkedTileDataManager(Object pMemPool, D2DrlgRoom drlgRoom1, 
            D2DrlgRoom drlgRoom2, D2DrlgTileDataStrc pTileData, int nTileType,
            int nPackedTileInformation, int nX, int nY) {
        if (drlgRoom1 == null || drlgRoom2 == null || pTileData == null) {
            return;
        }
        
        // 重映射索引数组（-1 表示停止不做任何事，-2 表示忽略重映射但仍初始化瓦片数据）
        final int[] nRemapIndices = {
            -1,  // [0] TILETYPE_FLOOR
            0,   // [1] TILETYPE_WALL_LEFT
            1,   // [2] TILETYPE_WALL_RIGHT
            2,   // [3] TILETYPE_WALL_TOP_CORNER_RIGHT
            -1,  // [4] TILETYPE_WALL_TOP_CORNER_LEFT - 这个不重映射
            3,   // [5] TILETYPE_WALL_TOP_RIGHT
            4,   // [6] TILETYPE_WALL_BOTTOM_LEFT
            5,   // [7] TILETYPE_WALL_BOTTOM_RIGHT
            -2,  // [8] TILETYPE_SHADOW
            -2,  // [9] TILETYPE_WALL_LEFT_EXIT
            -1,  // [10] TILETYPE_WALL_RIGHT_EXIT
            -1,  // [11] TILETYPE_WALL_LEFT_DOOR
            -1,  // [12] TILETYPE_WALL_RIGHT_DOOR
            -1,  // [13] TILETYPE_TREE
        };
        
        // 墙壁瓦片类型重映射表
        final int[][] nWallTileTypeRemap = {
            // [TILETYPE_WALL_LEFT] -> [pTileData->nTileType - 1]
            {TILETYPE_WALL_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_LEFT},
            // [TILETYPE_WALL_RIGHT]
            {TILETYPE_WALL_LEFT, TILETYPE_WALL_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_RIGHT, TILETYPE_WALL_RIGHT},
            // [TILETYPE_WALL_TOP_CORNER_RIGHT]
            {TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT},
            // [TILETYPE_WALL_TOP_RIGHT]
            {TILETYPE_WALL_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_TOP_RIGHT, TILETYPE_WALL_BOTTOM_LEFT, TILETYPE_WALL_LEFT},
            // [TILETYPE_WALL_BOTTOM_LEFT]
            {TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_TOP_CORNER_RIGHT, TILETYPE_WALL_BOTTOM_LEFT, TILETYPE_WALL_RIGHT},
            // [TILETYPE_WALL_BOTTOM_RIGHT]
            {TILETYPE_WALL_LEFT, TILETYPE_WALL_RIGHT, TILETYPE_WALL_TOP_CORNER_RIGHT, 
             TILETYPE_WALL_TOP_CORNER_LEFT, TILETYPE_WALL_LEFT, TILETYPE_WALL_RIGHT, TILETYPE_WALL_BOTTOM_RIGHT},
        };
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        int v10 = (nTileType >= 0 && nTileType < nRemapIndices.length) ? nRemapIndices[nTileType] : -1;
        
        // 如果瓦片有 MAPTILE_UNK_0x1 标志
        if ((pTileData.getDwFlags() & 0x1) != 0) { // MAPTILE_UNK_0x1
            if (tileTypeIsAWallWithDoor(pTileData.getNTileType())) {
                initializeTileDataFlags(drlgRoom1, pTileData, nPackedTileInformation, 
                        pTileData.getNTileType(), nX, nY);
            }
            return;
        }
        
        // 如果没有 bLayerAbove 标志
        if (!tileInfo.isBLayerAbove()) {
            boolean b = false;
            if (!tileTypeIsAWallWithDoor(nTileType)) {
                if (tileTypeIsAWallWithDoor(pTileData.getNTileType()) 
                        && (nX == drlgRoom2.getNTileXPos() || nY == drlgRoom2.getNTileYPos())) {
                    return;
                }
                b = true;
            }
            
            if (b || (nX != drlgRoom1.getNTileXPos() && nY != drlgRoom1.getNTileYPos())) {
                boolean bIsTileTypeANormalWallOrFloor = pTileData.getNTileType() <= TILETYPE_WALL_BOTTOM_RIGHT;
                if (v10 < 0 || !bIsTileTypeANormalWallOrFloor) {
                    if (v10 != -1) {
                        return;
                    }
                } else {
                    // 执行瓦片类型重映射
                    if (v10 < 6 && pTileData.getNTileType() >= TILETYPE_WALL_LEFT) {
                        int nWallIdx = pTileData.getNTileType() - TILETYPE_WALL_LEFT; // 从 0 到 6
                        if (nWallIdx >= 0 && nWallIdx < 7 && v10 < nWallTileTypeRemap.length) {
                            nTileType = nWallTileTypeRemap[v10][nWallIdx];
                        }
                    }
                }
            }
        }
        
        // 处理右上角墙壁的特殊情况
        if (pTileData.getNTileType() == TILETYPE_WALL_TOP_CORNER_RIGHT) {
            if (nTileType != TILETYPE_WALL_TOP_CORNER_RIGHT) {
                if (pTileData.getUnk0x20() != null) {
                    pTileData.getUnk0x20().setDwFlags(
                            pTileData.getUnk0x20().getDwFlags() | MAPTILE_HIDDEN);
                    // 更新碰撞检测（使用已实现的函数）
                    if (drlgRoom2.getRoom() != null) {
                        com.d2moo.common.collision.D2CommonCollision.firstFn(
                                drlgRoom2.getRoom(), pTileData.getUnk0x20(), null);
                    }
                }
            }
        } else {
            if (nTileType == TILETYPE_WALL_TOP_CORNER_RIGHT) {
                pTileData.setDwFlags(pTileData.getDwFlags() | ((3 << MAPTILE_WALL_LAYER_BIT) | MAPTILE_HIDDEN));
                // 更新碰撞检测（使用已实现的函数）
                if (drlgRoom2.getRoom() != null) {
                    com.d2moo.common.collision.D2CommonCollision.firstFn(
                            drlgRoom2.getRoom(), pTileData, null);
                }
                addLinkedTileData(pMemPool, drlgRoom1, TILETYPE_WALL_TOP_CORNER_RIGHT, 
                        nPackedTileInformation, nX, nY);
            }
        }
        
        // 如果瓦片类型不匹配，或者需要更新瓦片缓存
        if (nTileType != pTileData.getNTileType() 
                || (pTileData.getNTileType() == TILETYPE_FLOOR 
                        && pTileData.getPTile() != null)) {
            // 使用 D2CMP 函数获取瓦片风格和序列（如果需要检查）
            Object pCurrentTile = pTileData.getPTile();
            if (pCurrentTile != null) {
                int nCurrentStyle = D2Cmp.getTileStyle(pCurrentTile);
                int nCurrentSequence = D2Cmp.getTileSequence(pCurrentTile);
                // 可以在这里添加风格和序列的检查逻辑
            }
            
            // 如果类型不匹配，更新瓦片缓存
            Object pTileCache = getTileCache(drlgRoom2, nTileType, nPackedTileInformation);
            pTileData.setNTileType(nTileType);
            
            if (pTileCache != pTileData.getPTile() && drlgRoom2.getRoom() != null) {
                // 更新碰撞检测（使用已实现的函数）
                if (drlgRoom2.getRoom() != null) {
                    com.d2moo.common.collision.D2CommonCollision.firstFn(
                            drlgRoom2.getRoom(), pTileData, pTileCache);
                }
            }
            
            pTileData.setPTile(pTileCache);
        }
        
        // 初始化瓦片数据标志
        initializeTileDataFlags(drlgRoom1, pTileData, nPackedTileInformation, 
                pTileData.getNTileType(), nX, nY);
    }
    
    /**
     * D2Common.0x6FD89CC0
     * 获取或创建链接瓦片数据
     * 在相邻房间中查找匹配的链接瓦片，如果找到则调用管理器，否则创建新的链接瓦片
     * @param pMemPool 内存池
     * @param drlgRoom 房间
     * @param nTileType 瓦片类型
     * @param nPackedTileInformation 打包的瓦片信息
     * @param nX X坐标
     * @param nY Y坐标
     */
    public static void getCreateLinkedTileData(Object pMemPool, D2DrlgRoom drlgRoom, int nTileType,
            int nPackedTileInformation, int nX, int nY) {
        if (drlgRoom == null) {
            return;
        }
        
        D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nPackedTileInformation);
        
        // 遍历相邻房间
        if (drlgRoom.getPpRoomsNear() != null) {
            for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
                D2DrlgRoom pNearRoomEx = drlgRoom.getPpRoomsNear()[i];
                
                if (pNearRoomEx != drlgRoom && pNearRoomEx.getTileGrid() != null 
                        && DrlgDrlgRoom.areXYInsideCoordinatesOrOnBorder(
                                pNearRoomEx.getDrlgCoord(), nX, nY)) {
                    
                    // 遍历瓦片链接
                    D2DrlgTileLinkStrc pTileLink = pNearRoomEx.getTileGrid().getPMapLinks();
                    while (pTileLink != null) {
                        D2DrlgTileDataStrc pTileData = pTileLink.getPMapTile();
                        
                        // 检查是否为地板或非地板类型匹配
                        if ((!pTileLink.isBFloor() && nTileType != TILETYPE_FLOOR) 
                                || (pTileLink.isBFloor() && nTileType == TILETYPE_FLOOR)) {
                            
                            // 遍历链接的瓦片数据
                            while (pTileData != null) {
                                // 检查坐标是否匹配
                                if (pNearRoomEx.getNTileXPos() + pTileData.getNPosX() == nX 
                                        && pNearRoomEx.getNTileYPos() + pTileData.getNPosY() == nY) {
                                    
                                    // 检查瓦片类型和阴影标志
                                    if (pTileData.getNTileType() != TILETYPE_WALL_TOP_CORNER_LEFT 
                                            && (pTileData.getNTileType() == TILETYPE_SHADOW 
                                                    || !tileInfo.isBShadow())) {
                                        
                                        // 检查墙壁层
                                        if (!hasMapTileLayer(pTileData.getDwFlags()) 
                                                || getMapTileLayer(pTileData.getDwFlags()) == tileInfo.getNWallLayer()) {
                                            // 找到匹配的瓦片，调用管理器
                                            linkedTileDataManager(pMemPool, drlgRoom, pNearRoomEx, 
                                                    pTileData, nTileType, nPackedTileInformation, nX, nY);
                                            return;
                                        }
                                    }
                                }
                                
                                pTileData = pTileData.getUnk0x20();
                            }
                        }
                        
                        pTileLink = pTileLink.getPNext();
                    }
                }
            }
        }
        
        // 如果没有找到匹配的链接瓦片，创建新的
        addLinkedTileData(pMemPool, drlgRoom, nTileType, nPackedTileInformation, nX, nY);
    }
}
