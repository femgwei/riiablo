package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.dungeon.Dungeon;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 核心模块
 * 对应 C++ 文件：DrlgDrlg.cpp
 * 
 * 注意：本模块依赖以下其他模块的函数，需要先实现：
 * - D2CMP 相关函数（瓦片库加载）
 * - DATATBLS 相关函数（数据表查询）
 * - DUNGEON 相关函数（坐标转换）
 * - SEED 相关函数（随机数生成）
 */
public class DrlgDrlg {
    
    /**
     * D2Common.0x6FD74120 (#10014)
     * 分配 Drlg 结构
     */
    public static D2DrlgStrc allocDrlg(D2DrlgAct act, byte actNo, Object archive, 
            int initSeed, int townLevelId, int flags, Object game, 
            byte difficulty, Object pfAutoMap, Object pfTownAutoMap) {
        // C++ 原始代码：
        // D2DrlgStrc* drlg = D2_CALLOC_STRC_POOL(pMemPool, D2DrlgStrc);
        // 
        // Java 实现：使用类型安全的结构体分配方法，无需强制类型转换
        D2DrlgStrc drlg = D2Pool.callocStrcPool(act.getPMemPool(), D2DrlgStrc.class);
        if (drlg == null) {
            D2Log.warning("Failed to allocate D2DrlgStrc from memory pool");
            return null;
        }
        
        drlg.setAct(act);
        drlg.setMempool(act.getPMemPool());
        // archive 在游戏中总是 null
        drlg.setArchive(archive);
        drlg.setActNo(actNo);
        
        // 初始化种子
        if (drlg.getSeed() == null) {
            drlg.setSeed(new D2Seed());
        }
        Seed.initLowSeed(drlg.getSeed(), initSeed);
        
        // 生成起始种子
        drlg.setStartSeed((int) Seed.rollRandomNumber(drlg.getSeed()));
        D2Log.debug("DRLG_ALLOC act=%d difficulty=%d gameSeed=%d startSeed=%d town=%d flags=0x%X",
            actNo, difficulty, initSeed, drlg.getStartSeed(), townLevelId, flags);
        
        drlg.setFlags(flags);
        drlg.setGame(game);
        drlg.setGameLowSeed(initSeed);
        drlg.setDifficulty(difficulty);
        drlg.setPfAutomap(pfAutoMap);
        drlg.setPfTownAutomap(pfTownAutoMap);
        
        // 根据 Act 加载不同的瓦片库
        // 从 drlg 获取瓦片库数组（D2DrlgStrc 已经有 tiles 字段）
        Object[] tiles = drlg.getTiles();
        
        String path = "";
        // D2C_Acts is 0-based (Act I = 0).  Keeping this explicit avoids
        // accidentally loading Act II tiles for an Act I DRLG.
        switch (actNo) {
            case D2C_Acts.ACT_I:
                path = "DATA\\GLOBAL\\Tiles\\Act1\\Town\\Floor.dt1";
                if (tiles != null) {
                    com.d2moo.common.d2cmp.D2Cmp.loadTileLibrarySlot(archive, tiles, path);
                }
                break;
            case D2C_Acts.ACT_II:
                // 生成随机偏移
                int staffLevelOffset = 0;
                int bossLevelOffset = 0;
                do {
                    staffLevelOffset = Seed.rollLimitedRandomNumber(drlg.getSeed(), 7);
                    bossLevelOffset = Seed.rollLimitedRandomNumber(drlg.getSeed(), 7);
                } while (staffLevelOffset == bossLevelOffset);
                
                // 使用关卡ID常量
                drlg.setStaffTombLevel(D2LevelIds.LEVEL_TALRASHASTOMB1 + staffLevelOffset);
                drlg.setBossTombLevel(D2LevelIds.LEVEL_TALRASHASTOMB1 + bossLevelOffset);
                
                path = "DATA\\GLOBAL\\Tiles\\Act2\\Town\\Ground.dt1";
                if (tiles != null) {
                    com.d2moo.common.d2cmp.D2Cmp.loadTileLibrarySlot(archive, tiles, path);
                }
                break;
            case D2C_Acts.ACT_III:
                // 生成丛林互连标志
                drlg.setJungleInterlink((int) (Seed.rollRandomNumber(drlg.getSeed()) & 1));
                
                path = "DATA\\GLOBAL\\Tiles\\ACT3\\Kurast\\sets.dt1";
                if (tiles != null) {
                    com.d2moo.common.d2cmp.D2Cmp.loadTileLibrarySlot(archive, tiles, path);
                }
                break;
            default:
                break;
        }
        
        // 初始化房间状态列表
        DrlgActivate.initializeRoomExStatusLists(drlg);
        
        // 创建关卡连接（已在 DrlgOutPlace 模块中实现）
        DrlgOutPlace.createLevelConnections(drlg, actNo);
        D2Log.debug("DRLG_ALLOC connections-created act=%d", actNo);
        
        // 如果指定了城镇关卡ID，初始化该关卡
        if (townLevelId != D2LevelIds.LEVEL_NONE) {
            initLevel(getLevel(drlg, townLevelId));
        }
        
        return drlg;
    }
    
