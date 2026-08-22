package com.d2moo.common.drlg;

import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 迷宫模块
 * 对应 C++ 文件：DrlgMaze.cpp
 */
public class DrlgMaze {
    
    /**
     * D2Common.0x6FD79480
     * 初始化关卡数据
     * 被 DrlgDrlg 依赖
     * 
     * 功能：
     * 1. 重置迷宫记录
     * 2. 初始化迷宫相关数据结构
     * 3. 设置关卡的基础参数
     */
    public static void initLevelData(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 1. 重置迷宫记录（不保留）
        resetMazeRecord(level, false);
        
        // 2. 初始化迷宫相关数据结构
        // 设置房间数量为 0
        level.setRooms(0);
        
        // 清空第一个房间
        level.setFirstRoomEx(null);
        
        // 初始化种子（如果还没有）
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        
        // 3. 设置关卡的基础参数
        // 从数据表加载迷宫记录
        D2MazeRecord mazeRecord = com.d2moo.common.datatbls.DataTbls.getMazeRecord(level.getLevelId());
        if (mazeRecord != null) {
            level.setPresetOrOutdoorsOrMaze(mazeRecord);
        }
        
        // 设置关卡位置和大小
        DrlgDrlg.setLevelPositionAndSize(level.getDrlg(), level);
    }
    
