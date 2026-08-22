package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 户外丛林模块
 * 对应 C++ 文件：DrlgOutJung.cpp
 */
public class DrlgOutJung {
    
    // 静态常量数组：关卡ID偏移量
    private static final int[] dword_6FDCFB18 = {0, 10, 20, 0};
    
    // 静态常量数组：文件索引随机化表
    private static final int[] dword_6FDCFB28 = {0, 1, 2, 1, 0, 2, 0, 2, 1, 1, 2, 0, 2, 0, 1, 2, 1, 0};
    
    /**
     * D2Common.0x6FD7FC20
     * 构建丛林
     * 被 DrlgOutPlace 依赖
     */
    public static void buildJungle(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        // 只处理蜘蛛森林到剥皮丛林之间的关卡
        if (level.getLevelId() < D2LevelIds.LEVEL_SPIDERFOREST || 
            level.getLevelId() > D2LevelIds.LEVEL_FLAYERJUNGLE) {
            return;
        }
        
        D2LevelDefBin pLevelDefBin = DataTbls.getLevelDefRecord(D2LevelIds.LEVEL_SPIDERFOREST);
        if (pLevelDefBin == null) {
            D2Log.warning("DRLGOUTJUNG_BuildJungle: Failed to get level def record");
            return;
        }
        
        // 计算网格大小（除以32，即右移5位）
        int nSizeX = pLevelDefBin.getDwSizeX(level.getDrlg().getDifficulty()) >> 5;
        int nSizeY = pLevelDefBin.getDwSizeY(level.getDrlg().getDifficulty()) >> 5;
        
        // 计算随机数：如果 nJungleDefs == 3，则范围是 0-5，否则是 0-1
        int nRand = Seed.rollLimitedRandomNumber(level.getSeed(), 
            4 * (level.getNJungleDefsCount() == 3 ? 1 : 0) + 2);
        
        if (level.getPJungleDefs() == null) {
            D2Log.warning("DRLGOUTJUNG_BuildJungle: pJungleDefs is null");
            return;
        }
        
        int nFileIndex = 0;
        int nDefId = 0;
        
        for (int i = 0; i < nSizeY; ++i) {
            if (level.getLevelId() == D2LevelIds.LEVEL_SPIDERFOREST && i == nSizeY - 1) {
                // 蜘蛛森林的最后一行：生成 JUNGLE_HEAD
                int nJungleDef = level.getPJungleDefs()[nSizeX * nSizeY - 1];
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 4 * i, 
                    D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_HEAD, 
                    nJungleDef == 0 ? 0 : -1, false);
                nDefId += 2;
            } else if (level.getLevelId() == D2LevelIds.LEVEL_FLAYERJUNGLE && i == 0) {
                // 剥皮丛林的第一行：生成 JUNGLE_TAIL
                int nJungleDef = level.getPJungleDefs()[1];
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, 
                    D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_TAIL, 
                    nJungleDef == 0 ? 0 : -1, false);
                nDefId += 2;
            } else {
                // 正常处理每一行
                for (int j = 0; j < nSizeX; ++j) {
                    ++nDefId;
                    int nJungleDef = level.getPJungleDefs()[nDefId - 1];
                    int v19 = -1;
                    
                    if (nJungleDef > D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_TAIL) {
                        if (nFileIndex >= 3) {
                            D2Log.warning("DRLGOUTJUNG_BuildJungle: nFileIndex >= 3");
                        }
                        
                        // 根据关卡ID添加偏移量
                        int levelOffset = level.getLevelId() - D2LevelIds.LEVEL_SPIDERFOREST;
                        if (levelOffset >= 0 && levelOffset < dword_6FDCFB18.length) {
                            nJungleDef += dword_6FDCFB18[levelOffset];
                        }
                        
                        // 从随机化表中获取文件索引
                        int tableIndex = nFileIndex + 3 * nRand;
                        if (tableIndex >= 0 && tableIndex < dword_6FDCFB28.length) {
                            v19 = dword_6FDCFB28[tableIndex];
                        }
                        ++nFileIndex;
                    }
                    
                    if (nJungleDef != 0) {
                        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 4 * j, 4 * i, 
                            nJungleDef, v19, false);
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7FE50
     * 构建下库拉斯特
     */
    public static void buildLowerKurast(D2DrlgLevel level) {
        if (level == null || level.getLevelCoords() == null || level.getDrlg() == null) {
            return;
        }
        
        // 计算网格尺寸（除以8再减1）
        int nWidth = level.getLevelCoords().getNWidth() / 8 - 1;
        int nHeight = level.getLevelCoords().getNHeight() / 8 - 1;
        int v5 = (level.getLevelCoords().getNWidth() / 8 - 1) / 2;
        
        // 根据 bJungleInterlink 决定 v10 的值
        int v10;
        if (level.getDrlg().getJungleInterlink() != 0) {
            v10 = 1;
        } else {
            v10 = nWidth - 1;
        }
        
        // 生成北边界（顶部）
        for (int i = 1; i < nWidth; ++i) {
            int nPrestId = D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_N;
            if (i == v10) {
                nPrestId = D2LvlPrestIds.LVLPREST_ACT3_SLUMS_GATE_N; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, 0, nPrestId, -1, false);
        }
        
        // 生成南边界（底部）
        for (int i = 1; i < nWidth; i += (i == v5 ? 1 : 0) + 1) {
            int nPrestId = D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_S;
            if (i == v5) {
                nPrestId = D2LvlPrestIds.LVLPREST_ACT3_SLUMS_GATE_S; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, nHeight, nPrestId, -1, false);
        }
        
        // 生成东西边界
        for (int i = 1; i < nHeight; ++i) {
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, i, 
                D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_E, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, i, 
                D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_W, -1, false);
        }
        
        // 生成四个角
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_NW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_NE, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_SW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_SE, -1, false);
    }
    