    /**
     * D2Common.0x6FD743B0 (#10012)
     * 释放 Drlg 结构
     */
    public static void freeDrlg(D2DrlgStrc drlg) {
        // 释放所有关卡
        D2DrlgLevel level = drlg.getLevel();
        while (level != null) {
            D2DrlgLevel nextLevel = level.getPNextLevel();
            freeLevel(drlg.getMempool(), level, false);
            D2Pool.freePool(drlg.getMempool(), level);
            level = nextLevel;
        }
        drlg.setLevel(null);
        
        // 释放所有传送门
        D2DrlgWarp warp = drlg.getWarp();
        while (warp != null) {
            D2DrlgWarp nextWarp = warp.getPNext();
            D2Pool.freePool(drlg.getMempool(), warp);
            warp = nextWarp;
        }
        drlg.setWarp(null);
        
        // 释放 Drlg 结构本身
        D2Pool.freePool(drlg.getMempool(), drlg);
    }
    
    /**
     * D2Common.0x6FD74440
     * 释放关卡
     */
    public static void freeLevel(Object memPool, D2DrlgLevel level, boolean alloc) {
        if (alloc) {
            if (level.getPresetMaps() == null && level.getRooms() > 0) {
                int[] presetMaps = D2Pool.callocIntArrayPool(memPool, level.getRooms());
                level.setPresetMaps(presetMaps);
            }
        } else {
            if (level.getPresetMaps() != null) {
                D2Pool.freePool(memPool, level.getPresetMaps());
                level.setPresetMaps(null);
            }
        }
        
        // 释放所有房间
        D2DrlgRoom nextRoomEx = level.getFirstRoomEx();
        if (nextRoomEx != null) {
            int nCounter = 0;
            do {
                D2DrlgRoom drlgRoom = nextRoomEx;
                nextRoomEx = nextRoomEx.getDrlgRoomNext();
                
                if (level.getPresetMaps() != null) {
                    level.getPresetMaps()[nCounter] = drlgRoom.getOtherFlags() & 1;
                    ++nCounter;
                }
                
                DrlgDrlgRoom.freeRoomEx(drlgRoom);
            } while (nextRoomEx != null);
            
            level.setFirstRoomEx(null);
            level.setRooms(0);
        }
        
        // 释放当前地图
        if (level.getPCurrentMap() != null) {
            DrlgPreset.freeDrlgMap(memPool, level.getPCurrentMap());
            level.setPCurrentMap(null);
        }
        
        // 根据关卡类型释放数据
        switch (level.getDrlgType()) {
            case D2DrlgTypes.DRLGTYPE_MAZE:
                DrlgMaze.resetMazeRecord(level, alloc);
                break;
            case D2DrlgTypes.DRLGTYPE_PRESET:
                DrlgPreset.resetDrlgMap(level, alloc);
                break;
            case D2DrlgTypes.DRLGTYPE_OUTDOOR:
                DrlgOutdoors.freeOutdoorInfo(level, alloc);
                break;
            default:
                D2Log.warning("DRLG_FreeLevel: Unknown drlg type: " + level.getDrlgType());
                break;
        }
        
        // 清空瓦片信息
        level.setNTileInfo(0);
        
        // 清空传送门坐标
        level.setNRoomCoords(0);
        
        // 释放丛林定义
        if (!alloc && level.getPJungleDefs() != null) {
            D2Pool.freePool(memPool, level.getPJungleDefs());
            level.setPJungleDefs(null);
        }
        
        // 释放构建数据
        D2DrlgBuildStrc nextDrlgBuild = level.getPBuild();
        if (nextDrlgBuild != null) {
            do {
                D2DrlgBuildStrc drlgBuild = nextDrlgBuild;
                nextDrlgBuild = nextDrlgBuild.getPNext();
                D2Pool.freePool(memPool, drlgBuild);
            } while (nextDrlgBuild != null);
            
            level.setPBuild(null);
        }
    }
    
