package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LvlPrestTxt;
import com.d2moo.common.dungeon.Dungeon;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2BinaryReader;

/**
 * Drlg 预设模块
 * 对应 C++ 文件：DrlgPreset.cpp
 * 
 * 注意：本模块依赖以下其他模块的函数，需要先实现：
 * - SEED 相关函数（随机数生成）
 * - DATATBLS 相关函数（数据表查询）
 * - DUNGEON 相关函数（坐标转换、房间管理）
 * - DRLGGRID 相关函数（网格操作）
 * - D2CMP 相关函数（文件加载）
 */
public class DrlgPreset {
    
    /**
     * D2Common.0x6FD867A0
     * 生成硬编码的预设单位
     * 被 DrlgActivate 依赖
     */
    public static void spawnHardcodedPresetUnits(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getMazeOrOutdoor() == null || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) {
            D2Log.warning("DRLGPRESET_SpawnHardcodedPresetUnits: maze is null or not D2DrlgPresetRoomStrc");
            return;
        }
        
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) drlgRoom.getMazeOrOutdoor();
        D2DrlgMapStrc mazeMap = presetRoom.getPMap();
        
        if (mazeMap == null) {
            D2Log.warning("DRLGPRESET_SpawnHardcodedPresetUnits: mazeMap is null");
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 如果文件未加载，则加载 DS1 文件
        if (mazeMap.getPFile() == null) {
            Object[] ppDrlgFile = new Object[1];
            loadDrlgFile(ppDrlgFile, drlgRoom.getLevel().getDrlg().getArchive(), 
                mazeMap.getPLvlPrestTxtRecord().getSzFile(mazeMap.getNPickedFile()),
                drlgRoom.getLevel().getDrlg());
            mazeMap.setPFile((D2DrlgFileStrc)ppDrlgFile[0]);
            
            if (mazeMap.getPFile() != null) {
                addPresetUnitToDrlgMap(memPool, mazeMap, drlgRoom.getSeed());
            }
        }
        
        // 处理硬编码的特殊单位
        if (mazeMap.isBInited()) {
            mazeMap.setBInited(false);
            
            int nLevelPrestId = mazeMap.getNLevelPrest();
            
            // 处理 ACT1_WILD_BORDER 和 BLOODMOOR 的特殊情况
            if ((nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1 
                    && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_4)
                && (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_BLOODMOOR 
                    && mazeMap.getNPickedFile() == 3)) {
                
                int[] x = new int[1];
                int[] y = new int[1];
                x[0] = mazeMap.getPDrlgCoord().getNPosX() + mazeMap.getPDrlgCoord().getNWidth() / 2;
                y[0] = mazeMap.getPDrlgCoord().getNPosY() + mazeMap.getPDrlgCoord().getNHeight() / 2;
                
                Dungeon.gameTileToSubtileCoords(x, y);
                int nX = x[0];
                int nY = y[0];
                
                // 使用 MONSTER_NAVI 作为怪物ID
                int nClassId = D2MonsterIds.MONSTER_NAVI;
                
                // 验证怪物ID是否有效（使用 Monsters 模块验证）
                if (com.d2moo.common.monsters.Monsters.validateMonsterId(nClassId)) {
                    // Native deliberately passes NULL here. The unit belongs to
                    // pMazeMap until InitPresetRoomGrids transfers it to a room.
                    // Passing drlgRoom links the same node into both lists and
                    // creates a self-cycle during that transfer.
                    allocateAndAddPresetUnitToMap(null, memPool, D2UnitTypes.UNIT_MONSTER,
                        nClassId, D2MonModes.MONMODE_NEUTRAL, nX, nY, mazeMap, false);
                } else {
                    D2Log.warning("DRLGPRESET_SpawnHardcodedPresetUnits: Invalid monster ID: " + nClassId + " (MONSTER_NAVI)");
                }
            }
            // 处理城镇、河流和崔斯特瑞姆
            else if (nLevelPrestId == D2LvlPrestIds.LVLPREST_ACT1_TOWN_1 
                    || nLevelPrestId == D2LvlPrestIds.LVLPREST_ACT1_TOWN_1_TRANSITION_S
                    || (nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT1_RIVER_UPPER 
                        && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT1_BRIDGE)
                    || nLevelPrestId == D2LvlPrestIds.LVLPREST_ACT1_TRISTRAM) {
                
                if ((drlgRoom.getFlags() & D2DrlgRoomFlags.AUTOMAP_REVEAL) != 0) {
                    // 创建临时坐标和网格用于河流生成
                    D2DrlgCoord tDrlgCoord = new D2DrlgCoord();
                    tDrlgCoord.setNPosX(0);
                    tDrlgCoord.setNPosY(0);
                    tDrlgCoord.setNWidth(mazeMap.getPDrlgCoord().getNWidth());
                    tDrlgCoord.setNHeight(mazeMap.getPDrlgCoord().getNHeight());
                    
                    // 注意：D2DrlgFileStrc 结构已实现，可以通过 pFile 访问 pFloorLayer
                    // 当前实现使用临时网格对象，实际使用时可以从 pFile 获取层数据
                    D2DrlgGridStrc tDrlgGrid = new D2DrlgGridStrc();
                    
                    // 设置实际坐标
                    tDrlgCoord.setNPosX(mazeMap.getPDrlgCoord().getNPosX());
                    tDrlgCoord.setNPosY(mazeMap.getPDrlgCoord().getNPosY());
                    
                    if (mazeMap.getPLvlPrestTxtRecord() != null 
                            && mazeMap.getPLvlPrestTxtRecord().getDwDef() == D2LvlPrestIds.LVLPREST_ACT1_RIVER_LOWER) {
                        spawnRiver(mazeMap, tDrlgCoord, memPool, tDrlgGrid, -1);
                    } else {
                        // 遍历查找河流瓦片
                        for (int nOffsetX = 0; nOffsetX < tDrlgCoord.getNWidth(); ++nOffsetX) {
                            int nGridEntry = DrlgDrlgGrid.getGridEntry(tDrlgGrid, nOffsetX, 0);
                            D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nGridEntry);
                            
                            if (tileInfo.getNTileStyle() == 2 && tileInfo.getNTileSequence() == 24) {
                                spawnRiver(mazeMap, tDrlgCoord, memPool, tDrlgGrid, nOffsetX);
                                nOffsetX += (5 - 1); // nRiverBlockSizeInTilesX - 1 = 5 - 1
                            }
                        }
                    }
                    
                    // 重置网格
                    DrlgDrlgGrid.resetGrid(tDrlgGrid);
                }
            }
        }
        
        // 设置标志位
        drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.PRESET_UNITS_ADDED);
    }
    
    /**
     * 辅助函数：添加预设单位到地图
     */
    public static void addPresetUnitToMap(D2DrlgMapStrc mazeMap, D2PresetUnit newPresetUnit, boolean bSpawned) {
        if (mazeMap == null || newPresetUnit == null) {
            return;
        }
        
        newPresetUnit.setPNext(mazeMap.getPPresetUnit());
        mazeMap.setPPresetUnit(newPresetUnit);
        if (bSpawned) {
            newPresetUnit.setBSpawned(true);
        }
    }
    
    /**
     * 辅助函数：分配并添加预设单位到地图
     */
    public static D2PresetUnit allocateAndAddPresetUnitToMap(D2DrlgRoom drlgRoom, Object memPool, 
            int nUnitType, int nIndex, int nMode, int nX, int nY, D2DrlgMapStrc mazeMap, boolean bSpawned) {
        D2PresetUnit presetUnit = DrlgDrlgRoom.allocPresetUnit(drlgRoom, memPool, nUnitType, nIndex, nMode, nX, nY);
        if (presetUnit != null) {
            addPresetUnitToMap(mazeMap, presetUnit, bSpawned);
        }
        return presetUnit;
    }
    
    /**
     * D2Common.0x6FD86540
     * 添加预设单位到 Drlg 地图
     */
    public static void addPresetUnitToDrlgMap(Object memPool, D2DrlgMapStrc drlgMap, D2Seed seed) {
        if (drlgMap == null || drlgMap.getPFile() == null) {
            return;
        }
        
        D2DrlgFileStrc pFile = drlgMap.getPFile();
        
        // 计算坐标偏移
        int[] x = new int[1];
        int[] y = new int[1];
        x[0] = drlgMap.getPDrlgCoord().getNPosX();
        y[0] = drlgMap.getPDrlgCoord().getNPosY();
        
        Dungeon.gameTileToSubtileCoords(x, y);
        int nX = x[0];
        int nY = y[0];
        
        // 遍历预设单位链表
        D2PresetUnit pPresetUnit = pFile.getPPresetUnit();
        while (pPresetUnit != null) {
            int nIndex = pPresetUnit.getNIndex();
            boolean bSkip = false;
            
            // 处理怪物单位
            if (pPresetUnit.getNUnitType() == D2UnitTypes.UNIT_MONSTER) {
                int nMonStatsTxtRecordCount = DataTbls.getMonStatsTxtRecordCount();
                
                if (nIndex < nMonStatsTxtRecordCount) {
                    // 验证索引有效性
                    if (nIndex < 0 || nIndex >= nMonStatsTxtRecordCount) {
                        nIndex = -1;
                    }
                    
                    // 检查特殊怪物（ACT2VENDOR1, ACT2VENDOR2, LIGHTNINGSPIRE, FIRETOWER）
                    if (nIndex == D2MonsterIds.MONSTER_ACT2VENDOR1 
                            || nIndex == D2MonsterIds.MONSTER_ACT2VENDOR2
                            || nIndex == D2MonsterIds.MONSTER_LIGHTNINGSPIRE
                            || nIndex == D2MonsterIds.MONSTER_FIRETOWER) {
                        if (Seed.rollLimitedRandomNumber(seed, 3) != 0) {
                            bSkip = true;
                        }
                    }
                } else {
                    // 处理超级唯一怪物
                    int nSuperUniquesTxtRecordCount = DataTbls.getSuperUniquesTxtRecordCount();
                    
                    if (pPresetUnit.getNIndex() - nMonStatsTxtRecordCount >= nSuperUniquesTxtRecordCount) {
                        // 计算超级唯一怪物的索引
                        int nSuperUniqueIndex = pPresetUnit.getNIndex() - nMonStatsTxtRecordCount - nSuperUniquesTxtRecordCount;
                        
                        if (nSuperUniqueIndex == D2SuperUniques.SUPERUNIQUE_THE_TORMENTOR) {
                            if ((Seed.rollRandomNumber(seed) & 3) == 0) {
                                bSkip = true;
                            }
                        } else if (nSuperUniqueIndex == D2SuperUniques.SUPERUNIQUE_TAINTBREEDER) {
                            if ((Seed.rollRandomNumber(seed) & 1) == 0) {
                                bSkip = true;
                            }
                        } else if (nSuperUniqueIndex == D2SuperUniques.SUPERUNIQUE_RIFTWRAITH_THE_CANNIBAL) {
                            if ((Seed.rollRandomNumber(seed) & 3) != 0) {
                                bSkip = true;
                            }
                        }
                    }
                }
            }
            // 处理对象单位
            else if (pPresetUnit.getNUnitType() == D2UnitTypes.UNIT_OBJECT) {
                if (nIndex == D2ObjectIds.OBJECT_FLOORTRAP 
                        || nIndex == D2ObjectIds.OBJECT_TOMBFLOORTRAP) {
                    if ((Seed.rollRandomNumber(seed) & 1) != 0) {
                        bSkip = true;
                    }
                } else if (nIndex == 581) {
                    if ((Seed.rollRandomNumber(seed) & 3) == 0) {
                        bSkip = true;
                    }
                }
            }
            
            if (!bSkip) {
                // 复制预设单位并添加到地图
                D2PresetUnit pNewPresetUnit = copyPresetUnit(memPool, pPresetUnit, nX, nY);
                if (pNewPresetUnit != null) {
                    pNewPresetUnit.setPNext(drlgMap.getPPresetUnit());
                    drlgMap.setPPresetUnit(pNewPresetUnit);
                }
            }
            
            pPresetUnit = pPresetUnit.getPNext();
        }
    }
    
    /**
     * D2Common.0x6FD86AC0
     * 添加预设河流对象
     */
    public static void addPresetRiverObjects(D2DrlgMapStrc drlgMap, Object memPool, int nOffsetX, D2DrlgGridStrc drlgGrid) {
        if (drlgMap == null || drlgGrid == null) {
            return;
        }
        
        int nX = nOffsetX < 0 ? 0 : nOffsetX;
        int nHeight = drlgMap.getPDrlgCoord().getNHeight();
        
        for (int nY = 0; nY < nHeight; ++nY) {
            int[] objectX = new int[1];
            int[] objectY = new int[1];
            objectX[0] = nOffsetX + drlgMap.getPDrlgCoord().getNPosX();
            objectY[0] = nY + drlgMap.getPDrlgCoord().getNPosY();
            
            Dungeon.gameTileToSubtileCoords(objectX, objectY);
            int nObjectX = objectX[0] - 5; // nRiverBlockSizeInTilesX = 5
            int nObjectY = objectY[0];
            
            // 添加河流对象（直接创建并添加到地图，因为 allocateAndAddPresetUnitToMap 需要 drlgRoom）
            D2PresetUnit river1 = new D2PresetUnit();
            river1.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            river1.setNIndex(D2ObjectIds.OBJECT_RIVER1);
            river1.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            river1.setNXpos(nObjectX);
            river1.setNYpos(nObjectY);
            river1.setBSpawned(true);
            addPresetUnitToMap(drlgMap, river1, true);
            
            D2PresetUnit river2a = new D2PresetUnit();
            river2a.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            river2a.setNIndex(D2ObjectIds.OBJECT_RIVER2);
            river2a.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            river2a.setNXpos(nObjectX + 5);
            river2a.setNYpos(nObjectY);
            river2a.setBSpawned(true);
            addPresetUnitToMap(drlgMap, river2a, true);
            
            D2PresetUnit river2b = new D2PresetUnit();
            river2b.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            river2b.setNIndex(D2ObjectIds.OBJECT_RIVER2);
            river2b.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            river2b.setNXpos(nObjectX + 10);
            river2b.setNYpos(nObjectY);
            river2b.setBSpawned(true);
            addPresetUnitToMap(drlgMap, river2b, true);
            
            D2PresetUnit river2c = new D2PresetUnit();
            river2c.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            river2c.setNIndex(D2ObjectIds.OBJECT_RIVER2);
            river2c.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            river2c.setNXpos(nObjectX + 15);
            river2c.setNYpos(nObjectY);
            river2c.setBSpawned(true);
            addPresetUnitToMap(drlgMap, river2c, true);
            
            D2PresetUnit river3 = new D2PresetUnit();
            river3.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            river3.setNIndex(D2ObjectIds.OBJECT_RIVER3);
            river3.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            river3.setNXpos(nObjectX + 20);
            river3.setNYpos(nObjectY);
            river3.setBSpawned(true);
            addPresetUnitToMap(drlgMap, river3, true);
            
            // 检查网格条目
            int nGridEntry = DrlgDrlgGrid.getGridEntry(drlgGrid, nX, nY);
            D2C_PackedTileInformation tileInfo = new D2C_PackedTileInformation(nGridEntry);
            
            if (tileInfo.getNTileStyle() == 4) {
                int nSequence = tileInfo.getNTileSequence();
                if (nSequence == 0 || nSequence == 4 || nSequence == 8 || nSequence == 16 
                        || nSequence == 29 || nSequence == 39) {
                    nY += (4 - 1); // nRiverBlockSizeInTilesY - 1 = 4 - 1
                }
            }
        }
    }
    
    /**
     * 生成河流（辅助函数）
     */
    public static void spawnRiver(D2DrlgMapStrc mazeMap, D2DrlgCoord drlgCoord, Object drlgMemPool, 
            D2DrlgGridStrc drlgGrid, int nOffsetX) {
        if (mazeMap == null || drlgCoord == null || mazeMap.getPFile() == null) {
            return;
        }
        
        D2DrlgFileStrc pFile = mazeMap.getPFile();
        
        int[] nSubtileX = new int[1];
        int[] nSubtileY = new int[1];
        nSubtileX[0] = drlgCoord.getNPosX() + nOffsetX + 1;
        nSubtileY[0] = drlgCoord.getNPosY();
        
        int[] nEndSubtileX = new int[1];
        int[] nEndSubtileY = new int[1];
        nEndSubtileX[0] = 0;
        nEndSubtileY[0] = drlgCoord.getNPosY() + pFile.getNHeight();
        
        Dungeon.gameTileToSubtileCoords(nSubtileX, nSubtileY);
        Dungeon.gameTileToSubtileCoords(nEndSubtileX, nEndSubtileY);
        
        while (nSubtileY[0] < nEndSubtileY[0]) {
            // 添加不可见河流声音对象（需要 drlgRoom 参数，这里使用 null）
            // 注意：allocateAndAddPresetUnitToMap 需要 drlgRoom 参数，但 spawnRiver 没有
            // 这里先创建预设单位并添加到地图
            D2PresetUnit presetUnit = new D2PresetUnit();
            presetUnit.setNUnitType(D2UnitTypes.UNIT_OBJECT);
            presetUnit.setNIndex(D2ObjectIds.OBJECT_INVISIBLE_RIVER_SOUND1);
            presetUnit.setNMode(D2ObjModes.OBJMODE_NEUTRAL);
            presetUnit.setNXpos(nSubtileX[0]);
            presetUnit.setNYpos(nSubtileY[0]);
            presetUnit.setBSpawned(true);
            
            addPresetUnitToMap(mazeMap, presetUnit, true);
            
            nSubtileY[0] += D2DrlgRoomTileConstants.DRLGROOMTILE_SUBTILES_SIZE;
        }
        
        addPresetRiverObjects(mazeMap, drlgMemPool, nOffsetX, drlgGrid);
    }
    
    // 全局文件列表（对应 C++ 的 gpLevelFilesList_6FDEA700）
    private static D2LevelFileListStrc gpLevelFilesList = null;
    
    /**
     * D2Common.0x6FD86190
     * 加载 Drlg 文件
     */
    public static void loadDrlgFile(Object[] ppDrlgFile, Object hArchive, String szFile) {
        loadDrlgFile(ppDrlgFile, hArchive, szFile, null);
    }
    
    /**
     * D2Common.0x6FD86100 (重载版本，带 drlg 参数)
     * 加载 Drlg 文件（DS1 文件）
     * 
     * @param ppDrlgFile 输出参数，返回加载的文件结构
     * @param hArchive 存档句柄
     * @param szFile 文件名
     * @param drlg Drlg 结构（用于获取内存池）
     */
    public static void loadDrlgFile(Object[] ppDrlgFile, Object hArchive, String szFile, D2DrlgStrc drlg) {
        if (ppDrlgFile == null || ppDrlgFile.length == 0 || szFile == null) {
            return;
        }
        
        // 注意：Java 中通常不需要临界区保护，因为 Java 的同步机制（synchronized）已经提供了线程安全
        // 如果需要多线程访问，可以使用 synchronized 关键字或 java.util.concurrent 包中的工具
        // 对应 C++ 的 gpLvlSubTypeFilesCriticalSection（在 Java 中通常不需要）
        // 当前实现为简化版本，不包含线程同步
        
        // 检查文件是否已加载
        D2LevelFileListStrc pLevelFile = gpLevelFilesList;
        while (pLevelFile != null && !pLevelFile.getSzPath().equals(szFile)) {
            pLevelFile = pLevelFile.getPNext();
        }
        
        if (pLevelFile != null) {
            // 文件已加载，增加引用计数并返回
            pLevelFile.incrementRefCount();
            ppDrlgFile[0] = pLevelFile.getPFile();
        } else {
            // 文件未加载，创建新的文件列表节点和文件结构
            pLevelFile = D2Pool.callocStrcPool(null, D2LevelFileListStrc.class);
            if (pLevelFile == null) {
                pLevelFile = new D2LevelFileListStrc();
            }
            
            D2DrlgFileStrc pDrlgFile = D2Pool.callocStrcPool(null, D2DrlgFileStrc.class);
            if (pDrlgFile == null) {
                pDrlgFile = new D2DrlgFileStrc();
            }
            
            pLevelFile.setSzPath(szFile);
            pLevelFile.setNRefCount(1);
            pLevelFile.setPFile(pDrlgFile);
            pLevelFile.setPNext(gpLevelFilesList);
            gpLevelFilesList = pLevelFile;
            
            // 解析 DS1 文件
            parseDS1File(pDrlgFile, hArchive, szFile, drlg);
            
            ppDrlgFile[0] = pDrlgFile;
        }
        
        // 注意：Java 中通常不需要临界区释放，因为 Java 的同步机制会自动管理
        // 对应 C++ 的临界区释放（在 Java 中通常不需要）
    }
    
    /**
     * D2Common.0x6FD86190
     * 释放 Drlg 文件
     */
    public static void freeDrlgFile(Object[] ppDrlgFile) {
        if (ppDrlgFile == null || ppDrlgFile.length == 0 || ppDrlgFile[0] == null) {
            return;
        }
        
        D2DrlgFileStrc pFile = (D2DrlgFileStrc) ppDrlgFile[0];
        
        // 注意：Java 中通常不需要临界区保护，因为 Java 的同步机制（synchronized）已经提供了线程安全
        // 如果需要多线程访问，可以使用 synchronized 关键字或 java.util.concurrent 包中的工具
        // 对应 C++ 的临界区保护（在 Java 中通常不需要）
        
        // 查找文件列表节点
        D2LevelFileListStrc pPrevious = null;
        D2LevelFileListStrc pCurrent = gpLevelFilesList;
        while (pCurrent != null && pCurrent.getPFile() != pFile) {
            pPrevious = pCurrent;
            pCurrent = pCurrent.getPNext();
        }
        
        if (pCurrent == null) {
            D2Log.warning("DRLGPRESET_FreeDrlgFile: File not found in global file list");
            return;
        }
        
        // 减少引用计数
        pCurrent.decrementRefCount();
        
        // 如果引用计数为 0，释放文件
        if (pCurrent.getNRefCount() <= 0) {
            // 从列表中移除
            if (pPrevious != null) {
                pPrevious.setPNext(pCurrent.getPNext());
            } else {
                gpLevelFilesList = pCurrent.getPNext();
            }
            
            // 注意：Java 中通常不需要临界区释放，因为 Java 的同步机制会自动管理
        // 对应 C++ 的临界区释放（在 Java 中通常不需要）
            
            // 释放文件内容
            if (pFile.getPDS1File() != null) {
                // D2_FREE(pFile.getPDS1File());
            }
            
            if (pFile.getPSubstGroups() != null) {
                D2Pool.freePool(null, pFile.getPSubstGroups());
            }
            
            // 释放预设单位链表
            D2PresetUnit pPresetUnit = pFile.getPPresetUnit();
            while (pPresetUnit != null) {
                D2PresetUnit pNextPresetUnit = pPresetUnit.getPNext();
                freePresetUnit(null, pPresetUnit);
                pPresetUnit = pNextPresetUnit;
            }
            
            // 释放文件结构和列表节点
            D2Pool.freePool(null, pFile);
            D2Pool.freePool(null, pCurrent);
            ppDrlgFile[0] = null;
        } else {
            // 注意：Java 中通常不需要临界区释放，因为 Java 的同步机制会自动管理
        // 对应 C++ 的临界区释放（在 Java 中通常不需要）
        }
    }
    
    /**
     * D2Common.0x6FD86D80
     * 分配预设房间数据
     * 被 DrlgDrlgRoom 依赖
     */
    public static void allocPresetRoomData(D2DrlgRoom drlgRoom) {
        // 分配预设房间数据
        Object memPool = drlgRoom.getLevel() != null && drlgRoom.getLevel().getDrlg() != null
            ? drlgRoom.getLevel().getDrlg().getMempool() : null;
        
        D2DrlgPresetRoomStrc presetRoom = D2Pool.callocStrcPool(memPool, D2DrlgPresetRoomStrc.class);
        if (presetRoom == null) {
            presetRoom = new D2DrlgPresetRoomStrc();
        }
        drlgRoom.setMazeOrOutdoor(presetRoom);
    }
    
    /**
     * D2Common.0x6FD86C80
     * 释放预设房间数据
     * 被 DrlgDrlgRoom 依赖
     */
    public static void freePresetRoomData(D2DrlgRoom drlgRoom) {
        Object maze = drlgRoom.getMazeOrOutdoor();
        if (maze == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 释放网格
        freeDrlgGrids(memPool, drlgRoom);
        
        // 释放墓碑瓦片（如果有）
        if (maze instanceof D2DrlgPresetRoomStrc) {
            D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) maze;
            if (presetRoom.getPTombStoneTiles() != null) {
                D2Pool.freePool(memPool, presetRoom.getPTombStoneTiles());
            }
        }
        
        // 释放预设房间数据本身
        D2Pool.freePool(memPool, maze);
        drlgRoom.setMazeOrOutdoor(null);
    }
    
    /**
     * D2Common.0x6FD86CE0
     * 释放 Drlg 网格
     */
    public static void freeDrlgGrids(Object memPool, D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        Object maze = drlgRoom.getMazeOrOutdoor();
        if (maze == null || !(maze instanceof D2DrlgPresetRoomStrc)) {
            return;
        }
        
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) maze;
        
        // 释放墙壁网格数组 [4]
        for (int i = 0; i < 4; i++) {
            DrlgDrlgGrid.freeGrid(memPool, presetRoom.getPWallGrid(i));
        }
        
        // 释放瓦片类型网格数组 [4]
        for (int i = 0; i < 4; i++) {
            DrlgDrlgGrid.freeGrid(memPool, presetRoom.getPTileTypeGrid(i));
        }
        
        // 释放地板网格数组 [2]
        for (int i = 0; i < 2; i++) {
            DrlgDrlgGrid.freeGrid(memPool, presetRoom.getPFloorGrid(i));
        }
        
        // 释放单元格网格
        DrlgDrlgGrid.freeGrid(memPool, presetRoom.getPCellGrid());
        
        // 释放迷宫网格（如果有）
        if (presetRoom.getPMazeGrid() != null) {
            DrlgDrlgGrid.freeGrid(memPool, presetRoom.getPMazeGrid());
        }
    }
    
    /**
     * D2Common.0x6FD86D60
     * 从预设房间释放 Drlg 网格
     */
    public static void freeDrlgGridsFromPresetRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getMazeOrOutdoor() != null) {
            freeDrlgGrids(drlgRoom.getLevel().getDrlg().getMempool(), drlgRoom);
        }
    }
    
    /**
     * D2Common.0x6FD86430
     * 释放预设单位
     * 被 DrlgDrlgRoom 依赖
     */
    public static void freePresetUnit(Object memPool, D2PresetUnit presetUnit) {
        if (presetUnit == null) {
            return;
        }
        
        // 释放 MapAI（如果存在）
        if (presetUnit.getPMapAI() != null) {
            freeMapAI(memPool, presetUnit.getPMapAI());
        }
        
        // 释放预设单位本身
        D2Pool.freePool(memPool, presetUnit);
    }
    
    /**
     * D2Common.0x6FD86E50
     * 初始化预设房间网格
     * 被 DrlgRoomTile 依赖
     */
    public static void initPresetRoomGrids(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getMazeOrOutdoor() == null 
                || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) {
            return;
        }
        
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) drlgRoom.getMazeOrOutdoor();
        D2DrlgMapStrc mazeMap = presetRoom.getPMap();
        
        if (mazeMap == null || mazeMap.getPFile() == null) {
            return;
        }
        
        D2DrlgFileStrc pFile = mazeMap.getPFile();
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 计算坐标偏移
        D2DrlgCoord pDrlgCoord = new D2DrlgCoord();
        pDrlgCoord.setNPosX(drlgRoom.getNTileXPos() - mazeMap.getPDrlgCoord().getNPosX());
        pDrlgCoord.setNPosY(drlgRoom.getNTileYPos() - mazeMap.getPDrlgCoord().getNPosY());
        pDrlgCoord.setNWidth(drlgRoom.getNTileWidth() + 1);
        pDrlgCoord.setNHeight(drlgRoom.getNTileHeight() + 1);
        
        int nWidth = mazeMap.getPDrlgCoord().getNWidth() + 1;
        
        // 初始化墙壁网格和瓦片类型网格
        for (int i = 0; i < pFile.getNWallLayers(); ++i) {
            // 获取层数据（从 DS1 解析得到的是 int[] 数组）
            Object wallLayerObj = pFile.getPWallLayer(i);
            Object tileTypeLayerObj = pFile.getPTileTypeLayer(i);
            
            // 类型转换：确保是 int[] 数组
            int[] wallLayer = null;
            int[] tileTypeLayer = null;
            
            if (wallLayerObj instanceof int[]) {
                wallLayer = (int[]) wallLayerObj;
            } else if (wallLayerObj != null) {
                D2Log.warning("DRLGPRESET_InitPresetRoomGrids: Invalid wall layer type at index " + i);
            }
            
            if (tileTypeLayerObj instanceof int[]) {
                tileTypeLayer = (int[]) tileTypeLayerObj;
            } else if (tileTypeLayerObj != null) {
                D2Log.warning("DRLGPRESET_InitPresetRoomGrids: Invalid tile type layer type at index " + i);
            }
            
            // 填充墙壁网格和瓦片类型网格
            if (wallLayer != null) {
                DrlgDrlgGrid.fillNewCellFlags(memPool, presetRoom.getPWallGrid(i), 
                    wallLayer, pDrlgCoord, nWidth);
            }
            if (tileTypeLayer != null) {
                DrlgDrlgGrid.fillNewCellFlags(memPool, presetRoom.getPTileTypeGrid(i), 
                    tileTypeLayer, pDrlgCoord, nWidth);
            }
        }
        
        // 处理边缘网格标志
        if (pFile.getNWallLayers() > 0) {
            // 修改边缘网格标志
            DrlgDrlgGrid.alterEdgeGridFlags(presetRoom.getPWallGrid(0), 132, DrlgDrlgGrid.FlagOperation.OR);
        }
        
        // 设置墙壁层标志
        for (int i = 1; i < pFile.getNWallLayers(); ++i) {
            // 修改所有网格标志
            DrlgDrlgGrid.alterAllGridFlags(presetRoom.getPWallGrid(i), i << 18, DrlgDrlgGrid.FlagOperation.OR);
        }
        
        // 初始化地板网格
        for (int i = 0; i < pFile.getNFloorLayers(); ++i) {
            // 获取地板层数据
            Object floorLayerObj = pFile.getPFloorLayer(i);
            
            // 类型转换：确保是 int[] 数组
            int[] floorLayer = null;
            if (floorLayerObj instanceof int[]) {
                floorLayer = (int[]) floorLayerObj;
            } else if (floorLayerObj != null) {
                D2Log.warning("DRLGPRESET_InitPresetRoomGrids: Invalid floor layer type at index " + i);
            }
            
            // 填充地板网格
            if (floorLayer != null) {
                DrlgDrlgGrid.fillNewCellFlags(memPool, presetRoom.getPFloorGrid(i), 
                    floorLayer, pDrlgCoord, nWidth);
                DrlgDrlgGrid.alterAllGridFlags(presetRoom.getPFloorGrid(i), i << 18, DrlgDrlgGrid.FlagOperation.OR);
            }
        }
        
        // 初始化单元格网格（阴影层）
        Object shadowLayerObj = pFile.getPShadowLayer();
        if (shadowLayerObj != null) {
            // 阴影层是 byte[] 数组，需要转换为 int[] 或使用特殊处理
            // 注意：fillNewCellFlags 期望 int[]，但阴影层是 byte[]
            // 这里需要根据实际需求处理
            if (shadowLayerObj instanceof byte[]) {
                byte[] shadowLayer = (byte[]) shadowLayerObj;
                // 将 byte[] 转换为 int[]（每个 byte 扩展为 int）
                int[] shadowLayerInt = new int[shadowLayer.length];
                for (int j = 0; j < shadowLayer.length; ++j) {
                    shadowLayerInt[j] = shadowLayer[j] & 0xFF;
                }
                DrlgDrlgGrid.fillNewCellFlags(memPool, presetRoom.getPCellGrid(), 
                    shadowLayerInt, pDrlgCoord, nWidth);
            } else if (shadowLayerObj instanceof int[]) {
                DrlgDrlgGrid.fillNewCellFlags(memPool, presetRoom.getPCellGrid(), 
                    (int[]) shadowLayerObj, pDrlgCoord, nWidth);
            }
        }
        
        // 处理地板边缘标志
        for (int i = 0; i < pFile.getNFloorLayers(); ++i) {
            // 修改边缘网格标志
            DrlgDrlgGrid.alterEdgeGridFlags(presetRoom.getPFloorGrid(i), 132, DrlgDrlgGrid.FlagOperation.OR);
        }
        
        // 处理单元格边缘标志
        // 修改边缘网格标志
        DrlgDrlgGrid.alterEdgeGridFlags(presetRoom.getPCellGrid(), 132, DrlgDrlgGrid.FlagOperation.OR);
        
        // 处理预设单位：将属于当前房间的预设单位从地图移动到房间
        pDrlgCoord.setNPosX(drlgRoom.getNTileXPos());
        pDrlgCoord.setNPosY(drlgRoom.getNTileYPos());
        pDrlgCoord.setNWidth(drlgRoom.getNTileWidth());
        pDrlgCoord.setNHeight(drlgRoom.getNTileHeight());
        
        int[] x = new int[1];
        int[] y = new int[1];
        x[0] = pDrlgCoord.getNPosX();
        y[0] = pDrlgCoord.getNPosY();
        Dungeon.gameTileToSubtileCoords(x, y);
        pDrlgCoord.setNPosX(x[0]);
        pDrlgCoord.setNPosY(y[0]);
        
        x[0] = pDrlgCoord.getNWidth();
        y[0] = pDrlgCoord.getNHeight();
        Dungeon.gameTileToSubtileCoords(x, y);
        pDrlgCoord.setNWidth(x[0]);
        pDrlgCoord.setNHeight(y[0]);
        
        D2PresetUnit pPresetUnit = mazeMap.getPPresetUnit();
        D2PresetUnit pPrevious = null;
        
        while (pPresetUnit != null) {
            if (DrlgDrlgRoom.areXYInsideCoordinates(pDrlgCoord, pPresetUnit.getNXpos(), pPresetUnit.getNYpos())) {
                // 调整坐标
                pPresetUnit.setNXpos(pPresetUnit.getNXpos() - 5 * drlgRoom.getNTileXPos());
                pPresetUnit.setNYpos(pPresetUnit.getNYpos() - 5 * drlgRoom.getNTileYPos());
                
                // 从地图链表中移除
                if (pPrevious != null) {
                    pPrevious.setPNext(pPresetUnit.getPNext());
                } else {
                    mazeMap.setPPresetUnit(pPresetUnit.getPNext());
                }
                
                // 添加到房间链表
                pPresetUnit.setPNext(drlgRoom.getPresetUnits());
                drlgRoom.setPresetUnits(pPresetUnit);
                
                // 继续处理下一个
                if (pPrevious != null) {
                    pPresetUnit = pPrevious.getPNext();
                } else {
                    pPresetUnit = mazeMap.getPPresetUnit();
                }
            } else {
                pPrevious = pPresetUnit;
                pPresetUnit = pPresetUnit.getPNext();
            }
        }
    }
    
    /**
     * D2Common.0x6FD87130
     * 添加预设房间地图瓦片
     * 被 DrlgRoomTile 依赖
     */
    public static void addPresetRoomMapTiles(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getMazeOrOutdoor() == null 
                || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) {
            return;
        }
        
        // 分配瓦片网格
        DrlgRoomTile.allocTileGrid(drlgRoom);
        
        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) drlgRoom.getMazeOrOutdoor();
        D2DrlgMapStrc mazeMap = presetRoom.getPMap();
        
        if (mazeMap == null || mazeMap.getPFile() == null) {
            return;
        }
        
        D2LvlPrestTxt pLvlPrestTxtRecord = mazeMap.getPLvlPrestTxtRecord();
        if (pLvlPrestTxtRecord == null) {
            return;
        }
        
        // 检查是否需要杀死边缘
        boolean bKillEdgeX = false;
        boolean bKillEdgeY = false;
        if (pLvlPrestTxtRecord.getDwKillEdge() != 0) {
            bKillEdgeX = (drlgRoom.getNTileXPos() + drlgRoom.getNTileWidth() 
                    == mazeMap.getPDrlgCoord().getNPosX() + mazeMap.getPDrlgCoord().getNWidth());
            bKillEdgeY = (drlgRoom.getNTileYPos() + drlgRoom.getNTileHeight() 
                    == mazeMap.getPDrlgCoord().getNPosY() + mazeMap.getPDrlgCoord().getNHeight());
        }
        
        // 处理城镇或墓地特殊逻辑
        boolean bTownOrGraveyard = (presetRoom.getNLevelPrest() == D2LvlPrestIds.LVLPREST_ACT1_TOWN_1 
                || presetRoom.getNLevelPrest() == D2LvlPrestIds.LVLPREST_ACT1_GRAVEYARD);
        
        // 注意：城镇/墓地的特殊网格处理可以在后续实现
        // 当前实现：使用标准网格处理逻辑
        // 如果需要特殊处理，可以在这里添加：
        // if (bTownOrGraveyard) {
        //     // 创建临时网格用于城镇/墓地
        //     // 处理城镇/墓地的特殊逻辑（如边界处理、特殊瓦片等）
        // }
        
        // 处理地板层
        for (int i = 0; i < mazeMap.getPFile().getNFloorLayers(); ++i) {
            boolean bCheckCoordinatesValidity = (i == 0 && pLvlPrestTxtRecord.getDwFillBlanks() != 0);
            DrlgRoomTile.countAllTileTypes(drlgRoom, presetRoom.getPFloorGrid(i),
                    bCheckCoordinatesValidity, bKillEdgeX, bKillEdgeY);

            if (pLvlPrestTxtRecord.getDwAnimate() != 0) {
                DrlgDrlgAnim.testLoadAnimatedRoomTiles(drlgRoom, presetRoom.getPFloorGrid(i),
                        null, 0, bKillEdgeX ? 1 : 0, bKillEdgeY ? 1 : 0);
            }
        }

        // 处理墙壁层
        for (int i = 0; i < mazeMap.getPFile().getNWallLayers(); ++i) {
            DrlgRoomTile.countWallWarpTiles(drlgRoom, presetRoom.getPWallGrid(i),
                    presetRoom.getPTileTypeGrid(i), bKillEdgeX, bKillEdgeY);
            DrlgRoomTile.countAllTileTypes(drlgRoom, presetRoom.getPWallGrid(i),
                    false, bKillEdgeX, bKillEdgeY);

            if (pLvlPrestTxtRecord.getDwAnimate() != 0) {
                DrlgDrlgAnim.testLoadAnimatedRoomTiles(drlgRoom, presetRoom.getPWallGrid(i),
                        presetRoom.getPTileTypeGrid(i), DrlgRoomTile.TILETYPE_FLOOR, bKillEdgeX ? 1 : 0, bKillEdgeY ? 1 : 0);
            }
        }

        // 处理单元格网格
        DrlgRoomTile.countAllTileTypes(drlgRoom, presetRoom.getPCellGrid(), false, bKillEdgeX, bKillEdgeY);

        // Counting only reserves sizes. Native DRLGPRESET_AddPresetRoomMapTiles
        // then allocates and materializes every layer into pTileGrid; without
        // this half preset rooms exist but export as empty rooms.
        DrlgRoomTile.allocTileData(drlgRoom);
        for (int i = 0; i < mazeMap.getPFile().getNFloorLayers(); ++i) {
            boolean checkCoordinates = i == 0 && pLvlPrestTxtRecord.getDwFillBlanks() != 0;
            DrlgRoomTile.loadInitRoomTiles(drlgRoom, presetRoom.getPFloorGrid(i), null,
                    checkCoordinates, bKillEdgeX, bKillEdgeY);
        }
        for (int i = 0; i < mazeMap.getPFile().getNWallLayers(); ++i) {
            DrlgRoomTile.loadInitRoomTiles(drlgRoom, presetRoom.getPWallGrid(i),
                    presetRoom.getPTileTypeGrid(i), false, bKillEdgeX, bKillEdgeY);
        }
        DrlgRoomTile.loadInitRoomTiles(drlgRoom, presetRoom.getPCellGrid(), null,
                false, bKillEdgeX, bKillEdgeY);

        if (drlgRoom.getTileGrid() != null && drlgRoom.getTileGrid().getPTiles() != null) {
            drlgRoom.getTileGrid().getPTiles().setNWalls(drlgRoom.getTileGrid().getNWalls());
            drlgRoom.getTileGrid().getPTiles().setNFloors(drlgRoom.getTileGrid().getNFloors());
            drlgRoom.getTileGrid().getPTiles().setNRoofs(drlgRoom.getTileGrid().getNShadows());
        }
        if (pLvlPrestTxtRecord.getDwLogicals() != 0) {
            DrlgDrlgLogic.initializeDrlgCoordList(drlgRoom, presetRoom.getPTileTypeGrid(0),
                    presetRoom.getPFloorGrid(0), presetRoom.getPWallGrid(0));
        } else {
            DrlgDrlgLogic.allocCoordLists(drlgRoom);
        }
    }
    
    /**
     * D2Common.0x6FD86310
     * 复制预设单位
     */
    public static D2PresetUnit copyPresetUnit(Object memPool, D2PresetUnit presetUnit, int x, int y) {
        if (presetUnit == null) {
            return null;
        }
        
        D2PresetUnit newPresetUnit = D2Pool.callocStrcPool(memPool, D2PresetUnit.class);
        if (newPresetUnit == null) {
            newPresetUnit = new D2PresetUnit();
        }
        
        newPresetUnit.setNUnitType(presetUnit.getNUnitType());
        newPresetUnit.setNIndex(presetUnit.getNIndex());
        newPresetUnit.setNMode(presetUnit.getNMode());
        newPresetUnit.setNXpos(x + presetUnit.getNXpos());
        newPresetUnit.setNYpos(y + presetUnit.getNYpos());
        newPresetUnit.setBSpawned(presetUnit.isBSpawned());
        newPresetUnit.setDs1Raw(presetUnit.isDs1Raw());
        
        // 复制 MapAI（如果存在）
        if (presetUnit.getPMapAI() != null) {
            D2MapAIStrc newMapAI = createCopyOfMapAI(memPool, presetUnit.getPMapAI());
            newPresetUnit.setPMapAI(newMapAI);
            
            // 更新路径节点位置
            if (newMapAI != null && newMapAI.getPPosition() != null) {
                for (int i = 0; i < newMapAI.getNPathNodes(); ++i) {
                    D2MapAIPathPositionStrc position = newMapAI.getPPosition(i);
                    if (position != null) {
                        position.setNX(position.getNX() + x);
                        position.setNY(position.getNY() + y);
                    }
                }
            }
        }
        
        return newPresetUnit;
    }
    
    /**
     * D2Common.0x6FD86480 (#10020)
     * 创建 MapAI 的副本
     */
    public static D2MapAIStrc createCopyOfMapAI(Object memPool, D2MapAIStrc mapAI) {
        if (mapAI == null) {
            return null;
        }
        
        D2MapAIStrc newMapAI = D2Pool.callocStrcPool(memPool, D2MapAIStrc.class);
        if (newMapAI == null) {
            newMapAI = new D2MapAIStrc();
        }
        
        newMapAI.setNPathNodes(mapAI.getNPathNodes());
        
        // 分配并复制位置数组
        if (mapAI.getNPathNodes() > 0) {
            D2MapAIPathPositionStrc[] pNewPosition = D2Pool.callocArrayPool(
                memPool, D2MapAIPathPositionStrc.class, mapAI.getNPathNodes());
            
            if (pNewPosition != null && mapAI.getPPosition() != null) {
                for (int i = 0; i < mapAI.getNPathNodes(); ++i) {
                    D2MapAIPathPositionStrc srcPos = mapAI.getPPosition(i);
                    if (srcPos != null) {
                        D2MapAIPathPositionStrc dstPos = new D2MapAIPathPositionStrc(
                            srcPos.getNMapAIAction(), srcPos.getNX(), srcPos.getNY());
                        pNewPosition[i] = dstPos;
                    }
                }
            }
            
            newMapAI.setPPosition(pNewPosition);
        }
        
        return newMapAI;
    }
    
    /**
     * D2Common.0x6FD864F0 (#10021)
     * 交换 MapAI
     */
    public static D2MapAIStrc changeMapAI(D2MapAIStrc[] ppMapAI1, D2MapAIStrc[] ppMapAI2) {
        if (ppMapAI1 == null || ppMapAI2 == null || ppMapAI1.length == 0 || ppMapAI2.length == 0) {
            return null;
        }
        
        ppMapAI2[0] = ppMapAI1[0];
        ppMapAI1[0] = null;
        
        return ppMapAI2[0];
    }
    
    /**
     * D2Common.0x6FD86500 (#10022)
     * 释放 MapAI
     */
    public static void freeMapAI(Object memPool, D2MapAIStrc mapAI) {
        if (mapAI == null) {
            return;
        }
        
        // 释放位置数组
        if (mapAI.getPPosition() != null) {
            D2Pool.freePool(memPool, mapAI.getPPosition());
        }
        
        // 释放 MapAI 本身
        D2Pool.freePool(memPool, mapAI);
    }
    
    /**
     * D2Common.0x6FD881B0 (#10009)
     * 从房间获取选中的关卡预设文件路径
     */
    public static String getPickedLevelPrestFilePathFromRoomEx(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getType() == D2DrlgType.PRESET.getValue()) {
            // 注意：D2DrlgPresetRoomStrc 结构已实现，可以通过 drlgRoom.getMazeOrOutdoor() 获取
            // 当前实现：返回 null，表示未找到预设文件路径
            // 实际使用时，可以通过以下方式获取：
            // Object presetRoom = drlgRoom.getMazeOrOutdoor();
            // if (presetRoom != null && presetRoom instanceof D2DrlgPresetRoomStrc) {
            //     D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) presetRoom;
            //     if (preset.getPMap() != null) {
            //         Object map = preset.getPMap();
            //         if (map.getPLvlPrestTxtRecord() != null) {
            //         int pickedFile = map.getNPickedFile();
            //         return map.getPLvlPrestTxtRecord().getSzFile(pickedFile);
            //     }
            // }
            return "None";
        } else {
            return "None";
        }
    }

    /**
     * D2Common.0x6FD87F00
     * 获取关卡预设 X 尺寸（瓦片单位）
     * 对应 C++：DRLGPRESET_GetSizeX
     */
    public static int getSizeX(int nLvlPrestId) {
        com.d2moo.common.datatbls.D2LvlPrestTxt record = DataTbls.getLvlPrestTxtRecord(nLvlPrestId);
        return record != null ? record.getDwSizeX() : 8;
    }

    /**
     * D2Common.0x6FD87F10
     * 获取关卡预设 Y 尺寸（瓦片单位）
     * 对应 C++：DRLGPRESET_GetSizeY
     */
    public static int getSizeY(int nLvlPrestId) {
        com.d2moo.common.datatbls.D2LvlPrestTxt record = DataTbls.getLvlPrestTxtRecord(nLvlPrestId);
        return record != null ? record.getDwSizeY() : 8;
    }
    
    // DS1 文件格式常量
    private static final int DRLG_MAX_WALL_LAYERS = 4;
    private static final int DRLG_MAX_FLOOR_LAYERS = 2;
    
    // DS1 文件块类型常量
    private static final int DS1_BLOCK_TYPE_LAYER = 1;
    private static final int DS1_BLOCK_TYPE_ACT = 2;
    private static final int DS1_BLOCK_TYPE_COLLISION = 3;
    private static final int DS1_BLOCK_TYPE_PRESET_UNITS = 4;
    private static final int DS1_BLOCK_TYPE_SUBSTITUTION_GROUPS = 5;
    
    /**
     * D2Common.0x6FD86200 (DRLGPRESET_ParseDS1File)
     * 解析 DS1 文件
     * 
     * DS1 文件格式：
     * - 文件头：宽度、高度、层数等
     * - 层数据块：瓦片类型层、墙壁层、地板层、阴影层
     * - 预设单位块：怪物、对象、NPC 等
     * - 替换组块：用于瓦片替换
     * 
     * @param pDrlgFile DS1 文件结构
     * @param hArchive 存档句柄（用于读取文件）
     * @param szFile 文件名
     */
    private static void parseDS1File(D2DrlgFileStrc pDrlgFile, Object hArchive, String szFile, D2DrlgStrc drlg) {
        if (pDrlgFile == null || szFile == null || szFile.isEmpty()) {
            return;
        }
        byte[] fileData = readDS1FileData(hArchive, szFile);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DRLGPRESET_ParseDS1File: Failed to read DS1 file: " + szFile);
            return;
        }

        try {
            parseDS1FileData(pDrlgFile, fileData, szFile);
            D2Log.debug("DRLGPRESET_ParseDS1File: Parsed DS1 file: %s"
                    + " version=%d size=%dx%d walls=%d floors=%d groups=%d",
                    szFile, D2BinaryReader.readInt32(fileData, 0),
                    pDrlgFile.getNWidth(), pDrlgFile.getNHeight(),
                    pDrlgFile.getNWallLayers(), pDrlgFile.getNFloorLayers(),
                    pDrlgFile.getNSubstGroups());
        } catch (IllegalArgumentException e) {
            D2Log.warning("DRLGPRESET_ParseDS1File: Invalid DS1 file=%s error=%s",
                    szFile, e.getMessage());
        }
    }

    /** Direct translation of D2MOO DRLGPRESET_ParseDS1File's stream layout. */
    private static void parseDS1FileData(D2DrlgFileStrc out, byte[] data, String fileName) {
        Ds1Cursor in = new Ds1Cursor(data);
        in.section("header");
        int version = in.readInt();
        int width = in.readInt();
        int height = in.readInt();
        if (width < 0 || width > 1024 || height < 0 || height > 1024) {
            throw new IllegalArgumentException("invalid dimensions " + width + "x" + height);
        }
        out.setPDS1File(data);
        out.setNWidth(width);
        out.setNHeight(height);

        if (version >= 8) in.readInt(); // act; unit remapping is not required by LvlSub
        int substMethod = version >= 10 ? in.readInt() : 0;
        out.setNSubstMethod(substMethod);
        if (version >= 3) {
            in.section("dependencies");
            int strings = in.readCount("dependency strings", 4096);
            for (int i = 0; i < strings; i++) in.skipCString();
        }
        if (version >= 9 && version < 14) in.skipInts(2);

        int area = Math.multiplyExact(width + 1, height + 1);
        in.section("tile layers");
        if (version < 4) {
            out.setNWallLayers(1);
            out.setNFloorLayers(1);
            out.setPWallLayer(0, in.readIntLayer(area));
            out.setPFloorLayer(0, in.readIntLayer(area));
            out.setPTileTypeLayer(0, in.readIntLayer(area));
            out.setPSubstGroupTags(in.readIntLayer(area));
        } else {
            int walls = in.readCount("wall layers", DRLG_MAX_WALL_LAYERS);
            int floors = version < 16 ? 1 : in.readCount("floor layers", DRLG_MAX_FLOOR_LAYERS);
            out.setNWallLayers(walls);
            out.setNFloorLayers(floors);
            for (int i = 0; i < walls; i++) {
                out.setPWallLayer(i, in.readIntLayer(area));
                out.setPTileTypeLayer(i, in.readIntLayer(area));
            }
            for (int i = 0; i < floors; i++) out.setPFloorLayer(i, in.readIntLayer(area));
        }

        in.section("shadow layer");
        out.setPShadowLayer(in.readIntLayer(area));
        if (substMethod > 0 && substMethod <= 2) {
            in.section("substitution tag layer");
            out.setPSubstGroupTags(in.readIntLayer(area));
        }

        if (version > 1) {
            in.section("preset units");
            int units = in.readCount("preset units", 100000);
            D2PresetUnit first = null;
            for (int i = 0; i < units; i++) {
                D2PresetUnit unit = new D2PresetUnit();
                unit.setNUnitType(in.readInt());
                unit.setNIndex(in.readInt());
                unit.setNXpos(in.readInt());
                unit.setNYpos(in.readInt());
                if (version > 5) unit.setBSpawned(in.readInt() != 0);
                // Native D2Common resolves this index through Obj/MonPreset
                // when the unit is spawned. Keep its provenance so an
                // external renderer does not confuse it with hard-coded
                // class ids added later by DRLGROOMTILE_AddTilePresetUnits.
                unit.setDs1Raw(true);
                unit.setPNext(first);
                first = unit;
            }
            out.setPPresetUnit(first);
        }

        if (version >= 12 && substMethod > 0 && substMethod <= 2) {
            in.section("substitution groups");
            if (version >= 18) in.skipInts(1);
            int groups = in.readCount("substitution groups", 100000);
            int intsPerGroup = version >= 13 ? 5 : 4;
            int expectedGroupBytes = Math.multiplyExact(groups,
                    Math.multiplyExact(intsPerGroup, Integer.BYTES));
            int missingGroupBytes = expectedGroupBytes - in.remaining();
            if (missingGroupBytes > 0) {
                int maximumNativePadding = (intsPerGroup - 1) * Integer.BYTES;
                if (missingGroupBytes > maximumNativePadding || in.remaining() % Integer.BYTES != 0) {
                    throw new IllegalArgumentException("truncated substitution groups"
                            + " at offset " + in.offset()
                            + " expected=" + expectedGroupBytes
                            + " remaining=" + in.remaining());
                }
                // Retail Trees.ds1 declares one final group whose last three
                // ints are absent. D2's archive allocator exposes zero-filled
                // tail padding, so native D2Common parses a zero-extended
                // final group. Limit compatibility to less than one group.
                D2Log.warning("DRLGPRESET_ParseDS1File: zero-padding final substitution group"
                                + " file=%s version=%d groups=%d missingBytes=%d",
                        fileName, version, groups, missingGroupBytes);
            }
            in.section("substitution group entries count=" + groups);
            D2DrlgSubstGroupStrc[] values = new D2DrlgSubstGroupStrc[groups];
            for (int i = 0; i < groups; i++) {
                D2DrlgSubstGroupStrc group = new D2DrlgSubstGroupStrc();
                group.getTBox().setNPosX(in.readIntOrZero());
                group.getTBox().setNPosY(in.readIntOrZero());
                group.getTBox().setNWidth(in.readIntOrZero());
                group.getTBox().setNHeight(in.readIntOrZero());
                if (version >= 13) group.setField_14(in.readIntOrZero());
                values[i] = group;
            }
            out.setNSubstGroups(groups);
            out.setPSubstGroups(values);
        }
    }

    private static final class Ds1Cursor {
        private final byte[] data;
        private int offset;
        private String section = "unknown";
        private int sectionOffset;

        Ds1Cursor(byte[] data) { this.data = data; }

        void section(String section) {
            this.section = section;
            this.sectionOffset = offset;
        }

        int readInt() {
            require(4);
            int value = D2BinaryReader.readInt32(data, offset);
            offset += 4;
            return value;
        }

        int readIntOrZero() {
            return remaining() >= Integer.BYTES ? readInt() : 0;
        }

        int readCount(String label, int maximum) {
            int value = readInt();
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException("invalid " + label + " count " + value
                        + " at offset " + (offset - 4));
            }
            return value;
        }

        int[] readIntLayer(int length) {
            require(Math.multiplyExact(length, 4));
            int[] values = new int[length];
            for (int i = 0; i < length; i++) values[i] = readInt();
            return values;
        }

        void skipInts(int count) {
            int bytes = Math.multiplyExact(count, 4);
            require(bytes);
            offset += bytes;
        }

        void skipCString() {
            while (offset < data.length && data[offset++] != 0) {
                // scan to the NUL terminator
            }
            if (offset > data.length || (offset == data.length && data[offset - 1] != 0)) {
                throw new IllegalArgumentException("unterminated dependency string");
            }
        }

        int remaining() {
            return data.length - offset;
        }

        int offset() {
            return offset;
        }

        void require(int length) {
            if (length < 0 || offset > data.length - length) {
                throw new IllegalArgumentException("unexpected end at offset " + offset
                        + " need=" + length + " remaining=" + (data.length - offset)
                        + " section=" + section + " sectionOffset=" + sectionOffset);
            }
        }
    }
    
    /**
     * 读取 DS1 文件数据
     * @param hArchive 存档句柄（MPQ 存档，可为 null）
     * @param szFile 文件名
     * @return 文件数据，如果读取失败返回 null
     */
    private static byte[] readDS1FileData(Object hArchive, String szFile) {
        if (szFile == null || szFile.isEmpty()) {
            D2Log.warning("DRLGPRESET_ReadDS1FileData: Invalid file name");
            return null;
        }
        
        // 使用文件读取工具类读取文件
        byte[] fileData = D2FileReader.readFile(hArchive, szFile);
        
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DRLGPRESET_ReadDS1FileData: Failed to read DS1 file: " + szFile);
            return null;
        }
        
        D2Log.debug("DRLGPRESET_ReadDS1FileData: Successfully read DS1 file: " + szFile + " (size: " + fileData.length + " bytes)");
        return fileData;
    }
    
    /**
     * 解析 DS1 文件头
     * DS1 文件头格式（简化版）：
     * - 4 bytes: 版本号
     * - 4 bytes: 宽度
     * - 4 bytes: 高度
     * - 4 bytes: 墙壁层数
     * - 4 bytes: 地板层数
     * 
     * @param pDrlgFile DS1 文件结构
     * @param fileData 文件数据
     * @param offset 起始偏移
     * @return 解析后的偏移位置，如果失败返回 -1
     */
    private static int parseDS1Header(D2DrlgFileStrc pDrlgFile, byte[] fileData, int offset) {
        if (pDrlgFile == null || fileData == null || offset < 0) {
            return -1;
        }
        
        // DS1 文件头格式（根据实际格式调整）：
        // - 4 bytes: 版本号
        // - 4 bytes: 宽度
        // - 4 bytes: 高度
        // - 4 bytes: 墙壁层数
        // - 4 bytes: 地板层数
        // 总共至少 20 字节
        
        if (!D2BinaryReader.hasEnoughData(fileData, offset, 20)) {
            D2Log.warning("DRLGPRESET_ParseDS1Header: Not enough data for header, offset: " + offset + ", length: " + fileData.length);
            // 使用默认值
            pDrlgFile.setNWidth(8);
            pDrlgFile.setNHeight(8);
            pDrlgFile.setNWallLayers(0);
            pDrlgFile.setNFloorLayers(0);
            return offset;
        }
        
        // 读取文件头数据
        int version = D2BinaryReader.readInt32(fileData, offset);
        int width = D2BinaryReader.readInt32(fileData, offset + 4);
        int height = D2BinaryReader.readInt32(fileData, offset + 8);
        int wallLayers = D2BinaryReader.readInt32(fileData, offset + 12);
        int floorLayers = D2BinaryReader.readInt32(fileData, offset + 16);
        
        // 验证数据有效性
        if (width <= 0 || width > 1024 || height <= 0 || height > 1024) {
            D2Log.warning("DRLGPRESET_ParseDS1Header: Invalid dimensions, width: " + width + ", height: " + height);
            // 使用默认值
            width = 8;
            height = 8;
        }
        
        if (wallLayers < 0 || wallLayers > DRLG_MAX_WALL_LAYERS) {
            D2Log.warning("DRLGPRESET_ParseDS1Header: Invalid wall layers: " + wallLayers);
            wallLayers = Math.max(0, Math.min(wallLayers, DRLG_MAX_WALL_LAYERS));
        }
        
        if (floorLayers < 0 || floorLayers > DRLG_MAX_FLOOR_LAYERS) {
            D2Log.warning("DRLGPRESET_ParseDS1Header: Invalid floor layers: " + floorLayers);
            floorLayers = Math.max(0, Math.min(floorLayers, DRLG_MAX_FLOOR_LAYERS));
        }
        
        // 设置解析的值
        pDrlgFile.setNWidth(width);
        pDrlgFile.setNHeight(height);
        pDrlgFile.setNWallLayers(wallLayers);
        pDrlgFile.setNFloorLayers(floorLayers);
        
        D2Log.debug("DRLGPRESET_ParseDS1Header: Parsed header - version: " + version 
                + ", width: " + width + ", height: " + height 
                + ", wallLayers: " + wallLayers + ", floorLayers: " + floorLayers);
        
        // 返回下一个块的偏移（文件头为 20 字节）
        return offset + 20;
    }
    
    /**
     * 解析 DS1 层数据
     * 层数据包括：
     * - 瓦片类型层（Tile Type Layer）
     * - 墙壁层（Wall Layer）
     * - 地板层（Floor Layer）
     * - 阴影层（Shadow Layer）
     * 
     * @param pDrlgFile DS1 文件结构
     * @param fileData 文件数据
     * @param offset 起始偏移
     * @return 解析后的偏移位置
     */
    private static int parseDS1Layers(D2DrlgFileStrc pDrlgFile, byte[] fileData, int offset, D2DrlgStrc drlg) {
        if (pDrlgFile == null || fileData == null || offset < 0) {
            return offset;
        }
        
        int nWidth = pDrlgFile.getNWidth();
        int nHeight = pDrlgFile.getNHeight();
        int nWallLayers = pDrlgFile.getNWallLayers();
        int nFloorLayers = pDrlgFile.getNFloorLayers();
        
        if (nWidth <= 0 || nHeight <= 0) {
            D2Log.warning("DRLGPRESET_ParseDS1Layers: Invalid dimensions, width: " + nWidth + ", height: " + nHeight);
            return offset;
        }
        
        int layerSize = nWidth * nHeight;
        Object memPool = (drlg != null) ? drlg.getMempool() : null;
        
        // 解析瓦片类型层和墙壁层（每个墙壁层都有对应的瓦片类型层）
        for (int i = 0; i < nWallLayers && i < DRLG_MAX_WALL_LAYERS; ++i) {
            // 检查是否有足够的数据
            int tileTypeLayerSize = layerSize * 4; // 每个 int 4 字节
            int wallLayerSize = layerSize * 4;
            
            if (!D2BinaryReader.hasEnoughData(fileData, offset, tileTypeLayerSize + wallLayerSize)) {
                D2Log.warning("DRLGPRESET_ParseDS1Layers: Not enough data for wall layer " + i);
                break;
            }
            
            // 分配并解析瓦片类型层
            int[] tileTypeLayer = D2Pool.callocIntArrayPool(memPool, layerSize);
            for (int j = 0; j < layerSize; ++j) {
                tileTypeLayer[j] = D2BinaryReader.readInt32(fileData, offset + j * 4);
            }
            pDrlgFile.setPTileTypeLayer(i, tileTypeLayer);
            offset += tileTypeLayerSize;
            
            // 分配并解析墙壁层
            int[] wallLayer = D2Pool.callocIntArrayPool(memPool, layerSize);
            for (int j = 0; j < layerSize; ++j) {
                wallLayer[j] = D2BinaryReader.readInt32(fileData, offset + j * 4);
            }
            pDrlgFile.setPWallLayer(i, wallLayer);
            offset += wallLayerSize;
        }
        
        // 解析地板层
        for (int i = 0; i < nFloorLayers && i < DRLG_MAX_FLOOR_LAYERS; ++i) {
            int floorLayerSize = layerSize * 4; // 每个 int 4 字节
            
            if (!D2BinaryReader.hasEnoughData(fileData, offset, floorLayerSize)) {
                D2Log.warning("DRLGPRESET_ParseDS1Layers: Not enough data for floor layer " + i);
                break;
            }
            
            // 分配并解析地板层
            int[] floorLayer = D2Pool.callocIntArrayPool(memPool, layerSize);
            for (int j = 0; j < layerSize; ++j) {
                floorLayer[j] = D2BinaryReader.readInt32(fileData, offset + j * 4);
            }
            pDrlgFile.setPFloorLayer(i, floorLayer);
            offset += floorLayerSize;
        }
        
        // 解析阴影层（每个 byte 1 字节）
        int shadowLayerSize = layerSize;
        if (D2BinaryReader.hasEnoughData(fileData, offset, shadowLayerSize)) {
            byte[] shadowLayer = D2Pool.callocPool(memPool, shadowLayerSize);
            for (int j = 0; j < layerSize; ++j) {
                shadowLayer[j] = (byte) D2BinaryReader.readUInt8(fileData, offset + j);
            }
            pDrlgFile.setPShadowLayer(shadowLayer);
            offset += shadowLayerSize;
        } else {
            D2Log.warning("DRLGPRESET_ParseDS1Layers: Not enough data for shadow layer");
        }
        
        D2Log.debug("DRLGPRESET_ParseDS1Layers: Parsed layers - wallLayers: " + nWallLayers 
                + ", floorLayers: " + nFloorLayers + ", offset: " + offset);
        
        return offset;
    }
    
    /**
     * 解析 DS1 预设单位
     * 预设单位包括：怪物、对象、NPC 等
     * 
     * @param pDrlgFile DS1 文件结构
     * @param fileData 文件数据
     * @param offset 起始偏移
     * @return 解析后的偏移位置
     */
    private static int parseDS1PresetUnits(D2DrlgFileStrc pDrlgFile, byte[] fileData, int offset, D2DrlgStrc drlg) {
        if (pDrlgFile == null || fileData == null || offset < 0) {
            return offset;
        }
        
        // DS1 预设单位格式（根据实际格式调整）：
        // - 4 bytes: 预设单位数量
        // 对于每个预设单位（每个 20 字节）：
        //   - 4 bytes: 单位类型（UNIT_TYPE_MONSTER, UNIT_TYPE_OBJECT, UNIT_TYPE_NPC）
        //   - 4 bytes: 单位ID（怪物ID、对象ID等）
        //   - 4 bytes: 模式
        //   - 4 bytes: X 坐标
        //   - 4 bytes: Y 坐标
        
        // 检查是否有足够的数据读取预设单位数量
        if (!D2BinaryReader.hasEnoughData(fileData, offset, 4)) {
            D2Log.warning("DRLGPRESET_ParseDS1PresetUnits: Not enough data for preset unit count");
            return offset;
        }
        
        // 读取预设单位数量
        int nPresetUnitCount = D2BinaryReader.readInt32(fileData, offset);
        offset += 4;
        
        if (nPresetUnitCount <= 0 || nPresetUnitCount > 10000) {
            D2Log.warning("DRLGPRESET_ParseDS1PresetUnits: Invalid preset unit count: " + nPresetUnitCount);
            return offset;
        }
        
        // 检查是否有足够的数据读取所有预设单位
        int presetUnitSize = 20; // 每个预设单位 20 字节
        int totalSize = nPresetUnitCount * presetUnitSize;
        
        if (!D2BinaryReader.hasEnoughData(fileData, offset, totalSize)) {
            D2Log.warning("DRLGPRESET_ParseDS1PresetUnits: Not enough data for preset units, count: " + nPresetUnitCount);
            return offset;
        }
        
        Object memPool = (drlg != null) ? drlg.getMempool() : null;
        D2PresetUnit pFirstPresetUnit = null;
        D2PresetUnit pLastPresetUnit = null;
        
        // 解析每个预设单位
        for (int i = 0; i < nPresetUnitCount; ++i) {
            // 分配预设单位对象
            D2PresetUnit pPresetUnit = D2Pool.callocStrcPool(memPool, D2PresetUnit.class);
            if (pPresetUnit == null) {
                pPresetUnit = new D2PresetUnit();
            }
            
            // 读取预设单位数据
            int nUnitType = D2BinaryReader.readInt32(fileData, offset);
            int nIndex = D2BinaryReader.readInt32(fileData, offset + 4);
            int nMode = D2BinaryReader.readInt32(fileData, offset + 8);
            int nXpos = D2BinaryReader.readInt32(fileData, offset + 12);
            int nYpos = D2BinaryReader.readInt32(fileData, offset + 16);
            
            // 设置预设单位数据
            pPresetUnit.setNUnitType(nUnitType);
            pPresetUnit.setNIndex(nIndex);
            pPresetUnit.setNMode(nMode);
            pPresetUnit.setNXpos(nXpos);
            pPresetUnit.setNYpos(nYpos);
            pPresetUnit.setBSpawned(false); // 初始状态为未生成
            pPresetUnit.setPMapAI(null); // MapAI 稍后设置
            pPresetUnit.setPNext(null);
            
            // 链接到链表
            if (pFirstPresetUnit == null) {
                pFirstPresetUnit = pPresetUnit;
                pLastPresetUnit = pPresetUnit;
            } else {
                pLastPresetUnit.setPNext(pPresetUnit);
                pLastPresetUnit = pPresetUnit;
            }
            
            offset += presetUnitSize;
        }
        
        // 设置预设单位链表
        pDrlgFile.setPPresetUnit(pFirstPresetUnit);
        
        D2Log.debug("DRLGPRESET_ParseDS1PresetUnits: Parsed " + nPresetUnitCount + " preset units, offset: " + offset);
        
        return offset;
    }
    
    /**
     * 解析 DS1 替换组
     * 替换组用于瓦片替换功能
     * 
     * @param pDrlgFile DS1 文件结构
     * @param fileData 文件数据
     * @param offset 起始偏移
     * @return 解析后的偏移位置
     */
    private static int parseDS1SubstitutionGroups(D2DrlgFileStrc pDrlgFile, byte[] fileData, int offset, D2DrlgStrc drlg) {
        if (pDrlgFile == null || fileData == null || offset < 0) {
            return offset;
        }
        
        // DS1 替换组格式（根据实际格式调整）：
        // - 4 bytes: 替换组数量
        // 对于每个替换组（每个 24 字节）：
        //   - 4 bytes: 左上角 X 坐标
        //   - 4 bytes: 左上角 Y 坐标
        //   - 4 bytes: 右下角 X 坐标
        //   - 4 bytes: 右下角 Y 坐标
        //   - 4 bytes: 字段10
        //   - 4 bytes: 字段14（替换组中的变体数量）
        
        // 检查是否有足够的数据读取替换组数量
        if (!D2BinaryReader.hasEnoughData(fileData, offset, 4)) {
            D2Log.warning("DRLGPRESET_ParseDS1SubstitutionGroups: Not enough data for substitution group count");
            pDrlgFile.setNSubstGroups(0);
            pDrlgFile.setPSubstGroups(null);
            return offset;
        }
        
        // 读取替换组数量
        int nSubstGroupCount = D2BinaryReader.readInt32(fileData, offset);
        offset += 4;
        
        if (nSubstGroupCount <= 0 || nSubstGroupCount > 1000) {
            D2Log.warning("DRLGPRESET_ParseDS1SubstitutionGroups: Invalid substitution group count: " + nSubstGroupCount);
            pDrlgFile.setNSubstGroups(0);
            pDrlgFile.setPSubstGroups(null);
            return offset;
        }
        
        // 检查是否有足够的数据读取所有替换组
        int substGroupSize = 24; // 每个替换组 24 字节
        int totalSize = nSubstGroupCount * substGroupSize;
        
        if (!D2BinaryReader.hasEnoughData(fileData, offset, totalSize)) {
            D2Log.warning("DRLGPRESET_ParseDS1SubstitutionGroups: Not enough data for substitution groups, count: " + nSubstGroupCount);
            pDrlgFile.setNSubstGroups(0);
            pDrlgFile.setPSubstGroups(null);
            return offset;
        }
        
        Object memPool = (drlg != null) ? drlg.getMempool() : null;
        
        // 分配替换组数组
        D2DrlgSubstGroupStrc[] pSubstGroups = D2Pool.callocArrayPool(memPool, D2DrlgSubstGroupStrc.class, nSubstGroupCount);
        if (pSubstGroups == null) {
            pSubstGroups = new D2DrlgSubstGroupStrc[nSubstGroupCount];
        }
        
        // 解析每个替换组
        for (int i = 0; i < nSubstGroupCount; ++i) {
            // 分配替换组对象
            D2DrlgSubstGroupStrc pSubstGroup = D2Pool.callocStrcPool(memPool, D2DrlgSubstGroupStrc.class);
            if (pSubstGroup == null) {
                pSubstGroup = new D2DrlgSubstGroupStrc();
            }
            
            // 读取替换组数据
            int nX1 = D2BinaryReader.readInt32(fileData, offset);
            int nY1 = D2BinaryReader.readInt32(fileData, offset + 4);
            int nX2 = D2BinaryReader.readInt32(fileData, offset + 8);
            int nY2 = D2BinaryReader.readInt32(fileData, offset + 12);
            int field_10 = D2BinaryReader.readInt32(fileData, offset + 16);
            int field_14 = D2BinaryReader.readInt32(fileData, offset + 20);
            
            // 设置坐标框
            D2DrlgCoord tBox = pSubstGroup.getTBox();
            tBox.setNPosX(nX1);
            tBox.setNPosY(nY1);
            tBox.setNWidth(nX2 - nX1 + 1);
            tBox.setNHeight(nY2 - nY1 + 1);
            
            // 设置其他字段
            pSubstGroup.setField_10(field_10);
            pSubstGroup.setField_14(field_14);
            
            // 添加到数组
            pSubstGroups[i] = pSubstGroup;
            
            offset += substGroupSize;
        }
        
        // 设置替换组数组
        pDrlgFile.setNSubstGroups(nSubstGroupCount);
        pDrlgFile.setPSubstGroups(pSubstGroups);
        
        D2Log.debug("DRLGPRESET_ParseDS1SubstitutionGroups: Parsed " + nSubstGroupCount + " substitution groups, offset: " + offset);
        
        return offset;
    }
    
    /**
     * D2Common.0x6FD86A50
     * 释放 Drlg 地图
     * @param memPool 内存池
     * @param pDrlgMap Drlg 地图结构
     */
    public static void freeDrlgMap(Object memPool, Object pDrlgMap) {
        if (!(pDrlgMap instanceof D2DrlgMapStrc)) return;

        // pCurrentMap is a linked list. Every map owns one reference obtained
        // through loadDrlgFile, so release every node through the shared file
        // cache instead of clearing the first file's arrays in place.
        D2DrlgMapStrc drlgMap = (D2DrlgMapStrc) pDrlgMap;
        while (drlgMap != null) {
            D2DrlgMapStrc next = drlgMap.getPNext();
            if (drlgMap.getPFile() != null) {
                Object[] file = { drlgMap.getPFile() };
                freeDrlgFile(file);
                drlgMap.setPFile(null);
            }
            drlgMap.setPNext(null);
            drlgMap = next;
        }
    }
    
    /**
     * D2Common.0x6FD86A80
     * 重置 Drlg 地图
     * @param level 关卡
     * @param alloc 是否分配（true 表示分配，false 表示释放）
     */
    public static void resetDrlgMap(D2DrlgLevel level, boolean alloc) {
        if (level == null) {
            return;
        }
        
        Object memPool = level.getDrlg().getMempool();
        
        if (alloc) {
            // 分配时不需要特殊处理，地图会在需要时加载
        } else {
            // 释放时清空当前地图
            if (level.getPCurrentMap() != null) {
                freeDrlgMap(memPool, level.getPCurrentMap());
                level.setPCurrentMap(null);
            }
        }
    }
    
    /**
     * D2Common.0x6FD86A00
     * 初始化关卡数据
     * @param level 关卡
     */
    public static void initLevelData(D2DrlgLevel level) {
        if (level == null) {
            return;
        }

        // Native DRLGPRESET_InitLevelData chooses the DS1 variant while the
        // level seed is still in its allocation state. Delaying this roll
        // changes both the selected map and every later random decision.
        D2LvlPrestTxt prest = DataTbls.getLvlPrestTxtRecordFromLevelId(level.getLevelId());
        if (prest == null) {
            D2Log.warning("DRLGPRESET_InitLevelData: no LvlPrest claims level=%d",
                level.getLevelId());
            return;
        }

        D2DrlgPresetInfoStrc preset = new D2DrlgPresetInfoStrc();
        preset.setPDrlgMap(null);
        preset.setNDirection(prest.getDwFiles() > 0
            ? Seed.rollLimitedRandomNumber(level.getSeed(), prest.getDwFiles()) : -1);
        level.setPreset(preset);
        DrlgDrlg.setLevelPositionAndSize(level.getDrlg(), level);
    }
    
    /**
     * D2Common.0x6FD86B00
     * 生成预设关卡
     * @param level 关卡
     */
    public static void generateLevel(D2DrlgLevel level) {
        if (level == null || level.getPreset() == null) {
            return;
        }

        D2LvlPrestTxt prest = DataTbls.getLvlPrestTxtRecordFromLevelId(level.getLevelId());
        if (prest == null) {
            D2Log.warning("DRLGPRESET_GenerateLevel: no LvlPrest claims level=%d",
                level.getLevelId());
            return;
        }

        D2DrlgMapStrc map = allocDrlgMap(
            level, prest.getDwDef(), level.getLevelCoords(), level.getSeed());
        if (map == null) {
            D2Log.warning("DRLGPRESET_GenerateLevel: failed to allocate map level=%d prest=%d",
                level.getLevelId(), prest.getDwDef());
            return;
        }
        level.getPreset().setPDrlgMap(map);

        if (level.getPreset().getNDirection() == -1) {
            level.getPreset().setNDirection(map.getNPickedFile());
        } else {
            setPickedFileInDrlgMap(map, level.getPreset().getNDirection());
        }

        buildArea(level, map, 0, 0);
    }
    
    /**
     * D2Common.0x6FD86C00
     * 分配 Drlg 地图
     * @param level 关卡
     * @param nLevelPrest 预设ID
     * @param pDrlgCoord 坐标
     * @param seed 随机数种子
     * @return 分配的 Drlg 地图结构
     */
    public static D2DrlgMapStrc allocDrlgMap(D2DrlgLevel level, int nLevelPrest, 
            D2DrlgCoord pDrlgCoord, D2Seed seed) {
        if (level == null || pDrlgCoord == null) {
            return null;
        }
        
        Object memPool = level.getDrlg().getMempool();
        D2DrlgMapStrc drlgMap = D2Pool.callocStrcPool(memPool, D2DrlgMapStrc.class);
        if (drlgMap == null) {
            drlgMap = new D2DrlgMapStrc();
        }
        
        // Direct translation of DRLGPRESET_AllocDrlgMap. The random file roll
        // is significant even when a caller subsequently supplies a picked
        // file because it advances the level seed in the native code.
        drlgMap.setNLevelPrest(nLevelPrest);
        drlgMap.setBHasInfo(false);
        drlgMap.setBInited(true);
        drlgMap.setNPops(0);
        
        // 从数据表获取预设文本记录
        D2LvlPrestTxt lvlPrestTxtRecord = com.d2moo.common.datatbls.DataTbls.getLvlPrestTxtRecord(nLevelPrest);
        if (lvlPrestTxtRecord == null) return null;
        drlgMap.setPLvlPrestTxtRecord(lvlPrestTxtRecord);
        int files = lvlPrestTxtRecord.getDwFiles();
        drlgMap.setNPickedFile(files > 0 ? Seed.rollLimitedRandomNumber(seed, files) : 0);

        D2DrlgCoord mapCoord = new D2DrlgCoord();
        mapCoord.setNPosX(pDrlgCoord.getNPosX());
        mapCoord.setNPosY(pDrlgCoord.getNPosY());
        if (lvlPrestTxtRecord.getDwSizeX() != 0 && lvlPrestTxtRecord.getDwSizeY() != 0) {
            mapCoord.setNWidth(lvlPrestTxtRecord.getDwSizeX());
            mapCoord.setNHeight(lvlPrestTxtRecord.getDwSizeY());
        } else {
            mapCoord.setNWidth(pDrlgCoord.getNWidth());
            mapCoord.setNHeight(pDrlgCoord.getNHeight());
        }
        drlgMap.setPDrlgCoord(mapCoord);

        drlgMap.setPNext((D2DrlgMapStrc) level.getPCurrentMap());
        level.setPCurrentMap(drlgMap);
        
        return drlgMap;
    }
    
    /**
     * D2Common.0x6FD86C50
     * 设置 Drlg 地图中选中的文件
     * @param pDrlgMap Drlg 地图结构
     * @param nPickedFile 选中的文件索引
     */
    public static void setPickedFileInDrlgMap(D2DrlgMapStrc pDrlgMap, int nPickedFile) {
        if (pDrlgMap == null) {
            return;
        }
        
        pDrlgMap.setNPickedFile(nPickedFile);
    }
    
    /**
     * D2Common.0x6FD86D00
     * 构建区域
     * @param level 关卡
     * @param pDrlgMap Drlg 地图结构
     * @param a6a 参数（可能是方向或其他标志）
     * @param a7 参数（可能是标志或其他值）
     */
    public static void buildArea(D2DrlgLevel level, D2DrlgMapStrc pDrlgMap, int nFlags, int bSingleRoom) {
        if (level == null || pDrlgMap == null) {
            return;
        }

        D2LvlPrestTxt prest = pDrlgMap.getPLvlPrestTxtRecord();
        D2DrlgCoord map = pDrlgMap.getPDrlgCoord();
        if (prest == null || map == null || map.getNWidth() <= 0 || map.getNHeight() <= 0) return;
        if (prest.getDwOutdoors() != 0) nFlags |= 0x80000;

        if (bSingleRoom != 0) {
            initPresetRoomData(level, pDrlgMap, map, prest.getDwDt1Mask(), nFlags, 1);
            return;
        }

        int xEnd = map.getNPosX() + map.getNWidth();
        int yEnd = map.getNPosY() + map.getNHeight();
        for (int y = map.getNPosY(); y < yEnd; y += 8) {
            for (int x = map.getNPosX(); x < xEnd; x += 8) {
                D2DrlgCoord roomCoord = new D2DrlgCoord();
                roomCoord.setNPosX(x);
                roomCoord.setNPosY(y);
                roomCoord.setNWidth(Math.min(8, xEnd - x));
                roomCoord.setNHeight(Math.min(8, yEnd - y));
                initPresetRoomData(level, pDrlgMap, roomCoord, prest.getDwDt1Mask(), nFlags, 0);
            }
        }
        D2Log.debug("DRLGPRESET_BuildArea level=%d prest=%d file=%d pos=(%d,%d) size=%dx%d rooms=%d flags=0x%X",
                level.getLevelId(), pDrlgMap.getNLevelPrest(), pDrlgMap.getNPickedFile(),
                map.getNPosX(), map.getNPosY(), map.getNWidth(), map.getNHeight(),
                ((map.getNWidth() + 7) / 8) * ((map.getNHeight() + 7) / 8), nFlags);
    }

    private static D2DrlgRoom initPresetRoomData(D2DrlgLevel level, D2DrlgMapStrc map,
            D2DrlgCoord coord, int dt1Mask, int roomFlags, int presetFlags) {
        D2DrlgRoom room = DrlgDrlgRoom.allocRoomEx(level, D2DrlgTypes.DRLGTYPE_PRESET);
        if (room == null) return null;
        room.setDt1Mask(dt1Mask);
        room.setFlags(room.getFlags() | roomFlags);
        room.setNTileXPos(coord.getNPosX());
        room.setNTileYPos(coord.getNPosY());
        room.setNTileWidth(coord.getNWidth());
        room.setNTileHeight(coord.getNHeight());

        D2DrlgPresetRoomStrc presetRoom = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
        presetRoom.setPMap(map);
        presetRoom.setDwFlags(presetFlags);
        presetRoom.setNLevelPrest(map.getPLvlPrestTxtRecord().getDwDef());
        presetRoom.setNPickedFile(map.getNPickedFile());
        if (map.getPLvlPrestTxtRecord().getDwPopulate() == 0) {
            room.setFlags(room.getFlags() | D2DrlgRoomFlags.POPULATION_ZERO);
        }
        DrlgDrlgRoom.addRoomExToLevel(level, room);
        return room;
    }
}
