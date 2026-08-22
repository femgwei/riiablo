package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.seed.Seed;

/**
 * Drlg 户外荒野模块
 * 对应 C++ 文件：DrlgOutWild.cpp
 */
public class DrlgOutWild {
    
    // 河流预设文件映射表
    private static class RiverFileMap {
        int nUpperFile;
        int nLowerFile;
        
        RiverFileMap(int upper, int lower) {
            nUpperFile = upper;
            nLowerFile = lower;
        }
    }
    
    private static final RiverFileMap[] stru_6FDD0CA0 = {
        new RiverFileMap(2, 2),
        new RiverFileMap(0, 3),
        new RiverFileMap(1, 1),
        new RiverFileMap(3, 0),
        new RiverFileMap(0, 2),
        new RiverFileMap(0, 1),
        new RiverFileMap(1, 0),
        new RiverFileMap(2, 0),
        new RiverFileMap(2, 3),
        new RiverFileMap(1, 3),
        new RiverFileMap(3, 1),
        new RiverFileMap(3, 2)
    };
    
    /**
     * D2Common.0x6FD84D30
     * 初始化 Act1 户外关卡
     * 被 DrlgOutdoors 依赖
     * 
     * 功能：
     * 1. 处理顶点方向（根据坐标差计算方向）
     * 2. 设置网格链接标志
     * 3. 放置边界
     * 4. 添加次要边界
     * 5. 生成河流（可选）
     * 6. 生成悬崖洞穴（可选）
     * 7. 生成城镇过渡和洞穴（可选）
     * 8. 生成特殊预设（可选）
     */
    public static void initAct1OutdoorLevel(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        D2DrlgVertexStrc pVertex = outdoors.getPVertex();
        
        if (pVertex == null) {
            D2Log.warning("DRLG_OUTWILD no vertex level=%d", level.getLevelId());
            return;
        }
        D2Log.debug("DRLG_OUTWILD begin level=%d grid=%dx%d flags=0x%X", level.getLevelId(),
                outdoors.getNGridWidth(), outdoors.getNGridHeight(), outdoors.getDwFlags());
        
        // 1. 处理顶点方向
        // 遍历顶点链表，根据坐标差计算方向
        D2DrlgVertexStrc pCurrent = pVertex;
        D2DrlgVertexStrc pStart = pVertex;
        int[] pDiffX = new int[1];
        int[] pDiffY = new int[1];
        
        do {
            // 获取当前顶点到下一个顶点的坐标差
            DrlgDrlgVer.getCoordDiff(pCurrent, pDiffX, pDiffY);
            
            // 根据坐标差计算方向
            // 方向值：0=无方向, 1=东, 2=南, 3=西, 4=北
            // 简化实现：根据坐标差设置方向
            byte nDirection = 0;
            int nDiffX = pDiffX[0];
            int nDiffY = pDiffY[0];
            
            if (nDiffX > 0 && nDiffY == 0) {
                nDirection = 1; // 东
            } else if (nDiffX == 0 && nDiffY > 0) {
                nDirection = 2; // 南
            } else if (nDiffX < 0 && nDiffY == 0) {
                nDirection = 3; // 西
            } else if (nDiffX == 0 && nDiffY < 0) {
                nDirection = 4; // 北
            } else if (nDiffX != 0 || nDiffY != 0) {
                // 对角线方向，使用主要方向
                if (Math.abs(nDiffX) > Math.abs(nDiffY)) {
                    nDirection = (byte)(nDiffX > 0 ? 1 : 3);
                } else {
                    nDirection = (byte)(nDiffY > 0 ? 2 : 4);
                }
            }
            
            pCurrent.setNDirection(nDirection);
            
            // 移动到下一个顶点
            pCurrent = pCurrent.getPNext();
        } while (pCurrent != null && pCurrent != pStart);
        
        // 2. 设置网格链接标志
        D2Log.debug("DRLG_OUTWILD linkFlags begin level=%d", level.getLevelId());
        DrlgOutPlace.setOutGridLinkFlags(level);
        D2Log.debug("DRLG_OUTWILD linkFlags end level=%d", level.getLevelId());
        logGrid2Flags(level, outdoors, "linkFlags");
        
        // 3. 放置边界
        D2Log.debug("DRLG_OUTWILD borders begin level=%d", level.getLevelId());
        DrlgOutPlace.placeAct1245OutdoorBorders(level);
        D2Log.debug("DRLG_OUTWILD borders end level=%d", level.getLevelId());
        logGrid2Flags(level, outdoors, "borders");
        
        // 4. 添加次要边界
        // Act1 使用荒野边界预设
        DrlgOutdoors.addAct124SecondaryBorder(level, 1, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1);
        logGrid2Flags(level, outdoors, "secondary1");
        DrlgOutdoors.addAct124SecondaryBorder(level, 2, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1);
        logGrid2Flags(level, outdoors, "secondary2");
        DrlgOutdoors.addAct124SecondaryBorder(level, 3, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1);
        logGrid2Flags(level, outdoors, "secondary3");
        D2Log.debug("DRLG_OUTWILD secondaryBorders end level=%d", level.getLevelId());
        
        // 5-8. 生成河流、悬崖洞穴、城镇过渡和洞穴、特殊预设
        // 这些功能可以根据关卡ID和具体需求调用相应的函数
        // 目前保留为可选功能，可以在后续根据需求实现
        
        D2Log.debug("DRLGOUTWILD_InitAct1OutdoorLevel: Act1 outdoor level initialized successfully");
    }

