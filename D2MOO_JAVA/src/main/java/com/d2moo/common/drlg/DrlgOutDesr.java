package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LvlPrestTxt;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;

/**
 * Drlg 户外沙漠模块
 * 对应 C++ 文件：DrlgOutDesr.cpp
 */
public class DrlgOutDesr {
    
    /**
     * D2Common.0x6FD7D430
     * 初始化 Act2 户外关卡
     * 被 DrlgOutdoors 依赖
     */
    public static void initAct2OutdoorLevel(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 预设ID数组
        final int[] nLevelPrestIds1 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_08X08,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BONE_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BONE_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_HEAD_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_MESA_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_WAGON_1
        };
        
        final int[] nLevelPrestIds2 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_08X08,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_HEAD_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_MESA_1
        };
        
        final int[] nLevelPrestIds3 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_3,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_4
        };
        
        final int[] nLevelPrestIds4 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_3
        };
        
        // 设置网格链接标志
        DrlgOutPlace.setOutGridLinkFlags(level);
        
        // 放置边界
        DrlgOutPlace.placeAct1245OutdoorBorders(level);
        
        // 根据关卡ID执行不同的初始化逻辑
        int nLevelId = level.getLevelId();
        
        switch (nLevelId) {
            case D2LevelIds.LEVEL_ROCKYWASTE:
                placeDesertTransitionToTown(level);
                placeBorders(level);
                addExits(level);
                DrlgOutdoors.spawnAct12Shrines(level, 5);
                placePresetVariants(level, nLevelPrestIds1, nLevelPrestIds1.length, false);
                break;
                
            case D2LevelIds.LEVEL_DRYHILLS:
                placeCliffs(level);
                placeBorders(level);
                addExits(level);
                DrlgOutdoors.spawnAct12Waypoint(level);
                DrlgOutdoors.spawnAct12Shrines(level, 5);
                placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, false);
                placePresetVariants(level, nLevelPrestIds3, nLevelPrestIds3.length, true);
                break;
                
            case D2LevelIds.LEVEL_FAROASIS:
                placeCliffs(level);
                placeBorders(level);
                addExits(level);
                placePresetVariants(level, nLevelPrestIds4, nLevelPrestIds4.length, false);
                DrlgOutdoors.spawnAct12Waypoint(level);
                DrlgOutdoors.spawnAct12Shrines(level, 5);
                placeFillsInFarOasis(level);
                break;
                
            case D2LevelIds.LEVEL_LOSTCITY:
                placeCliffs(level);
                placeBorders(level);
                addExits(level);
                placeRuinsInLostCity(level);
                DrlgOutdoors.spawnAct12Waypoint(level);
                DrlgOutdoors.spawnAct12Shrines(level, 5);
                placeFillsInLostCity(level);
                break;
                
            case D2LevelIds.LEVEL_VALLEYOFSNAKES:
                addExits(level);
                break;
                
            case D2LevelIds.LEVEL_CANYONOFMAGI:
                placeTombEntriesInCanyon(level);
                placeBorders(level);
                DrlgOutdoors.spawnAct12Shrines(level, 5);
                placeFillsInCanyon(level);
                break;
                
            default:
                break;
        }
    }
    
    /**
     * 放置沙漠到城镇的过渡（辅助函数）
     * D2Common.0x6FD7D430 (内联函数)
     */
    private static void placeDesertTransitionToTown(D2DrlgLevel level) {
        if (level == null || level.getPresetOrOutdoorsOrMaze() == null 
                || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        
        // 查找城镇房间数据
        D2DrlgOrth pTownRoomData = outdoors.getPRoomData();
        while (pTownRoomData != null && pTownRoomData.getPLevel().getLevelId() != D2LevelIds.LEVEL_LUTGHOLEIN) {
            pTownRoomData = pTownRoomData.getPNext();
        }
        
        if (pTownRoomData != null) {
            if (pTownRoomData.getNDirection() == 3) {
                // 北方过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, outdoors.getNGridHeight() - 1, 
                    D2LvlPrestIds.LVLPREST_ACT2_DESERT_TRANSITION_N, -1, false);
            } else {
                // 西方过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, outdoors.getNGridWidth() - 1, 0, 
                    D2LvlPrestIds.LVLPREST_ACT2_DESERT_TRANSITION_W, -1, false);
            }
        }
    }
    
    /**
     * D2Common.0x6FD7D870
     * 放置预设变体
     */
    public static void placePresetVariants(D2DrlgLevel level, int[] pLevelPrestIds, 
            int nVariants, boolean iterateFiles) {
        if (level == null || pLevelPrestIds == null || nVariants <= 0) {
            return;
        }
        
        // 确保种子已初始化
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        
        // 随机选择起始索引
        int nRand = Seed.rollLimitedRandomNumber(level.getSeed(), nVariants);
        
        for (int i = 0; i < nVariants; ++i) {
            int nLevelPrestId = pLevelPrestIds[nRand];
            
            if (iterateFiles) {
                // 获取预设文本记录以获取文件数量
                D2LvlPrestTxt pLvlPrestTxtRecord = DataTbls.getLvlPrestTxtRecord(nLevelPrestId);
                if (pLvlPrestTxtRecord != null) {
                    int nFiles = pLvlPrestTxtRecord.getDwFiles();
                    for (int nFile = 0; nFile < nFiles; ++nFile) {
                        DrlgOutdoors.spawnOutdoorLevelPreset(level, nLevelPrestId, nFile, 0, (char)15);
                    }
                } else {
                    D2Log.warning("DRLGOUTDESR_PlacePresetVariants: Failed to get LvlPrestTxt record for preset ID: " + nLevelPrestId);
                }
            } else {
                // 放置单个预设（随机选择文件）
                DrlgOutdoors.spawnOutdoorLevelPreset(level, nLevelPrestId, -1, 0, (char)15);
            }
            
            // 循环到下一个预设
            nRand = (nRand + 1) % nVariants;
        }
    }
    
    /**
     * D2Common.0x6FD7D950
     * 放置悬崖
     */
    public static void placeCliffs(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 确保种子已初始化
        if (level.getSeed() == null) {
            level.setSeed(new D2Seed());
        }
        
        // 悬崖初始化数据（8种变体，每种5个元素）
        final D2DrlgOutDesertInitStrc[][] pOutDesertInit = {
            // 变体 0
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 1, 0, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_PATH, -1, 2, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 6, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 2, 8, 4)
            },
            // 变体 1
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 1, 0, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 2, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_PATH, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 6, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 2, 8, 4)
            },
            // 变体 2
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 1, 0, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 2, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_PATH, -1, 6, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 2, 8, 4)
            },
            // 变体 3
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 2, 8, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_WALL, -1, 6, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_TOP, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_PATH, -1, 4, 6),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 2, 4, 8)
            },
            // 变体 4
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_ENDS, 2, 8, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_PATH, -1, 6, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_TOP, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 6),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 2, 4, 8)
            },
            // 变体 5
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 1, 4, 0),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_PATH, -1, 4, 2),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 6),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 2, 4, 8)
            },
            // 变体 6
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 1, 4, 0),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 2),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_PATH, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 6),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 2, 4, 8)
            },
            // 变体 7
            {
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 1, 4, 0),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 2),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_WALL, -1, 4, 4),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_PATH, -1, 4, 6),
                new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_ENDS, 2, 4, 8)
            }
        };
        
        // 随机选择变体（0-7）
        int nRand = (int)(Seed.rollRandomNumber(level.getSeed()) & 7L);
        
        // 放置5个悬崖元素
        for (int i = 0; i < 5; ++i) {
            D2DrlgOutDesertInitStrc init = pOutDesertInit[nRand][i];
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, init.getNX(), init.getNY(), 
                init.getNLvlPrestId(), init.getNRand(), false);
        }
    }
    
    /**
     * D2Common.0x6FD7D9B0
     * 放置边界
     */
    public static void placeBorders(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        DrlgOutdoors.addAct124SecondaryBorder(level, 2, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_1);
        DrlgOutdoors.addAct124SecondaryBorder(level, 1, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_1);
        DrlgOutdoors.addAct124SecondaryBorder(level, 3, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_1);
    }
    
    /**
     * D2Common.0x6FD7D9F0
     * 添加出口
     */
    public static void addExits(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        int nLevelPrestId = 0;
        int nLevelId = level.getLevelId();
        
        switch (nLevelId) {
            case D2LevelIds.LEVEL_ROCKYWASTE:
            case D2LevelIds.LEVEL_DRYHILLS:
                nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT2_DESERT_TOMB_1;
                break;
                
            case D2LevelIds.LEVEL_FAROASIS:
                nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT2_DESERT_LAIR_1;
                break;
                
            case D2LevelIds.LEVEL_LOSTCITY:
                nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_SEWER;
                break;
                
            case D2LevelIds.LEVEL_VALLEYOFSNAKES:
                nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT2_DESERT_TOMB_2;
                break;
                
            default:
                D2Log.warning("DRLGOUTDESR_AddExits: Unknown level ID: " + nLevelId);
                return;
        }
        
        // 放置出口预设
        if (!DrlgOutdoors.spawnOutdoorLevelPreset(level, nLevelPrestId, -1, 0, (char)15)) {
            D2Log.warning("DRLGOUTDESR_AddExits: Failed to spawn exit preset for level: " + nLevelId);
        }
    }
    
    /**
     * D2Common.0x6FD7DA60
     * 在遥远绿洲放置填充物
     */
    public static void placeFillsInFarOasis(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        final int[] nLevelPrestIds1 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_08X08,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_HEAD_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_MESA_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_WAGON_1
        };
        
        final int[] nLevelPrestIds2 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_1
        };
        
        placePresetVariants(level, nLevelPrestIds1, nLevelPrestIds1.length, false);
        placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, true);
        placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, true);
    }
    
    /**
     * D2Common.0x6FD7DAC0
     * 在失落之城放置废墟
     */
    public static void placeRuinsInLostCity(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        final int[] nLevelPrestIds = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_ELDER,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_16X16,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_16X08,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_08X16
        };
        
        placePresetVariants(level, nLevelPrestIds, nLevelPrestIds.length, false);
    }
    
    /**
     * D2Common.0x6FD7DB00
     * 在失落之城放置填充物
     */
    public static void placeFillsInLostCity(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        final int[] nLevelPrestIds1 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_OASIS_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_HEAD_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_MESA_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_2
        };
        
        final int[] nLevelPrestIds2 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_RUINS_08X08
        };
        
        placePresetVariants(level, nLevelPrestIds1, nLevelPrestIds1.length, false);
        placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, true);
        placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, true);
    }
    
    /**
     * D2Common.0x6FD7DB70
     * 在峡谷放置墓穴入口
     */
    public static void placeTombEntriesInCanyon(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 墓穴入口初始化数据（9个元素）
        final D2DrlgOutDesertInitStrc[] pOutDesertInit = {
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_KING_ENDS, 0, 8, 0),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_KING_TOMB, 2, 6, 0),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_KING_TOMB, 1, 4, 0),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_RIGHT_KING_TOMB, 0, 2, 0),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_TOP_KING_TOMB, 0, 0, 0),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_KING_TOMB, 0, 0, 2),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_KING_TOMB, 1, 0, 4),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_KING_TOMB, 2, 0, 6),
            new D2DrlgOutDesertInitStrc(D2LvlPrestIds.LVLPREST_ACT2_DESERT_CLIFF_LEFT_KING_ENDS, 0, 0, 8)
        };
        
        // 放置9个墓穴入口
        for (int i = 0; i < pOutDesertInit.length; ++i) {
            D2DrlgOutDesertInitStrc init = pOutDesertInit[i];
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, init.getNX(), init.getNY(), 
                init.getNLvlPrestId(), init.getNRand(), false);
        }
        
        // 放置传送点
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 4, 4, 
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_VALLEY_WARP, -1, false);
    }
    
    /**
     * D2Common.0x6FD7DBC0
     * 在峡谷放置填充物
     */
    public static void placeFillsInCanyon(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        final int[] nLevelPrestIds1 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BONE_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BONE_2,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_3,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_BERMS_4,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_FILL_WAGON_1
        };
        
        final int[] nLevelPrestIds2 = {
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_VALLEY_RUIN_1,
            D2LvlPrestIds.LVLPREST_ACT2_DESERT_VALLEY_RUIN_2
        };
        
        placePresetVariants(level, nLevelPrestIds1, nLevelPrestIds1.length, false);
        placePresetVariants(level, nLevelPrestIds2, nLevelPrestIds2.length, true);
    }
}