    /**
     * D2Common.0x6FD748D0 (#10013)
     * 分配关卡
     */
    public static D2DrlgLevel allocLevel(D2DrlgStrc drlg, int levelId) {
        D2DrlgLevel level = D2Pool.callocStrcPool(drlg.getMempool(), D2DrlgLevel.class);
        if (level == null) {
            D2Log.warning("Failed to allocate D2DrlgLevel from memory pool");
            return null;
        }
        
        level.setDrlg(drlg);
        level.setLevelId(levelId);
        
        // 从数据表获取关卡定义记录
        D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef == null) {
            D2Log.warning("Failed to get level def record for level id: " + levelId);
            return null;
        }
        level.setLevelType(levelDef.getDwLevelType());
        level.setDrlgType(levelDef.getDwDrlgType());
        
        if ((drlg.getFlags() & D2DrlgFlags.ONCLIENT) != 0) {
            // 设置自动地图显示标志
            level.setFlags(level.getFlags() | D2DrlgLevelFlags.AUTOMAP_REVEAL);
        }
        
        // 初始化种子
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        Seed.initLowSeed(level.getSeed(), levelId + drlg.getStartSeed());
        
        // 根据关卡类型初始化数据
        switch (level.getDrlgType()) {
            case D2DrlgTypes.DRLGTYPE_MAZE:
                DrlgMaze.initLevelData(level);
                break;
            case D2DrlgTypes.DRLGTYPE_PRESET:
                DrlgPreset.initLevelData(level);
                break;
            case D2DrlgTypes.DRLGTYPE_OUTDOOR:
                DrlgOutdoors.allocOutdoorInfo(level);
                break;
            default:
                D2Log.warning("DRLG_AllocLevel: Unknown drlg type: " + level.getDrlgType());
                break;
        }
        
        level.setPNextLevel(drlg.getLevel());
        drlg.setLevel(level);
        