    /**
     * D2Common.0x6FD7FFA0
     * 构建库拉斯特集市
     */
    public static void buildKurastBazaar(D2DrlgLevel level) {
        if (level == null || level.getLevelCoords() == null || level.getDrlg() == null) {
            return;
        }
        
        // 计算网格尺寸（除以8再减1）
        int nWidth = level.getLevelCoords().getNWidth() / 8 - 1;
        int nHeight = level.getLevelCoords().getNHeight() / 8 - 1;
        
        // 根据 bJungleInterlink 决定 v7 和 v8 的值
        int v7, v8;
        if (level.getDrlg().getJungleInterlink() != 0) {
            v7 = level.getLevelCoords().getNWidth() / 8 - 2;
            v8 = 1;
        } else {
            v7 = 1;
            v8 = level.getLevelCoords().getNWidth() / 8 - 2;
        }
        
        // 生成南北边界
        for (int i = 1; i < nWidth; ++i) {
            int nPrestIdN = D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_N;
            if (i == v7) {
                nPrestIdN = D2LvlPrestIds.LVLPREST_ACT3_BURBS_GATE_N; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, 0, nPrestIdN, -1, false);
            
            int nPrestIdS = D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_S;
            if (i == v8) {
                nPrestIdS = D2LvlPrestIds.LVLPREST_ACT3_BURBS_GATE_S; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, nHeight, nPrestIdS, -1, false);
        }
        
        // 生成东西边界
        for (int i = 1; i < nHeight; ++i) {
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, i, 
                D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_E, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, i, 
                D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_W, -1, false);
        }
        
