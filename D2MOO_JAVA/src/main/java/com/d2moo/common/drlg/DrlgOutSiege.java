package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LvlPrestTxt;
import com.d2moo.common.seed.Seed;

/**
 * Drlg 户外围攻模块
 * 对应 C++ 文件：DrlgOutSiege.cpp
 */
public class DrlgOutSiege {
    
    // 坐标结构（用于边界追踪）
    private static class D2CoordStrc {
        int nX;
        int nY;
        
        D2CoordStrc(int x, int y) {
            nX = x;
            nY = y;
        }
    }
    
    private static final D2CoordStrc[] stru_6FDD09C8 = {
        new D2CoordStrc(-1, 0),
        new D2CoordStrc(0, -1),
        new D2CoordStrc(1, 0),
        new D2CoordStrc(0, 1),
        new D2CoordStrc(0, -1),
        new D2CoordStrc(1, 0),
        new D2CoordStrc(0, 1),
        new D2CoordStrc(-1, 0),
        new D2CoordStrc(-1, 0),
        new D2CoordStrc(0, -1),
        new D2CoordStrc(1, 0),
        new D2CoordStrc(0, 1),
    };
    
    /**
     * D2Common.0x6FD84100
     * 获取查找ID
     * 根据关卡ID返回查找ID（TUNDRAWASTELANDS 返回 5，其他返回 4）
     */
    private static int sub_6FD84100(D2DrlgLevel level) {
        return (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) ? 5 : 4;
    }
    
    /**
     * D2Common.0x6FD846C0
     * 设置路障关卡的特殊链接标志
     */
    private static void sub_6FD846C0(D2DrlgLevel level) {
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc();
        tPackedInfo.setBLvlLink(true);
        
        D2DrlgGridStrc pGrid = pOutdoors.getPGrid(2);
        DrlgDrlgGrid.alterGridFlag(pGrid, pOutdoors.getNGridWidth() - 2, 
                pOutdoors.getNGridHeight() - 4, tPackedInfo.getNPackedValue(), 
                DrlgDrlgGrid.FlagOperation.OR);
        DrlgDrlgGrid.alterGridFlag(pGrid, pOutdoors.getNGridWidth() - 2, 
                pOutdoors.getNGridHeight() - 3, tPackedInfo.getNPackedValue(), 
                DrlgDrlgGrid.FlagOperation.OR);
    }
    