    /**
     * D2Common.0x6FD794A0
     * 生成关卡
     * 被 DrlgDrlg 依赖
     * 
     * 这是迷宫生成的主函数，根据关卡类型执行不同的生成逻辑
     * 
     * @param level 关卡
     */
    public static void generateLevel(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2MazeRecord)) {
            return;
        }
        
        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze;
        D2DrlgStrc drlg = level.getDrlg();
        
        // 创建第一个房间
        D2DrlgRoom drlgRoom = DrlgDrlgRoom.allocRoomEx(level, D2DrlgType.PRESET.getValue());
        if (drlgRoom == null) {
            return;
        }
        
        setRoomSize(drlgRoom);
        
        // 计算第一个房间的位置（居中）
        D2DrlgCoord levelCoords = level.getLevelCoords();
        if (levelCoords != null) {
            drlgRoom.setNTileXPos(levelCoords.getNPosX() + (levelCoords.getNWidth() - drlgRoom.getNTileWidth()) / 2);
            drlgRoom.setNTileYPos(levelCoords.getNPosY() + (levelCoords.getNHeight() - drlgRoom.getNTileHeight()) / 2);
        }
        
        DrlgDrlgRoom.addRoomExToLevel(level, drlgRoom);
        
        D2DrlgRoom levelFirstRoomEx = level.getFirstRoomEx();
        
        // 根据关卡类型执行不同的生成逻辑
        int nLevelType = level.getLevelType();
        int nRooms;
        int nDirection;
        int nRand;
        D2DrlgRoom randomRoomEx;
        
        switch (nLevelType) {
            case LVLTYPE_ACT1_CAVE:
            case LVLTYPE_ACT1_CRYPT:
                // Act1 洞穴和墓穴：随机添加房间
                nRooms = mazeRecord.getDwRooms(drlg.getDifficulty());
                if (level.getLevelId() == drlg.getStaffTombLevel()) {
                    nRooms *= 3;
                } else if (level.getLevelId() == drlg.getBossTombLevel()) {
                    nRooms *= 2;
                }
                
                while (level.getRooms() < nRooms) {
                    randomRoomEx = getRandomRoomExFromLevel(level);
                    if (randomRoomEx == null) {
                        break;
                    }
                    nDirection = (int)(Seed.rollRandomNumber(randomRoomEx.getSeed()) & 3);
                    if (!hasMapDS1(randomRoomEx)) {
                        addAdjacentMazeRoom(randomRoomEx, nDirection, true);
                    }
                }
                
                // 注意：某些关卡类型可能需要额外的特殊预设放置逻辑
                // 这些可以在后续根据需要添加
                break;
                
            case LVLTYPE_ACT1_BARRACKS:
            case LVLTYPE_ACT1_JAIL:
            case LVLTYPE_ACT2_SEWER:
            case LVLTYPE_ACT2_HAREM:
            case LVLTYPE_ACT2_BASEMENT:
            case LVLTYPE_ACT3_SPIDER:
            case LVLTYPE_ACT3_KURAST:
            case LVLTYPE_ACT3_DUNGEON:
            case LVLTYPE_ACT3_SEWER:
            case LVLTYPE_ACT5_ICE_CAVES:
            case LVLTYPE_ACT5_TEMPLE:
                // 需要初始化基础布局的关卡
                initBasicMazeLayout(level, 2);
                
                // 对于需要构建基础迷宫的关卡
                if (nLevelType == LVLTYPE_ACT3_KURAST 
                        || nLevelType == LVLTYPE_ACT3_DUNGEON 
                        || nLevelType == LVLTYPE_ACT3_SEWER
                        || nLevelType == LVLTYPE_ACT5_ICE_CAVES) {
                    buildBasicMaze(level);
                }
                
                // 注意：某些关卡类型可能需要额外的特殊预设放置逻辑
                // 这些可以在后续根据需要添加（如 Act3、Act5 的特殊处理）
                break;
                
            case LVLTYPE_ACT1_CATACOMBS:
                // Act1 地下墓穴：特殊处理第一个房间
                if (level.getLevelId() == D2LevelIds.LEVEL_CATACOMBSLEV1) {
                    addAdjacentMazeRoom(levelFirstRoomEx, 1, true); // NORTH
                    addAdjacentMazeRoom(levelFirstRoomEx, 2, true); // EAST
                    addAdjacentMazeRoom(levelFirstRoomEx, 3, true); // SOUTH
                    addAdjacentMazeRoom(levelFirstRoomEx, 0, true); // WEST
                    setPickedFileAndPresetId(levelFirstRoomEx, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_PREV_NSEW, -1, false);
                } else {
                    if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                        addAdjacentMazeRoom(levelFirstRoomEx, 0, true); // WEST
                        addAdjacentMazeRoom(levelFirstRoomEx, 2, true); // EAST
                        setPickedFileAndPresetId(levelFirstRoomEx, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_PREV_EW, -1, false);
                    } else {
                        addAdjacentMazeRoom(levelFirstRoomEx, 1, true); // NORTH
                        addAdjacentMazeRoom(levelFirstRoomEx, 3, true); // SOUTH
                        setPickedFileAndPresetId(levelFirstRoomEx, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_PREV_NS, -1, false);
                    }
                }
                
                // 构建基础迷宫
                int nRoomsCatacombs = 0;
                Object presetOrOutdoorsOrMazeCatacombs = level.getPresetOrOutdoorsOrMaze();
                if (presetOrOutdoorsOrMazeCatacombs instanceof D2MazeRecord) {
                    D2MazeRecord mazeRecordCatacombs = (D2MazeRecord) presetOrOutdoorsOrMazeCatacombs;
                    nRoomsCatacombs = mazeRecordCatacombs.getDwRooms(level.getDrlg().getDifficulty());
                    
                    if (level.getLevelId() == level.getDrlg().getStaffTombLevel()) {
                        nRoomsCatacombs *= 3;
                    } else if (level.getLevelId() == level.getDrlg().getBossTombLevel()) {
                        nRoomsCatacombs *= 2;
                    }
                }
                
                while (level.getRooms() < nRoomsCatacombs) {
                    D2DrlgRoom randomRoomExCatacombs = getRandomRoomExFromLevel(level);
                    if (randomRoomExCatacombs == null) {
                        break;
                    }
                    int nDirectionCatacombs = (int)(Seed.rollRandomNumber(randomRoomExCatacombs.getSeed()) & 3);
                    if (!hasMapDS1(randomRoomExCatacombs)) {
                        addAdjacentMazeRoom(randomRoomExCatacombs, nDirectionCatacombs, true);
                    }
                }
                
                // 放置特殊预设
                int nRandCatacombs = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
                D2MazeLevelIdStrc[] nAct1CatacombsNextIds = {
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_N, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_N, -1, 3),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_E, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_E, -1, 0),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_S, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_S, -1, 1),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_W, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_W, -1, 2),
                };
                scanReplaceSpecialPreset(level, nAct1CatacombsNextIds[nRandCatacombs], new int[]{nRandCatacombs});
                
                if (level.getLevelId() == D2LevelIds.LEVEL_CATACOMBSLEV2) {
                    D2MazeLevelIdStrc[] nAct1CatacombsWaypointIds = {
                        new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_N, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_N, -1, 3),
                        new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_E, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_E, -1, 0),
                        new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_S, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_S, -1, 1),
                        new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_W, D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_W, -1, 2),
                    };
                    scanReplaceSpecialPreset(level, nAct1CatacombsWaypointIds[nRandCatacombs], new int[]{nRandCatacombs});
                }
                break;
                
            case LVLTYPE_ACT2_TOMB:
                // Act2 墓穴：特殊处理
                if (level.getLevelId() == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2) {
                    // 设置特殊预设 TAINTED_SUN_X
                    setPickedFileAndPresetId(level.getFirstRoomEx(), D2LvlPrestIds.LVLPREST_ACT2_TOMB_TAINTED_SUN_X, -1, false);
                } else {
                    placeAct2TombPrev_Act5BaalPrev(level);
                    buildBasicMaze(level);
                    placeAct2TombStuff(level);
                }
                break;
                
            case LVLTYPE_ACT2_LAIR:
                // Act2 巢穴
                initBasicMazeLayout(level, 2);
                buildBasicMaze(level);
                placeAct2LairStuff(level);
                break;
                
            case LVLTYPE_ACT2_ARCANE:
                // Act2 神秘避难所
                placeArcaneSanctuary(level);
                
                // 放置召唤者房间
                int nArcaneRand = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
                D2MazeLevelIdStrc[] nAct2ArcaneSummonerIds = {
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_ARCANE_N, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_N, -1, 3),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_ARCANE_E, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_E, -1, 0),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_ARCANE_S, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_S, -1, 1),
                    new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_ARCANE_W, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_W, -1, 2),
                };
                scanReplaceSpecialPreset(level, nAct2ArcaneSummonerIds[nArcaneRand], new int[]{nArcaneRand});
                break;
                
            case LVLTYPE_ACT4_LAVA:
                // Act4 熔岩
                buildBasicMaze(level);
                break;
                
            case LVLTYPE_ACT5_BAAL:
                // Act5 巴尔
                placeAct2TombPrev_Act5BaalPrev(level);
                buildBasicMaze(level);
                placeAct5BaalStuff(level);
                break;
                
            case LVLTYPE_ACT5_LAVA:
                // Act5 熔岩
                placeAct5LavaPresets(level);
                break;
                
            default:
                D2Log.warning("DRLGMAZE_GenerateLevel: Unknown level type: " + nLevelType);
                return;
        }
        
        // 特殊关卡处理
        if (level.getLevelId() == D2LevelIds.LEVEL_BARRACKS) {
            placeAct1Barracks(level);
        } else if (level.getLevelId() == D2LevelIds.LEVEL_RIVEROFFLAME) {
            placeAct4Lava(level);
        } else {
            // 更新房间坐标
            DrlgDrlg.updateRoomExCoordinates(level);
        }
    }
    
    /**
     * D2Common.0x6FD7D3D0
     * 重置迷宫记录
     * 被 DrlgDrlg 依赖
     * 
     * 功能：
     * 1. 获取关卡的迷宫记录
     * 2. 根据 keepMazeRecord 标志决定是否重置
     * 3. 重置迷宫相关的数据结构
     */
    public static void resetMazeRecord(D2DrlgLevel level, boolean keepMazeRecord) {
        if (level == null) {
            return;
        }
        
        // 获取迷宫记录（从 presetOrOutdoorsOrMaze union 中获取）
        Object mazeData = level.getPresetOrOutdoorsOrMaze();
        
        if (mazeData instanceof D2MazeRecord) {
            D2MazeRecord mazeRecord = (D2MazeRecord) mazeData;
            
            if (!keepMazeRecord) {
                // 重置迷宫记录
                // 注意：这里不释放记录本身，只是重置其内容
                // 实际实现可能需要根据具体需求重置特定字段
                mazeRecord.setDwSizeX(0);
                mazeRecord.setDwSizeY(0);
                mazeRecord.setDwMerge(0);
                for (int i = 0; i < 3; ++i) {
                    mazeRecord.setDwRooms(i, 0);
                }
            }
        } else {
            // 如果没有迷宫记录，从数据表加载
            D2MazeRecord mazeRecord = com.d2moo.common.datatbls.DataTbls.getMazeRecord(level.getLevelId());
            if (mazeRecord != null) {
                level.setPresetOrOutdoorsOrMaze(mazeRecord);
            }
        }
    }
    
    // 关卡类型常量
    private static final int LVLTYPE_ACT1_CAVE = 0;
    private static final int LVLTYPE_ACT1_CRYPT = 1;
    private static final int LVLTYPE_ACT1_BARRACKS = 2;
    private static final int LVLTYPE_ACT1_JAIL = 3;
    private static final int LVLTYPE_ACT1_CATACOMBS = 4;
    private static final int LVLTYPE_ACT2_SEWER = 5;
    private static final int LVLTYPE_ACT2_HAREM = 6;
    private static final int LVLTYPE_ACT2_BASEMENT = 7;
    private static final int LVLTYPE_ACT2_TOMB = 8;
    private static final int LVLTYPE_ACT2_LAIR = 9;
    private static final int LVLTYPE_ACT2_ARCANE = 10;
    private static final int LVLTYPE_ACT3_KURAST = 11;
    private static final int LVLTYPE_ACT3_SPIDER = 12;
    private static final int LVLTYPE_ACT3_DUNGEON = 13;
    private static final int LVLTYPE_ACT3_SEWER = 14;
    private static final int LVLTYPE_ACT4_LAVA = 15;
    private static final int LVLTYPE_ACT5_ICE_CAVES = 16;
    private static final int LVLTYPE_ACT5_TEMPLE = 17;
    private static final int LVLTYPE_ACT5_BAAL = 18;
    private static final int LVLTYPE_ACT5_LAVA = 19;
    
    // 硬编码预设重映射表（用于 harem, basement 和 spider cave）
    private static final int[][] nHardcodedPresetsRemapping = {
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_ACT2_CORRUPT_HAREM_SW, D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_SW, D2LvlPrestIds.LVLPREST_ACT3_SPIDER_SW },
        { D2LvlPrestIds.LVLPREST_ACT2_CORRUPT_HAREM_SE, D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_SE, D2LvlPrestIds.LVLPREST_ACT3_SPIDER_SE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_ACT2_CORRUPT_HAREM_NW, D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_NW, D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NW },
        { D2LvlPrestIds.LVLPREST_ACT2_CORRUPT_HAREM_NE, D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_NE, D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
    };
    
    // 扩展硬编码预设重映射表（用于 Nilhatak's temple 和 lava maps）
    private static final int[][] nExpansionHardcodedPresetsRemapping = {
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_W },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_E },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_EW },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_S },
        { D2LvlPrestIds.LVLPREST_ACT5_TEMPLE_SW, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_ACT5_TEMPLE_SE_UP, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_N },
        { D2LvlPrestIds.LVLPREST_ACT5_TEMPLE_NW, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_ACT5_TEMPLE_NE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT5_LAVA_NS },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE }
    };
    
    /**
     * D2Common.0x6FD78F70
     * 选择房间预设
     * 根据房间的正交链接方向和关卡类型选择预设ID
     * 
     * @param drlgRoom 房间
     * @param resetFlag 是否重置标志
     */
    public static void pickRoomPreset(D2DrlgRoom drlgRoom, boolean resetFlag) {
        if (drlgRoom == null || drlgRoom.getLevel() == null) {
            return;
        }
        
        int nPickedFile = -1;
        int nLevelPrest = 0;
        
        // 遍历正交链接，根据方向计算预设ID的基础值
        D2DrlgOrth pDrlgOrth = drlgRoom.getDrlgOrth();
        while (pDrlgOrth != null) {
            switch (pDrlgOrth.getNDirection()) {
                case 0: // WEST
                    nLevelPrest |= 1;
                    break;
                case 1: // NORTH
                    nLevelPrest |= 8;
                    break;
                case 2: // EAST
                    nLevelPrest |= 2;
                    break;
                case 3: // SOUTH
                    nLevelPrest |= 4;
                    break;
                default:
                    break;
            }
            pDrlgOrth = pDrlgOrth.getPNext();
        }
        
        // 根据关卡类型调整预设ID
        int nLevelType = drlgRoom.getLevel().getLevelType();
        switch (nLevelType) {
            case LVLTYPE_ACT1_CAVE:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT1_DOE_ENTRANCE;
                break;
            case LVLTYPE_ACT1_CRYPT:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT1_GRAVEYARD;
                break;
            case LVLTYPE_ACT1_BARRACKS:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_COURT_CONNECT;
                break;
            case LVLTYPE_ACT1_JAIL:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_FORGE_N;
                break;
            case LVLTYPE_ACT1_CATACOMBS:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT1_CATHEDRAL;
                break;
            case LVLTYPE_ACT2_SEWER:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT2_TOWN;
                break;
            case LVLTYPE_ACT2_HAREM:
                if (nLevelPrest < nHardcodedPresetsRemapping.length) {
                    nLevelPrest = nHardcodedPresetsRemapping[nLevelPrest][0];
                }
                break;
            case LVLTYPE_ACT2_BASEMENT:
                if (nLevelPrest < nHardcodedPresetsRemapping.length) {
                    nLevelPrest = nHardcodedPresetsRemapping[nLevelPrest][1];
                }
                if (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_PALACECELLARLEV1 
                        && nLevelPrest == D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_NW) {
                    nPickedFile = 2;
                }
                if (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_PALACECELLARLEV3 
                        && (nLevelPrest == D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_NW 
                            || nLevelPrest == D2LvlPrestIds.LVLPREST_ACT2_BASEMENT_SE)) {
                    nPickedFile = 3;
                }
                break;
            case LVLTYPE_ACT2_TOMB:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_ELDER;
                break;
            case LVLTYPE_ACT2_LAIR:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT2_DURIELS_LAIR;
                break;
            case LVLTYPE_ACT2_ARCANE:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT2_LAIR_TIGHT_SPOT_S;
                break;
            case LVLTYPE_ACT3_KURAST:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT3_TEMPLE_6;
                break;
            case LVLTYPE_ACT3_SPIDER:
                if (nLevelPrest < nHardcodedPresetsRemapping.length) {
                    nLevelPrest = nHardcodedPresetsRemapping[nLevelPrest][2];
                }
                if (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_SPIDERCAVE 
                        && nLevelPrest == D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NE) {
                    nLevelPrest = D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NE;
                }
                if (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_SPIDERCAVERN 
                        && nLevelPrest == D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NW) {
                    nLevelPrest = D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NW;
                }
                break;
            case LVLTYPE_ACT3_DUNGEON:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NE;
                break;
            case LVLTYPE_ACT3_SEWER:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_TREASURE_2;
                break;
            case LVLTYPE_ACT4_LAVA:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT4_LAVA_X;
                break;
            case LVLTYPE_ACT5_ICE_CAVES:
                if (drlgRoom.getLevel() != null) {
                    Object presetOrOutdoorsOrMaze = drlgRoom.getLevel().getPresetOrOutdoorsOrMaze();
                    if (presetOrOutdoorsOrMaze instanceof D2MazeRecord) {
                        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze;
                        D2DrlgStrc drlg = drlgRoom.getLevel().getDrlg();
                        if (drlg != null && mazeRecord.getDwRooms(drlg.getDifficulty()) != 1) {
                            nLevelPrest += D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_16_SNOW;
                        }
                    }
                }
                break;
            case LVLTYPE_ACT5_TEMPLE:
                if (nLevelPrest < nExpansionHardcodedPresetsRemapping.length) {
                    nLevelPrest = nExpansionHardcodedPresetsRemapping[nLevelPrest][0];
                }
                break;
            case LVLTYPE_ACT5_BAAL:
                nLevelPrest += D2LvlPrestIds.LVLPREST_ACT5_LAVA_NS;
                break;
            case LVLTYPE_ACT5_LAVA:
                if (nLevelPrest < nExpansionHardcodedPresetsRemapping.length) {
                    nLevelPrest = nExpansionHardcodedPresetsRemapping[nLevelPrest][1];
                }
                break;
            default:
                D2Log.warning("DRLGMAZE_PickRoomPreset: Unknown level type: " + nLevelType);
                return;
        }
        
        // 设置预设ID和文件
        if (nLevelPrest != 0) {
            setPickedFileAndPresetId(drlgRoom, nLevelPrest, nPickedFile, resetFlag);
        }
    }
    
    /**
     * D2Common.0x6FD7A5D0
     * 构建基础迷宫
     * 随机添加房间直到达到目标数量
     * 
     * @param level 关卡
     */
    public static void buildBasicMaze(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2MazeRecord)) {
            return;
        }
        
        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze;
        D2DrlgStrc drlg = level.getDrlg();
        
        // 获取目标房间数量（根据难度）
        int nRooms = mazeRecord.getDwRooms(drlg.getDifficulty());
        
        // 特殊关卡处理
        if (level.getLevelId() == drlg.getStaffTombLevel()) {
            nRooms *= 3;
        }
        
        if (level.getLevelId() == drlg.getBossTombLevel()) {
            nRooms *= 2;
        }
        
        // 循环添加房间直到达到目标数量
        while (level.getRooms() < nRooms) {
            D2DrlgRoom randomRoomEx = getRandomRoomExFromLevel(level);
            if (randomRoomEx == null) {
                break;
            }
            
            // 随机选择方向（0-3，对应4个基本方向）
            int nDirection = (int)(Seed.rollRandomNumber(randomRoomEx.getSeed()) & 3);
            
            // 如果房间没有地图 DS1，尝试添加新房间
            if (!hasMapDS1(randomRoomEx)) {
                D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(randomRoomEx.getLevel(), D2DrlgType.PRESET.getValue());
                if (newRoomEx == null) {
                    continue;
                }
                
                setRoomSize(newRoomEx);
                
                // 根据方向计算位置
                switch (nDirection) {
                    case 0: // WEST
                        newRoomEx.setNTileXPos(randomRoomEx.getNTileXPos() - randomRoomEx.getNTileWidth());
                        newRoomEx.setNTileYPos(randomRoomEx.getNTileYPos());
                        break;
                    case 1: // NORTH
                        newRoomEx.setNTileXPos(randomRoomEx.getNTileXPos());
                        newRoomEx.setNTileYPos(randomRoomEx.getNTileYPos() - randomRoomEx.getNTileHeight());
                        break;
                    case 2: // EAST
                        newRoomEx.setNTileXPos(randomRoomEx.getNTileXPos() + randomRoomEx.getNTileWidth());
                        newRoomEx.setNTileYPos(randomRoomEx.getNTileYPos());
                        break;
                    case 3: // SOUTH
                        newRoomEx.setNTileXPos(randomRoomEx.getNTileXPos());
                        newRoomEx.setNTileYPos(randomRoomEx.getNTileYPos() + randomRoomEx.getNTileHeight());
                        break;
                    default:
                        DrlgDrlgRoom.freeRoomEx(newRoomEx);
                        continue;
                }
                
                // 检查是否与正交链接重叠
                D2DrlgOrth pDrlgOrth = randomRoomEx.getDrlgOrth();
                boolean bOverlaps = false;
                while (pDrlgOrth != null) {
                    if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                            newRoomEx.getDrlgCoord(), pDrlgOrth.getPBox(), 0)) {
                        bOverlaps = true;
                        break;
                    }
                    pDrlgOrth = pDrlgOrth.getPNext();
                }
                
                if (!bOverlaps) {
                    // 检查是否与其他房间重叠
                    if (checkRoomNotOverlaping(newRoomEx.getLevel(), newRoomEx, randomRoomEx, 0)) {
                        // 分配正交链接
                        allocDrlgOrthsForRooms(randomRoomEx, newRoomEx, nDirection);
                        // 合并房间
                        mergeMazeRooms(newRoomEx);
                        // 添加到关卡
                        DrlgDrlgRoom.addRoomExToLevel(randomRoomEx.getLevel(), newRoomEx);
                        // 选择预设
                        pickRoomPreset(randomRoomEx, true);
                        pickRoomPreset(newRoomEx, true);
                    } else {
                        DrlgDrlgRoom.freeRoomEx(newRoomEx);
                    }
                } else {
                    DrlgDrlgRoom.freeRoomEx(newRoomEx);
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7A110
     * 初始化基础迷宫布局
     * 在4个方向上创建基础房间链
     * 
     * @param level 关卡
     * @param roomsPerDirection 每个方向的房间数量
     */
    public static void initBasicMazeLayout(D2DrlgLevel level, int roomsPerDirection) {
        if (level == null || level.getFirstRoomEx() == null) {
            return;
        }
        
        D2DrlgRoom firstRoomEx = level.getFirstRoomEx();
        D2DrlgRoom drlgRoom = firstRoomEx;
        
        // 在 NORTH 方向创建房间链（方向1）
        for (int i = roomsPerDirection - 1; i > 0; --i) {
            D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
            if (newRoomEx == null) {
                continue;
            }
            
            setRoomSize(newRoomEx);
            
            if (linkMazeRooms(newRoomEx, drlgRoom, 1)) { // 1 = NORTH
                allocDrlgOrthsForRooms(drlgRoom, newRoomEx, 1);
                mergeMazeRooms(newRoomEx);
                DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
                pickRoomPreset(drlgRoom, true);
                pickRoomPreset(newRoomEx, true);
            } else {
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
                newRoomEx = null;
            }
            
            if (newRoomEx != null) {
                drlgRoom = newRoomEx;
            }
        }
        
        // 在 WEST 方向创建房间链（方向0）
        drlgRoom = firstRoomEx;
        for (int i = roomsPerDirection - 1; i > 0; --i) {
            D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
            if (newRoomEx == null) {
                continue;
            }
            
            setRoomSize(newRoomEx);
            
            if (linkMazeRooms(newRoomEx, drlgRoom, 0)) { // 0 = WEST
                allocDrlgOrthsForRooms(drlgRoom, newRoomEx, 0);
                mergeMazeRooms(newRoomEx);
                DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
                pickRoomPreset(drlgRoom, true);
                pickRoomPreset(newRoomEx, true);
            } else {
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
                newRoomEx = null;
            }
            
            if (newRoomEx != null) {
                drlgRoom = newRoomEx;
            }
        }
        
        // 在 SOUTH 方向创建房间链（方向3）
        drlgRoom = firstRoomEx;
        for (int i = roomsPerDirection - 1; i > 0; --i) {
            D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
            if (newRoomEx == null) {
                continue;
            }
            
            setRoomSize(newRoomEx);
            
            if (linkMazeRooms(newRoomEx, drlgRoom, 3)) { // 3 = SOUTH
                allocDrlgOrthsForRooms(drlgRoom, newRoomEx, 3);
                mergeMazeRooms(newRoomEx);
                DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
                pickRoomPreset(drlgRoom, true);
                pickRoomPreset(newRoomEx, true);
            } else {
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
                newRoomEx = null;
            }
            
            if (newRoomEx != null) {
                drlgRoom = newRoomEx;
            }
        }
        
        // 在 EAST 方向创建房间链（方向2）
        drlgRoom = firstRoomEx;
        for (int i = roomsPerDirection - 1; i > 0; --i) {
            D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
            if (newRoomEx == null) {
                continue;
            }
            
            setRoomSize(newRoomEx);
            
            if (linkMazeRooms(newRoomEx, drlgRoom, 2)) { // 2 = EAST
                allocDrlgOrthsForRooms(drlgRoom, newRoomEx, 2);
                mergeMazeRooms(newRoomEx);
                DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
                pickRoomPreset(drlgRoom, true);
                pickRoomPreset(newRoomEx, true);
            } else {
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
                newRoomEx = null;
            }
            
            if (newRoomEx != null) {
                drlgRoom = newRoomEx;
            }
        }
    }
    
    // 辅助函数：检查房间是否有地图 DS1
    private static boolean hasMapDS1(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getMazeOrOutdoor() == null 
                || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) {
            return false;
        }
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) drlgRoom.getMazeOrOutdoor();
        // DRLGPRESETROOMFLAG_HAS_MAP_DS1 = 0x1
        return (presetRoom.getDwFlags() & 0x1) != 0;
    }
    
    // 辅助函数：检查房间是否不与其他房间重叠
    private static boolean checkRoomNotOverlaping(D2DrlgLevel level, D2DrlgRoom drlgRoom1, 
            D2DrlgRoom ignoredRoom, int margin) {
        for (D2DrlgRoom currentRoomEx = level.getFirstRoomEx(); currentRoomEx != null; 
                currentRoomEx = currentRoomEx.getDrlgRoomNext()) {
            if (currentRoomEx != drlgRoom1 && currentRoomEx != ignoredRoom) {
                if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                        drlgRoom1.getDrlgCoord(), currentRoomEx.getDrlgCoord(), margin)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // 辅助函数：设置预设ID和文件
    private static void setPickedFileAndPresetId(D2DrlgRoom drlgRoom, int nLevelPrest, 
            int nPickedFile, boolean bResetFlag) {
        if (drlgRoom == null || drlgRoom.getMazeOrOutdoor() == null 
                || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) {
            return;
        }
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) drlgRoom.getMazeOrOutdoor();
        presetRoom.setNPickedFile(nPickedFile);
        presetRoom.setNLevelPrest(nLevelPrest);
        
        if (bResetFlag) {
            presetRoom.setDwFlags(presetRoom.getDwFlags() & (~0x1)); // 清除 HAS_MAP_DS1 标志
        } else {
            presetRoom.setDwFlags(presetRoom.getDwFlags() | 0x1); // 设置 HAS_MAP_DS1 标志
        }
    }
    
    /**
     * 迷宫关卡ID结构（对应 C++ 的 D2MazeLevelIdStrc）
     * 用于存储预设ID替换信息
     */
    public static class D2MazeLevelIdStrc {
        public int nLevelPrestId1;  // 原始预设ID
        public int nLevelPrestId2;  // 替换后的预设ID
        public int nPickedFile;     // 文件索引（-1 表示随机）
        public int nDirection;      // 方向
        
        public D2MazeLevelIdStrc(int nLevelPrestId1, int nLevelPrestId2, int nPickedFile, int nDirection) {
            this.nLevelPrestId1 = nLevelPrestId1;
            this.nLevelPrestId2 = nLevelPrestId2;
            this.nPickedFile = nPickedFile;
            this.nDirection = nDirection;
        }
    }
    
    /**
     * D2Common.0x6FD7B330
     * 替换房间预设
     * 在关卡中查找具有指定预设ID的房间，并将其替换为新的预设ID
     * 
     * @param level 关卡
     * @param nLevelPrestId1 原始预设ID
     * @param nLevelPrestId2 替换后的预设ID
     * @param nPickedFile 文件索引（-1 表示随机）
     * @param bResetFlag 是否重置标志
     * @return 找到并替换的房间，如果未找到返回 null
     */
    public static D2DrlgRoom replaceRoomPreset(D2DrlgLevel level, int nLevelPrestId1, 
            int nLevelPrestId2, int nPickedFile, boolean bResetFlag) {
        if (level == null) {
            return null;
        }
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (!hasMapDS1(drlgRoom)) {
                Object mazeOrOutdoor = drlgRoom.getMazeOrOutdoor();
                if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == nLevelPrestId1) {
                        setPickedFileAndPresetId(drlgRoom, nLevelPrestId2, nPickedFile, bResetFlag);
                        return drlgRoom;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * D2Common.0x6FD7B710
     * 扫描并替换特殊预设
     * 首先尝试替换现有房间的预设，如果找不到则创建新房间
     * 
     * @param level 关卡
     * @param pMazeInit 迷宫初始化结构（包含预设ID替换信息）
     * @param pRand 随机数指针（可选，用于更新随机数）
     */
    public static void scanReplaceSpecialPreset(D2DrlgLevel level, D2MazeLevelIdStrc pMazeInit, int[] pRand) {
        if (level == null || pMazeInit == null) {
            return;
        }
        
        // 首先尝试替换现有房间的预设
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (!hasMapDS1(drlgRoom)) {
                Object mazeOrOutdoor = drlgRoom.getMazeOrOutdoor();
                if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == pMazeInit.nLevelPrestId1) {
                        presetRoom.setNLevelPrest(pMazeInit.nLevelPrestId2);
                        presetRoom.setNPickedFile(pMazeInit.nPickedFile);
                        presetRoom.setDwFlags(presetRoom.getDwFlags() | 0x1); // HAS_MAP_DS1
                        
                        if (pRand != null && pRand.length > 0) {
                            pRand[0] = (pRand[0] + 1) % 4;
                        }
                        return;
                    }
                }
            }
        }
        
        // 如果找不到匹配的房间，创建新房间
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (!hasMapDS1(drlgRoom)) {
                D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
                if (newRoomEx == null) {
                    continue;
                }
                
                setRoomSize(newRoomEx);
                
                if (!linkMazeRooms(newRoomEx, drlgRoom, pMazeInit.nDirection)) {
                    DrlgDrlgRoom.freeRoomEx(newRoomEx);
                } else {
                    allocDrlgOrthsForRooms(drlgRoom, newRoomEx, pMazeInit.nDirection);
                    DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
                    
                    pickRoomPreset(drlgRoom, true);
                    
                    Object mazeOrOutdoor = newRoomEx.getMazeOrOutdoor();
                    if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                        presetRoom.setNLevelPrest(pMazeInit.nLevelPrestId2);
                        presetRoom.setNPickedFile(pMazeInit.nPickedFile);
                        presetRoom.setDwFlags(presetRoom.getDwFlags() | 0x1); // HAS_MAP_DS1
                    }
                    break;
                }
            }
        }
        
        if (pRand != null && pRand.length > 0) {
            pRand[0] = (pRand[0] + 1) % 4;
        }
    }
    
    /**
     * 辅助函数：为神秘避难所分支放置房间
     * 对应 C++ 的 DRLGMAZE_PlaceRoomForArcaneBranch
     * 
     * @param parentRoom 父房间
     * @param nArcaneMapBranch 分支方向（0-3）
     * @return 新创建的房间，如果失败返回 null
     */
    private static D2DrlgRoom placeRoomForArcaneBranch(D2DrlgRoom parentRoom, int nArcaneMapBranch) {
        int nDirection = nArcaneMapBranch % 4;
        D2DrlgRoom newRoomEx = addAdjacentMazeRoom(parentRoom, nDirection, true);
        if (newRoomEx != null) {
            pickRoomPreset(parentRoom, true);
            pickRoomPreset(newRoomEx, true);
        }
        return newRoomEx;
    }
    
    /**
     * 辅助函数：根据房间索引计算神秘避难所的方向
     * 对应 C++ 的 DRLGMAZE_ArcaneSanctuaryDirectionFromRoomIdx
     * 
     * 生成顺序：
     *                 2 →  3 →  4 →  5 →  6
     *                 ↑                   ↓
     *         W → 0 → 1        12         7 →  8
     *                           ↑         ↓
     *                14 ← 13 ← 11 ← 10  ← 9
     * 
     * @param nBranchDirection 分支方向（0-3）
     * @param nBranchRoomIdx 房间索引（0-14）
     * @return 方向
     */
    private static int arcaneSanctuaryDirectionFromRoomIdx(int nBranchDirection, int nBranchRoomIdx) {
        switch (nBranchRoomIdx) {
            default:
                return nBranchDirection;     // 向前（面向分支方向）
            case 2:
            case 12:
                return (nBranchDirection + 3) % 4; // 向左（面向分支方向）
            case 7:
            case 9:
                return (nBranchDirection + 1) % 4; // 向右（面向分支方向）
            case 10:
            case 11:
            case 13:
            case 14:
                return (nBranchDirection + 2) % 4; // 向后（面向分支方向）
        }
    }
    
    /**
     * D2Common.0x6FD7ABC0
     * 放置神秘避难所
     * 生成 Act2 神秘避难所的迷宫布局，包含4个分支，每个分支15个房间
     * 
     * @param level 关卡
     */
    public static void placeArcaneSanctuary(D2DrlgLevel level) {
        if (level == null || level.getFirstRoomEx() == null) {
            return;
        }
        
        D2DrlgRoom levelFirstRoomEx = level.getFirstRoomEx();
        
        // 随机选择起始方向
        int nRand = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
        
        // 每个分支的房间数
        final int nRoomsPerBranch = 15;
        // 分支方向数
        final int nBranchDirections = 4;
        
        // 房间数组
        D2DrlgRoom[] pDrlgRoomArray = new D2DrlgRoom[nRoomsPerBranch * nBranchDirections];
        
        // 为每个分支生成房间
        for (int nBranchDirection = 0; nBranchDirection < nBranchDirections; ++nBranchDirection) {
            D2DrlgRoom parentRoom = levelFirstRoomEx;
            for (int nBranchRoomIdx = 0; nBranchRoomIdx < nRoomsPerBranch; ++nBranchRoomIdx) {
                int nRoomDirection = arcaneSanctuaryDirectionFromRoomIdx(nBranchDirection, nBranchRoomIdx);
                D2DrlgRoom newRoom = placeRoomForArcaneBranch(parentRoom, nRoomDirection);
                
                // 根据注释，索引 8 和 12 的房间没有"子"房间，所以下一个房间将从当前父房间开始
                // 游戏也没有将其保存在房间数组中，这意味着它的 pMaze->nPickedFile 将保持不变
                if (nBranchRoomIdx != 8 && nBranchRoomIdx != 12) {
                    pDrlgRoomArray[nBranchRoomIdx + nBranchDirection * nRoomsPerBranch] = newRoom;
                    if (newRoom != null) {
                        parentRoom = newRoom;
                    }
                }
            }
        }
        
        // 设置每个房间的文件索引
        for (int i = 0; i < pDrlgRoomArray.length; ++i) {
            if (pDrlgRoomArray[i] != null) {
                Object mazeOrOutdoor = pDrlgRoomArray[i].getMazeOrOutdoor();
                if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    presetRoom.setNPickedFile((nRand + i / 15) % 4);
                }
            }
        }
        
        // 设置第一个房间的文件索引为 4
        Object mazeOrOutdoor = levelFirstRoomEx.getMazeOrOutdoor();
        if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
            D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
            presetRoom.setNPickedFile(4);
        }
    }
    
    /**
     * D2Common.0x6FD7AAC0
     * 放置 Act2 墓穴前一个房间或 Act5 巴尔前一个房间
     * 在第一个房间的3个方向上创建新房间，并根据关卡类型设置第一个房间的特殊预设
     * 
     * @param level 关卡
     */
    public static void placeAct2TombPrev_Act5BaalPrev(D2DrlgLevel level) {
        if (level == null || level.getFirstRoomEx() == null) {
            return;
        }
        
        // 预设ID映射表
        int[][] presetMapping = {
            { D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NSE, D2LvlPrestIds.LVLPREST_ACT5_BAAL_PREV_NSE },
            { D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_SEW, D2LvlPrestIds.LVLPREST_ACT5_BAAL_PREV_SEW },
            { D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NSW, D2LvlPrestIds.LVLPREST_ACT5_BAAL_PREV_NSW },
            { D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NEW, D2LvlPrestIds.LVLPREST_ACT5_BAAL_PREV_NEW },
        };
        
        // 警告：level.getFirstRoomEx() 可能会改变，所以保存一个副本
        D2DrlgRoom levelFirstRoomEx = level.getFirstRoomEx();
        
        // 随机选择起始方向
        int nDirection = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
        
        // 在3个方向上创建新房间
        for (int i = 0; i < 3; ++i) {
            D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(levelFirstRoomEx.getLevel(), D2DrlgType.PRESET.getValue());
            if (newRoomEx == null) {
                continue;
            }
            
            setRoomSize(newRoomEx);
            
            if (linkMazeRooms(newRoomEx, levelFirstRoomEx, nDirection)) {
                allocDrlgOrthsForRooms(levelFirstRoomEx, newRoomEx, nDirection);
                mergeMazeRooms(newRoomEx);
                DrlgDrlgRoom.addRoomExToLevel(levelFirstRoomEx.getLevel(), newRoomEx);
                pickRoomPreset(levelFirstRoomEx, true);
                pickRoomPreset(newRoomEx, true);
            } else {
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
            }
            
            nDirection = (nDirection + 1) % 4;
        }
        
        // 根据关卡类型设置第一个房间的特殊预设
        int nLevelType = level.getLevelType();
        if (nLevelType == LVLTYPE_ACT2_TOMB) {
            setPickedFileAndPresetId(levelFirstRoomEx, presetMapping[nDirection][0], -1, false);
        } else if (nLevelType == LVLTYPE_ACT5_BAAL) {
            setPickedFileAndPresetId(levelFirstRoomEx, presetMapping[nDirection][1], -1, false);
        }
    }
    
    /**
     * D2Common.0x6FD79240
     * 获取可以放置房间的东侧位置
     * 查找关卡中最东侧（X坐标最小）且可以在西侧放置房间的房间
     */
    public static D2DrlgRoom getFreeLocationForRoomEast(D2DrlgLevel level) {
        if (level == null) {
            return null;
        }
        
        D2DrlgRoom pResult = null;
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (pResult == null || drlgRoom.getNTileXPos() < pResult.getNTileXPos()) {
                if (checkIfMayPlaceAdjacentPresetRoom(drlgRoom, 0)) { // 0 = WEST
                    pResult = drlgRoom;
                }
            }
        }
        
        return pResult;
    }
    
    /**
     * D2Common.0x6FD79240
     * 获取可以放置房间的西侧位置
     * 查找关卡中最西侧（X坐标最大）且可以在东侧放置房间的房间
     */
    public static D2DrlgRoom getFreeLocationForRoomWest(D2DrlgLevel level) {
        if (level == null) {
            return null;
        }
        
        D2DrlgRoom pResult = null;
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (pResult == null || drlgRoom.getNTileXPos() > pResult.getNTileXPos()) {
                if (checkIfMayPlaceAdjacentPresetRoom(drlgRoom, 2)) { // 2 = EAST
                    pResult = drlgRoom;
                }
            }
        }
        
        return pResult;
    }
    
    /**
     * D2Common.0x6FD79360
     * 获取可以放置房间的北侧位置
     * 查找关卡中最北侧（Y坐标最大）且可以在南侧放置房间的房间
     */
    public static D2DrlgRoom getFreeLocationForRoomNorth(D2DrlgLevel level) {
        if (level == null) {
            return null;
        }
        
        D2DrlgRoom pResult = null;
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if ((pResult == null || drlgRoom.getNTileYPos() > pResult.getNTileYPos())
                    && checkIfMayPlaceAdjacentPresetRoom(drlgRoom, 3)) { // 3 = SOUTH
                pResult = drlgRoom;
            }
        }
        
        return pResult;
    }
    
    /**
     * 获取可以放置房间的南侧位置
     * 查找关卡中最南侧（Y坐标最小）且可以在北侧放置房间的房间
     */
    public static D2DrlgRoom getFreeLocationForRoomSouth(D2DrlgLevel level) {
        if (level == null) {
            return null;
        }
        
        D2DrlgRoom pResult = null;
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if ((pResult == null || drlgRoom.getNTileYPos() < pResult.getNTileYPos())
                    && checkIfMayPlaceAdjacentPresetRoom(drlgRoom, 1)) { // 1 = NORTH
                pResult = drlgRoom;
            }
        }
        
        return pResult;
    }
    
    /**
     * D2Common.0x6FD7B8B0
     * 初始化房间固定预设
     * 在指定方向创建一个新房间并设置固定的预设ID
     * 
     * @param drlgRoom 房间
     * @param nDirection 方向
     * @param nLvlPrestId 预设ID
     * @param nFile 文件索引
     * @param bUseInitPreset 是否使用初始预设
     * @return 新创建的房间，如果失败返回 null
     */
    public static D2DrlgRoom initRoomFixedPreset(D2DrlgRoom drlgRoom, int nDirection, 
            int nLvlPrestId, int nFile, boolean bUseInitPreset) {
        if (drlgRoom == null) {
            return null;
        }
        
        D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
        if (newRoomEx == null) {
            return null;
        }
        
        setRoomSize(newRoomEx);
        
        if (linkMazeRooms(newRoomEx, drlgRoom, nDirection)) {
            allocDrlgOrthsForRooms(drlgRoom, newRoomEx, nDirection);
            DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), newRoomEx);
            
            if (bUseInitPreset) {
                pickRoomPreset(drlgRoom, true);
            }
            
            setPickedFileAndPresetId(newRoomEx, nLvlPrestId, nFile, false);
            return newRoomEx;
        } else {
            DrlgDrlgRoom.freeRoomEx(newRoomEx);
            return null;
        }
    }
    
    /**
     * D2Common.0x6FD7B330
     * 添加特殊预设
     * 在关卡中查找一个没有地图DS1的房间，并在指定方向创建新房间设置特殊预设
     * 
     * @param level 关卡
     * @param nDirection 方向
     * @param nLvlPrestId 预设ID
     * @param nFile 文件索引
     */
    public static void addSpecialPreset(D2DrlgLevel level, int nDirection, int nLvlPrestId, int nFile) {
        if (level == null) {
            return;
        }
        
        for (D2DrlgRoom drlgRoom = level.getFirstRoomEx(); drlgRoom != null; 
                drlgRoom = drlgRoom.getDrlgRoomNext()) {
            if (!hasMapDS1(drlgRoom)) {
                if (initRoomFixedPreset(drlgRoom, nDirection, nLvlPrestId, nFile, true) != null) {
                    break;
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7CA20
     * 放置 Act1 兵营
     * 连接兵营关卡与外部修道院关卡，并放置特殊预设（Next 和 Forge）
     * 
     * @param level 关卡
     */
    public static void placeAct1Barracks(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        // 预设ID数组
        D2MazeLevelIdStrc[] nAct1BarracksNextIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_N, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_NEXT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_E, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_NEXT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_S, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_NEXT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_W, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_NEXT_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct1BarracksForgeIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_N, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_FORGE_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_E, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_FORGE_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_S, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_FORGE_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_W, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_FORGE_W, -1, 2),
        };
        
        // 函数指针数组（用于获取入口房间）
        @SuppressWarnings("unchecked")
        java.util.function.Function<D2DrlgLevel, D2DrlgRoom>[] pfGetBarracksEntryRoom = new java.util.function.Function[] {
            (java.util.function.Function<D2DrlgLevel, D2DrlgRoom>) (l -> DrlgMaze.getFreeLocationForRoomWest(l)),
            (java.util.function.Function<D2DrlgLevel, D2DrlgRoom>) (l -> DrlgMaze.getFreeLocationForRoomNorth(l)),
            (java.util.function.Function<D2DrlgLevel, D2DrlgRoom>) (l -> DrlgMaze.getFreeLocationForRoomEast(l)),
        };
        
        D2DrlgStrc drlg = level.getDrlg();
        D2DrlgLevel outerCloisterLevel = DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_OUTERCLOISTER);
        if (outerCloisterLevel == null) {
            return;
        }
        
        // 获取方向（从预设信息中获取，如果不存在则默认为0）
        int nDirection = 0;
        D2DrlgPresetInfoStrc preset = outerCloisterLevel.getPreset();
        if (preset != null) {
            nDirection = preset.getNDirection();
        }
        
        D2DrlgRoom drlgRoom = null;
        if (nDirection < pfGetBarracksEntryRoom.length) {
            drlgRoom = pfGetBarracksEntryRoom[nDirection].apply(level);
        }
        
        if (drlgRoom == null) {
            return;
        }
        
        int nX = outerCloisterLevel.getLevelCoords().getNPosX();
        int nY = outerCloisterLevel.getLevelCoords().getNPosY();
        D2DrlgRoom barracksRoomEx = null;
        
        // 根据方向创建兵营入口房间
        switch (nDirection) {
            case 0: // WEST
                barracksRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
                if (barracksRoomEx == null) {
                    return;
                }
                setRoomSize(barracksRoomEx);
                
                if (linkMazeRooms(barracksRoomEx, drlgRoom, 2)) { // 2 = EAST
                    allocDrlgOrthsForRooms(drlgRoom, barracksRoomEx, 2);
                    DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), barracksRoomEx);
                    if (barracksRoomEx != null) {
                        pickRoomPreset(drlgRoom, true);
                        setPickedFileAndPresetId(barracksRoomEx, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_COURT_CONNECT, 0, false);
                    }
                } else {
                    DrlgDrlgRoom.freeRoomEx(barracksRoomEx);
                    barracksRoomEx = null;
                }
                
                if (barracksRoomEx != null) {
                    D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                    ppDrlgOrth[0] = barracksRoomEx.getDrlgOrth();
                    DrlgDrlgRoom.addOrth(ppDrlgOrth, outerCloisterLevel, 2, false);
                    barracksRoomEx.setDrlgOrth(ppDrlgOrth[0]);
                    
                    Object presetOrOutdoorsOrMaze2 = level.getPresetOrOutdoorsOrMaze();
                    if (presetOrOutdoorsOrMaze2 instanceof D2MazeRecord) {
                        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze2;
                        nX -= mazeRecord.getDwSizeX() + barracksRoomEx.getNTileXPos();
                        nY += outerCloisterLevel.getLevelCoords().getNHeight() / 2 - barracksRoomEx.getNTileYPos();
                    }
                }
                break;
                
            case 1: // NORTH
                barracksRoomEx = DrlgDrlgRoom.allocRoomEx(drlgRoom.getLevel(), D2DrlgType.PRESET.getValue());
                if (barracksRoomEx == null) {
                    return;
                }
                setRoomSize(barracksRoomEx);
                
                if (linkMazeRooms(barracksRoomEx, drlgRoom, 3)) { // 3 = SOUTH
                    allocDrlgOrthsForRooms(drlgRoom, barracksRoomEx, 3);
                    DrlgDrlgRoom.addRoomExToLevel(drlgRoom.getLevel(), barracksRoomEx);
                    if (barracksRoomEx != null) {
                        pickRoomPreset(drlgRoom, true);
                        setPickedFileAndPresetId(barracksRoomEx, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_COURT_CONNECT, 1, false);
                    }
                } else {
                    DrlgDrlgRoom.freeRoomEx(barracksRoomEx);
                    barracksRoomEx = null;
                }
                
                if (barracksRoomEx != null) {
                    D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                    ppDrlgOrth[0] = barracksRoomEx.getDrlgOrth();
                    DrlgDrlgRoom.addOrth(ppDrlgOrth, outerCloisterLevel, 3, false);
                    barracksRoomEx.setDrlgOrth(ppDrlgOrth[0]);
                    
                    nX += outerCloisterLevel.getLevelCoords().getNWidth() / 2 - barracksRoomEx.getNTileXPos() - 6;
                    Object presetOrOutdoorsOrMaze2 = level.getPresetOrOutdoorsOrMaze();
                    if (presetOrOutdoorsOrMaze2 instanceof D2MazeRecord) {
                        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze2;
                        nY -= mazeRecord.getDwSizeY() + barracksRoomEx.getNTileYPos();
                    }
                }
                break;
                
            case 2: // EAST
                barracksRoomEx = addAdjacentMazeRoom(drlgRoom, 0, false); // 0 = WEST
                if (barracksRoomEx != null) {
                    pickRoomPreset(drlgRoom, true);
                    setPickedFileAndPresetId(barracksRoomEx, D2LvlPrestIds.LVLPREST_ACT1_BARRACKS_COURT_CONNECT, 2, false);
                    
                    D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                    ppDrlgOrth[0] = barracksRoomEx.getDrlgOrth();
                    DrlgDrlgRoom.addOrth(ppDrlgOrth, outerCloisterLevel, 0, false);
                    barracksRoomEx.setDrlgOrth(ppDrlgOrth[0]);
                    
                    nX += outerCloisterLevel.getLevelCoords().getNWidth() - barracksRoomEx.getNTileXPos();
                    nY += outerCloisterLevel.getLevelCoords().getNHeight() / 2 - barracksRoomEx.getNTileYPos() + 1;
                }
                break;
            default:
                return;
        }
        
        if (barracksRoomEx == null) {
            return;
        }
        
        // 随机选择放置 Next 或 Forge
        D2MazeLevelIdStrc pMazeLevelIds = null;
        if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
            // 放置 Next 和 Forge
            pMazeLevelIds = nAct1BarracksNextIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
                    if (!hasMapDS1(i)) {
                        if (initRoomFixedPreset(i, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, 
                                pMazeLevelIds.nPickedFile, true) != null) {
                            break;
                        }
                    }
                }
            }
            
            pMazeLevelIds = nAct1BarracksForgeIds[(nDirection + 1) % 4];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
        } else {
            // 只放置 Forge
            pMazeLevelIds = nAct1BarracksForgeIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            
            int nRand = (nDirection + 1) % 4;
            scanReplaceSpecialPreset(level, nAct1BarracksNextIds[nRand], new int[]{nRand});
        }
        
        // 调整所有房间的位置
        for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
            i.setNTileXPos(i.getNTileXPos() + nX);
            i.setNTileYPos(i.getNTileYPos() + nY);
        }
        
        // 更新关卡坐标
        int[] tileMinX = new int[1];
        int[] tileMinY = new int[1];
        int[] tileMaxX = new int[1];
        int[] tileMaxY = new int[1];
        DrlgDrlg.getMinAndMaxCoordinatesFromLevel(level, tileMinX, tileMinY, tileMaxX, tileMaxY);
        level.getLevelCoords().setNPosX(tileMinX[0]);
        level.getLevelCoords().setNPosY(tileMinY[0]);
        level.getLevelCoords().setNWidth(tileMaxX[0] - tileMinX[0]);
        level.getLevelCoords().setNHeight(tileMaxY[0] - tileMinY[0]);
    }
    
    /**
     * D2Common.0x6FD7CA40
     * 放置 Act4 熔岩
     * 连接熔岩关卡与混沌避难所，并放置桥梁和特殊预设
     * 
     * @param level 关卡
     */
    public static void placeAct4Lava(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        D2DrlgStrc drlg = level.getDrlg();
        
        // 只在非 Act5 时执行
        if (drlg.getActNo() == 5) {
            return;
        }
        
        D2MazeLevelIdStrc[] nAct4LavaForgeIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT4_LAVA_W, D2LvlPrestIds.LVLPREST_ACT4_LAVA_FORGE_W, -1, 2),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT4_LAVA_E, D2LvlPrestIds.LVLPREST_ACT4_LAVA_FORGE_E, -1, 0),
        };
        
        D2DrlgLevel chaosSanctum = DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_CHAOSSANCTUARY);
        if (chaosSanctum == null) {
            return;
        }
        
        // 在北侧创建传送点房间
        D2DrlgRoom parentRoom = getFreeLocationForRoomNorth(level);
        if (parentRoom == null) {
            return;
        }
        
        D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(parentRoom.getLevel(), D2DrlgType.PRESET.getValue());
        if (newRoomEx == null) {
            return;
        }
        setRoomSize(newRoomEx);
        
        if (linkMazeRooms(newRoomEx, parentRoom, 3)) { // 3 = SOUTH
            allocDrlgOrthsForRooms(parentRoom, newRoomEx, 3);
            DrlgDrlgRoom.addRoomExToLevel(parentRoom.getLevel(), newRoomEx);
            if (newRoomEx != null) {
                pickRoomPreset(parentRoom, true);
                setPickedFileAndPresetId(newRoomEx, D2LvlPrestIds.LVLPREST_ACT4_LAVA_WARP_N, -1, false);
            }
        } else {
            DrlgDrlgRoom.freeRoomEx(newRoomEx);
            newRoomEx = null;
        }
        
        // 在南侧创建桥梁房间
        parentRoom = getFreeLocationForRoomSouth(level);
        if (parentRoom == null) {
            return;
        }
        
        // 创建3个桥梁房间
        D2DrlgRoom bridgeRoomEx1 = addAdjacentMazeRoom(parentRoom, 1, false); // 1 = NORTH
        if (bridgeRoomEx1 != null) {
            pickRoomPreset(parentRoom, true);
            setPickedFileAndPresetId(bridgeRoomEx1, D2LvlPrestIds.LVLPREST_ACT4_BRIDGE_1, -1, false);
            
            D2DrlgRoom bridgeRoomEx2 = addAdjacentMazeRoom(bridgeRoomEx1, 1, false); // 1 = NORTH
            if (bridgeRoomEx2 != null) {
                setPickedFileAndPresetId(bridgeRoomEx2, D2LvlPrestIds.LVLPREST_ACT4_BRIDGE_2, -1, false);
                
                D2DrlgRoom bridgeRoomEx3 = addAdjacentMazeRoom(bridgeRoomEx2, 1, false); // 1 = NORTH
                if (bridgeRoomEx3 != null) {
                    setPickedFileAndPresetId(bridgeRoomEx3, D2LvlPrestIds.LVLPREST_ACT4_BRIDGE_2, -1, false);
                    
                    // 连接到混沌避难所
                    D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                    ppDrlgOrth[0] = bridgeRoomEx3.getDrlgOrth();
                    DrlgDrlgRoom.addOrth(ppDrlgOrth, chaosSanctum, 1, false); // 1 = NORTH
                    bridgeRoomEx3.setDrlgOrth(ppDrlgOrth[0]);
                    
                    // 计算偏移量
                    int nX = chaosSanctum.getLevelCoords().getNPosX() + 2 * bridgeRoomEx3.getNTileWidth() - bridgeRoomEx3.getNTileXPos();
                    int nY = chaosSanctum.getLevelCoords().getNPosY() + chaosSanctum.getLevelCoords().getNHeight() - bridgeRoomEx3.getNTileYPos();
                    
                    // 替换特殊预设
                    int nRand = (int)(Seed.rollRandomNumber(level.getSeed()) & 1);
                    scanReplaceSpecialPreset(level, nAct4LavaForgeIds[nRand], null);
                    
                    // 填充空白空间
                    fillBlankMazeSpaces(level, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, bridgeRoomEx3);
                    
                    // 调整所有房间的位置
                    for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
                        i.setNTileXPos(i.getNTileXPos() + nX);
                        i.setNTileYPos(i.getNTileYPos() + nY);
                    }
                    
                    // 更新关卡坐标
                    int[] tileMinX = new int[1];
                    int[] tileMinY = new int[1];
                    int[] tileMaxX = new int[1];
                    int[] tileMaxY = new int[1];
                    DrlgDrlg.getMinAndMaxCoordinatesFromLevel(level, tileMinX, tileMinY, tileMaxX, tileMaxY);
                    level.getLevelCoords().setNPosX(tileMinX[0]);
                    level.getLevelCoords().setNPosY(tileMinY[0]);
                    level.getLevelCoords().setNWidth(tileMaxX[0] - tileMinX[0]);
                    level.getLevelCoords().setNHeight(tileMaxY[0] - tileMinY[0]);
                }
            }
        }
    }
    
    // 辅助函数：检查方向是否已存在
    private static boolean orthWithDirectionExists(D2DrlgRoom drlgRoom, int nDirection) {
        D2DrlgOrth pDrlgOrth = drlgRoom.getDrlgOrth();
        while (pDrlgOrth != null) {
            if (pDrlgOrth.getNDirection() == nDirection) {
                return true;
            }
            pDrlgOrth = pDrlgOrth.getPNext();
        }
        return false;
    }
    
    // 辅助函数：检查房间是否与正交链接重叠
    private static boolean checkIfRoomOverlapsOrth(D2DrlgRoom newRoomEx, D2DrlgOrth pDrlgOrth) {
        while (pDrlgOrth != null) {
            if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    newRoomEx.getDrlgCoord(), pDrlgOrth.getPBox(), 0)) {
                return true;
            }
            pDrlgOrth = pDrlgOrth.getPNext();
        }
        return false;
    }
    
    // 辅助函数：检查房间是否与父房间外的其他房间重叠
    private static boolean checkIfRoomOverlapsAythingOtherThanParent(
            D2DrlgRoom newRoomEx, D2DrlgRoom parentRoomEx) {
        // 检查是否与父房间的正交链接重叠
        if (checkIfRoomOverlapsOrth(newRoomEx, parentRoomEx.getDrlgOrth())) {
            return true;
        }
        // 检查是否与其他房间重叠（排除父房间）
        return !checkRoomNotOverlaping(newRoomEx.getLevel(), newRoomEx, parentRoomEx, 0);
    }
    
    // 辅助函数：分配正交链接（简化版本，实际需要调用 DrlgDrlgRoom 的函数）
    private static void allocDrlgOrthsForRooms(D2DrlgRoom drlgRoom1, D2DrlgRoom drlgRoom2, int direction) {
        // 创建双向链接
        D2DrlgOrth[] ppDrlgOrth1 = new D2DrlgOrth[1];
        ppDrlgOrth1[0] = drlgRoom1.getDrlgOrth();
        DrlgDrlgRoom.addOrth(ppDrlgOrth1, drlgRoom2.getLevel(), direction, true);
        drlgRoom1.setDrlgOrth(ppDrlgOrth1[0]);
        
        // 计算反向方向
        int oppositeDirection = (direction + 2) % 4; // 简单的反向计算（仅适用于4方向）
        // 对于8方向，需要更复杂的映射
        if (direction >= 4) {
            // 对角线方向的反向
            oppositeDirection = direction + 4;
            if (oppositeDirection >= 8) {
                oppositeDirection -= 8;
            }
        }
        
        D2DrlgOrth[] ppDrlgOrth2 = new D2DrlgOrth[1];
        ppDrlgOrth2[0] = drlgRoom2.getDrlgOrth();
        DrlgDrlgRoom.addOrth(ppDrlgOrth2, drlgRoom1.getLevel(), oppositeDirection, true);
        drlgRoom2.setDrlgOrth(ppDrlgOrth2[0]);
    }
    
    // 辅助函数：计算曼哈顿距离（复制自 DrlgDrlg 的逻辑）
    private static void computeManhattanDistance(D2DrlgCoord coord1, D2DrlgCoord coord2, 
            int[] pDistanceX, int[] pDistanceY) {
        // 负距离表示我们在另一个矩形"内部"
        if (coord1.getNPosX() >= coord2.getNPosX()) {
            pDistanceX[0] = coord1.getNPosX() - coord2.getNWidth() - coord2.getNPosX();
        } else {
            pDistanceX[0] = coord2.getNPosX() - coord1.getNWidth() - coord1.getNPosX();
        }
        
        if (coord1.getNPosY() >= coord2.getNPosY()) {
            pDistanceY[0] = coord1.getNPosY() - coord2.getNHeight() - coord2.getNPosY();
        } else {
            pDistanceY[0] = coord2.getNPosY() - coord1.getNHeight() - coord1.getNPosY();
        }
    }
    
    // 辅助函数：获取矩形曼哈顿距离并检查是否不重叠
    private static boolean getRectanglesManhattanDistanceAndCheckNotOverlapping(
            D2DrlgCoord coord1, D2DrlgCoord coord2, int nMaxDistance, 
            int[] pDistanceX, int[] pDistanceY) {
        // 计算曼哈顿距离
        computeManhattanDistance(coord1, coord2, pDistanceX, pDistanceY);
        return pDistanceX[0] >= nMaxDistance || pDistanceY[0] >= nMaxDistance;
    }
    
    /**
     * D2Common.0x6FD7A340
     * 链接迷宫房间
     * 根据方向计算新房间的位置，并检查是否与其他房间重叠
     */
    public static boolean linkMazeRooms(D2DrlgRoom drlgRoom1, D2DrlgRoom drlgRoom2, int direction) {
        if (drlgRoom1 == null || drlgRoom2 == null) {
            return false;
        }
        
        // 根据方向计算新房间的位置
        switch (direction) {
            case 0: // WEST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() - drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos());
                break;
            case 1: // NORTH
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() - drlgRoom2.getNTileHeight());
                break;
            case 2: // EAST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() + drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos());
                break;
            case 3: // SOUTH
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() + drlgRoom2.getNTileHeight());
                break;
            case 4: // NORTHWEST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() - drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() - drlgRoom2.getNTileHeight());
                break;
            case 5: // NORTHEAST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() + drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() - drlgRoom2.getNTileHeight());
                break;
            case 6: // SOUTHEAST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() + drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() + drlgRoom2.getNTileHeight());
                break;
            case 7: // SOUTHWEST
                drlgRoom1.setNTileXPos(drlgRoom2.getNTileXPos() - drlgRoom2.getNTileWidth());
                drlgRoom1.setNTileYPos(drlgRoom2.getNTileYPos() + drlgRoom2.getNTileHeight());
                break;
            default:
                break;
        }
        
        // 检查是否与 drlgRoom2 的正交链接重叠
        D2DrlgOrth pDrlgOrth = drlgRoom2.getDrlgOrth();
        while (pDrlgOrth != null) {
            if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    drlgRoom1.getDrlgCoord(), pDrlgOrth.getPBox(), 0)) {
                return false;
            }
            pDrlgOrth = pDrlgOrth.getPNext();
        }
        
        // 检查是否与其他房间重叠
        return checkRoomNotOverlaping(drlgRoom1.getLevel(), drlgRoom1, drlgRoom2, 0);
    }
    
    /**
     * D2Common.0x6FD7A450
     * 合并迷宫房间
     * 检查是否可以与其他房间合并，如果可以则创建链接
     */
    public static void mergeMazeRooms(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getLevel() == null || hasMapDS1(drlgRoom)) {
            return;
        }
        
        D2DrlgLevel level = drlgRoom.getLevel();
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2MazeRecord)) {
            return;
        }
        
        D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze;
        
        for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
            if (i != drlgRoom && !hasMapDS1(i)) {
                int[] nX = new int[1];
                int[] nY = new int[1];
                
                if (!getRectanglesManhattanDistanceAndCheckNotOverlapping(
                        drlgRoom.getDrlgCoord(), i.getDrlgCoord(), 1, nX, nY)) {
                    if (nX[0] != nY[0]) {
                        // 检查是否已经存在链接
                        D2DrlgOrth pDrlgOrth = drlgRoom.getDrlgOrth();
                        boolean bFound = false;
                        while (pDrlgOrth != null) {
                            if (pDrlgOrth.getPLevel() == i.getLevel()) {
                                bFound = true;
                                break;
                            }
                            pDrlgOrth = pDrlgOrth.getPNext();
                        }
                        
                        if (!bFound) {
                            // 根据合并概率决定是否合并
                            int nRandom = Seed.rollLimitedRandomNumber(i.getSeed(), 1000);
                            if (nRandom < mazeRecord.getDwMerge()) {
                                int nDirection = DrlgDrlg.getDirectionFromCoordinates(
                                        i.getDrlgCoord(), drlgRoom.getDrlgCoord());
                                if (nDirection != -1) { // DIRECTION_INVALID
                                    // 创建链接
                                    D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                                    ppDrlgOrth[0] = i.getDrlgOrth();
                                    DrlgDrlgRoom.addOrth(ppDrlgOrth, drlgRoom.getLevel(), nDirection, true);
                                    i.setDrlgOrth(ppDrlgOrth[0]);
                                    pickRoomPreset(i, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7A570
     * 从关卡获取随机房间
     * 
     * 功能：
     * 1. 使用随机数生成器选择一个随机索引
     * 2. 遍历房间链表，找到对应索引的房间
     * 3. 返回随机选择的房间
     */
    public static D2DrlgRoom getRandomRoomExFromLevel(D2DrlgLevel level) {
        if (level == null) {
            return null;
        }
        
        // 使用 Seed 模块生成随机数
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        
        // 计算房间数量
        int roomCount = level.getRooms();
        if (roomCount == 0) {
            return null;
        }
        
        // 随机选择一个房间索引
        int index = (int)Seed.rollLimitedRandomNumber(level.getSeed(), roomCount);
        
        // 遍历房间链表，找到对应索引的房间
        D2DrlgRoom currentRoom = level.getFirstRoomEx();
        int currentIndex = 0;
        
        while (currentRoom != null && currentIndex < index) {
            currentRoom = currentRoom.getDrlgRoomNext();
            currentIndex++;
        }
        
        return currentRoom;
    }
    
    /**
     * D2Common.0x6FD7CA20
     * 设置房间大小
     * 从关卡的迷宫记录中获取尺寸并设置到房间
     * 
     * @param drlgRoom 房间
     */
    public static void setRoomSize(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getLevel() == null) {
            return;
        }
        
        D2DrlgLevel level = drlgRoom.getLevel();
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        
        if (presetOrOutdoorsOrMaze instanceof D2MazeRecord) {
            D2MazeRecord mazeRecord = (D2MazeRecord) presetOrOutdoorsOrMaze;
            drlgRoom.setNTileWidth(mazeRecord.getDwSizeX());
            drlgRoom.setNTileHeight(mazeRecord.getDwSizeY());
        } else {
            D2Log.warning("DRLGMAZE_SetRoomSize: Level does not have a maze record");
        }
    }
    
    /**
     * D2Common.0x6FD7A9B0
     * 填充空白迷宫空间
     * 在所有房间的8个方向上尝试放置填充房间
     * 
     * @param level 关卡
     * @param nLevelPrest 预设ID
     * @param ignoreRoomEx 要忽略的房间（不填充）
     */
    public static void fillBlankMazeSpaces(D2DrlgLevel level, int nLevelPrest, D2DrlgRoom ignoreRoomEx) {
        if (level == null) {
            return;
        }
        
        int nRooms = level.getRooms();
        if (nRooms <= 0) {
            return;
        }
        
        // 创建房间数组（用于安全迭代，因为添加新房间会改变链表）
        D2DrlgRoom[] pDrlgRoomArray = new D2DrlgRoom[nRooms];
        D2DrlgRoom pTemp = level.getFirstRoomEx();
        for (int i = 0; i < nRooms; ++i) {
            pDrlgRoomArray[i] = pTemp;
            pTemp = pTemp.getDrlgRoomNext();
        }
        
        // 遍历所有房间，尝试在8个方向上填充
        for (int i = 0; i < nRooms; ++i) {
            D2DrlgRoom currentRoomEx = pDrlgRoomArray[i];
            if (currentRoomEx != ignoreRoomEx) {
                // 尝试在8个方向上放置房间
                for (int j = 0; j < 8; ++j) {
                    D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(currentRoomEx.getLevel(), D2DrlgType.PRESET.getValue());
                    if (newRoomEx == null) {
                        continue;
                    }
                    
                    setRoomSize(newRoomEx);
                    
                    // 尝试链接
                    if (linkMazeRooms(newRoomEx, currentRoomEx, j)) {
                        // 分配正交链接
                        allocDrlgOrthsForRooms(currentRoomEx, newRoomEx, j);
                        // 添加到关卡
                        DrlgDrlgRoom.addRoomExToLevel(currentRoomEx.getLevel(), newRoomEx);
                        // 设置预设ID
                        setPickedFileAndPresetId(newRoomEx, nLevelPrest, -1, false);
                    } else {
                        // 链接失败，释放房间
                        DrlgDrlgRoom.freeRoomEx(newRoomEx);
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD79EA0
     * 添加相邻迷宫房间（对应 C++ 的 DRLGMAZE_PlaceAdjacentPresetRoom）
     * 在指定方向放置一个相邻的预设房间
     * 
     * @param parentRoomEx 父房间
     * @param direction 方向（0=WEST, 1=NORTH, 2=EAST, 3=SOUTH, 4=NORTHWEST, 5=NORTHEAST, 6=SOUTHEAST, 7=SOUTHWEST）
     * @param mergeRooms 是否合并房间
     * @return 新创建的房间，如果失败返回 null
     */
    public static D2DrlgRoom addAdjacentMazeRoom(D2DrlgRoom parentRoomEx, int direction, boolean mergeRooms) {
        if (parentRoomEx == null || parentRoomEx.getLevel() == null) {
            return null;
        }
        
        // 分配新房间
        D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(parentRoomEx.getLevel(), D2DrlgType.PRESET.getValue());
        if (newRoomEx == null) {
            return null;
        }
        
        // 设置房间大小
        setRoomSize(newRoomEx);
        
        // 根据方向计算位置
        switch (direction) {
            case 0: // ALTDIR_WEST / WEST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() - parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos());
                break;
            case 1: // ALTDIR_NORTH / NORTH
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() - parentRoomEx.getNTileHeight());
                break;
            case 2: // ALTDIR_EAST / EAST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() + parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos());
                break;
            case 3: // ALTDIR_SOUTH / SOUTH
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() + parentRoomEx.getNTileHeight());
                break;
            case 4: // ALTDIR_NORTHWEST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() - parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() - parentRoomEx.getNTileHeight());
                break;
            case 5: // ALTDIR_NORTHEAST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() + parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() - parentRoomEx.getNTileHeight());
                break;
            case 6: // ALTDIR_SOUTHEAST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() + parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() + parentRoomEx.getNTileHeight());
                break;
            case 7: // ALTDIR_SOUTHWEST
                newRoomEx.setNTileXPos(parentRoomEx.getNTileXPos() - parentRoomEx.getNTileWidth());
                newRoomEx.setNTileYPos(parentRoomEx.getNTileYPos() + parentRoomEx.getNTileHeight());
                break;
            default:
                DrlgDrlgRoom.freeRoomEx(newRoomEx);
                return null;
        }
        
        // 检查是否与父房间外的其他房间重叠
        if (checkIfRoomOverlapsAythingOtherThanParent(newRoomEx, parentRoomEx)) {
            DrlgDrlgRoom.freeRoomEx(newRoomEx);
            return null;
        }
        
        // 分配正交链接
        allocDrlgOrthsForRooms(parentRoomEx, newRoomEx, direction);
        
        // 如果需要合并，调用合并函数
        if (mergeRooms) {
            mergeMazeRooms(newRoomEx);
        }
        
        // 添加到关卡
        DrlgDrlgRoom.addRoomExToLevel(parentRoomEx.getLevel(), newRoomEx);
        return newRoomEx;
    }
    
    /**
     * D2Common.0x6FD7B710
     * 检查是否可以放置相邻预设房间
     * 检查在指定方向是否可以放置一个相邻的预设房间
     * 
     * @param drlgRoom 房间
     * @param direction 方向
     * @return 如果可以放置返回 true，否则返回 false
     */
    public static boolean checkIfMayPlaceAdjacentPresetRoom(D2DrlgRoom drlgRoom, int direction) {
        if (drlgRoom == null) {
            return false;
        }
        
        // 如果房间已经有地图 DS1，不能放置
        if (hasMapDS1(drlgRoom)) {
            return false;
        }
        
        // 检查方向是否已存在
        if (orthWithDirectionExists(drlgRoom, direction)) {
            return false;
        }
        
        // 尝试放置相邻房间（测试模式）
        // 注意：这里需要临时创建房间来测试，但实际实现中应该避免实际添加到关卡
        // 为了简化，我们直接调用 addAdjacentMazeRoom，如果成功则释放
        D2DrlgRoom newRoomEx = addAdjacentMazeRoom(drlgRoom, direction, false);
        
        if (newRoomEx != null) {
            // 从关卡中移除（因为这只是测试）
            // 注意：addAdjacentMazeRoom 已经将房间添加到关卡，我们需要手动移除
            D2DrlgLevel level = drlgRoom.getLevel();
            if (level.getFirstRoomEx() == newRoomEx) {
                level.setFirstRoomEx(newRoomEx.getDrlgRoomNext());
                level.setRooms(level.getRooms() - 1);
            } else {
                // 从链表中移除
                D2DrlgRoom current = level.getFirstRoomEx();
                while (current != null && current.getDrlgRoomNext() != newRoomEx) {
                    current = current.getDrlgRoomNext();
                }
                if (current != null) {
                    current.setDrlgRoomNext(newRoomEx.getDrlgRoomNext());
                    level.setRooms(level.getRooms() - 1);
                }
            }
            
            // 选择预设（如果已实现）
            // pickRoomPreset(newRoomEx, true);
            
            // 释放房间
            DrlgDrlgRoom.freeRoomEx(newRoomEx);
            return true;
        }
        
        return false;
    }
    
    /**
     * D2Common.0x6FD7B8B0
     * 放置 Act2 墓穴特殊预设
     * 根据关卡ID放置各种特殊预设（Next、Waypoint、Chest、Leatherarm、Cube、Treasure、TalRasha、Kaa）
     * 
     * @param level 关卡
     */
    public static void placeAct2TombStuff(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 预设ID数组
        D2MazeLevelIdStrc[] nAct2TombNextIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_NEXT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_NEXT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_NEXT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_NEXT_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombWaypointIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_WAYPOINT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_WAYPOINT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_WAYPOINT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_WAYPOINT_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombChestIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CHEST_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CHEST_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CHEST_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CHEST_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombLeatherarmIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_LEATHERARM_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_LEATHERARM_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_LEATHERARM_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_LEATHERARM_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombCubeIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CUBE_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CUBE_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CUBE_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_CUBE_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombTreasureIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TREASURE_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TREASURE_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TREASURE_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TREASURE_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombTalRashaIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TALRASHA_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TALRASHA_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TALRASHA_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_TALRASHA_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2TombKaaIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_N, D2LvlPrestIds.LVLPREST_ACT2_TOMB_KAA_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_E, D2LvlPrestIds.LVLPREST_ACT2_TOMB_KAA_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_S, D2LvlPrestIds.LVLPREST_ACT2_TOMB_KAA_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_TOMB_W, D2LvlPrestIds.LVLPREST_ACT2_TOMB_KAA_W, -1, 2),
        };
        
        // 查找第一个房间（预设ID > 428）
        D2DrlgPresetRoomStrc presetRoom = null;
        for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
            Object mazeOrOutdoor = i.getMazeOrOutdoor();
            if (mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                D2DrlgPresetRoomStrc p = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                if (p.getNLevelPrest() > 428) {
                    presetRoom = p;
                    break;
                }
            }
        }
        
        if (presetRoom == null) {
            return;
        }
        
        // 根据预设ID确定方向
        int nDirection = 0;
        switch (presetRoom.getNLevelPrest()) {
            case D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NSW:
                nDirection = 0;
                break;
            case D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NEW:
                nDirection = 1;
                break;
            case D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_NSE:
                nDirection = 2;
                break;
            case D2LvlPrestIds.LVLPREST_ACT2_TOMB_PREV_SEW:
                nDirection = 3;
                break;
            default:
                D2Log.warning("DRLGMAZE_PlaceAct2TombStuff: Unknown preset ID: " + presetRoom.getNLevelPrest());
                return;
        }
        
        D2DrlgStrc drlg = level.getDrlg();
        D2MazeLevelIdStrc pMazeLevelIds = null;
        
        // 根据关卡ID放置特殊预设
        if (level.getLevelId() >= D2LevelIds.LEVEL_STONYTOMBLEV1 && 
                level.getLevelId() <= D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV1) {
            pMazeLevelIds = nAct2TombNextIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
                    if (!hasMapDS1(i)) {
                        if (initRoomFixedPreset(i, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, 
                                pMazeLevelIds.nPickedFile, true) != null) {
                            break;
                        }
                    }
                }
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == D2LevelIds.LEVEL_HALLSOFTHEDEADLEV2) {
            pMazeLevelIds = nAct2TombWaypointIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == D2LevelIds.LEVEL_STONYTOMBLEV2 || 
                level.getLevelId() == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2) {
            pMazeLevelIds = nAct2TombChestIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() >= D2LevelIds.LEVEL_TALRASHASTOMB1 && 
                level.getLevelId() <= D2LevelIds.LEVEL_TALRASHASTOMB7 && 
                level.getLevelId() != drlg.getStaffTombLevel()) {
            pMazeLevelIds = nAct2TombChestIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == D2LevelIds.LEVEL_STONYTOMBLEV2) {
            pMazeLevelIds = nAct2TombLeatherarmIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == D2LevelIds.LEVEL_HALLSOFTHEDEADLEV3) {
            pMazeLevelIds = nAct2TombCubeIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == D2LevelIds.LEVEL_STONYTOMBLEV2 || 
                level.getLevelId() == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2) {
            pMazeLevelIds = nAct2TombTreasureIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == drlg.getStaffTombLevel()) {
            pMazeLevelIds = nAct2TombTalRashaIds[nDirection];
            if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                    pMazeLevelIds.nPickedFile, false) == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            nDirection = (nDirection + 1) % 4;
        }
        
        if (level.getLevelId() == drlg.getBossTombLevel()) {
            scanReplaceSpecialPreset(level, nAct2TombKaaIds[nDirection], new int[]{nDirection});
        }
    }
    
    /**
     * D2Common.0x6FD7BC40
     * 放置 Act2 巢穴特殊预设
     * 根据关卡ID放置各种特殊预设（Prev、Next、Treasure、Tight Spot）
     * 
     * @param level 关卡
     */
    public static void placeAct2LairStuff(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 预设ID数组
        D2MazeLevelIdStrc[] nAct2LairPrevIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_N, D2LvlPrestIds.LVLPREST_ACT2_LAIR_PREV_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_E, D2LvlPrestIds.LVLPREST_ACT2_LAIR_PREV_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_S, D2LvlPrestIds.LVLPREST_ACT2_LAIR_PREV_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_W, D2LvlPrestIds.LVLPREST_ACT2_LAIR_PREV_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct2LairSpecialIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_N, D2LvlPrestIds.LVLPREST_ACT2_LAIR_NEXT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_E, D2LvlPrestIds.LVLPREST_ACT2_LAIR_NEXT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_S, D2LvlPrestIds.LVLPREST_ACT2_LAIR_NEXT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_W, D2LvlPrestIds.LVLPREST_ACT2_LAIR_NEXT_W, -1, 2),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_N, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_E, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_S, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_W, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_W, -1, 2),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT2_LAIR_S, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TIGHT_SPOT_S, -1, 1),
        };
        
        int nRand = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
        int nIndex = 0;
        D2MazeLevelIdStrc pMazeLevelIds = null;
        D2DrlgRoom pDrlgRoom = null;
        
        if (level.getLevelId() == D2LevelIds.LEVEL_MAGGOTLAIRLEV3) {
            // 特殊处理 LEVEL_MAGGOTLAIRLEV3
            pDrlgRoom = level.getFirstRoomEx();
            while (pDrlgRoom != null) {
                Object mazeOrOutdoor = pDrlgRoom.getMazeOrOutdoor();
                if (!hasMapDS1(pDrlgRoom) && mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == D2LvlPrestIds.LVLPREST_ACT2_LAIR_S) {
                        setPickedFileAndPresetId(pDrlgRoom, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TIGHT_SPOT_S, -1, false);
                        break;
                    }
                }
                pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
            }
            
            if (pDrlgRoom == null) {
                pDrlgRoom = level.getFirstRoomEx();
                while (pDrlgRoom != null) {
                    if (!hasMapDS1(pDrlgRoom)) {
                        if (initRoomFixedPreset(pDrlgRoom, 1, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TIGHT_SPOT_S, -1, true) != null) {
                            break;
                        }
                    }
                    pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
                }
            }
            
            pDrlgRoom = level.getFirstRoomEx();
            while (pDrlgRoom != null) {
                Object mazeOrOutdoor = pDrlgRoom.getMazeOrOutdoor();
                if (!hasMapDS1(pDrlgRoom) && mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == D2LvlPrestIds.LVLPREST_ACT2_LAIR_W) {
                        setPickedFileAndPresetId(pDrlgRoom, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_W, -1, false);
                        break;
                    }
                }
                pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
            }
            
            if (pDrlgRoom == null) {
                addSpecialPreset(level, 2, D2LvlPrestIds.LVLPREST_ACT2_LAIR_TREASURE_W, -1);
            }
            
            nIndex = 0;
        } else {
            // 普通处理
            pMazeLevelIds = nAct2LairSpecialIds[nRand];
            pDrlgRoom = level.getFirstRoomEx();
            while (pDrlgRoom != null) {
                Object mazeOrOutdoor = pDrlgRoom.getMazeOrOutdoor();
                if (!hasMapDS1(pDrlgRoom) && mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == pMazeLevelIds.nLevelPrestId1) {
                        setPickedFileAndPresetId(pDrlgRoom, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile, false);
                        break;
                    }
                }
                pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
            }
            
            if (pDrlgRoom == null) {
                addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
            }
            
            nIndex = (nRand + 1) % 4;
        }
        
        // 放置 Prev 预设
        pMazeLevelIds = nAct2LairPrevIds[nIndex];
        if (replaceRoomPreset(level, pMazeLevelIds.nLevelPrestId1, pMazeLevelIds.nLevelPrestId2, 
                pMazeLevelIds.nPickedFile, false) == null) {
            addSpecialPreset(level, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile);
        }
    }
    
    /**
     * D2Common.0x6FD7CA40
     * 放置 Act5 巴尔特殊预设
     * 根据关卡ID放置各种特殊预设（Next、Waypoint）
     * 
     * @param level 关卡
     */
    public static void placeAct5BaalStuff(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 预设ID数组
        D2MazeLevelIdStrc[] nAct5BaalNextIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_N, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_E, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_S, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_W, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_W, -1, 2),
        };
        
        D2MazeLevelIdStrc[] nAct5BaalWaypointIds = {
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_N, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_N, -1, 3),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_E, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_E, -1, 0),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_S, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_S, -1, 1),
            new D2MazeLevelIdStrc(D2LvlPrestIds.LVLPREST_ACT5_BAAL_W, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_W, -1, 2),
        };
        
        int nDirection = (int)(Seed.rollRandomNumber(level.getSeed()) & 3);
        D2MazeLevelIdStrc pMazeLevelIds = nAct5BaalNextIds[nDirection];
        D2DrlgRoom pDrlgRoom = level.getFirstRoomEx();
        
        // 放置 Next 预设
        while (pDrlgRoom != null) {
            Object mazeOrOutdoor = pDrlgRoom.getMazeOrOutdoor();
            if (!hasMapDS1(pDrlgRoom) && mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                if (presetRoom.getNLevelPrest() == pMazeLevelIds.nLevelPrestId1) {
                    setPickedFileAndPresetId(pDrlgRoom, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile, false);
                    break;
                }
            }
            pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
        }
        
        if (pDrlgRoom == null) {
            for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
                if (!hasMapDS1(i)) {
                    if (initRoomFixedPreset(i, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, 
                            pMazeLevelIds.nPickedFile, true) != null) {
                        break;
                    }
                }
            }
        }
        
        // 放置 Waypoint 预设（仅在 LEVEL_THEWORLDSTONEKEEPLEV2）
        if (level.getLevelId() == D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2) {
            pMazeLevelIds = nAct5BaalWaypointIds[(nDirection + 1) % 4];
            pDrlgRoom = level.getFirstRoomEx();
            while (pDrlgRoom != null) {
                Object mazeOrOutdoor = pDrlgRoom.getMazeOrOutdoor();
                if (!hasMapDS1(pDrlgRoom) && mazeOrOutdoor instanceof D2DrlgPresetRoomStrc) {
                    D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) mazeOrOutdoor;
                    if (presetRoom.getNLevelPrest() == pMazeLevelIds.nLevelPrestId1) {
                        setPickedFileAndPresetId(pDrlgRoom, pMazeLevelIds.nLevelPrestId2, pMazeLevelIds.nPickedFile, false);
                        break;
                    }
                }
                pDrlgRoom = pDrlgRoom.getDrlgRoomNext();
            }
            
            if (pDrlgRoom == null) {
                for (D2DrlgRoom i = level.getFirstRoomEx(); i != null; i = i.getDrlgRoomNext()) {
                    if (!hasMapDS1(i)) {
                        if (initRoomFixedPreset(i, pMazeLevelIds.nDirection, pMazeLevelIds.nLevelPrestId2, 
                                pMazeLevelIds.nPickedFile, true) != null) {
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 辅助函数：放置熔岩预设
     * 对应 C++ 的 PlaceLavaPreset
     * 
     * @param firstRoomEx 第一个房间
     * @param nSet 预设集合索引
     */
    private static void placeLavaPreset(D2DrlgRoom firstRoomEx, int nSet) {
        // 熔岩预设数组 [预设ID, 方向, 文件索引]
        int[][] dword_6FDCE850 = {
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_S, 1, 0 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_N, 3, 1 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_S, 1, 1 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_N, 3, 0 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_E, 0, 1 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_W, 2, 0 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_E, 0, 0 },
            { D2LvlPrestIds.LVLPREST_ACT5_LAVA_W, 2, 1 },
        };
        
        if (nSet < 0 || nSet >= dword_6FDCE850.length) {
            return;
        }
        
        int nDirection = dword_6FDCE850[nSet][1];
        int nLevelPrest = dword_6FDCE850[nSet][0];
        int nPickedFile = dword_6FDCE850[nSet][2];
        
        D2DrlgRoom newRoomEx = DrlgDrlgRoom.allocRoomEx(firstRoomEx.getLevel(), D2DrlgType.PRESET.getValue());
        if (newRoomEx == null) {
            return;
        }
        
        setRoomSize(newRoomEx);
        
        if (linkMazeRooms(newRoomEx, firstRoomEx, nDirection)) {
            allocDrlgOrthsForRooms(firstRoomEx, newRoomEx, nDirection);
            DrlgDrlgRoom.addRoomExToLevel(firstRoomEx.getLevel(), newRoomEx);
            pickRoomPreset(firstRoomEx, true);
            
            setPickedFileAndPresetId(newRoomEx, nLevelPrest, nPickedFile, false);
        } else {
            DrlgDrlgRoom.freeRoomEx(newRoomEx);
        }
    }
    
    /**
     * D2Common.0x6FD7A830
     * 放置 Act5 熔岩预设
     * 随机选择预设集合并放置两个熔岩预设，然后填充空白空间
     * 
     * @param level 关卡
     */
    public static void placeAct5LavaPresets(D2DrlgLevel level) {
        if (level == null || level.getFirstRoomEx() == null) {
            return;
        }
        
        D2DrlgRoom firstRoomEx = level.getFirstRoomEx();
        int nSet = 2 * ((int)(Seed.rollRandomNumber(firstRoomEx.getSeed()) & 3));
        
        // 放置两个熔岩预设
        // 注意：pLevel->pFirstRoomEx 可能在调用 PlaceLavaPreset 时改变，使用同一个房间是正确的
        placeLavaPreset(firstRoomEx, nSet);
        placeLavaPreset(firstRoomEx, nSet + 1);
        
        // 填充空白空间
        fillBlankMazeSpaces(level, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, null);
    }
}