        return level;
    }
    
    /**
     * D2Common.0x6FD749A0 (#10005)
     * 获取关卡（如果不存在则创建）
     */
    public static D2DrlgLevel getLevel(D2DrlgStrc drlg, int levelId) {
        D2DrlgLevel level = drlg.getLevel();
        while (level != null) {
            if (level.getLevelId() == levelId) {
                return level;
            }
            level = level.getPNextLevel();
        }
        
        return allocLevel(drlg, levelId);
    }
    
    /**
     * D2Common.0x6FD749D0
     * 获取赫拉迪克法杖墓穴关卡ID
     */
    public static int getHoradricStaffTombLevelId(D2DrlgStrc drlg) {
        if (drlg != null) {
            return drlg.getStaffTombLevel();
        }
        return 0;
    }
    
    /**
     * D2Common.0x6FD749E0
     * 从坐标获取方向
     */
    public static int getDirectionFromCoordinates(D2DrlgCoord coord1, D2DrlgCoord coord2) {
        if (coord1.getNPosX() <= coord2.getNPosX()) {
            if (coord2.getNPosX() == coord1.getNPosX() + coord1.getNWidth()) {
                return 2; // DIRECTION_SOUTHEAST
            }
        } else {
            if (coord1.getNPosX() == coord2.getNPosX() + coord2.getNWidth()) {
                return 0; // DIRECTION_SOUTHWEST
            }
        }
        
        if (coord1.getNPosY() <= coord2.getNPosY()) {
            if (coord2.getNPosY() == coord1.getNPosY() + coord1.getNHeight()) {
                return 3; // DIRECTION_NORTHEAST
            }
        } else {
            if (coord1.getNPosY() == coord2.getNPosY() + coord2.getNHeight()) {
                return 1; // DIRECTION_NORTHWEST
            }
        }
        
        return -1; // DIRECTION_INVALID
    }
    
    /**
     * 计算曼哈顿距离（辅助函数）
     * @param coord1 坐标1
     * @param coord2 坐标2
     * @param distanceX 输出的X距离（数组，用于返回）
     * @param distanceY 输出的Y距离（数组，用于返回）
     */
    private static void computeManhattanDistance(D2DrlgCoord coord1, D2DrlgCoord coord2, 
            int[] distanceX, int[] distanceY) {
        // 负距离表示我们在另一个矩形"内部"
        if (coord1.getNPosX() >= coord2.getNPosX()) {
            distanceX[0] = coord1.getNPosX() - coord2.getNWidth() - coord2.getNPosX();
        } else {
            distanceX[0] = coord2.getNPosX() - coord1.getNWidth() - coord1.getNPosX();
        }
        
        if (coord1.getNPosY() >= coord2.getNPosY()) {
            distanceY[0] = coord1.getNPosY() - coord2.getNHeight() - coord2.getNPosY();
        } else {
            distanceY[0] = coord2.getNPosY() - coord1.getNHeight() - coord1.getNPosY();
        }
    }
    
    /**
     * D2Common.0x6FD777B0
     * 使用曼哈顿距离检查是否不重叠
     * @param coord1 坐标1
     * @param coord2 坐标2
     * @param nMaxDistanceToAssumeCollision 最大距离（如果距离小于此值则认为碰撞）
     * @return 如果不重叠返回 true，否则返回 false
     */
    public static boolean checkNotOverlappingUsingManhattanDistance(D2DrlgCoord coord1, 
            D2DrlgCoord coord2, int nMaxDistanceToAssumeCollision) {
        int[] distanceX = new int[1];
        int[] distanceY = new int[1];
        computeManhattanDistance(coord1, coord2, distanceX, distanceY);
        return distanceX[0] >= nMaxDistanceToAssumeCollision || distanceY[0] >= nMaxDistanceToAssumeCollision;
    }
    
    /**
     * D2Common.0x6FD77800
     * 检查是否与正交边距重叠
     * @param coord1 坐标1
     * @param coord2 坐标2
     * @param nOrthogonalDistanceMax 正交距离最大值
     * @return 如果重叠返回 true，否则返回 false
     */
    public static boolean checkOverlappingWithOrthogonalMargin(D2DrlgCoord coord1, 
            D2DrlgCoord coord2, int nOrthogonalDistanceMax) {
        int[] distanceX = new int[1];
        int[] distanceY = new int[1];
        computeManhattanDistance(coord1, coord2, distanceX, distanceY);
        
        if (nOrthogonalDistanceMax != 0) {
            if (distanceX[0] == 0 && distanceY[0] <= nOrthogonalDistanceMax) {
                return true;
            }
            
            if (distanceY[0] == 0 && distanceX[0] <= nOrthogonalDistanceMax) {
                return true;
            }
        } else {
            if (distanceX[0] <= 0 && distanceY[0] <= 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * D2Common.0x6FD74A40
     * 为房间创建 Room 对象
     */
    public static void createRoomForRoomEx(D2DrlgStrc drlg, D2DrlgRoom drlgRoom) {
        D2DrlgCoords drlgCoords = new D2DrlgCoords();
        drlgCoords.setNTileXPos(drlgRoom.getDrlgCoord().getNTileXPos());
        drlgCoords.setNSubtileX(drlgRoom.getDrlgCoord().getNTileXPos());
        drlgCoords.setNTileYPos(drlgRoom.getDrlgCoord().getNTileYPos());
        drlgCoords.setNSubtileY(drlgRoom.getDrlgCoord().getNTileYPos());
        drlgCoords.setNTileWidth(drlgRoom.getDrlgCoord().getNTileWidth());
        drlgCoords.setNSubtileWidth(drlgRoom.getDrlgCoord().getNTileWidth());
        drlgCoords.setNTileHeight(drlgRoom.getDrlgCoord().getNTileHeight());
        drlgCoords.setNSubtileHeight(drlgRoom.getDrlgCoord().getNTileHeight());
        
        Dungeon.gameTileToSubtileCoords(drlgCoords);
        
        if (drlgRoom.getTileGrid() != null 
            && (drlgRoom.getTileGrid().getPTiles().getNWalls() > 0 
                || drlgRoom.getTileGrid().getPTiles().getNFloors() > 0)) {
            int dwFlags = 0;
            
            if ((drlgRoom.getFlags() & D2DrlgRoomFlags.AUTOMAP_REVEAL) != 0) {
                dwFlags = 4;
            } else if ((drlgRoom.getOtherFlags() & 1) != 0) {
                dwFlags = 1;
            } else {
                dwFlags = 0;
            }
            
            D2ActiveRoom room = Dungeon.allocRoom(drlg.getAct(), drlgRoom, drlgCoords, 
                drlgRoom.getTileGrid().getPTiles(), 
                (int)Seed.rollRandomNumber(drlgRoom.getSeed()), dwFlags);
            drlgRoom.setRoom(room);
        }
    }
    
    /**
     * D2Common.0x6FD74B30
     * 获取房间中心X坐标和传送门X坐标数组
     */
    public static int[] getRoomCenterX_RoomWarpXFromRoom(D2DrlgRoom drlgRoom) {
        return drlgRoom.getLevel().getNRoomCenterWarpX();
    }
    
    /**
     * D2Common.0x6FD74B40
     * 计算关卡传送门信息
     */
    public static void computeLevelWarpInfo(D2DrlgLevel level) {
        D2DrlgRoom drlgRoom = level.getFirstRoomEx();
        while (drlgRoom != null) {
            // 检查是否有传送点
            boolean bHasWarp = (drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_WAYPOINT_MASK) != 0;
            
            // 检查是否有其他传送门
            if ((drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_WARP_MASK) != 0 && !bHasWarp) {
                int nWarpIndex = 0;
                for (int warpMask = D2DrlgRoomFlags.HAS_WARP_0; 
                     (warpMask & D2DrlgRoomFlags.HAS_WARP_MASK) != 0; 
                     warpMask <<= 1) {
                    if ((drlgRoom.getFlags() & warpMask) != 0 
                        && DrlgDrlgWarp.getWarpDestinationFromArray(level, (byte)nWarpIndex) != -1) {
                        bHasWarp = true;
                    }
                    nWarpIndex++;
                }
            }
            
            if (bHasWarp) {
                int nRoomCoords = level.getNRoomCoords();
                int[] nRoomCenterWarpX = level.getNRoomCenterWarpX();
                int[] nRoomCenterWarpY = level.getNRoomCenterWarpY();
                
                // 将传送门放在房间中心
                nRoomCenterWarpX[nRoomCoords] = drlgRoom.getDrlgCoord().getNTileXPos() 
                    + drlgRoom.getDrlgCoord().getNTileWidth() / 2;
                nRoomCenterWarpY[nRoomCoords] = drlgRoom.getDrlgCoord().getNTileYPos() 
                    + drlgRoom.getDrlgCoord().getNTileHeight() / 2;
                
                // 转换为子瓦片坐标
                int[] x = new int[]{nRoomCenterWarpX[nRoomCoords]};
                int[] y = new int[]{nRoomCenterWarpY[nRoomCoords]};
                Dungeon.gameTileToSubtileCoords(x, y);
                nRoomCenterWarpX[nRoomCoords] = x[0];
                nRoomCenterWarpY[nRoomCoords] = y[0];
                
                level.setNRoomCoords(nRoomCoords + 1);
            }
            
            drlgRoom = drlgRoom.getDrlgRoomNext();
        }
    }
    
    /**
     * D2Common.0x6FD74C10 (#10006)
     * 初始化关卡
     */
    public static void initLevel(D2DrlgLevel level) {
        if (level == null) {
            D2Log.warning("DRLG_InitLevel: null level");
            return;
        }
        D2Log.debug("DRLG_INIT_LEVEL begin id=%d type=%d seedLow=%d seedHigh=%d",
            level.getLevelId(), level.getDrlgType(),
            level.getSeed() != null ? level.getSeed().getNLowSeed() : 0,
            level.getSeed() != null ? level.getSeed().getNHighSeed() : 0);
        // 初始化种子
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        Seed.initLowSeed(level.getSeed(), level.getLevelId() + level.getDrlg().getStartSeed());
        
        // 根据关卡类型生成关卡
        switch (level.getDrlgType()) {
            case D2DrlgTypes.DRLGTYPE_MAZE:
                DrlgMaze.generateLevel(level);
                break;
            case D2DrlgTypes.DRLGTYPE_PRESET:
                DrlgPreset.generateLevel(level);
                break;
            case D2DrlgTypes.DRLGTYPE_OUTDOOR:
                DrlgOutdoors.generateLevel(level);
                break;
            default:
                D2Log.warning("DRLG_InitLevel: unknown type=%d level=%d",
                    level.getDrlgType(), level.getLevelId());
                return;
        }
        
        D2Log.debug("DRLG_INIT_LEVEL end id=%d type=%d rooms=%d firstRoom=%s coord=%s",
            level.getLevelId(), level.getDrlgType(), level.getRooms(),
            level.getFirstRoomEx() != null ? "yes" : "no",
            level.getLevelCoords() != null ? String.format("(%d,%d %dx%d)",
                level.getLevelCoords().getNPosX(), level.getLevelCoords().getNPosY(),
                level.getLevelCoords().getNWidth(), level.getLevelCoords().getNHeight()) : "none");

        // 处理预设地图标志
        if (level.getRooms() > 0 && level.getPresetMaps() != null) {
            int nCounter = 0;
            D2DrlgRoom drlgRoom = level.getFirstRoomEx();
            while (drlgRoom != null) {
                if (level.getPresetMaps()[nCounter] != 0) {
                    drlgRoom.setOtherFlags(drlgRoom.getOtherFlags() | 1);
                }
                ++nCounter;
                drlgRoom = drlgRoom.getDrlgRoomNext();
            }
        }
        
        // 计算传送门信息
        computeLevelWarpInfo(level);
    }
    
    /**
     * D2Common.0x6FD74D50
     * 获取关卡中已填充的房间数量
     */
    public static int getNumberOfPopulatedRoomsInLevel(D2DrlgStrc drlg, int levelId) {
        D2DrlgLevel level = getLevel(drlg, levelId);
        int counter = 0;
        
        D2DrlgRoom room = level.getFirstRoomEx();
        while (room != null) {
            if ((room.getFlags() & D2DrlgRoomFlags.POPULATION_ZERO) == 0) {
                counter++;
            }
            room = room.getDrlgRoomNext();
        }
        
        return counter;
    }
    
    /**
     * D2Common.0x6FD74D90
     * 从关卡获取最小和最大坐标
     */
    public static void getMinAndMaxCoordinatesFromLevel(D2DrlgLevel level, 
            int[] tileMinX, int[] tileMinY, int[] tileMaxX, int[] tileMaxY) {
        D2DrlgRoom drlgRoom = level.getFirstRoomEx();
        
        if (drlgRoom == null) {
            return;
        }
        
        tileMinX[0] = drlgRoom.getDrlgCoord().getNTileXPos();
        tileMinY[0] = drlgRoom.getDrlgCoord().getNTileYPos();
        tileMaxX[0] = drlgRoom.getDrlgCoord().getNTileXPos() + drlgRoom.getDrlgCoord().getNTileWidth();
        tileMaxY[0] = drlgRoom.getDrlgCoord().getNTileYPos() + drlgRoom.getDrlgCoord().getNTileHeight();
        
        while (drlgRoom != null) {
            int roomMaxX = drlgRoom.getDrlgCoord().getNTileXPos() + drlgRoom.getDrlgCoord().getNTileWidth();
            int roomMaxY = drlgRoom.getDrlgCoord().getNTileYPos() + drlgRoom.getDrlgCoord().getNTileHeight();
            
            if (roomMaxX > tileMaxX[0]) {
                tileMaxX[0] = roomMaxX;
            }
            if (drlgRoom.getDrlgCoord().getNTileXPos() < tileMinX[0]) {
                tileMinX[0] = drlgRoom.getDrlgCoord().getNTileXPos();
            }
            if (roomMaxY > tileMaxY[0]) {
                tileMaxY[0] = roomMaxY;
            }
            if (drlgRoom.getDrlgCoord().getNTileYPos() < tileMinY[0]) {
                tileMinY[0] = drlgRoom.getDrlgCoord().getNTileYPos();
            }
            
            drlgRoom = drlgRoom.getDrlgRoomNext();
        }
    }
    
    /**
     * D2Common.0x6FD74E10
     * 更新房间坐标
     */
    public static void updateRoomExCoordinates(D2DrlgLevel level) {
        if (level == null) {
            D2Log.warning("DRLG_UpdateRoomExCoordinates: level is null");
            return;
        }
        
        if (level.getFirstRoomEx() == null) {
            D2Log.warning("DRLG_UpdateRoomExCoordinates: level.getFirstRoomEx() is null");
            return;
        }
        
        int[] tileMinX = new int[1];
        int[] tileMinY = new int[1];
        int[] tileMaxX = new int[1];
        int[] tileMaxY = new int[1];
        getMinAndMaxCoordinatesFromLevel(level, tileMinX, tileMinY, tileMaxX, tileMaxY);
        
        if (level.getLevelCoords().getNWidth() < tileMaxX[0] - tileMinX[0]) {
            D2Log.warning("DRLG_UpdateRoomExCoordinates: level width < tileMaxX - tileMinX");
        }
        
        if (level.getLevelCoords().getNHeight() < tileMaxY[0] - tileMinY[0]) {
            D2Log.warning("DRLG_UpdateRoomExCoordinates: level height < tileMaxY - tileMinY");
        }
        
        D2DrlgRoom drlgRoom = level.getFirstRoomEx();
        while (drlgRoom != null) {
            drlgRoom.getDrlgCoord().setNTileXPos(
                drlgRoom.getDrlgCoord().getNTileXPos() + level.getLevelCoords().getNPosX() - tileMinX[0]);
            drlgRoom.getDrlgCoord().setNTileYPos(
                drlgRoom.getDrlgCoord().getNTileYPos() + level.getLevelCoords().getNPosY() - tileMinY[0]);
            
            drlgRoom = drlgRoom.getDrlgRoomNext();
        }
    }
    
    /**
     * D2Common.0x6FD74EF0
     * 从关卡和坐标获取房间
     */
    public static D2DrlgRoom getRoomExFromLevelAndCoordinates(D2DrlgLevel level, int x, int y) {
        D2DrlgRoom drlgRoom = level.getFirstRoomEx();
        while (drlgRoom != null) {
            if (DrlgDrlgRoom.areXYInsideCoordinates(drlgRoom.getDrlgCoord(), x, y)) {
                return drlgRoom;
            }
            drlgRoom = drlgRoom.getDrlgRoomNext();
        }
        
        return null;
    }
    
    /**
     * D2Common.0x6FD74F70
     * 从坐标获取房间（带提示）
     */
    public static D2DrlgRoom getRoomExFromCoordinates(int x, int y, D2DrlgStrc drlg, 
            D2DrlgRoom drlgRoomHint, D2DrlgLevel level) {
        // 如果提供了提示房间，先检查提示房间及其附近房间
        if (drlgRoomHint != null) {
            if (DrlgDrlgRoom.areXYInsideCoordinates(drlgRoomHint.getDrlgCoord(), x, y)) {
                return drlgRoomHint;
            }
            
            // 检查附近房间
            if (drlgRoomHint.getPpRoomsNear() != null) {
                for (int i = 0; i < drlgRoomHint.getNRoomsNear(); ++i) {
                    if (drlgRoomHint != drlgRoomHint.getPpRoomsNear()[i]) {
                        if (DrlgDrlgRoom.areXYInsideCoordinates(
                            drlgRoomHint.getPpRoomsNear()[i].getDrlgCoord(), x, y)) {
                            return drlgRoomHint.getPpRoomsNear()[i];
                        }
                    }
                }
            }
        }
        
        // 如果没有提供关卡，需要根据坐标查找关卡
        if (level == null) {
            int nLevelId = 0;
            if (drlg.getLevel() != null) {
                D2DrlgLevel currentLevel = drlg.getLevel();
                
                while (!DrlgDrlgRoom.areXYInsideCoordinates(currentLevel.getLevelCoords(), x, y)) {
                    currentLevel = currentLevel.getPNextLevel();
                    if (currentLevel == null) {
                        break;
                    }
                }
                
                if (currentLevel != null) {
                    nLevelId = currentLevel.getLevelId();
                }
            }

            if (nLevelId == 0) {
                return null;
            }
            level = getLevel(drlg, nLevelId);
        }
        
        // 如果关卡还没有房间，初始化关卡
        if (level.getFirstRoomEx() == null) {
            initLevel(level);
        }
        
        return getRoomExFromLevelAndCoordinates(level, x, y);
    }
    
    /**
     * D2Common.0x6FD751C0
     * 判断是否为城镇关卡
     */
    public static boolean isTownLevel(int levelId) {
        return D2LevelIds.isTownLevel(levelId);
    }
    
    /**
     * D2Common.0x6FD75260 (#10000)
     * 从关卡ID获取关卡类型
     */
    public static int getLevelTypeFromLevelId(int levelId) {
        // 从数据表获取关卡定义记录
        D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef == null) {
            D2Log.warning("Failed to get level def record for level id: " + levelId);
            return 0;
        }
        return levelDef.getDwLevelType();
    }
    
    /**
     * D2Common.0x6FD75270
     * 设置关卡位置和大小
     */
    public static void setLevelPositionAndSize(D2DrlgStrc drlg, D2DrlgLevel level) {
        D2LevelDefBin levelDefBin = DataTbls.getLevelDefRecord(level.getLevelId());
        if (levelDefBin == null) {
            D2Log.warning("DRLG_SetLevelPositionAndSize: Failed to get level def record");
            return;
        }
        
        D2DrlgLevel pDependLevel = null;
        int nX = 0;
        int nY = 0;
        
        level.getLevelCoords().setNWidth(levelDefBin.getDwSizeX(drlg.getDifficulty()));
        level.getLevelCoords().setNHeight(levelDefBin.getDwSizeY(drlg.getDifficulty()));
        
        if (levelDefBin.getDwDepend() != 0) {
            pDependLevel = getLevel(drlg, levelDefBin.getDwDepend());
            
            nX = pDependLevel.getLevelCoords().getNPosX();
            nY = pDependLevel.getLevelCoords().getNPosY();
        }
        
        level.getLevelCoords().setNPosX(nX + levelDefBin.getDwOffsetX());
        level.getLevelCoords().setNPosY(nY + levelDefBin.getDwOffsetY());
    }
    
    /**
     * D2Common.0x6FD75300 (#10001)
     * 从关卡ID获取 Act 编号（0-based：0=Act I, 1=Act II, …, 4=Act V）
     */
    public static byte getActNoFromLevelId(int levelId) {
        int act1Based = D2LevelIds.getActFromLevelId(levelId);
        return (byte) (act1Based > 0 ? act1Based - 1 : 0);
    }
    
    /**
     * D2Common.0x6FD75330 (#10004)
     * 从关卡ID获取是否保存怪物
     */
    public static int getSaveMonstersFromLevelId(int levelId) {
        // 从数据表获取关卡定义记录
        com.d2moo.common.datatbls.D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef == null) {
            D2Log.warning("Failed to get level def record for level id: " + levelId);
            return 0;
        }
        return levelDef.getDwSaveMonsters();
    }
    
    /**
     * D2Common.0x6FD75350 (#10002)
     * 从关卡ID获取LOS绘制
     */
    public static int getLOSDrawFromLevelId(int levelId) {
        // 从数据表获取关卡定义记录
        D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef == null) {
            D2Log.warning("Failed to get level def record for level id: " + levelId);
            return 0;
        }
        return levelDef.getDwLOSDraw();
    }
    
    /**
     * D2Common.0x6FD75370
     * 从关卡ID获取传送门
     */
    public static D2DrlgWarp getDrlgWarpFromLevelId(D2DrlgStrc drlg, int levelId) {
        // 先查找现有的传送门
        D2DrlgWarp warp = drlg.getWarp();
        while (warp != null) {
            if (warp.getNLevel() == levelId) {
                return warp;
            }
            warp = warp.getPNext();
        }
        
        // 如果不存在，创建新的传送门
        warp = D2Pool.callocStrcPool(drlg.getMempool(), D2DrlgWarp.class);
        if (warp == null) {
            D2Log.warning("Failed to allocate D2DrlgWarp from memory pool");
            return null;
        }
        warp.setNLevel(levelId);
        
        D2LevelDefBin levelDef = DataTbls.getLevelDefRecord(levelId);
        if (levelDef != null) {
            for (int i = 0; i < 8; ++i) {
                warp.getNVis()[i] = levelDef.getDwVis()[i];
                warp.getNWarp()[i] = levelDef.getDwWarp()[i];
            }
        }
        
        warp.setPNext(drlg.getWarp());
        drlg.setWarp(warp);
        
        return warp;
    }
    
    /**
     * D2Common.0x6FD753F0
     * 设置传送门ID
     */
    public static void setWarpId(D2DrlgWarp drlgWarp, int vis, int warp, int id) {
        // 查找匹配的 vis 值
        for (int i = 0; i < 8; ++i) {
            if (drlgWarp.getNVis()[i] == vis) {
                drlgWarp.getNWarp()[i] = warp;
                return;
            }
        }
        
        // 如果没有找到匹配的 vis，且 id == -1，查找空槽
        if (id == -1) {
            for (int i = 0; i < 8; ++i) {
                if (drlgWarp.getNVis()[i] == 0 && drlgWarp.getNWarp()[i] == -1) {
                    drlgWarp.getNVis()[i] = vis;
                    drlgWarp.getNWarp()[i] = warp;
                    return;
                }
            }
            D2Log.warning("DRLG_SetWarpId: No available slot found");
            return;
        }
        
        // 使用指定的 id
        if (id < 0 || id >= 8) {
            D2Log.warning("DRLG_SetWarpId: Invalid id: " + id);
            return;
        }
        
        drlgWarp.getNVis()[id] = vis;
        drlgWarp.getNWarp()[id] = warp;
    }
    
    /**
     * D2Common.0x6FD75450
     * 判断是否在客户端
     */
    public static boolean isOnClient(D2DrlgStrc drlg) {
        if (drlg == null) {
            D2Log.warning("DRLG_IsOnClient: drlg is null");
            return false;
        }
        
        return (drlg.getFlags() & D2DrlgFlags.ONCLIENT) != 0;
    }
}