    private static void logGrid2Flags(D2DrlgLevel level, D2DrlgOutdoorInfoStrc outdoors, String stage) {
        int total = 0;
        int unk00 = 0;
        int unk07 = 0;
        int unk08 = 0;
        int picked = 0;
        int link = 0;
        for (int y = 0; y < outdoors.getNGridHeight(); y++) {
            for (int x = 0; x < outdoors.getNGridWidth(); x++) {
                D2DrlgOutdoorPackedGrid2InfoStrc info =
                        DrlgOutdoors.getPackedGrid2Info(outdoors, x, y);
                total++;
                if (info.isNUnkb00()) unk00++;
                if (info.isNUnkb07()) unk07++;
                if (info.isNUnkb08()) unk08++;
                if (info.isBHasPickedFile()) picked++;
                if (info.isBLvlLink()) link++;
            }
        }
        D2Log.debug("DRLG_OUTWILD grid2 stage=%s level=%d total=%d unk00=%d unk07=%d unk08=%d picked=%d link=%d",
                stage, level.getLevelId(), total, unk00, unk07, unk08, picked, link);
    }
    
    /**
     * D2Common.0x6FD84CA0
     * 获取桥梁坐标
     * 
     * 功能：
     * 1. 从关卡的户外信息中获取网格
     * 2. 在网格中查找桥梁预设（LVLPREST_ACT3_BRIDGE）
     * 3. 返回桥梁的坐标
     */
    public static void getBridgeCoords(D2DrlgLevel level, int[] x, int[] y) {
        if (level == null || x == null || x.length == 0 || y == null || y.length == 0) {
            if (x != null && x.length > 0) x[0] = -1;
            if (y != null && y.length > 0) y[0] = -1;
            return;
        }
        
        // 初始化返回值
        x[0] = -1;
        y[0] = -1;
        
        // 获取户外信息
        Object presetOrOutdoors = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoors instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoors;
        
        // 在网格中查找桥梁预设
        // 桥梁预设 ID 通常是 LVLPREST_ACT3_BRIDGE
        int nBridgePrestId = D2LvlPrestIds.LVLPREST_ACT3_BRIDGE;
        
        // 遍历网格查找桥梁预设
        int nGridWidth = outdoors.getNGridWidth();
        int nGridHeight = outdoors.getNGridHeight();
        
        for (int gridY = 0; gridY < nGridHeight; ++gridY) {
            for (int gridX = 0; gridX < nGridWidth; ++gridX) {
                // 使用 DrlgOutdoors 模块的函数测试是否为桥梁预设
                if (DrlgOutdoors.testOutdoorLevelPreset(level, gridX, gridY, nBridgePrestId, 0, (byte)15)) {
                    x[0] = gridX;
                    y[0] = gridY;
                    return; // 找到桥梁，返回
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD85060
     * 测试生成河流
     * 检查指定X坐标是否可以生成河流（检查是否有方向冲突）
     * 
     * @param level 关卡
     * @param x X坐标（网格坐标）
     * @return 如果可以生成河流返回 true，否则返回 false
     */
    public static boolean testSpawnRiver(D2DrlgLevel level, int x) {
        if (level == null) {
            return false;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return false;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        int nGridHeight = pOutdoors.getNGridHeight();
        
        // 检查整列是否有方向冲突
        for (int nY = 0; nY < nGridHeight; ++nY) {
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo1 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, x, nY);
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo2 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, x + 1, nY);
            
            if (packedInfo1 != null && packedInfo1.isBHasDirection()) {
                return false;
            }
            if (packedInfo2 != null && packedInfo2.isBHasDirection()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD850B0
     * 生成河流
     * 在指定X坐标生成河流预设
     * 
     * @param level 关卡
     * @param x X坐标（网格坐标）
     */
    public static void spawnRiver(D2DrlgLevel level, int x) {
        if (level == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        int nGridHeight = pOutdoors.getNGridHeight();
        
        // 为每一行生成上部和下部河流预设
        for (int nY = 0; nY < nGridHeight; ++nY) {
            spawnRiverPreset(level, x, nY, false); // 上部河流
            spawnRiverPreset(level, x, nY, true);   // 下部河流
        }
        
        // 如果设置了河流或桥梁标志，尝试生成桥梁
        if ((pOutdoors.getDwFlags() & (DrlgOutdoors.OUTDOOR_RIVER | DrlgOutdoors.OUTDOOR_BRIDGE)) != 0) {
            int nRand = Seed.rollLimitedRandomNumber(level.getSeed(), nGridHeight - 2);
            
            for (int i = 0; i < nGridHeight - 2; ++i) {
                int nY = (nRand + i) % (nGridHeight - 1);
                
                if (DrlgOutdoors.testGridCellSpawnValid(level, x - 1, nY) 
                        && ((pOutdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_BRIDGE) != 0 
                            || DrlgOutdoors.testGridCellSpawnValid(level, x + 2, nY))) {
                    
                    D2DrlgOutdoorPackedGrid2InfoStrc packedInfo1 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, x, nY);
                    D2DrlgOutdoorPackedGrid2InfoStrc packedInfo2 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, x + 1, nY);
                    
                    if (packedInfo1 != null && packedInfo1.getNPickedFile() == 3
                            && packedInfo2 != null && packedInfo2.getNPickedFile() == 3) {
                        // 生成桥梁
                        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x, nY, D2LvlPrestIds.LVLPREST_ACT1_BRIDGE, 1, false);
                        int nBridgeFile = ((pOutdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_BRIDGE) != 0) ? 3 : 2;
                        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x + 1, nY, D2LvlPrestIds.LVLPREST_ACT1_BRIDGE, nBridgeFile, false);
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD850F0 (DRLGOUTWILD_SpawnRiverPreset)
     * 生成河流预设
     * 根据边界和网格信息选择合适的河流预设文件
     * 
     * @param level 关卡
     * @param x X坐标（网格坐标）
     * @param y Y坐标（网格坐标）
     * @param bLowerRiver 是否为下部河流
     */
    private static void spawnRiverPreset(D2DrlgLevel level, int x, int y, boolean bLowerRiver) {
        int nPickedFile = 0;
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = DrlgOutdoors.getPackedGrid2Info(pOutdoors, x + (bLowerRiver ? 1 : 0), y);
        
        D2DrlgGridStrc pGrid = pOutdoors.getPGrid(0);
        int nFlags2 = DrlgDrlgGrid.getGridEntry(pGrid, x + (bLowerRiver ? 1 : 0), y);
        
        if (nFlags2 != 0) {
            if (nFlags2 != D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_4 || (tPackedInfo != null && tPackedInfo.getNPickedFile() != 3)) {
                int nIdx = nFlags2 - D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1;
                if (nIdx >= 0 && nIdx < stru_6FDD0CA0.length) {
                    nPickedFile = bLowerRiver ? stru_6FDD0CA0[nIdx].nLowerFile : stru_6FDD0CA0[nIdx].nUpperFile;
                }
            } else {
                nPickedFile = 3;
            }
        } else {
            if (tPackedInfo != null && tPackedInfo.isNUnkb08()) {
                nPickedFile = 0;
            } else {
                nPickedFile = 3;
            }
        }
        
        int nLvlPrestId = bLowerRiver ? D2LvlPrestIds.LVLPREST_ACT1_RIVER_LOWER : D2LvlPrestIds.LVLPREST_ACT1_RIVER_UPPER;
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x + (bLowerRiver ? 1 : 0), y, nLvlPrestId, nPickedFile, false);
    }
    
    /**
     * D2Common.0x6FD85390
     * 生成悬崖洞穴
     * 根据网格条目值生成对应的悬崖洞穴预设
     * 
     * @param level 关卡
     * @param x X坐标（网格坐标）
     * @param y Y坐标（网格坐标）
     * @return 如果成功生成洞穴返回 true，否则返回 false
     */
    public static boolean spawnCliffCaves(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return false;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return false;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        D2DrlgGridStrc pGrid = pOutdoors.getPGrid(0);
        
        int nGridEntry = DrlgDrlgGrid.getGridEntry(pGrid, x, y);
        
        switch (nGridEntry) {
            case 16: // LVLPREST_ACT1_WILD_CLIFF_BORDER_2 对应的值
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x, y, D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_CAVE_LEFT, -1, false);
                pOutdoors.setDwFlags(pOutdoors.getDwFlags() | DrlgOutdoors.OUTDOOR_OUT_CAVES);
                return true;
                
            case 17: // LVLPREST_ACT1_WILD_CLIFF_BORDER_3 对应的值
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x, y, D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_CAVE_RIGHT, -1, false);
                pOutdoors.setDwFlags(pOutdoors.getDwFlags() | DrlgOutdoors.OUTDOOR_OUT_CAVES);
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * D2Common.0x6FD853F0
     * 生成城镇过渡和洞穴
     * 根据关卡标志生成城镇过渡预设和洞穴入口
     * 
     * @param level 关卡
     */
    public static void spawnTownTransitionsAndCaves(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        
        // 如果不是 MOOMOOFARM 关卡
        if (level.getLevelId() != D2LevelIds.LEVEL_MOOMOOFARM) {
            // 如果设置了河流标志（0x10），尝试生成河流
            if ((pOutdoors.getDwFlags() & 0x10) != 0) {
                int nX = pOutdoors.getNGridWidth() / 2 - 1;
                
                if (pOutdoors.getNGridHeight() <= 0) {
                    spawnRiver(level, nX);
                } else {
                    int nY = 0;
                    while (true) {
                        D2DrlgOutdoorPackedGrid2InfoStrc packedInfo1 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, nX, nY);
                        D2DrlgOutdoorPackedGrid2InfoStrc packedInfo2 = DrlgOutdoors.getPackedGrid2Info(pOutdoors, nX + 1, nY);
                        
                        if ((packedInfo1 != null && packedInfo1.isBHasDirection())
                                || (packedInfo2 != null && packedInfo2.isBHasDirection())) {
                            break;
                        }
                        
                        ++nY;
                        
                        if (nY >= pOutdoors.getNGridHeight()) {
                            spawnRiver(level, nX);
                            break;
                        }
                    }
                }
            }
            
            // 生成城镇过渡预设（根据标志位）
            if ((pOutdoors.getDwFlags() & 0x80) != 0) {
                // 西南方向过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, D2LvlPrestIds.LVLPREST_ACT1_TOWN_1_TRANSITION_S, 1, false);
            }
            
            if ((pOutdoors.getDwFlags() & 0x100) != 0) {
                // 西北方向过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, pOutdoors.getNGridWidth() - 7, 0, 
                        D2LvlPrestIds.LVLPREST_ACT1_TOWN_1_TRANSITION_S, 2, false);
            }
            
            if ((pOutdoors.getDwFlags() & 0x200) != 0) {
                // 东南方向过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 1, D2LvlPrestIds.LVLPREST_ACT1_TOWN_1_TRANSITION_E, 1, false);
            }
            
            if ((pOutdoors.getDwFlags() & 0x400) != 0) {
                // 东北方向过渡
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, pOutdoors.getNGridHeight() - 6, 
                        D2LvlPrestIds.LVLPREST_ACT1_TOWN_1_TRANSITION_E, 1, false);
            }
            
            // 如果还没有生成洞穴（0x40 标志未设置），生成洞穴入口
            if ((pOutdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_OUT_CAVES) == 0) {
                boolean fAdded = false;
                
                if (level.getLevelId() == D2LevelIds.LEVEL_BLOODMOOR) {
                    // BLOODMOOR 关卡：生成 DOE 入口（远离城镇）
                    D2DrlgLevel pRogueEncampment = DrlgDrlg.getLevel(level.getDrlg(), D2LevelIds.LEVEL_ROGUEENCAMPMENT);
                    if (pRogueEncampment != null) {
                        fAdded = DrlgOutdoors.spawnPresetFarAway(level, pRogueEncampment.getLevelCoords(), 
                                D2LvlPrestIds.LVLPREST_ACT1_DOE_ENTRANCE, -1, 1, (char)15);
                    }
                } else {
                    // 其他关卡：生成普通洞穴入口
                    fAdded = DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_CAVE_ENTRANCE, -1, 1, (char)15);
                }
                
                if (!fAdded) {
                    D2Log.warning("DRLGOUTWILD_SpawnTownTransitionsAndCaves: Failed to spawn cave entrance");
                }
                
                // 设置洞穴标志
                pOutdoors.setDwFlags(pOutdoors.getDwFlags() | DrlgOutdoors.OUTDOOR_OUT_CAVES);
            }
        }
    }
    
    /**
     * D2Common.0x6FD85520
     * 生成特殊预设
     * 根据关卡ID生成对应的特殊预设（池塘、小屋、石头填充等）
     * 
     * @param level 关卡
     */
    public static void spawnSpecialPresets(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        switch (level.getLevelId()) {
            case D2LevelIds.LEVEL_BLOODMOOR:
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_POND, -1);
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) == 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                }
                
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_2, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_COLDPLAINS:
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) != 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                    
                    if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                        DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_3, -1);
                    }
                } else {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                }
                
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_BISHIBOSH, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_2, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_STONYFIELD:
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_CAIRN_STONES, -1);
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_CAMP, -1);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_TOWER_TOME, -1, 0, (char)15);
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) != 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                    
                    if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                        DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_3, -1);
                    }
                } else {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                }
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) == 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_1, -1);
                }
                
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_1, -1);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_CORRAL_FILL, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_DARKWOOD:
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_INIFUS, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_RUIN, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_TREE_FILL, -1, 0, (char)15);
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) != 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                    
                    if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                        DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_3, -1);
                    }
                } else {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, -1);
                }
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) == 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_2, -1);
                }
                
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_2, -1);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_2, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_BLACKMARSH:
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_TOWER_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_SWAMP_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_SWAMP_FILL_2, -1, 0, (char)15);
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) != 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                    
                    if ((Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                        DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_3, -1);
                    }
                } else {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_1, -1);
                }
                
                if ((Seed.rollRandomNumber(level.getSeed()) & 3) == 0) {
                    DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_1, -1);
                }
                
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_1, -1);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_2, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_TAMOEHIGHLAND:
                spawnCottage(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_2, 1);
                spawnCottage(level, D2LvlPrestIds.LVLPREST_ACT1_FALLEN_CAMP_2, 0);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_CORRAL_FILL, -1, 0, (char)15);
                return;
                
            case D2LevelIds.LEVEL_BURIALGROUNDS:
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 1, 1, D2LvlPrestIds.LVLPREST_ACT1_GRAVEYARD, -1, false);
                return;
                
            case D2LevelIds.LEVEL_MOOMOOFARM:
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_BIVOUAC, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_POND, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_CORRAL_FILL, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_SWAMP_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_SWAMP_FILL_2, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_1, -1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT1_STONE_FILL_2, -1, 0, (char)15);
                return;
                
            default:
                return;
        }
    }
    
    /**
     * D2Common.0x6FD85920
     * 生成小屋
     * 根据随机数生成小屋预设（可能生成1个或2个）
     * 
     * @param level 关卡
     * @param nLvlPrestId 预设ID
     * @param a3 参数（如果为1，可能额外生成 COTTAGES_3）
     */
    public static void spawnCottage(D2DrlgLevel level, int nLvlPrestId, int a3) {
        if (level == null) {
            return;
        }
        
        if ((Seed.rollRandomNumber(level.getSeed()) & 3) != 0) {
            DrlgOutdoors.spawnRandomOutdoorDS1(level, nLvlPrestId, -1);
            
            if (a3 != 0 && (Seed.rollRandomNumber(level.getSeed()) & 1) != 0) {
                DrlgOutdoors.spawnRandomOutdoorDS1(level, D2LvlPrestIds.LVLPREST_ACT1_COTTAGES_3, -1);
            }
        } else {
            DrlgOutdoors.spawnRandomOutdoorDS1(level, nLvlPrestId, -1);
            DrlgOutdoors.spawnRandomOutdoorDS1(level, nLvlPrestId, -1);
        }
    }
}