    /**
     * D2Common.0x6FD84110
     * 初始化 Act5 户外关卡
     * 被 DrlgOutdoors 依赖
     * 
     * 功能：
     * 1. 处理血腥丘陵的特殊逻辑（生成围攻到城镇的预设）
     * 2. 设置网格链接标志
     * 3. 遍历顶点，生成预设
     * 4. 处理路障关卡的特殊逻辑
     * 5. 处理边界预设（悬崖和峡谷）
     * 6. 调用其他辅助函数（入口/出口、洞穴、连接、次要边界、监狱、特殊预设）
     */
    public static void initAct5OutdoorLevel(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        
        // 处理血腥丘陵关卡的特殊逻辑
        if (level.getLevelId() == D2LevelIds.LEVEL_BLOODYFOOTHILLS) {
            D2LvlPrestTxt pLvlPrestTxtRecord = DataTbls.getLvlPrestTxtRecord(D2LvlPrestIds.LVLPREST_ACT5_SIEGE_TO_TOWN);
            if (pLvlPrestTxtRecord == null) {
                D2Log.warning("DRLGOUTSIEGE_InitAct5OutdoorLevel: Failed to get LvlPrestTxt record for LVLPREST_ACT5_SIEGE_TO_TOWN");
                return;
            }
            
            int nSize = pOutdoors.getNGridWidth() - pLvlPrestTxtRecord.getDwSizeX() / 8;
            for (int i = 0; i < 15; ++i) {
                if (nSize < 0) {
                    D2Log.warning("DRLGOUTSIEGE_InitAct5OutdoorLevel: Siege Level is the wrong size");
                    break;
                }
                
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nSize, 0, 
                        i + D2LvlPrestIds.LVLPREST_ACT5_SIEGE_TO_TOWN, 0, false);
                nSize -= pLvlPrestTxtRecord.getDwSizeX() / 8;
            }
        } else {
            // 设置网格链接标志
            DrlgOutPlace.setOutGridLinkFlags(level);
            
            // 遍历顶点生成预设
            D2DrlgVertexStrc pPreviousVertex = pOutdoors.getPVertex();
            if (pPreviousVertex == null) {
                return;
            }
            
            D2DrlgVertexStrc pDrlgVertex = pPreviousVertex.getPNext();
            if (pDrlgVertex == null) {
                return;
            }
            
            int nLookupId = sub_6FD84100(level);
            int[] pDiffX = new int[1];
            int[] pDiffY = new int[1];
            
            do {
                DrlgDrlgVer.getCoordDiff(pPreviousVertex, pDiffX, pDiffY);
                int nPreviousDiffX = pDiffX[0];
                int nPreviousDiffY = pDiffY[0];
                
                DrlgDrlgVer.getCoordDiff(pDrlgVertex, pDiffX, pDiffY);
                int nCurrentDiffX = pDiffX[0];
                int nCurrentDiffY = pDiffY[0];
                
                int nPreviousDiffXAbs = Math.abs(nPreviousDiffX);
                int nPreviousDiffYAbs = Math.abs(nPreviousDiffY);
                
                int nPreviousX = pPreviousVertex.getNPosX() & 0xFFFFFFFE;
                int nPreviousY = pPreviousVertex.getNPosY() & 0xFFFFFFFE;
                
                int nCurrentX = pDrlgVertex.getNPosX() & 0xFFFFFFFE;
                int nCurrentY = pDrlgVertex.getNPosY() & 0xFFFFFFFE;
                
                int nLevelPrestId = DrlgOutPlace.sub_6FD80BE0(nPreviousDiffX, nPreviousDiffY, nLookupId);
                
                // 如果顶点标志没有设置（标志位 2），生成预设
                if ((pPreviousVertex.getDwFlags() & 2) == 0) {
                    while (nPreviousX != nCurrentX || nPreviousY != nCurrentY) {
                        nPreviousX += 2 * nPreviousDiffX;
                        nPreviousY += 2 * nPreviousDiffY;
                        
                        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nPreviousX, nPreviousY, nLevelPrestId, -1, false);
                        
                        // 设置网格标志
                        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc();
                        tPackedInfo.setNUnkb00(true);
                        D2DrlgGridStrc pGrid = pOutdoors.getPGrid(2);
                        DrlgDrlgGrid.alterGridFlag(pGrid, nPreviousX, nPreviousY, 
                                tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                    }
                }
                
                // 如果顶点标志设置了（标志位 1），设置关卡链接标志
                if ((pPreviousVertex.getDwFlags() & 1) != 0) {
                    int nX = Math.max(pPreviousVertex.getNPosX(), pDrlgVertex.getNPosX());
                    int nXCapped = (nX - 4 * nPreviousDiffXAbs) & 0xFFFFFFFE;
                    int nY = Math.max(pPreviousVertex.getNPosY(), pDrlgVertex.getNPosY());
                    int nYCapped = (nY - 4 * nPreviousDiffYAbs) & 0xFFFFFFFE;
                    
                    D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc();
                    tPackedInfo.setBLvlLink(true);
                    D2DrlgGridStrc pGrid = pOutdoors.getPGrid(2);
                    DrlgDrlgGrid.alterGridFlag(pGrid, nXCapped, nYCapped, 
                            tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                    DrlgDrlgGrid.alterGridFlag(pGrid, nXCapped + 2 * nPreviousDiffXAbs, 
                            nYCapped + 2 * nPreviousDiffYAbs, tPackedInfo.getNPackedValue(), 
                            DrlgDrlgGrid.FlagOperation.OR);
                }
                
                // 根据两个顶点的差值生成预设
                int nRand = DrlgOutPlace.sub_6FD80C10(2 * nPreviousDiffX, 2 * nPreviousDiffY, 
                        2 * nCurrentDiffX, 2 * nCurrentDiffY, nLookupId);
                if (nRand != 0) {
                    DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nCurrentX, nCurrentY, nRand, -1, false);
                    
                    // 设置网格标志
                    D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc();
                    tPackedInfo.setNUnkb00(true);
                    D2DrlgGridStrc pGrid = pOutdoors.getPGrid(2);
                    DrlgDrlgGrid.alterGridFlag(pGrid, nCurrentX, nCurrentY, 
                            tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                }
                
                pPreviousVertex = pDrlgVertex;
                pDrlgVertex = pDrlgVertex.getPNext();
            } while (pPreviousVertex != null && pDrlgVertex != null && pPreviousVertex != pOutdoors.getPVertex());
            
            // 处理路障关卡的特殊逻辑
            if (level.getLevelId() == D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1) {
                sub_6FD846C0(level);
            }
            
            // 处理边界预设（从悬崖边界转换为峡谷边界）
            int nLastX = pOutdoors.getNGridWidth() - 2;
            int nLastY = pOutdoors.getNGridHeight() - 2;
            int nX = nLastX;
            int nY = 0;
            
            int nLevelPrestIdCliff = (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) 
                    ? D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1_SNOW 
                    : D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1;
            int nLevelPrestIdRavine = (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) 
                    ? D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_1_SNOW 
                    : D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_1;
            
            D2DrlgGridStrc pGrid = pOutdoors.getPGrid(0);
            while (nX != 0 || nY != nLastY) {
                int nGridEntry = DrlgDrlgGrid.getGridEntry(pGrid, nX, nY);
                int nIndex = nGridEntry - nLevelPrestIdCliff;
                
                if (nIndex >= 0 && nIndex < stru_6FDD09C8.length) {
                    DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, 
                            nIndex + nLevelPrestIdRavine, -1, false);
                    
                    nX += 2 * stru_6FDD09C8[nIndex].nX;
                    nY += 2 * stru_6FDD09C8[nIndex].nY;
                } else {
                    break; // 防止无限循环
                }
            }
            
            // 放置角落预设
            int nCornerPreset1 = (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) 
                    ? D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_RAVINE_BORDER_7_SNOW 
                    : D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_RAVINE_BORDER_7;
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, pOutdoors.getNGridWidth() - 2, 0, 
                    nCornerPreset1, -1, false);
            
