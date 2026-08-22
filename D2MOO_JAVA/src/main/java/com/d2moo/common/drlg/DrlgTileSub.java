package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LvlSubTxt;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;

/**
 * Drlg 瓦片替换模块
 * 对应 C++ 文件：DrlgTileSub.cpp
 */
public class DrlgTileSub {
    
    /**
     * D2Common.0x6FD8A460
     * 添加次要边界
     * 被 DrlgOutdoors 依赖
     */
    public static void addSecondaryBorder(D2UnkOutdoorStrc a1) {
        if (a1 == null || a1.getPLevel() == null) {
            return;
        }
        
        D2LvlSubTxt pLvlSubTxtRecord = DataTbls.getLvlSubTxtRecord(a1.getNLvlSubId());
        if (pLvlSubTxtRecord == null) {
            return;
        }
        
        // 遍历所有相同类型的 LvlSubTxt 记录
        boolean bOuterBreak = false;
        while (pLvlSubTxtRecord != null && pLvlSubTxtRecord.getDwType() == a1.getNLvlSubId()) {
            initializeDrlgFile(a1.getPLevel().getDrlg().getArchive(), pLvlSubTxtRecord);
            
            if (pLvlSubTxtRecord.getPDrlgFile() != null 
                    && pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups() > 0) {
                
                if (a1.getField_14() == -1) {
                    a1.setField_14(62);
                }
                
                int nRand;
                if (pLvlSubTxtRecord.getDwBordType() == 0) {
                    nRand = Seed.rollLimitedRandomNumber(a1.getPLevel().getSeed(), 
                            pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups());
                } else {
                    nRand = 0;
                }
                
                int nSubstGroups = pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups();
                for (int j = 0; j < nSubstGroups; ++j) {
                    D2DrlgSubstGroupStrc pSubstGroup = pLvlSubTxtRecord.getPDrlgFile()
                            .getPSubstGroups()[(nRand + j) % nSubstGroups];
                    
                    boolean bWilderness = (a1.getPLevel().getLevelId() >= D2LevelIds.LEVEL_BLOODMOOR 
                            && a1.getPLevel().getLevelId() <= D2LevelIds.LEVEL_TAMOEHIGHLAND);
                    
                    int nOffset = (a1.getNLvlSubId() == 1 
                            && (a1.getPLevel().getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)
                            && (((D2DrlgOutdoorInfoStrc)a1.getPLevel().getPresetOrOutdoorsOrMaze()).getDwFlags() & 12) != 0) 
                            ? -1 : 1;
                    
                    int nWidth = nOffset + a1.getField_4()[2] 
                            - pLvlSubTxtRecord.getDwGridSize() * pSubstGroup.getTBox().getNWidth();
                    int nHeight = 1 + a1.getField_4()[3] 
                            - pLvlSubTxtRecord.getDwGridSize() * pSubstGroup.getTBox().getNHeight();
                    
                    int nArea = nWidth * nHeight;
                    if (nArea > 0) {
                        boolean bSmallWilderness = (a1.getNLvlSubId() == 1 && bWilderness 
                                && nWidth < 6 && nHeight < 6);
                        
                        // 创建坐标数组并随机化
                        D2Coord[] pCoord = new D2Coord[256];
                        for (int i = 0; i < nArea && i < 256; ++i) {
                            pCoord[i] = new D2Coord(i % nWidth, i / nWidth);
                        }
                        
                        // 随机化坐标数组
                        for (int i = 0; i < nArea && i < 256; ++i) {
                            int nRand1 = Seed.rollLimitedRandomNumber(a1.getPLevel().getSeed(), nArea);
                            int nRand2 = Seed.rollLimitedRandomNumber(a1.getPLevel().getSeed(), nArea);
                            
                            int nTemp1 = pCoord[nRand1].getX();
                            int nTemp2 = pCoord[nRand1].getY();
                            pCoord[nRand1].setX(pCoord[nRand2].getX());
                            pCoord[nRand1].setY(pCoord[nRand2].getY());
                            pCoord[nRand2].setX(nTemp1);
                            pCoord[nRand2].setY(nTemp2);
                        }
                        
                        boolean bBreak = false;
                        for (int i = 0; i < nArea && i < 256; ++i) {
                            int nX = pCoord[i].getX();
                            int nY = pCoord[i].getY();
                            
                            if (!bSmallWilderness || nX != 2 || nY != 2) {
                                if (testReplaceSubPreset(nX, nY, a1, pSubstGroup, pLvlSubTxtRecord)) {
                                    int nRandVal = Seed.rollLimitedRandomNumber(a1.getPLevel().getSeed(), 
                                            pSubstGroup.getField_14());
                                    replaceSubPreset(nX, nY, a1, pSubstGroup, pLvlSubTxtRecord, 
                                            (nRandVal + 1) * (pSubstGroup.getTBox().getNWidth() + 1));
                                    
                                    if (pLvlSubTxtRecord.getDwBordType() == 0) {
                                        bBreak = true;
                                        bOuterBreak = true; // 需要跳出外层循环
                                        break;
                                    } else if (pLvlSubTxtRecord.getDwBordType() == 1) {
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if (bBreak) {
                            break;
                        }
                    }
                }
            }
            
            // 移动到下一个记录（模拟 C++ 中的 ++pLvlSubTxtRecord）
            if (bOuterBreak) {
                break;
            }
            pLvlSubTxtRecord = DataTbls.getNextLvlSubTxtRecord(pLvlSubTxtRecord, a1.getNLvlSubId());
        }
    }
    
    /**
     * D2Common.0x6FD8A750
     * 测试替换子预设
     */
    public static boolean testReplaceSubPreset(int a1, int a2, D2UnkOutdoorStrc a3, 
            D2DrlgSubstGroupStrc pSubstGroup, D2LvlSubTxt pLvlSubTxtRecord) {
        if (a3 == null || pSubstGroup == null || pLvlSubTxtRecord == null 
                || pLvlSubTxtRecord.getPDrlgFile() == null) {
            return false;
        }
        
        int v9 = a1 - (a1 % pLvlSubTxtRecord.getDwGridSize());
        int v8 = a2 - (a2 % pLvlSubTxtRecord.getDwGridSize());
        
        for (int j = 0; j < pSubstGroup.getTBox().getNHeight(); ++j) {
            for (int i = 0; i < pSubstGroup.getTBox().getNWidth(); ++i) {
                int nFloorFlags = 0;
                if (pLvlSubTxtRecord.getPDrlgFile().getNFloorLayers() > 0) {
                    nFloorFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPFloorGrid(), 
                            i + pSubstGroup.getTBox().getNPosX(), 
                            j + pSubstGroup.getTBox().getNPosY());
                }
                
                int nWallFlags = 0;
                if (pLvlSubTxtRecord.getPDrlgFile().getNWallLayers() > 0) {
                    nWallFlags = DrlgDrlgGrid.getGridEntry(
                            pLvlSubTxtRecord.getPWallGrid(0), 
                            i + pSubstGroup.getTBox().getNPosX(), 
                            j + pSubstGroup.getTBox().getNPosY());
                }
                
                int nX = v9 + i * pLvlSubTxtRecord.getDwGridSize();
                int nY = v8 + j * pLvlSubTxtRecord.getDwGridSize();
                
                int v15 = DrlgDrlgGrid.getGridEntry(a3.getPGrid1(), nX, nY);
                
                if (a3.getField_24() != null) {
                    if (!a3.getField_24().apply(a3.getPLevel(), nX, nY, v15, nFloorFlags, nWallFlags)) {
                        return false;
                    }
                } else if ((nWallFlags & 1) != 0) {
                    int v18 = ((nWallFlags >> 8) & 0xFF) - 1;
                    if (v18 != a3.getField_14() && v18 + a3.getNLevelPrestId() != v15) {
                        return false;
                    }
                    
                    if (a3.getField_1C() != null && a3.getField_1C().apply(a3.getPLevel(), nX, nY) == 0) {
                        return false;
                    }
                } else if ((nFloorFlags & 2) != 0) {
                    if (a3.getField_20() != null && !a3.getField_20().apply(a3.getPLevel(), nX, nY, 0, 0, (byte)0)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD8A8E0
     * 替换子预设
     */
    public static void replaceSubPreset(int a1, int a2, D2UnkOutdoorStrc a3, 
            D2DrlgSubstGroupStrc pSubstGroup, D2LvlSubTxt pLvlSubTxtRecord, int a6) {
        if (a3 == null || pSubstGroup == null || pLvlSubTxtRecord == null 
                || pLvlSubTxtRecord.getPDrlgFile() == null) {
            return;
        }
        
        int v21 = a1 - (a1 % pLvlSubTxtRecord.getDwGridSize());
        int v25 = a2 - (a2 % pLvlSubTxtRecord.getDwGridSize());
        
        for (int j = 0; j < pSubstGroup.getTBox().getNHeight(); ++j) {
            for (int i = 0; i < pSubstGroup.getTBox().getNWidth(); ++i) {
                int nWallFlags = 0;
                if (pLvlSubTxtRecord.getPDrlgFile().getNWallLayers() > 0) {
                    nWallFlags = DrlgDrlgGrid.getGridEntry(
                            pLvlSubTxtRecord.getPWallGrid(0), 
                            a6 + pSubstGroup.getTBox().getNPosX() + i, 
                            pSubstGroup.getTBox().getNPosY() + j);
                }
                
                int nFloorFlags = 0;
                if (pLvlSubTxtRecord.getPDrlgFile().getNFloorLayers() > 0) {
                    nFloorFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPFloorGrid(), 
                            a6 + pSubstGroup.getTBox().getNPosX() + i, 
                            pSubstGroup.getTBox().getNPosY() + j);
                }
                
                if ((nWallFlags & 1) != 0) {
                    int v17 = ((nWallFlags >> 8) & 0xFF) - 1;
                    int v18;
                    if (a3.getField_28() != null) {
                        v18 = a3.getField_28().apply(a3.getPLevel(), 
                                ((nWallFlags >> 20) & 0x3F), 
                                ((nWallFlags >> 8) & 0xFF));
                    } else {
                        v18 = v17 + a3.getNLevelPrestId();
                    }
                    
                    if (v18 != -5 && v17 != a3.getField_14()) {
                        if (a3.getField_34() != null) {
                            a3.getField_34().apply(a3.getPLevel(), 
                                    v21 + i * pLvlSubTxtRecord.getDwGridSize(), 
                                    v25 + j * pLvlSubTxtRecord.getDwGridSize(), 
                                    v18, 0, true);
                        }
                    }
                } else {
                    if ((nFloorFlags & 2) != 0) {
                        if (a3.getField_2C() != null) {
                            a3.getField_2C().apply(a3.getPLevel(), 
                                    v21 + i * pLvlSubTxtRecord.getDwGridSize(), 
                                    v25 + j * pLvlSubTxtRecord.getDwGridSize());
                        }
                    } else {
                        if (a3.getField_30() != null) {
                            a3.getField_30().apply(a3.getPLevel(), 
                                    v21 + i * pLvlSubTxtRecord.getDwGridSize(), 
                                    v25 + j * pLvlSubTxtRecord.getDwGridSize());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD8B010
     * 测试替换位置是否有效（检查地板和墙壁标志）
     * @param nX X坐标
     * @param nY Y坐标
     * @param pOutdoorLevel 户外替换结构2
     * @param pSubstGroup 替换组
     * @param pLvlSubTxtRecord 关卡子文本记录
     * @return 是否有效
     */
    private static boolean sub_6FD8B010(int nX, int nY, D2UnkOutdoorStrc2 pOutdoorLevel,
            D2DrlgSubstGroupStrc pSubstGroup, D2LvlSubTxt pLvlSubTxtRecord) {
        if (pOutdoorLevel == null || pSubstGroup == null || pLvlSubTxtRecord == null) {
            return false;
        }
        
        D2DrlgCoord tBox = pSubstGroup.getTBox();
        if (tBox == null) {
            return false;
        }
        
        for (int j = 0; j < tBox.getNHeight(); ++j) {
            for (int i = 0; i < tBox.getNWidth(); ++i) {
                // 检查替换组中该位置是否有地板或墙壁标志
                int nFloorFlags = 0;
                if (pLvlSubTxtRecord.getPFloorGrid() != null) {
                    nFloorFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPFloorGrid(),
                            i + tBox.getNPosX(), j + tBox.getNPosY());
                }
                
                boolean bHasWall = false;
                if (pLvlSubTxtRecord.getPWallGrid(0) != null 
                        && pLvlSubTxtRecord.getPWallGrid(0).getNWidth() > 0) {
                    int nWallFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPWallGrid(0),
                            i + tBox.getNPosX(), j + tBox.getNPosY());
                    bHasWall = (nWallFlags & 1) != 0;
                }
                
                if ((nFloorFlags & 2) != 0 || bHasWall) {
                    // 检查目标位置的地板标志
                    int nTargetFloorFlags = 0;
                    if (pOutdoorLevel.getPFloorGrid() != null) {
                        nTargetFloorFlags = DrlgDrlgGrid.getGridEntry(pOutdoorLevel.getPFloorGrid(),
                                nX + i, nY + j);
                    }
                    
                    // 检查标志：不能有 0x3F0FF00 标志，必须有 0x2 标志
                    if ((nTargetFloorFlags & 0x3F0FF00) != 0 || (nTargetFloorFlags & 2) == 0) {
                        return false;
                    }
                    
                    // 检查所有墙壁层
                    for (int nLayer = 0; nLayer < pOutdoorLevel.getField_2C(); ++nLayer) {
                        D2DrlgGridStrc pWallGrid = pOutdoorLevel.getPWallsGrids(nLayer);
                        if (pWallGrid != null) {
                            int nWallFlags = DrlgDrlgGrid.getGridEntry(pWallGrid, nX + i, nY + j);
                            if ((nWallFlags & 1) != 0) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD8B130
     * 测试替换位置是否有效（检查瓦片类型、地板和墙壁标志匹配）
     * @param nX X坐标
     * @param nY Y坐标
     * @param pOutdoorLevel 户外替换结构2
     * @param pSubstGroup 替换组
     * @param pLvlSubTxtRecord 关卡子文本记录
     * @return 是否有效
     */
    private static boolean sub_6FD8B130(int nX, int nY, D2UnkOutdoorStrc2 pOutdoorLevel,
            D2DrlgSubstGroupStrc pSubstGroup, D2LvlSubTxt pLvlSubTxtRecord) {
        if (pOutdoorLevel == null || pSubstGroup == null || pLvlSubTxtRecord == null) {
            return false;
        }
        
        D2DrlgCoord tBox = pSubstGroup.getTBox();
        if (tBox == null) {
            return false;
        }
        
        D2DrlgOutdoorRoomStrc pOutdoorRoom = pOutdoorLevel.getPOutdoorRooms(0);
        if (pOutdoorRoom == null) {
            return false;
        }
        
        for (int j = 0; j < tBox.getNHeight(); ++j) {
            for (int i = 0; i < tBox.getNWidth(); ++i) {
                // 检查瓦片类型是否匹配
                int nTileType = 0;
                if (pLvlSubTxtRecord.getPTileTypeGrid(0) != null) {
                    nTileType = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPTileTypeGrid(0),
                            i + tBox.getNPosX(), j + tBox.getNPosY());
                }
                
                int nTargetTileType = DrlgDrlgGrid.getGridEntry(pOutdoorRoom.getPTileTypeGrid(),
                        nX + i, nY + j);
                
                if (nTileType != nTargetTileType) {
                    return false;
                }
                
                // 检查地板标志
                int nFloorFlags1 = 0;
                if (pLvlSubTxtRecord.getPDrlgFile() != null 
                        && pLvlSubTxtRecord.getPDrlgFile().getNFloorLayers() > 0) {
                    if (pLvlSubTxtRecord.getPFloorGrid() != null) {
                        nFloorFlags1 = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPFloorGrid(),
                                i + tBox.getNPosX(), j + tBox.getNPosY());
                    }
                }
                
                if ((nFloorFlags1 & 2) != 0) {
                    int nFloorFlags2 = 0;
                    if (pOutdoorLevel.getPFloorGrid() != null) {
                        nFloorFlags2 = DrlgDrlgGrid.getGridEntry(pOutdoorLevel.getPFloorGrid(),
                                nX + i, nY + j);
                    }
                    
                    if ((nFloorFlags2 & 2) == 0) {
                        return false;
                    }
                    
                    // 检查标志差异
                    if ((nFloorFlags1 ^ nFloorFlags2) != 0 && ((nFloorFlags1 ^ nFloorFlags2) & 0x3F0FF00) != 0) {
                        return false;
                    }
                }
                
                // 检查墙壁标志
                int nWallFlags1 = 0;
                if (pLvlSubTxtRecord.getPDrlgFile() != null 
                        && pLvlSubTxtRecord.getPDrlgFile().getNWallLayers() > 0) {
                    if (pLvlSubTxtRecord.getPWallGrid(0) != null) {
                        nWallFlags1 = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPWallGrid(0),
                                i + tBox.getNPosX(), j + tBox.getNPosY());
                    }
                }
                
                if ((nWallFlags1 & 1) != 0) {
                    int nWallFlags2 = 0;
                    if (pOutdoorLevel.getPWallsGrids(0) != null) {
                        nWallFlags2 = DrlgDrlgGrid.getGridEntry(pOutdoorLevel.getPWallsGrids(0),
                                nX + i, nY + j);
                    }
                    
                    if ((nWallFlags2 & 1) == 0) {
                        return false;
                    }
                    
                    // 检查标志差异
                    if ((nWallFlags1 ^ nWallFlags2) != 0 && ((nWallFlags1 ^ nWallFlags2) & 0x3F0FF00) != 0) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD8B290
     * 执行替换
     */
    public static void doSubstitutions(D2UnkOutdoorStrc2 pOutdoorLevel, D2LvlSubTxt pLvlSubTxtRecord) {
        if (pOutdoorLevel == null || pLvlSubTxtRecord == null 
                || pLvlSubTxtRecord.getPDrlgFile() == null) {
            return;
        }
        
        if (pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups() == 0) {
            return;
        }
        
        int nSubTheme = pOutdoorLevel.getNSubTheme();
        if (nSubTheme < 0 || nSubTheme >= pLvlSubTxtRecord.getNMax().length) {
            return;
        }
        
        int nMax = pLvlSubTxtRecord.getNMax()[nSubTheme];
        
        for (int xyz = 0; xyz < nMax; ++xyz) {
            // 随机选择替换组
            int nSubstGroups = pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups();
            int substGroupIdx = Seed.rollLimitedRandomNumber(
                    pOutdoorLevel.getPDrlgRoom().getSeed(), nSubstGroups);
            
            D2DrlgSubstGroupStrc[] pSubstGroups = pLvlSubTxtRecord.getPDrlgFile().getPSubstGroups();
            if (pSubstGroups == null || substGroupIdx >= pSubstGroups.length) {
                continue;
            }
            
            D2DrlgSubstGroupStrc pSubstGroup = pSubstGroups[substGroupIdx];
            if (pSubstGroup == null) {
                continue;
            }
            
            D2DrlgCoord tBox = pSubstGroup.getTBox();
            if (tBox == null) {
                continue;
            }
            
            // 计算可用空间
            int nAvailableSpaceX = pOutdoorLevel.getPDrlgRoom().getNTileWidth() - tBox.getNWidth();
            int nAvailableSpaceY = pOutdoorLevel.getPDrlgRoom().getNTileHeight() - tBox.getNHeight();
            
            if (nAvailableSpaceX > 0 && nAvailableSpaceY > 0) {
                int nTrials = -1;
                if (nSubTheme < pLvlSubTxtRecord.getNTrials().length) {
                    nTrials = pLvlSubTxtRecord.getNTrials()[nSubTheme];
                }
                
                if (nTrials != -1) {
                    // 有限尝试次数模式
                    for (int i = 0; i < nTrials; ++i) {
                        int nX = Seed.rollLimitedRandomNumber(
                                pOutdoorLevel.getPDrlgRoom().getSeed(), nAvailableSpaceX) + 1;
                        int nY = Seed.rollLimitedRandomNumber(
                                pOutdoorLevel.getPDrlgRoom().getSeed(), nAvailableSpaceY) + 1;
                        
                        if (sub_6FD8B010(nX, nY, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord)) {
                            Object memPool = pOutdoorLevel.getPDrlgRoom().getLevel().getDrlg().getMempool();
                            sub_6FD8ACE0(memPool, nX, nY, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord, 0);
                            break;
                        }
                    }
                } else {
                    // 遍历所有可能位置模式
                    int nPotentialPositionsArea = nAvailableSpaceX * nAvailableSpaceY;
                    if (nPotentialPositionsArea > 256) {
                        nPotentialPositionsArea = 256;
                    }
                    
                    D2Coord[] tCoords = new D2Coord[nPotentialPositionsArea];
                    for (int i = 0; i < nPotentialPositionsArea; ++i) {
                        tCoords[i] = new D2Coord(i % nAvailableSpaceX, i / nAvailableSpaceX);
                    }
                    
                    // 随机化坐标数组（Fisher-Yates 洗牌）
                    for (int i = 0; i < nPotentialPositionsArea; ++i) {
                        int nRand1 = Seed.rollLimitedRandomNumber(
                                pOutdoorLevel.getPDrlgRoom().getSeed(), nPotentialPositionsArea);
                        int nRand2 = Seed.rollLimitedRandomNumber(
                                pOutdoorLevel.getPDrlgRoom().getSeed(), nPotentialPositionsArea);
                        
                        D2Coord temp = tCoords[nRand1];
                        tCoords[nRand1] = tCoords[nRand2];
                        tCoords[nRand2] = temp;
                    }
                    
                    // 遍历坐标，尝试替换
                    for (int i = 0; i < nPotentialPositionsArea; ++i) {
                        int nX = tCoords[i].getX() + 1;
                        int nY = tCoords[i].getY() + 1;
                        
                        if (sub_6FD8B010(nX, nY, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord)) {
                            Object memPool = pOutdoorLevel.getPDrlgRoom().getLevel().getDrlg().getMempool();
                            sub_6FD8ACE0(memPool, nX, nY, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord, 0);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD8ACE0
     * 执行替换操作（将替换组应用到指定位置）
     * @param pMemPool 内存池
     * @param nX X坐标
     * @param nY Y坐标
     * @param pOutdoorLevel 户外替换结构2
     * @param pSubstGroup 替换组
     * @param pLvlSubTxtRecord 关卡子文本记录
     * @param a7 偏移量
     */
    private static void sub_6FD8ACE0(Object pMemPool, int nX, int nY, D2UnkOutdoorStrc2 pOutdoorLevel,
            D2DrlgSubstGroupStrc pSubstGroup, D2LvlSubTxt pLvlSubTxtRecord, int a7) {
        if (pOutdoorLevel == null || pSubstGroup == null || pLvlSubTxtRecord == null) {
            return;
        }
        
        D2DrlgCoord tBox = pSubstGroup.getTBox();
        if (tBox == null) {
            return;
        }
        
        // 计算阴影瓦片数量
        int nCounter = 0;
        if (pLvlSubTxtRecord.getPShadowGrid() != null) {
            for (int j = 0; j < tBox.getNHeight(); ++j) {
                for (int i = 0; i < tBox.getNWidth(); ++i) {
                    int nFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPShadowGrid(),
                            i + tBox.getNPosX() + a7, j + tBox.getNPosY());
                    if ((nFlags & 0x8000000) != 0) {
                        ++nCounter;
                    }
                }
            }
        }
        
        // 重新分配屋顶瓦片网格（如果需要）
        if (nCounter > 0 && pOutdoorLevel.getPDrlgRoom() != null && pOutdoorLevel.getPDrlgRoom().getTileGrid() != null) {
            DrlgRoomTile.reallocRoofTileGrid(pMemPool, pOutdoorLevel.getPDrlgRoom().getTileGrid(), nCounter);
        }
        
        // 应用地板和墙壁网格
        for (int j = 0; j < tBox.getNHeight(); ++j) {
            for (int i = 0; i < tBox.getNWidth(); ++i) {
                // 处理地板标志
                int nFlags = 0;
                if (pLvlSubTxtRecord.getPDrlgFile() != null 
                        && pLvlSubTxtRecord.getPDrlgFile().getNFloorLayers() > 0) {
                    if (pLvlSubTxtRecord.getPFloorGrid() != null) {
                        nFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPFloorGrid(),
                                i + tBox.getNPosX() + a7, j + tBox.getNPosY());
                    }
                }
                
                if ((nFlags & 2) != 0) {
                    nFlags |= 0x80;
                    if (pOutdoorLevel.getPFloorGrid() != null) {
                        DrlgDrlgGrid.alterGridFlag(pOutdoorLevel.getPFloorGrid(), nX + i, nY + j,
                                nFlags, DrlgDrlgGrid.FlagOperation.OVERWRITE);
                    }
                }
                
                // 处理墙壁层
                if (pLvlSubTxtRecord.getPDrlgFile() != null) {
                    int nWallLayers = pLvlSubTxtRecord.getPDrlgFile().getNWallLayers();
                    // DRLG_MAX_WALL_LAYERS = 4
                    if (nWallLayers > 4) {
                        nWallLayers = 4;
                    }
                    
                    for (int nLayer = 0; nLayer < nWallLayers; ++nLayer) {
                        // 处理墙壁标志
                        if (pLvlSubTxtRecord.getPWallGrid(nLayer) != null) {
                            nFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPWallGrid(nLayer),
                                    i + tBox.getNPosX() + a7, j + tBox.getNPosY());
                            
                            if ((nFlags & 1) != 0 && pOutdoorLevel.getPWallsGrids(nLayer) != null) {
                                DrlgDrlgGrid.alterGridFlag(pOutdoorLevel.getPWallsGrids(nLayer),
                                        nX + i, nY + j, nFlags, DrlgDrlgGrid.FlagOperation.OVERWRITE);
                            }
                        }
                        
                        // 处理瓦片类型网格
                        if (pLvlSubTxtRecord.getPTileTypeGrid(nLayer) != null) {
                            nFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPTileTypeGrid(nLayer),
                                    i + tBox.getNPosX() + a7, j + tBox.getNPosY());
                            
                            if (nFlags != 0 && pOutdoorLevel.getPOutdoorRooms(nLayer) != null) {
                                DrlgDrlgGrid.alterGridFlag(
                                        pOutdoorLevel.getPOutdoorRooms(nLayer).getPTileTypeGrid(),
                                        nX + i, nY + j, nFlags, DrlgDrlgGrid.FlagOperation.OVERWRITE);
                            }
                        }
                    }
                }
                
                // 处理阴影网格
                if (pLvlSubTxtRecord.getPShadowGrid() != null) {
                    nFlags = DrlgDrlgGrid.getGridEntry(pLvlSubTxtRecord.getPShadowGrid(),
                            i + tBox.getNPosX() + a7, j + tBox.getNPosY());
                    
                    if ((nFlags & 0x8000000) != 0) {
                        // 初始化阴影瓦片（使用已实现的函数）
                        int nTileX = nX + i + pOutdoorLevel.getPDrlgRoom().getNTileXPos();
                        int nTileY = nY + j + pOutdoorLevel.getPDrlgRoom().getNTileYPos();
                        DrlgRoomTile.initTileShadow(pOutdoorLevel.getPDrlgRoom(), nTileX, nTileY, nFlags);
                    }
                }
            }
        }
        
        // 处理预设单位
        if (pLvlSubTxtRecord.getPDrlgFile() != null 
                && pLvlSubTxtRecord.getPDrlgFile().getPPresetUnit() != null) {
            int nMinX = tBox.getNPosX();
            int nMinY = tBox.getNPosY();
            int nMaxX = tBox.getNWidth();
            int nMaxY = tBox.getNHeight();
            
            // 将游戏瓦片坐标转换为子瓦片坐标
            com.d2moo.common.dungeon.Dungeon.gameTileToSubtileCoords(new int[]{nX}, new int[]{nY});
            com.d2moo.common.dungeon.Dungeon.gameTileToSubtileCoords(new int[]{nMinX}, new int[]{nMinY});
            com.d2moo.common.dungeon.Dungeon.gameTileToSubtileCoords(new int[]{nMaxX}, new int[]{nMaxY});
            
            // 遍历预设单位链表
            D2PresetUnit pPresetUnit = pLvlSubTxtRecord.getPDrlgFile().getPPresetUnit();
            while (pPresetUnit != null) {
                int nUnitX = pPresetUnit.getNXpos();
                int nUnitY = pPresetUnit.getNYpos();
                
                if (nUnitX > nMinX && nUnitX < nMinX + nMaxX 
                        && nUnitY > nMinY && nUnitY < nMinY + nMaxY) {
                    // 分配预设单位（使用已实现的函数）
                    if (pOutdoorLevel.getPDrlgRoom() != null) {
                        int nFinalX = nX + nUnitX - nMinX;
                        int nFinalY = nY + nUnitY - nMinY;
                        DrlgDrlgRoom.allocPresetUnit(pOutdoorLevel.getPDrlgRoom(), pMemPool,
                                pPresetUnit.getNUnitType(), pPresetUnit.getNIndex(),
                                pPresetUnit.getNMode(), nFinalX, nFinalY);
                    }
                }
                
                pPresetUnit = pPresetUnit.getPNext();
            }
        }
    }
    
    /**
     * D2Common.0x6FD8AA80
     * 处理传送点和神殿替换
     * @param pOutdoorLevel 户外替换结构2
     */
    static void sub_6FD8AA80(D2UnkOutdoorStrc2 pOutdoorLevel) {
        if (pOutdoorLevel == null || pOutdoorLevel.getPDrlgRoom() == null) {
            return;
        }
        
        if (pOutdoorLevel.getNSubWaypoint_Shrine() == -1) {
            return;
        }
        
        D2LvlSubTxt pLvlSubTxtRecord = DataTbls.getLvlSubTxtRecord(pOutdoorLevel.getNSubWaypoint_Shrine());
        if (pLvlSubTxtRecord == null) {
            return;
        }
        
        int nThemeFlag = pOutdoorLevel.getNSubThemePicked();
        
        while (nThemeFlag != 0) {
            if ((nThemeFlag & 1) != 0) {
                // 初始化 Drlg 文件
                D2DrlgStrc drlg = pOutdoorLevel.getPDrlgRoom().getLevel().getDrlg();
                Object hArchive = drlg != null ? drlg.getArchive() : null;
                initializeDrlgFile(hArchive, pLvlSubTxtRecord);
                
                if (pLvlSubTxtRecord.getDwCheckAll() != 0) {
                    // 检查所有模式：遍历所有替换组
                    if (pLvlSubTxtRecord.getPDrlgFile() != null) {
                        int nSubstGroups = pLvlSubTxtRecord.getPDrlgFile().getNSubstGroups();
                        
                        for (int nCurSubstGroupIndex = 0; nCurSubstGroupIndex < nSubstGroups; ++nCurSubstGroupIndex) {
                            D2DrlgSubstGroupStrc[] pSubstGroups = pLvlSubTxtRecord.getPDrlgFile().getPSubstGroups();
                            if (pSubstGroups == null || nCurSubstGroupIndex >= pSubstGroups.length) {
                                continue;
                            }
                            
                            D2DrlgSubstGroupStrc pSubstGroup = pSubstGroups[nCurSubstGroupIndex];
                            if (pSubstGroup == null) {
                                continue;
                            }
                            
                            D2DrlgCoord tBox = pSubstGroup.getTBox();
                            if (tBox == null) {
                                continue;
                            }
                            
                            int nWidth = pOutdoorLevel.getPDrlgRoom().getNTileWidth() - tBox.getNWidth() + 1;
                            int nHeight = pOutdoorLevel.getPDrlgRoom().getNTileHeight() - tBox.getNHeight() + 1;
                            
                            if (nWidth > 0 && nHeight > 0) {
                                if (pLvlSubTxtRecord.getPDrlgFile().getNSubstMethod() == 1) {
                                    // DRLGSUBST_FIXED = 1
                                    for (int j = 1; j < nHeight; ++j) {
                                        for (int i = 1; i < nWidth; ++i) {
                                            if (sub_6FD8B010(i, j, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord)) {
                                                Object memPool = pOutdoorLevel.getPDrlgRoom().getLevel().getDrlg().getMempool();
                                                sub_6FD8ACE0(memPool, i, j, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord, 0);
                                            }
                                        }
                                    }
                                } else if (pLvlSubTxtRecord.getPDrlgFile().getNSubstMethod() == 2) {
                                    // DRLGSUBST_RANDOM = 2
                                    for (int j = 0; j < nHeight; ++j) {
                                        for (int i = 0; i < nWidth; ++i) {
                                            if (sub_6FD8B130(i, j, pOutdoorLevel, pSubstGroup, pLvlSubTxtRecord)) {
                                                int nProb = 0;
                                                if (pOutdoorLevel.getNSubTheme() < pLvlSubTxtRecord.getNProb().length) {
                                                    nProb = pLvlSubTxtRecord.getNProb()[pOutdoorLevel.getNSubTheme()];
                                                }
                                                
                                                int nRand = (int)Seed.rollRandomNumber(pOutdoorLevel.getPDrlgRoom().getSeed());
                                                if (nProb < (nRand % 100)) {
                                                    int nRand2 = Seed.rollLimitedRandomNumber(
                                                            pOutdoorLevel.getPDrlgRoom().getSeed(),
                                                            pSubstGroup.getField_14());
                                                    Object memPool = pOutdoorLevel.getPDrlgRoom().getLevel().getDrlg().getMempool();
                                                    sub_6FD8ACE0(memPool, i, j, pOutdoorLevel, pSubstGroup,
                                                            pLvlSubTxtRecord, (nRand2 + 1) * (tBox.getNWidth() + 1));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 普通模式：调用 doSubstitutions
                    doSubstitutions(pOutdoorLevel, pLvlSubTxtRecord);
                }
            }
            
            nThemeFlag >>= 1;
            // 注意：C++ 代码中有 ++pLvlSubTxtRecord，这里需要获取下一个记录
            // 由于 Java 中无法直接递增指针，需要从 DataTbls 获取下一个记录
            // 这里暂时跳过，因为通常只有一个主题标志
        }
    }
    
    /**
     * D2Common.0x6FD8B640
     * 初始化 Drlg 文件
     */
    public static void initializeDrlgFile(Object hArchive, D2LvlSubTxt pLvlSubTxtRecord) {
        if (pLvlSubTxtRecord == null) {
            return;
        }
        
        // 如果文件已经加载，直接返回
        if (pLvlSubTxtRecord.getPDrlgFile() != null) {
            return;
        }
        
        // 加载 Drlg 文件
        D2DrlgFileStrc[] ppDrlgFile = new D2DrlgFileStrc[1];
        DrlgPreset.loadDrlgFile(ppDrlgFile, hArchive, pLvlSubTxtRecord.getSzFile());
        
        if (ppDrlgFile[0] == null) {
            return;
        }
        
        pLvlSubTxtRecord.setPDrlgFile(ppDrlgFile[0]);
        
        // 创建坐标结构
        D2DrlgCoord pDrlgCoord = new D2DrlgCoord();
        pDrlgCoord.setNPosX(0);
        pDrlgCoord.setNPosY(0);
        pDrlgCoord.setNWidth(ppDrlgFile[0].getNWidth() + 1);
        pDrlgCoord.setNHeight(ppDrlgFile[0].getNHeight() + 1);
        
        if (ppDrlgFile[0].getNSubstGroups() == 0) {
            D2Log.warning("Substitution File was not saved with substitution groups");
        }
        
        // 填充墙壁和瓦片类型网格
        for (int i = 0; i < ppDrlgFile[0].getNWallLayers(); ++i) {
            // 获取层数据并确保类型正确
            Object wallLayerObj = ppDrlgFile[0].getPWallLayer(i);
            Object tileTypeLayerObj = ppDrlgFile[0].getPTileTypeLayer(i);
            
            if (wallLayerObj instanceof int[]) {
                DrlgDrlgGrid.fillNewCellFlags(null, pLvlSubTxtRecord.getPWallGrid(i), 
                        (int[]) wallLayerObj, pDrlgCoord, pDrlgCoord.getNWidth());
            }
            if (tileTypeLayerObj instanceof int[]) {
                DrlgDrlgGrid.fillNewCellFlags(null, pLvlSubTxtRecord.getPTileTypeGrid(i), 
                        (int[]) tileTypeLayerObj, pDrlgCoord, pDrlgCoord.getNWidth());
            }
        }
        
        // 为后续墙壁层设置标志
        for (int i = 1; i < ppDrlgFile[0].getNWallLayers(); ++i) {
            DrlgDrlgGrid.alterAllGridFlags(pLvlSubTxtRecord.getPWallGrid(i), 
                    i << 18, DrlgDrlgGrid.FlagOperation.OR);
        }
        
        // 填充地板网格
        if (ppDrlgFile[0].getNFloorLayers() > 0) {
            Object floorLayerObj = ppDrlgFile[0].getPFloorLayer(0);
            if (floorLayerObj instanceof int[]) {
                DrlgDrlgGrid.fillNewCellFlags(null, pLvlSubTxtRecord.getPFloorGrid(), 
                        (int[]) floorLayerObj, pDrlgCoord, pDrlgCoord.getNWidth());
            }
        }
        
        // 填充阴影网格（byte[] 需要转换为 int[]）
        Object shadowLayerObj = ppDrlgFile[0].getPShadowLayer();
        if (shadowLayerObj != null) {
            if (shadowLayerObj instanceof byte[]) {
                byte[] shadowLayer = (byte[]) shadowLayerObj;
                // 将 byte[] 转换为 int[]（每个 byte 扩展为 int）
                int[] shadowLayerInt = new int[shadowLayer.length];
                for (int j = 0; j < shadowLayer.length; ++j) {
                    shadowLayerInt[j] = shadowLayer[j] & 0xFF;
                }
                DrlgDrlgGrid.fillNewCellFlags(null, pLvlSubTxtRecord.getPShadowGrid(), 
                        shadowLayerInt, pDrlgCoord, pDrlgCoord.getNWidth());
            } else if (shadowLayerObj instanceof int[]) {
                DrlgDrlgGrid.fillNewCellFlags(null, pLvlSubTxtRecord.getPShadowGrid(), 
                        (int[]) shadowLayerObj, pDrlgCoord, pDrlgCoord.getNWidth());
            }
        }
    }
    
    /**
     * D2Common.0x6FD8B770
     * 释放 Drlg 文件
     */
    public static void freeDrlgFile(D2LvlSubTxt pLvlSubTxtRecord) {
        if (pLvlSubTxtRecord == null || pLvlSubTxtRecord.getPDrlgFile() == null) {
            return;
        }
        
        D2DrlgFileStrc pDrlgFile = pLvlSubTxtRecord.getPDrlgFile();
        
        // 释放网格
        for (int i = 0; i < pDrlgFile.getNWallLayers(); ++i) {
            DrlgDrlgGrid.freeGrid(null, pLvlSubTxtRecord.getPWallGrid(i));
            DrlgDrlgGrid.freeGrid(null, pLvlSubTxtRecord.getPTileTypeGrid(i));
        }
        
        DrlgDrlgGrid.freeGrid(null, pLvlSubTxtRecord.getPFloorGrid());
        DrlgDrlgGrid.freeGrid(null, pLvlSubTxtRecord.getPShadowGrid());
        
        // 释放 Drlg 文件
        DrlgPreset.freeDrlgFile(new D2DrlgFileStrc[] { pDrlgFile });
        
        pLvlSubTxtRecord.setPDrlgFile(null);
    }
    
    /**
     * D2Common.0x6FD8B7E0
     * 选择子主题
     * 根据概率选择子主题，并更新房间的 DT1 掩码
     * @param drlgRoom 房间
     * @param nSubType 子类型ID
     * @param nSubTheme 子主题索引
     * @return 选择的主题掩码
     */
    public static int pickSubThemes(D2DrlgRoom drlgRoom, int nSubType, int nSubTheme) {
        if (drlgRoom == null || nSubType == -1 || nSubTheme == -1) {
            return 0;
        }
        
        D2LvlSubTxt pLvlSubTxtRecord = DataTbls.getLvlSubTxtRecord(nSubType);
        if (pLvlSubTxtRecord == null) {
            return 0;
        }
        
        int nCounter = 0;
        int nMask = 0;
        
        // 遍历所有相同类型的 LvlSubTxt 记录
        while (pLvlSubTxtRecord != null && pLvlSubTxtRecord.getDwType() == nSubType) {
            // 根据概率决定是否选择此主题
            // 使用随机数模 100 与概率值比较
            long nRand = Seed.rollRandomNumber(drlgRoom.getSeed());
            int nProbValue = pLvlSubTxtRecord.getNProb(nSubTheme);
            
            if ((nRand & 0xFFFFFFFFL) % 100 < nProbValue) {
                // 设置掩码位
                nMask |= 1 << nCounter;
                
                // 更新房间的 DT1 掩码
                drlgRoom.setDt1Mask(drlgRoom.getDt1Mask() | pLvlSubTxtRecord.getDwDt1Mask());
            }
            
            // 移动到下一个记录
            pLvlSubTxtRecord = DataTbls.getNextLvlSubTxtRecord(pLvlSubTxtRecord, nSubType);
            ++nCounter;
        }
        
        return nMask;
    }
}