        // 生成四个角
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_NW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_NE, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_SW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_SE, -1, false);
    }
    
    /**
     * D2Common.0x6FD800E0
     * 构建上库拉斯特
     */
    public static void buildUpperKurast(D2DrlgLevel level) {
        if (level == null || level.getLevelCoords() == null || level.getDrlg() == null) {
            return;
        }
        
        // 计算网格尺寸（除以8再减1）
        int nWidth = level.getLevelCoords().getNWidth() / 8 - 1;
        int nHeight = level.getLevelCoords().getNHeight() / 8 - 1;
        
        // 根据 bJungleInterlink 决定 v8 的值
        int v8;
        if (level.getDrlg().getJungleInterlink() != 0) {
            v8 = nWidth - 1;
        } else {
            v8 = 1;
        }
        
        // 生成北边界（顶部），中间位置跳过
        for (int i = 1; i < nWidth; i += (i == nWidth / 2 ? 1 : 0) + 1) {
            int nPrestId = D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_N;
            if (i == nWidth / 2) {
                nPrestId = D2LvlPrestIds.LVLPREST_ACT3_METRO_GATE_N; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, 0, nPrestId, -1, false);
        }
        
        // 生成南边界（底部）
        for (int i = 1; i < nWidth; ++i) {
            int nPrestId = D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_S;
            if (i == v8) {
                nPrestId = D2LvlPrestIds.LVLPREST_ACT3_METRO_GATE_S; // 门
            }
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, nHeight, nPrestId, -1, false);
        }
        
        // 生成东西边界
        for (int i = 1; i < nHeight; ++i) {
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, i, 
                D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_E, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, i, 
                D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_W, -1, false);
        }
        
        // 生成四个角
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_NW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, 0, 
            D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_NE, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_SW, -1, false);
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nWidth, nHeight, 
            D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_SE, -1, false);
    }
    
    /**
     * D2Common.0x6FD80230
     * 生成随机预设
     */
    public static void spawnRandomPreset(D2DrlgLevel level, int nLevelPrestId1, 
            int nLevelPrestId2, int a4) {
        if (level == null || level.getPresetOrOutdoorsOrMaze() == null || 
            !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        
        // 计算变体数量
        int nVariants = nLevelPrestId2 - nLevelPrestId1 + 1;
        int v34 = 0; // 已生成的预设数量
        
        int v8 = outdoors.getNGridWidth() * outdoors.getNGridHeight();
        
        if (v8 > 0) {
            // 分配坐标数组（每个坐标需要2个int：x和y）
            Object memPool = level.getDrlg().getMempool();
            int[] v11 = D2Pool.callocIntArrayPool(memPool, 2 * v8);
            
            if (v11 == null) {
                D2Log.warning("DRLGOUTJUNG_SpawnRandomPreset: Failed to allocate coordinate array");
                return;
            }
            
            // 初始化坐标数组
            for (int i = 0; i < v8; ++i) {
                int v12 = i / outdoors.getNGridWidth();
                int v13 = i % outdoors.getNGridWidth();
                
                v11[2 * i] = v13;
                v11[2 * i + 1] = v12;
            }
            
            // 随机打乱坐标数组
            for (int i = 0; i < v8; ++i) {
                int v14 = Seed.rollLimitedRandomNumber(level.getSeed(), v8);
                int v19 = Seed.rollLimitedRandomNumber(level.getSeed(), v8);
                
                // 交换两个坐标
                int v23 = v11[2 * v14];
                int v24 = v11[2 * v14 + 1];
                
                v11[2 * v14] = v11[2 * v19];
                v11[2 * v14 + 1] = v11[2 * v19 + 1];
                
                v11[2 * v19] = v23;
                v11[2 * v19 + 1] = v24;
            }
            
            // 尝试在每个坐标位置生成预设
            for (int i = 0; i < v8; ++i) {
                int a2b = v11[2 * i];
                int a3a = v11[2 * i + 1];
                
                // 随机选择一个变体
                int v26 = Seed.rollLimitedRandomNumber(level.getSeed(), nVariants);
                int nPrestId = v26 + nLevelPrestId1;
                
                // 测试是否可以放置预设
                if (DrlgOutdoors.testOutdoorLevelPreset(level, a2b, a3a, nPrestId, 0, (byte)15)) {
                    DrlgOutdoors.spawnOutdoorLevelPresetEx(level, a2b, a3a, nPrestId, -1, false);
                    
                    ++v34;
                    
                    // 如果指定了最大数量且已达到，则停止
                    if (a4 > 0 && v34 >= a4) {
                        break;
                    }
                }
            }
            
            // 释放坐标数组
            D2Pool.freePool(memPool, v11);
        }
    }
}