            int nCornerPreset2 = (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) 
                    ? D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_CLIFF_BORDER_5_SNOW 
                    : D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_CLIFF_BORDER_5;
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, pOutdoors.getNGridHeight() - 2, 
                    nCornerPreset2, -1, false);
            
            // 调用其他辅助函数
            placeBarricadeEntrancesAndExits(level);
            placeCaves(level);
            
            if (level.getLevelId() == D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1) {
                connectBarricadeAndSiege(level);
            }
            
            addAct5SecondaryBorder(level);
            placePrisons(level);
            placeSpecialPresets(level);
        }
        
        D2Log.debug("DRLGOUTSIEGE_InitAct5OutdoorLevel: Act5 outdoor level initialized successfully");
    }
    
    // Act5 洞穴放置配置结构
    private static class D2DrlgOutSiegeInitStrc {
        int nLevelId;
        int field_4;
        int field_8;
        int nLevelPrestId1;
        int nLevelPrestId2;
        
        D2DrlgOutSiegeInitStrc(int levelId, int f4, int f8, int prestId1, int prestId2) {
            nLevelId = levelId;
            field_4 = f4;
            field_8 = f8;
            nLevelPrestId1 = prestId1;
            nLevelPrestId2 = prestId2;
        }
    }
    
    private static final D2DrlgOutSiegeInitStrc[] stru_6FDD0988 = {
        new D2DrlgOutSiegeInitStrc(D2LevelIds.LEVEL_ARREATPLATEAU, 0, 0, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_CAVE_32X16, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_CAVE_16X32),
        new D2DrlgOutSiegeInitStrc(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 0, 1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FROM_CAVE_32X16_SNOW, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FROM_CAVE_16X32_SNOW),
        new D2DrlgOutSiegeInitStrc(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 0, 0, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_CAVE_32X16_SNOW, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_CAVE_16X32_SNOW),
    };
    
    /**
     * D2Common.0x6FD844F0
     * 放置洞穴
     * 根据关卡ID和尺寸放置对应的洞穴预设
     * 
     * @param level 关卡
     */
    public static void placeCaves(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        int nLevelPrestId = 0;
        int nX = 0;
        int nY = 0;
        
        for (int i = 0; i < stru_6FDD0988.length; ++i) {
            if (level.getLevelId() == stru_6FDD0988[i].nLevelId) {
                // 根据关卡宽度和高度选择预设
                if (level.getLevelCoords().getNWidth() <= level.getLevelCoords().getNHeight()) {
                    nLevelPrestId = stru_6FDD0988[i].nLevelPrestId1;
                    nX = 2;
                    if (stru_6FDD0988[i].field_8 != 0) {
                        nY = pOutdoors.getNGridHeight() - 2;
                    } else {
                        nY = 0;
                    }
                } else {
                    nLevelPrestId = stru_6FDD0988[i].nLevelPrestId2;
                    nY = 2;
                    if (stru_6FDD0988[i].field_8 != 0) {
                        nX = pOutdoors.getNGridWidth() - 2;
                    } else {
                        nX = 0;
                    }
                }
                
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, nLevelPrestId, stru_6FDD0988[i].field_4, false);
            }
        }
    }
    
    /**
     * D2Common.0x6FD84580
     * 放置路障入口和出口
     * 在四个边界查找关卡链接标志，放置对应的入口和出口预设
     * 
     * @param level 关卡
     */
    public static void placeBarricadeEntrancesAndExits(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        int nRand = (level.getLevelId() == D2LevelIds.LEVEL_BLOODYFOOTHILLS) ? 1 : -1;
        
        // 查找顶部边界（Y=0）的关卡链接
        for (int i = 0; i < pOutdoors.getNGridHeight(); ++i) {
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = DrlgOutdoors.getPackedGrid2Info(pOutdoors, i, 0);
            if (packedInfo != null && packedInfo.isBLvlLink()) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, 0, 
                        D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_EXIT_32X16, 2 * nRand, false);
                break;
            }
        }
        
        // 查找底部边界（Y=nGridHeight-2）的关卡链接
        for (int i = 0; i < pOutdoors.getNGridHeight(); ++i) {
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = DrlgOutdoors.getPackedGrid2Info(pOutdoors, i, pOutdoors.getNGridHeight() - 2);
            if (packedInfo != null && packedInfo.isBLvlLink()) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, i, pOutdoors.getNGridHeight() - 2, 
                        D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_ENTRANCE_32X16, 2 * nRand, false);
                break;
            }
        }
        
        // 查找左侧边界（X=0）的关卡链接
        for (int i = 0; i < pOutdoors.getNGridHeight(); ++i) {
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = DrlgOutdoors.getPackedGrid2Info(pOutdoors, 0, i);
            if (packedInfo != null && packedInfo.isBLvlLink()) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, i, 
                        D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_EXIT_16X32, 2 * nRand, false);
                break;
            }
        }
        
        // 查找右侧边界（X=nGridWidth-2）的关卡链接
        for (int i = 0; i < pOutdoors.getNGridHeight(); ++i) {
            D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = DrlgOutdoors.getPackedGrid2Info(pOutdoors, pOutdoors.getNGridWidth() - 2, i);
            if (packedInfo != null && packedInfo.isBLvlLink()) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, pOutdoors.getNGridWidth() - 2, i, 
                        D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_ENTRANCE_16X32, 2 * nRand, false);
                break;
            }
        }
    }
    
    /**
     * D2Common.0x6FD84700
     * 添加 Act5 次要边界
     * 对应 C++ DRLGOUTSIEGE_AddACt5SecondaryBorder
     * 
     * 功能：
     * 1. 构造 D2UnkOutdoorStrc 结构
     * 2. 调用 DrlgTileSub.addSecondaryBorder 添加次要边界
     * 3. 使用 Act5 的 LvlSubId（LVLSUB_ACT5_BARRICADE = 2）
     * 
     * 注意：Act5 不使用 nLevelPrestId，而是使用回调函数 sub_6FD84820 和 sub_6FD84780
     */
    public static void addAct5SecondaryBorder(D2DrlgLevel level) {
        if (level == null || level.getLevelId() == D2LevelIds.LEVEL_BLOODYFOOTHILLS) {
            return;
        }
        
        if (!(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        
        // 构造 D2UnkOutdoorStrc 结构（参考 C++ 实现）
        D2UnkOutdoorStrc a1 = new D2UnkOutdoorStrc();
        a1.setPLevel(level);
        // field_4 = [nWidth, nHeight, nGridWidth, nGridHeight]
        a1.setField_4(new int[] { 
            outdoors.getNWidth(), 
            outdoors.getNHeight(), 
            outdoors.getNGridWidth(), 
            outdoors.getNGridHeight() 
        });
        a1.setPGrid1(outdoors.getPGrid(0));
        a1.setPGrid2(outdoors.getPGrid(2));
        // Act5 不使用 nLevelPrestId，设置为 -1
        a1.setField_14(-1);
        // Act5 次要边界的 LvlSubId（LVLSUB_ACT5_BARRICADE = 2，从枚举值计算）
        // LVLSUB_ACT5_BARRICADE 是第 3 个值（从 0 开始），所以是 2
        a1.setNLvlSubId(2); // LVLSUB_ACT5_BARRICADE
        // 使用 Act5 特定的回调函数
        a1.setField_24(DrlgOutSiege::sub_6FD84820);
        a1.setField_28(DrlgOutSiege::sub_6FD84780);
        a1.setField_2C(DrlgOutdoors::alterAdjacentPresetGridCells);
        a1.setField_30(DrlgOutdoors::setBlankGridCell);
        a1.setField_34((levelParam, x, y, levelPrestId, rand, a6) -> {
            DrlgOutdoors.spawnOutdoorLevelPresetEx(levelParam, x, y, levelPrestId, rand, a6);
        });
        
        // 调用 DrlgTileSub.addSecondaryBorder 添加次要边界
        DrlgTileSub.addSecondaryBorder(a1);
    }
    
    // Act5 次要边界配置结构
    private static class D2DrlgOutSiegeInitStrc2 {
        int nStyle;
        int field_4;
        int field_8;
        int nLevelPrestId1;
        int nLevelPrestId2;
        
        D2DrlgOutSiegeInitStrc2(int style, int f4, int f8, int prestId1, int prestId2) {
            nStyle = style;
            field_4 = f4;
            field_8 = f8;
            nLevelPrestId1 = prestId1;
            nLevelPrestId2 = prestId2;
        }
    }
    
    private static final D2DrlgOutSiegeInitStrc2[] stru_6FDD0A28 = {
        new D2DrlgOutSiegeInitStrc2(49, 1, 16, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1_SNOW),
        new D2DrlgOutSiegeInitStrc2(49, 31, 46, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 1, 1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_3, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_3_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 2, 3, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 4, 4, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_4, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_4_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 5, 5, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_3, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_3_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 6, 7, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_1_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 8, 8, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_4, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_4_SNOW),
        new D2DrlgOutSiegeInitStrc2(48, 30, 30, 0, 0),
        new D2DrlgOutSiegeInitStrc2(48, 31, 31, -5, -5),
    };
    
    /**
     * D2Common.0x6FD84780
     * 根据风格和序列获取预设 ID
     * 对应 C++ sub_6FD84780
     * 
     * @param level 关卡
     * @param nStyle 风格（48 或 49）
     * @param a3 序列值
     * @return 预设 ID，如果返回 -5 表示跳过
     */
    private static int sub_6FD84780(D2DrlgLevel level, int nStyle, int a3) {
        // 使用类级别的配置数组 stru_6FDD0A28
        
        // 断言：风格必须是 48 或 49
        if (nStyle != 48 && nStyle != 49) {
            D2Log.warning("sub_6FD84780: Invalid style " + nStyle + ", expected 48 or 49");
            return -5;
        }
        
        // 查找匹配的配置
        for (D2DrlgOutSiegeInitStrc2 config : stru_6FDD0A28) {
            if (nStyle == config.nStyle && a3 >= config.field_4 && a3 <= config.field_8) {
                if (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) {
                    return a3 + config.nLevelPrestId2 - config.field_4;
                } else {
                    return a3 + config.nLevelPrestId1 - config.field_4;
                }
            }
        }
        
        // 如果没有找到匹配的配置，返回 -5（跳过）
        D2Log.warning("sub_6FD84780: No matching config for style " + nStyle + ", sequence " + a3);
        return -5;
    }
    
    /**
     * D2Common.0x6FD84820
     * 测试是否可以放置预设
     * 对应 C++ sub_6FD84820
     * 
     * @param level 关卡
     * @param nX X 坐标
     * @param nY Y 坐标
     * @param a4 预设 ID
     * @param a5 未使用
     * @param a6 打包信息
     * @return 如果可以放置返回 true，否则返回 false
     */
    private static boolean sub_6FD84820(D2DrlgLevel level, int nX, int nY, int a4, int a5, int a6) {
        // 从 a6 中提取风格和序列
        int nStyle = (a6 >> 20) & 0x3F;
        int nSequence = (a6 >> 8) & 0xFF;
        
        // 调用 sub_6FD84780 获取预设 ID
        int v8 = sub_6FD84780(level, nStyle, nSequence);
        
        if (v8 == -5) {
            // 返回 -5 表示跳过
            return true;
        } else if (v8 == a4) {
            // 如果预设 ID 匹配，检查网格单元格是否不是关卡链接
            return DrlgOutdoors.testGridCellNonLvlLink(level, nX, nY) != 0;
        }
        
        return false;
    }
    
    // Act5 特殊预设配置结构
    private static class D2DrlgOutSiegeInitStrc3 {
        int nLevelId;
        int nLevelPrestId1;
        int nLevelPrestId2;
        int field_C;
        int field_10;
        int field_14;
        boolean bMustHave;
        
        D2DrlgOutSiegeInitStrc3(int levelId, int prestId1, int prestId2, int fC, int f10, int f14, boolean mustHave) {
            nLevelId = levelId;
            nLevelPrestId1 = prestId1;
            nLevelPrestId2 = prestId2;
            field_C = fC;
            field_10 = f10;
            field_14 = f14;
            bMustHave = mustHave;
        }
    }
    
    private static final D2DrlgOutSiegeInitStrc3[] stru_6FDD0AF8 = {
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_N, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_W, 0, 25, 1, true),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ARREATPLATEAU, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_N, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_W, 0, 25, 1, true),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_N, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_HELL_PORTAL_W, 1, 25, 1, true),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ARREATPLATEAU, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_WAYPOINT_DIRT, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_WAYPOINT_DIRT, -1, 25, 1, true),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_WAYPOINT_SNOW, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_WAYPOINT_SNOW, -1, 25, 1, true),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_N_TREASURE, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_W_TREASURE, -1, 10, 1, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_N_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_W_1, -1, 20, 4, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_N_2, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RUINS_W_2, -1, 20, 4, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ARREATPLATEAU, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FILLER_TREASURE, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FILLER_TREASURE, -1, 10, 1, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ARREATPLATEAU, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_BUILDING, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_BUILDING, -1, 8, 1, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_ARREATPLATEAU, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FILLER, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_FILLER, -1, 15, 5, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_LAKE_1, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_LAKE_1, -1, 9, 4, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_LAKE_2, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_LAKE_2, -1, 9, 4, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_OTHER, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_OTHER, -1, 9, 4, false),
        new D2DrlgOutSiegeInitStrc3(D2LevelIds.LEVEL_TUNDRAWASTELANDS, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_TREASURE, 
                D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_SNOW_TREASURE, -1, 5, 3, false),
    };
    
    /**
     * D2Common.0x6FD84870
     * 放置特殊预设
     * 根据关卡ID和尺寸放置对应的特殊预设
     * 
     * @param level 关卡
     */
    public static void placeSpecialPresets(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        int nLevelPrestId = 0;
        boolean bAdded = false;
        
        for (int i = 0; i < stru_6FDD0AF8.length; ++i) {
            if (level.getLevelId() == stru_6FDD0AF8[i].nLevelId) {
                // 根据关卡宽度和高度选择预设
                if (level.getLevelCoords().getNWidth() < level.getLevelCoords().getNHeight()) {
                    nLevelPrestId = stru_6FDD0AF8[i].nLevelPrestId1;
                } else {
                    nLevelPrestId = stru_6FDD0AF8[i].nLevelPrestId2;
                }
                
                bAdded = false;
                
                // 尝试生成指定次数的预设
                for (int j = 0; j < stru_6FDD0AF8[i].field_14; ++j) {
                    bAdded = DrlgOutdoors.spawnOutdoorLevelPreset(level, nLevelPrestId, 
                            stru_6FDD0AF8[i].field_C, 0, (char)15);
                }
                
                // 如果必须生成但未成功，记录警告
                if (!bAdded && stru_6FDD0AF8[i].bMustHave) {
                    D2Log.warning("DRLGOUTSIEGE_PlaceSpecialPresets: Failed to place required preset for level: " + level.getLevelId());
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD84910
     * 放置监狱
     * 在路障关卡中放置3个监狱预设
     * 
     * @param level 关卡
     */
    public static void placePrisons(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        if (level.getLevelId() != D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        int nPrisonsPlaced = 0;
        final int nPrisonsToPlace = 3;
        
        // 首先尝试随机放置（最多90次尝试）
        for (int nAttemptNumber = 0; nAttemptNumber < 90 && nPrisonsPlaced < nPrisonsToPlace; ++nAttemptNumber) {
            int nRandX = 2 * Seed.rollLimitedRandomNumber(level.getSeed(), pOutdoors.getNGridWidth() / 2);
            int nRandY = 2 * Seed.rollLimitedRandomNumber(level.getSeed(), pOutdoors.getNGridHeight() / 2);
            
            int nLevelPrestId = DrlgOutdoors.getPresetIndexFromGridCell(level, nRandX, nRandY);
            if (nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1 
                    && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_8) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nRandX, nRandY, nLevelPrestId + 16, -1, false);
                nPrisonsPlaced++;
            }
        }
        
        // 如果还没放置够，遍历整个网格
        if (nPrisonsPlaced < nPrisonsToPlace) {
            int nRandX = 2 * Seed.rollLimitedRandomNumber(level.getSeed(), pOutdoors.getNGridWidth() / 2);
            int nRandY = 2 * Seed.rollLimitedRandomNumber(level.getSeed(), pOutdoors.getNGridHeight() / 2);
            
            for (int j = 0; j < pOutdoors.getNGridHeight() && nPrisonsPlaced < nPrisonsToPlace; ++j) {
                for (int i = 0; i < pOutdoors.getNGridWidth() && nPrisonsPlaced < nPrisonsToPlace; ++i) {
                    int nX = (i + nRandX) % pOutdoors.getNGridWidth();
                    int nY = (j + nRandY) % pOutdoors.getNGridHeight();
                    
                    int nLevelPrestId = DrlgOutdoors.getPresetIndexFromGridCell(level, nX, nY);
                    if (nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_1 
                            && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_8) {
                        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, nLevelPrestId + 16, -1, false);
                        nPrisonsPlaced++;
                    }
                }
            }
        }
        
        if (nPrisonsPlaced < nPrisonsToPlace) {
            D2Log.warning("DRLGOUTSIEGE_PlacePrisons: Could not place enough prisons for quest 2. Placed: " + nPrisonsPlaced + "/" + nPrisonsToPlace);
        }
    }
    
    /**
     * D2Common.0x6FD84BB0
     * 连接路障和围攻
     * 在路障关卡中放置连接预设
     * 
     * @param level 关卡
     */
    public static void connectBarricadeAndSiege(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        if (level.getLevelId() != D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1) {
            return;
        }
        
        Object presetOrOutdoorsOrMaze = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        
        // 获取预设文本记录以获取尺寸
        D2LvlPrestTxt pLvlPrestTxtRecord = DataTbls.getLvlPrestTxtRecord(D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_SIEGE);
        if (pLvlPrestTxtRecord == null) {
            D2Log.warning("DRLGOUTSIEGE_ConnectBarricadeAndSiege: Failed to get LvlPrestTxt record");
            return;
        }
        
        int nX = pOutdoors.getNGridWidth() - pLvlPrestTxtRecord.getDwSizeX() / 8;
        int nY = pOutdoors.getNGridHeight() - pLvlPrestTxtRecord.getDwSizeY() / 8;
        
        // 放置连接预设
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_TO_SIEGE, -1, false);
        // 放置峡谷边界预设
        DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY - 2, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_RAVINE_BORDER_4, -1, false);
    }
}
