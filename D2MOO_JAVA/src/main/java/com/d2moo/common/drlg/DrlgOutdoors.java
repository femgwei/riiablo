package com.d2moo.common.drlg;

import com.d2moo.common.seed.Seed;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 户外模块
 * 对应 C++ 文件：DrlgOutdoors.cpp
 */
public class DrlgOutdoors {
    
    // 户外信息标志
    public static final int OUTDOOR_FLAG1 = 0x00000001;
    public static final int OUTDOOR_BRIDGE = 0x00000004;
    public static final int OUTDOOR_RIVER_OTHER = 0x00000008;
    public static final int OUTDOOR_RIVER = 0x00000010;
    public static final int OUTDOOR_CLIFFS = 0x00000020;
    public static final int OUTDOOR_OUT_CAVES = 0x00000040;
    public static final int OUTDOOR_SOUTHWEST = 0x00000080;
    public static final int OUTDOOR_NORTHWEST = 0x00000100;
    public static final int OUTDOOR_SOUTHEAST = 0x00000200;
    public static final int OUTDOOR_NORTHEAST = 0x00000400;
    
    /**
     * D2Common.0x6FD7EBA0
     * 分配户外信息
     * 被 DrlgDrlg 依赖
     */
    public static void allocOutdoorInfo(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        Object memPool = level.getDrlg().getMempool();
        D2DrlgOutdoorInfoStrc outdoorInfo = D2Pool.callocStrcPool(memPool, D2DrlgOutdoorInfoStrc.class);
        if (outdoorInfo == null) {
            outdoorInfo = new D2DrlgOutdoorInfoStrc();
        }
        level.setPresetOrOutdoorsOrMaze(outdoorInfo);
    }
    
    /**
     * D2Common.0x6FD7EBD0
     * 生成关卡
     * 被 DrlgDrlg 依赖
     */
    public static void generateLevel(D2DrlgLevel level) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoorInfo = level.getOutdoors();
        if (pOutdoorInfo == null) {
            D2Log.warning("DRLG_OUTDOOR missing outdoor info level=%d", level.getLevelId());
            return;
        }

        D2Log.debug("DRLG_OUTDOOR begin level=%d coord=(%d,%d %dx%d) type=%d levelType=%d flags=0x%X",
                level.getLevelId(), level.getLevelCoords().getNPosX(), level.getLevelCoords().getNPosY(),
                level.getLevelCoords().getNWidth(), level.getLevelCoords().getNHeight(),
                level.getDrlgType(), level.getLevelType(), pOutdoorInfo.getDwFlags());
        
        // 初始化户外信息尺寸
        pOutdoorInfo.setNWidth(0);
        pOutdoorInfo.setNHeight(0);
        pOutdoorInfo.setNGridWidth(level.getLevelCoords().getNWidth() / 8);
        pOutdoorInfo.setNGridHeight(level.getLevelCoords().getNHeight() / 8);
        D2Log.debug("DRLG_OUTDOOR grid level=%d size=%dx%d", level.getLevelId(),
                pOutdoorInfo.getNGridWidth(), pOutdoorInfo.getNGridHeight());
        
        // 初始化网格
        Object memPool = level.getDrlg().getMempool();
        DrlgDrlgGrid.initializeGridCells(memPool, pOutdoorInfo.getPGrid(0), 
                pOutdoorInfo.getNGridWidth(), pOutdoorInfo.getNGridHeight());
        DrlgDrlgGrid.initializeGridCells(memPool, pOutdoorInfo.getPGrid(1), 
                pOutdoorInfo.getNGridWidth(), pOutdoorInfo.getNGridHeight());
        DrlgDrlgGrid.initializeGridCells(memPool, pOutdoorInfo.getPGrid(2), 
                pOutdoorInfo.getNGridWidth(), pOutdoorInfo.getNGridHeight());
        DrlgDrlgGrid.initializeGridCells(memPool, pOutdoorInfo.getPGrid(3), 
                pOutdoorInfo.getNGridWidth(), pOutdoorInfo.getNGridHeight());
        
        // 创建顶点
        // 注意：getPVertex() 返回单个顶点，但 createVertices 需要数组
        // 这里需要创建一个数组来存储顶点
        D2DrlgVertexStrc[] pVertices = new D2DrlgVertexStrc[1];
        pVertices[0] = pOutdoorInfo.getPVertex();
        D2Log.debug("DRLG_OUTDOOR vertices begin level=%d roomData=%s", level.getLevelId(),
                pOutdoorInfo.getPRoomData() != null ? "yes" : "no");
        DrlgDrlgVer.createVertices(memPool, pVertices, 
                level.getLevelCoords(), (byte)0, pOutdoorInfo.getPRoomData());
        if (pVertices[0] != null) {
            pOutdoorInfo.setPVertex(pVertices[0]);
        }
        D2Log.debug("DRLG_OUTDOOR vertices created level=%d head=%s", level.getLevelId(),
                pOutdoorInfo.getPVertex() != null ? "yes" : "no");
        
        // 将顶点坐标除以 8（转换为网格坐标）
        D2DrlgVertexStrc pVertex = pOutdoorInfo.getPVertex();
        if (pVertex != null) {
            D2DrlgVertexStrc pCurrentVertex = pVertex;
            int vertexCount = 0;
            do {
                if (++vertexCount > 1024) {
                    throw new IllegalStateException("DRLG vertex list is not circular for level "
                            + level.getLevelId());
                }
                // DRLGVER_CreateVertices already returns level-local tile
                // coordinates, matching native D2MOO.  Only scale them to
                // outdoor 8x8 cells here.
                pCurrentVertex.setNPosX(pCurrentVertex.getNPosX() / 8);
                pCurrentVertex.setNPosY(pCurrentVertex.getNPosY() / 8);
                pCurrentVertex = pCurrentVertex.getPNext();
            } while (pCurrentVertex != null && pCurrentVertex != pVertex);
            D2Log.debug("DRLG_OUTDOOR vertices normalized level=%d count=%d", level.getLevelId(), vertexCount);
        }
        
        // 移除重复顶点（相同坐标的顶点合并）
        if (pVertex != null) {
            D2DrlgVertexStrc pCurrentVertex = pVertex;
            int vertexCount = 0;
            int removedCount = 0;
            do {
                if (++vertexCount > 1024) {
                    throw new IllegalStateException("DRLG duplicate-removal vertex list is not circular for level "
                            + level.getLevelId());
                }
                D2DrlgVertexStrc pNextVertex = pCurrentVertex.getPNext();
                if (pNextVertex != null
                        && pCurrentVertex.getNPosX() == pNextVertex.getNPosX()
                        && pCurrentVertex.getNPosY() == pNextVertex.getNPosY()) {
                    // Native also merges the closing vertex with the head.
                    // The previous guard skipped exactly that case and could
                    // leave a zero-length edge after tile->grid truncation.
                    if (pNextVertex == pVertex) {
                        pVertex = pCurrentVertex;
                        pOutdoorInfo.setPVertex(pVertex);
                    }
                    pCurrentVertex.setPNext(pNextVertex.getPNext());
                    pCurrentVertex.setDwFlags(pCurrentVertex.getDwFlags() | pNextVertex.getDwFlags());
                    pCurrentVertex.setNDirection(pNextVertex.getNDirection());
                    removedCount++;
                }
                pCurrentVertex = pCurrentVertex.getPNext();
            } while (pCurrentVertex != null && pCurrentVertex != pVertex);
            D2Log.debug("DRLG_OUTDOOR vertices deduplicated level=%d visits=%d removed=%d",
                    level.getLevelId(), vertexCount, removedCount);
        }
        
        // 根据 Act 初始化户外关卡
        byte nAct = DrlgDrlg.getActNoFromLevelId(level.getLevelId());
        switch (nAct) {
            case D2C_Acts.ACT_I:
                DrlgOutWild.initAct1OutdoorLevel(level);
                break;
            case D2C_Acts.ACT_II:
                DrlgOutDesr.initAct2OutdoorLevel(level);
                break;
            case D2C_Acts.ACT_III:
                DrlgOutPlace.initAct3OutdoorLevel(level);
                break;
            case D2C_Acts.ACT_IV:
                initAct4OutdoorLevel(level);
                break;
            case D2C_Acts.ACT_V:
                DrlgOutSiege.initAct5OutdoorLevel(level);
                break;
            default:
                break;
        }

        int[] grid2Flags = countGrid2Flags(pOutdoorInfo);
        D2Log.debug("DRLG_OUTDOOR grid2 afterInit level=%d total=%d unk00=%d unk07=%d unk08=%d picked=%d link=%d",
                level.getLevelId(), grid2Flags[0], grid2Flags[1], grid2Flags[2], grid2Flags[3],
                grid2Flags[4], grid2Flags[5]);
        
        // 根据关卡类型设置 DT1 掩码
        int dwDt1Mask = 0;
        switch (level.getLevelType()) {
            case D2LevelTypes.LVLTYPE_ACT1_WILDERNESS:
                dwDt1Mask = 0x44103;
                break;
            case D2LevelTypes.LVLTYPE_ACT3_JUNGLE:
                dwDt1Mask = 0x04;
                break;
            case D2LevelTypes.LVLTYPE_ACT2_DESERT:
            case D2LevelTypes.LVLTYPE_ACT3_KURAST:
            case D2LevelTypes.LVLTYPE_ACT4_MESA:
            case D2LevelTypes.LVLTYPE_ACT4_LAVA:
                dwDt1Mask = 0x01;
                break;
            case D2LevelTypes.LVLTYPE_ACT5_SIEGE:
            case D2LevelTypes.LVLTYPE_ACT5_BARRICADE:
                dwDt1Mask = 0x11;
                break;
            default:
                dwDt1Mask = 0x00;
                break;
        }
        
        // 遍历网格，创建房间或预设
        int nY = level.getLevelCoords().getNPosY();
        D2DrlgOutdoorPackedGrid2InfoStrc firstGrid2 = getPackedGrid2Info(pOutdoorInfo, 0, 0);
        D2Log.debug("DRLG_OUTDOOR grid2 sample level=%d packed=0x%X unk00=%s unk07=%s unk08=%s picked=%s",
                level.getLevelId(), firstGrid2.getNPackedValue(), firstGrid2.isNUnkb00(),
                firstGrid2.isNUnkb07(), firstGrid2.isNUnkb08(), firstGrid2.isBHasPickedFile());
        int createRoomCalls = 0;
        int skippedBlank = 0;
        int pickedFileCalls = 0;
        for (int j = 0; j < pOutdoorInfo.getNGridHeight(); ++j) {
            int nX = level.getLevelCoords().getNPosX();
            for (int i = 0; i < pOutdoorInfo.getNGridWidth(); ++i) {
                int a6a = DrlgDrlgGrid.getGridEntry(pOutdoorInfo.getPGrid(1), i, j);
                D2DrlgOutdoorPackedGrid2InfoStrc tGrid2PackedInfo = getPackedGrid2Info(pOutdoorInfo, i, j);
                
                if (tGrid2PackedInfo.isBHasPickedFile()) {
                    pickedFileCalls++;
                    int v14 = DrlgDrlgGrid.getGridEntry(pOutdoorInfo.getPGrid(0), i, j);
                    if (v14 != 0) {
                        D2DrlgCoord pDrlgCoord = new D2DrlgCoord();
                        pDrlgCoord.setNWidth(0);
                        pDrlgCoord.setNPosX(nX);
                        pDrlgCoord.setNHeight(0);
                        pDrlgCoord.setNPosY(nY);
                        
                        D2DrlgMapStrc pDrlgMap = DrlgPreset.allocDrlgMap(level, v14, pDrlgCoord, level.getSeed());
                        if (pDrlgMap != null) {
                            DrlgPreset.setPickedFileInDrlgMap(pDrlgMap, tGrid2PackedInfo.getNPickedFile());
                            DrlgPreset.buildArea(level, pDrlgMap, a6a, 0);
                        }
                    }
                } else if (!tGrid2PackedInfo.isNUnkb08()) {
                    createRoomCalls++;
                    DrlgOutPlace.createOutdoorRoomEx(level, nX, nY, 8, 8, a6a, 
                            tGrid2PackedInfo.getNPackedValue(), 
                            DrlgDrlgGrid.getGridEntry(pOutdoorInfo.getPGrid(3), i, j), 
                        dwDt1Mask);
                } else {
                    skippedBlank++;
                }
                
                nX += 8;
            }
            nY += 8;
        }
        D2Log.debug("DRLG_OUTDOOR end level=%d rooms=%d grid=%dx%d vertex=%s createCalls=%d picked=%d skippedBlank=%d",
                level.getLevelId(), level.getRooms(), pOutdoorInfo.getNGridWidth(),
                pOutdoorInfo.getNGridHeight(), pOutdoorInfo.getPVertex() != null ? "yes" : "no",
                createRoomCalls, pickedFileCalls, skippedBlank);
    }

    private static int[] countGrid2Flags(D2DrlgOutdoorInfoStrc outdoors) {
        int[] counts = new int[6];
        if (outdoors == null) return counts;
        for (int y = 0; y < outdoors.getNGridHeight(); y++) {
            for (int x = 0; x < outdoors.getNGridWidth(); x++) {
                D2DrlgOutdoorPackedGrid2InfoStrc info = getPackedGrid2Info(outdoors, x, y);
                counts[0]++;
                if (info.isNUnkb00()) counts[1]++;
                if (info.isNUnkb07()) counts[2]++;
                if (info.isNUnkb08()) counts[3]++;
                if (info.isBHasPickedFile()) counts[4]++;
                if (info.isBLvlLink()) counts[5]++;
            }
        }
        return counts;
    }
    
    /**
     * D2Common.0x6FD7F9B0
     * 初始化 Act4 户外关卡
     */
    public static void initAct4OutdoorLevel(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 静态常量数组：Mesa 预设 ID
        final int[] nMesaLvlPrestIds = {
            D2LvlPrestIds.LVLPREST_ACT4_MESA_1_24X24,
            D2LvlPrestIds.LVLPREST_ACT4_MESA_2_24X24,
            D2LvlPrestIds.LVLPREST_ACT4_MESA_3_24X24
        };
        
        // 静态常量数组：Pits 预设 ID
        final int[] nPitsLvlPrestIds = {
            D2LvlPrestIds.LVLPREST_ACT4_PITS_1_16X16,
            D2LvlPrestIds.LVLPREST_ACT4_PITS_2_16X16,
            D2LvlPrestIds.LVLPREST_ACT4_PITS_2_16X16
        };
        
        // 静态常量数组：Lava 预设 ID
        final int[] nLavaLvlPrestIds = {
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_ARM_N,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_ARM_W, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_HEART,
            D2LvlPrestIds.LVLPREST_ACT4_DIABLO_ARM_E, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_ARM_S,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_ENTRY,
            D2LvlPrestIds.LVLPREST_ACT4_LAVA_X, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X
        };
        
        if (level.getLevelId() == D2LevelIds.LEVEL_CHAOSSANCTUM) {
            DrlgOutPlace.setOutGridLinkFlags(level);
        } else {
            DrlgOutPlace.setOutGridLinkFlags(level);
            DrlgOutPlace.placeAct1245OutdoorBorders(level);
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = level.getOutdoors();
        if (pOutdoors == null) {
            return;
        }
        
        // 处理 Act4 特殊关卡
        if (level.getLevelId() >= D2LevelIds.LEVEL_OUTERSTEPPES 
                && level.getLevelId() <= D2LevelIds.LEVEL_CITYOFTHEDAMNED) {
            // 处理堡垒过渡预设
            if ((pOutdoors.getDwFlags() & 0x400000) != 0) {
                spawnOutdoorLevelPresetEx(level, 0, 1, D2LvlPrestIds.LVLPREST_ACT4_FORTRESS_TRANSITION, -1, false);
            }
            if ((pOutdoors.getDwFlags() & 0x800000) != 0) {
                spawnOutdoorLevelPresetEx(level, 0, 4, D2LvlPrestIds.LVLPREST_ACT4_FORTRESS_TRANSITION, -1, false);
            }
            
            // 添加次要边界
            addAct124SecondaryBorder(level, 1, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_1);
            addAct124SecondaryBorder(level, 2, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_1);
            addAct124SecondaryBorder(level, 3, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_1);
            
            // 处理城市诅咒的特殊预设
            if (level.getLevelId() == D2LevelIds.LEVEL_CITYOFTHEDAMNED) {
                spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT4_MESA_WARP, -1, 0, (char)15);
            }
            
            // 获取 Mesa 和 Pits 预设 ID
            int nMesaId = nMesaLvlPrestIds[level.getLevelId() - 104];
            int nPitId = nPitsLvlPrestIds[level.getLevelId() - 104];
            
            // 生成 Mesa 预设
            spawnOutdoorLevelPreset(level, nMesaId + 0, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 1, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 1, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 2, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 2, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 3, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 3, -1, 0, (char)15);
            
            // 处理绝望平原的特殊预设
            if (level.getLevelId() == D2LevelIds.LEVEL_PLAINSOFDESPAIR) {
                spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT4_MESA_2_IZUAL, -1, 0, (char)15);
            }
            
            spawnOutdoorLevelPreset(level, nMesaId + 4, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 4, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 4, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nMesaId + 4, -1, 0, (char)15);
            
            // 生成 Pits 预设
            spawnOutdoorLevelPreset(level, nPitId + 0, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 1, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 1, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 2, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 2, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 3, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 3, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 3, -1, 0, (char)15);
            spawnOutdoorLevelPreset(level, nPitId + 3, -1, 0, (char)15);
        } else if (level.getLevelId() == D2LevelIds.LEVEL_CHAOSSANCTUM) {
            // 生成 Lava 预设
            for (int i = 0; i < nLavaLvlPrestIds.length; ++i) {
                spawnOutdoorLevelPresetEx(level, 3 * (i % 5), 3 * (i / 5), nLavaLvlPrestIds[i], -1, false);
            }
        }
    }
    
    /**
     * D2Common.0x6FD7EEE0
     * 释放户外信息
     * 被 DrlgDrlg 依赖
     */
    public static void freeOutdoorInfo(D2DrlgLevel level, boolean keepRoomData) {
        if (level == null || level.getDrlg() == null) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = level.getOutdoors();
        if (pOutdoors == null) {
            return;
        }
        
        Object memPool = level.getDrlg().getMempool();
        
        // 清除标志位（0x20 和 0x40）
        if ((pOutdoors.getDwFlags() & 0x20) != 0) {
            pOutdoors.setDwFlags(pOutdoors.getDwFlags() ^ 0x20);
        }
        
        if ((pOutdoors.getDwFlags() & 0x40) != 0) {
            pOutdoors.setDwFlags(pOutdoors.getDwFlags() ^ 0x40);
        }
        
        // 释放所有网格
        for (int i = 0; i < 4; ++i) {
            DrlgDrlgGrid.freeGrid(memPool, pOutdoors.getPGrid(i));
        }
        
        // 释放主顶点链表
        // 注意：freeVertices 需要 Object[] 参数，其中 [0] 是指向顶点链表的指针
        // 在 Java 中，我们需要使用包装数组来模拟指针
        D2DrlgVertexStrc pVertex = pOutdoors.getPVertex();
        if (pVertex != null) {
            int freed = freeVertexList(memPool, pVertex);
            D2Log.debug("DRLG_OUTDOOR free mainVertices level=%d count=%d",
                    level.getLevelId(), freed);
            pOutdoors.setPVertex(null);
        }
        
        // 释放路径起点顶点链表
        for (int i = 0; i < 6; ++i) {
            D2DrlgVertexStrc pPathStart = pOutdoors.getPPathStarts(i);
            if (pPathStart != null) {
                int freed = freeVertexList(memPool, pPathStart);
                D2Log.debug("DRLG_OUTDOOR free pathVertices level=%d path=%d count=%d",
                        level.getLevelId(), i, freed);
                pOutdoors.setPPathStarts(i, null);
            }
        }
        
        // 清零顶点数组（对应 C++ 中的 memset）
        // pVertices[0-5], pVertices[6-11], pVertices[12-17], pVertices[18-23]
        for (int i = 0; i < 24; ++i) {
            D2DrlgVertexStrc vertex = pOutdoors.getPVertices(i);
            if (vertex != null) {
                vertex.setNPosX(0);
                vertex.setNPosY(0);
                vertex.setNDirection((byte)0);
                vertex.setDwFlags(0);
                vertex.setPNext(null);
            }
        }
        
        pOutdoors.setNVertices(0);
        
        // 如果不保留房间数据，释放房间数据和户外信息本身
        if (!keepRoomData) {
            // 释放房间数据（D2DrlgOrth 链表）
            D2DrlgOrth pRoomData = pOutdoors.getPRoomData();
            while (pRoomData != null) {
                D2DrlgOrth pNext = pRoomData.getPNext();
                D2Pool.freePool(memPool, pRoomData);
                pRoomData = pNext;
            }
            pOutdoors.setPRoomData(null);
            
            // 释放户外信息本身
            D2Pool.freePool(memPool, pOutdoors);
            level.setPresetOrOutdoorsOrMaze(null);
        }
    }

    /** Frees either a null-terminated or circular Java vertex list once. */
    private static int freeVertexList(Object memPool, D2DrlgVertexStrc head) {
        java.util.IdentityHashMap<D2DrlgVertexStrc, Boolean> visited =
                new java.util.IdentityHashMap<>();
        D2DrlgVertexStrc current = head;
        while (current != null && visited.put(current, Boolean.TRUE) == null) {
            D2DrlgVertexStrc next = current.getPNext();
            current.setPNext(null);
            D2Pool.freePool(memPool, current);
            current = next;
        }
        return visited.size();
    }
    
    /**
     * D2Common.0x6FD7DEF0
     * 生成户外关卡预设（扩展）
     * 在指定网格区域设置 grid[0]/grid[2] 的预设与 picked 信息。
     */
    public static void spawnOutdoorLevelPresetEx(D2DrlgLevel level, int x, int y,
            int nLevelPrestId, int nPickedFile, boolean border) {
        if (level == null) {
            return;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;

        int nSizeX = DrlgPreset.getSizeX(nLevelPrestId) / 8;
        int nSizeY = DrlgPreset.getSizeY(nLevelPrestId) / 8;

        // nPickedFile == -1：从 level.pBuild 链表中查找或创建该预设的 D2DrlgBuildStrc，轮转 nRand 得到 nPickedFile
        if (nPickedFile == -1) {
            D2DrlgBuildStrc pDrlgBuild = level.getBuild();
            while (pDrlgBuild != null) {
                if (pDrlgBuild.getNPreset() == nLevelPrestId) {
                    break;
                }
                pDrlgBuild = pDrlgBuild.getPNext();
            }

            if (pDrlgBuild == null) {
                com.d2moo.common.datatbls.D2LvlPrestTxt pLvlPrestTxtRecord =
                        com.d2moo.common.datatbls.DataTbls.getLvlPrestTxtRecord(nLevelPrestId);
                if (pLvlPrestTxtRecord == null) {
                    nPickedFile = 0;
                } else {
                    Object memPool = level.getDrlg() != null ? level.getDrlg().getMempool() : null;
                    pDrlgBuild = D2Pool.callocStrcPool(memPool, D2DrlgBuildStrc.class);
                    if (pDrlgBuild != null) {
                        pDrlgBuild.setNPreset(pLvlPrestTxtRecord.getDwDef());
                        pDrlgBuild.setNDivisor(pLvlPrestTxtRecord.getDwFiles());
                        int nFiles = pLvlPrestTxtRecord.getDwFiles();
                        int nRandVal = nFiles > 0
                                ? com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nFiles)
                                : 0;
                        pDrlgBuild.setNRand(nRandVal);
                        pDrlgBuild.setPNext(level.getBuild());
                        level.setBuild(pDrlgBuild);
                    }
                }
            }

            if (pDrlgBuild != null) {
                int nDiv = pDrlgBuild.getNDivisor();
                int nRandVal = (pDrlgBuild.getNRand() + 1) % (nDiv > 0 ? nDiv : 1);
                pDrlgBuild.setNRand(nRandVal);
                nPickedFile = nRandVal;
            } else if (nPickedFile == -1) {
                nPickedFile = 0;
            }
        }

        for (int j = y; j < y + nSizeY; ++j) {
            for (int i = x; i < x + nSizeX; ++i) {
                D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                tPackedInfo.setBHasPickedFile(true);
                tPackedInfo.setNPickedFile(nPickedFile & 0x0F);

                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), i, j, 0x000F0000, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), i, j, tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);

                if (border && ((nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1 && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_12)
                        || (nLevelPrestId >= D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_1 && nLevelPrestId <= D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_12))) {
                    tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                    tPackedInfo.setNUnkb00(true);
                    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), i, j, tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                }

                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(0), i, j, 0, DrlgDrlgGrid.FlagOperation.OVERWRITE);
            }
        }

        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(0), x, y, nLevelPrestId, DrlgDrlgGrid.FlagOperation.OVERWRITE);
    }
    
    /**
     * D2Common.0x6FD7E0F0
     * 在远离指定坐标的位置生成预设
     * 遍历网格，找到距离指定坐标最远且有效的预设位置
     * @param level 关卡
     * @param pDrlgCoord 参考坐标（预设应远离此坐标）
     * @param nLvlPrestId 预设 ID
     * @param nRand 随机数（-1 表示使用关卡种子）
     * @param nOffset 偏移量
     * @param nFlags 标志
     * @return 是否成功生成
     */
    public static boolean spawnPresetFarAway(D2DrlgLevel level, D2DrlgCoord pDrlgCoord,
            int nLvlPrestId, int nRand, int nOffset, char nFlags) {
        if (level == null || pDrlgCoord == null) {
            return false;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = level.getOutdoors();
        if (pOutdoors == null) {
            return false;
        }
        
        int nWidth = pOutdoors.getNGridWidth() - 2;
        int nHeight = pOutdoors.getNGridHeight() - 2;
        
        // 随机起始位置
        int nRandX = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nWidth);
        int nRandY = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nHeight);
        
        // 计算参考坐标的中心点
        int nBaseX = pDrlgCoord.getNPosX() + pDrlgCoord.getNWidth() / 2;
        int nBaseY = pDrlgCoord.getNPosY() + pDrlgCoord.getNHeight() / 2;
        
        int nX = -1;
        int nY = -1;
        int nMax = 0;
        
        // 遍历所有可能的网格位置（使用随机起始位置）
        for (int i = 0; i <= nHeight; ++i) {
            int nPosY = (i + nRandY) % nHeight + 1;
            
            for (int j = 0; j <= nWidth; ++j) {
                int nPosX = (j + nRandX) % nWidth + 1;
                
                // 测试该位置是否可以放置预设
                if (testOutdoorLevelPreset(level, nPosX, nPosY, nLvlPrestId, nOffset, (byte)nFlags)) {
                    // 计算到参考坐标的距离（使用加权距离公式）
                    int nAbsX = 8 * nPosX - nBaseX + level.getLevelCoords().getNPosX() + 4;
                    if (nAbsX < 0) {
                        nAbsX = -nAbsX;
                    }
                    
                    int nAbsY = 8 * nPosY - nBaseY + level.getLevelCoords().getNPosY() + 4;
                    if (nAbsY < 0) {
                        nAbsY = -nAbsY;
                    }
                    
                    // 加权距离：如果 X 距离 <= Y 距离，使用 nAbsX + 2 * nAbsY，否则使用 nAbsY + 2 * nAbsX
                    int nTemp;
                    if (nAbsX <= nAbsY) {
                        nTemp = nAbsX + 2 * nAbsY;
                    } else {
                        nTemp = nAbsY + 2 * nAbsX;
                    }
                    
                    // 选择距离最远的位置
                    if (nMax < nTemp / 2) {
                        nMax = nTemp / 2;
                        nX = nPosX;
                        nY = nPosY;
                    }
                }
            }
        }
        
        // 如果找到有效位置，生成预设
        if (nX == -1 || nY == -1) {
            return false;
        } else {
            spawnOutdoorLevelPresetEx(level, nX, nY, nLvlPrestId, nRand, false);
            return true;
        }
    }
    
    /**
     * D2Common.0x6FD7E330
     * 生成户外关卡预设
     * 在网格中随机查找位置并生成预设
     * @param level 关卡
     * @param nLevelPrestId 预设 ID
     * @param nRand 随机数（-1 表示使用关卡种子）
     * @param nOffset 偏移量
     * @param nFlags 标志
     * @return 是否成功生成
     */
    public static boolean spawnOutdoorLevelPreset(D2DrlgLevel level, int nLevelPrestId, 
            int nRand, int nOffset, char nFlags) {
        if (level == null) {
            return false;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = level.getOutdoors();
        if (pOutdoors == null) {
            return false;
        }
        
        // 计算可用区域（排除边界）
        int nWidth = pOutdoors.getNGridWidth() - 2;
        int nArea = nWidth * (pOutdoors.getNGridHeight() - 2);
        
        if (nArea <= 0) {
            return false;
        }
        
        // 创建坐标数组
        D2Coord[] pCoord = new D2Coord[256];
        for (int i = 0; i < nArea && i < 256; ++i) {
            pCoord[i] = new D2Coord(i % nWidth, i / nWidth);
        }
        
        // 随机化坐标数组
        for (int i = 0; i < nArea && i < 256; ++i) {
            int nRand1 = Seed.rollLimitedRandomNumber(level.getSeed(), nArea);
            int nRand2 = Seed.rollLimitedRandomNumber(level.getSeed(), nArea);
            
            int nX = pCoord[nRand1].getX();
            int nY = pCoord[nRand1].getY();
            
            pCoord[nRand1].setX(pCoord[nRand2].getX());
            pCoord[nRand1].setY(pCoord[nRand2].getY());
            
            pCoord[nRand2].setX(nX);
            pCoord[nRand2].setY(nY);
        }
        
        // 遍历坐标，尝试生成预设
        for (int i = 0; i < nArea && i < 256; ++i) {
            int nX = pCoord[i].getX() + 1;  // +1 因为排除了边界
            int nY = pCoord[i].getY() + 1;
            
            if (testOutdoorLevelPreset(level, nX, nY, nLevelPrestId, nOffset, (byte)nFlags)) {
                spawnOutdoorLevelPresetEx(level, nX, nY, nLevelPrestId, nRand, false);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * D2Common.0x6FD7E4D0
     * 随机生成户外 DS1 预设
     * 优先在 nUnkb07 标志的格子周围查找位置，如果找不到则回退到普通生成
     * @param level 关卡
     * @param nLvlPrestId 预设 ID
     * @param nRand 随机数（-1 表示使用关卡种子）
     * @return 是否成功生成
     */
    public static boolean spawnRandomOutdoorDS1(D2DrlgLevel level, int nLvlPrestId, int nRand) {
        if (level == null) {
            return false;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = level.getOutdoors();
        if (pOutdoors == null) {
            return false;
        }
        
        // 8 方向偏移数组
        final byte[] nOffsetX = { -1, 0, 0, 1, -1, 1, 1, -1 };
        final byte[] nOffsetY = { 0, -1, 1, 0, -1, 1, -1, 1 };
        
        int nWidth = pOutdoors.getNGridWidth() - 2;
        int nArea = nWidth * (pOutdoors.getNGridHeight() - 2);
        
        if (nArea > 0) {
            // 创建坐标数组（最多 256 个）
            int maxCoords = Math.min(nArea, 256);
            int[][] pCoord = new int[maxCoords][2];
            
            // 初始化坐标数组
            for (int i = 0; i < maxCoords; ++i) {
                pCoord[i][0] = i % nWidth;
                pCoord[i][1] = i / nWidth;
            }
            
            // Fisher-Yates 洗牌算法（随机交换坐标）
            for (int i = 0; i < maxCoords; ++i) {
                int nRand1 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), maxCoords);
                int nRand2 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), maxCoords);
                
                // 交换坐标
                int nTempX = pCoord[nRand1][0];
                int nTempY = pCoord[nRand1][1];
                pCoord[nRand1][0] = pCoord[nRand2][0];
                pCoord[nRand1][1] = pCoord[nRand2][1];
                pCoord[nRand2][0] = nTempX;
                pCoord[nRand2][1] = nTempY;
            }
            
            // 遍历坐标数组，查找 nUnkb07 标志的格子
            for (int i = 0; i < maxCoords; ++i) {
                int nX = pCoord[i][0] + 1;
                int nY = pCoord[i][1] + 1;
                
                // 检查该格子是否有 nUnkb07 标志
                D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = getPackedGrid2Info(pOutdoors, nX, nY);
                if (packedInfo.isNUnkb07()) {
                    // 在 8 个方向查找有效位置
                    for (int j = 0; j < 8; ++j) {
                        int nPosX = nX + nOffsetX[j];
                        int nPosY = nY + nOffsetY[j];
                        
                        // 测试该位置是否可以放置预设
                        if (testOutdoorLevelPreset(level, nPosX, nPosY, nLvlPrestId, 0, (byte)((char)15))) {
                            spawnOutdoorLevelPresetEx(level, nPosX, nPosY, nLvlPrestId, nRand, false);
                            return true;
                        }
                    }
                }
            }
            
            // 如果找不到 nUnkb07 标志的格子，回退到普通生成
            return spawnOutdoorLevelPreset(level, nLvlPrestId, nRand, 0, (char)15);
        }
        
        return false;
    }
    
    /**
     * D2Common.0x6FD7DD00
     * 从网格单元格获取预设索引
     */
    public static int getPresetIndexFromGridCell(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return 0;
        }

        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return 0;
        }

        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = getPackedGrid2Info(outdoors, x, y);
        if (packedInfo.isBHasPickedFile()) {
            // 对应 C++: DRLGGRID_GetGridEntry(&pLevel->pOutdoors->pGrid[0], nX, nY);
            return DrlgDrlgGrid.getGridEntry(outdoors.getPGrid(0), x, y);
        }

        return 0;
    }
    
    /**
     * D2Common.0x6FD7DDD0
     * 测试网格单元格生成是否有效
     */
    public static boolean testGridCellSpawnValid(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return false;
        }

        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return false;
        }

        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = getPackedGrid2Info(outdoors, x, y);

        // 对应 C++:
        // return !(tPackedInfo.nUnkb00 || tPackedInfo.nUnkb07 || tPackedInfo.nUnkb08
        //          || tPackedInfo.bHasPickedFile || tPackedInfo.nUnkb11 || tPackedInfo.nUnkb12);
        return !(packedInfo.isNUnkb00()
                || packedInfo.isNUnkb07()
                || packedInfo.isNUnkb08()
                || packedInfo.isBHasPickedFile()
                || packedInfo.isNUnkb11()
                || packedInfo.isNUnkb12());
    }

    /**
     * D2Common.0x6FD7DC20
     * 获取户外链接可见性标志
     * 对应 C++：DRLGOUTDOORS_GetOutLinkVisFlag
     */
    public static int getOutLinkVisFlag(D2DrlgLevel level, D2DrlgVertexStrc pDrlgVertex) {
        if (level == null || pDrlgVertex == null) {
            return 0;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return 0;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        D2DrlgCoord levelCoords = level.getLevelCoords();
        if (levelCoords == null) {
            return 0;
        }

        // C++: static const D2CoordStrc pOffsetCoords[] = { {-4,4}, {4,-4}, {12,4}, {4,12} };
        final int[][] pOffsetCoords = { { -4, 4 }, { 4, -4 }, { 12, 4 }, { 4, 12 } };

        int vx = pDrlgVertex.getNPosX();
        int vy = pDrlgVertex.getNPosY();
        int nGridWidth = outdoors.getNGridWidth();
        int nGridHeight = outdoors.getNGridHeight();
        int nIndex = getOutLinkDirectionIndex(vx, vy, nGridWidth, nGridHeight);
        if (nIndex < 0) {
            return 0;
        }

        int nX = levelCoords.getNPosX() + pOffsetCoords[nIndex][0] + 8 * vx;
        int nY = levelCoords.getNPosY() + pOffsetCoords[nIndex][1] + 8 * vy;

        for (D2DrlgOrth pRoomData = outdoors.getPRoomData(); pRoomData != null; pRoomData = pRoomData.getPNext()) {
            if (pRoomData.getNDirection() != nIndex) {
                continue;
            }
            D2DrlgCoord pBox = pRoomData.getPBox();
            if (pBox == null || !DrlgDrlgRoom.areXYInsideCoordinates(pBox, nX, nY)) {
                continue;
            }
            if (!pRoomData.isBInit()) {
                int[] pLevelIds = DrlgDrlgRoom.getVisArrayFromLevelId(level.getDrlg(), level.getLevelId());
                D2DrlgLevel pRoomLevel = pRoomData.getPLevel();
                if (pRoomLevel != null && pLevelIds != null) {
                    for (int i = 0; i < pLevelIds.length && i < 8; ++i) {
                        if (pLevelIds[i] == pRoomLevel.getLevelId()) {
                            return 1 << (i + 4);
                        }
                    }
                }
            }
            return 0;
        }
        return 0;
    }

    static int getOutLinkDirectionIndex(int x, int y, int gridWidth, int gridHeight) {
        if (x == 0) {
            // Native BOOL assignment: nIndex = (y == 0).
            return y == 0 ? 1 : 0;
        } else if (y == 0) {
            return (x == gridWidth - 1 ? 1 : 0) + 1;
        } else if (x == gridWidth - 1) {
            return (y == gridHeight - 1 ? 1 : 0) + 2;
        } else if (y == gridHeight - 1) {
            return 3;
        }
        return -1;
    }

    /**
     * D2Common.0x6FD7DD40
     * 修改相邻预设网格单元格（将指定格子的 grid[0] 和 grid[2] 覆写为 0）
     */
    public static void alterAdjacentPresetGridCells(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(0), x, y, 0, DrlgDrlgGrid.FlagOperation.OVERWRITE);
        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), x, y, 0, DrlgDrlgGrid.FlagOperation.OVERWRITE);
    }

    /**
     * D2Common.0x6FD7DD70
     * 将指定网格单元格设为“空白”（grid[0]=0，grid[2] 仅置 nUnkb08）
     */
    public static void setBlankGridCell(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;
        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(0), x, y, 0, DrlgDrlgGrid.FlagOperation.OVERWRITE);
        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
        tPackedInfo.setNUnkb08(true);
        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), x, y, tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OVERWRITE);
    }

    /**
     * D2Common.0x6FD7DDB0
     * 测试网格单元格是否为“非关卡链接”（bLvlLink == 0 时返回 1，否则 0）
     */
    public static int testGridCellNonLvlLink(D2DrlgLevel level, int x, int y) {
        if (level == null) {
            return 0;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return 0;
        }
        D2DrlgOutdoorPackedGrid2InfoStrc packed = getPackedGrid2Info((D2DrlgOutdoorInfoStrc) outdoorsObj, x, y);
        return packed.isBLvlLink() ? 0 : 1;
    }

    /**
     * D2Common.0x6FD7DDF0
     * 测试户外关卡预设是否可放置于指定区域（区域内每格均在网格内且 testGridCellSpawnValid）
     */
    public static boolean testOutdoorLevelPreset(D2DrlgLevel level, int nX, int nY,
            int nLevelPrestId, int nOffset, byte nFlags) {
        if (level == null || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return false;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        D2DrlgGridStrc grid2 = outdoors.getPGrid(2);
        if (grid2 == null) {
            return false;
        }

        int nXStart = nX;
        int nYStart = nY;
        int nSizeX;
        int nSizeY;

        if (nLevelPrestId != 0) {
            nSizeX = DrlgPreset.getSizeX(nLevelPrestId) / 8;
            nSizeY = DrlgPreset.getSizeY(nLevelPrestId) / 8;
        } else {
            nSizeX = 1;
            nSizeY = 1;
        }

        if (nOffset != 0) {
            if ((nFlags & 1) != 0) {
                nYStart -= nOffset;
                nSizeY += nOffset;
            }
            if ((nFlags & 2) != 0) {
                nSizeX += nOffset;
            }
            if ((nFlags & 4) != 0) {
                nSizeY += nOffset;
            }
            if ((nFlags & 8) != 0) {
                nXStart -= nOffset;
                nSizeX += nOffset;
            }
        }

        int nXEnd = nXStart + nSizeX;
        int nYEnd = nYStart + nSizeY;

        for (int i = nYStart; i < nYEnd; ++i) {
            for (int j = nXStart; j < nXEnd; ++j) {
                if (!DrlgDrlgGrid.isPointInsideGridArea(grid2, j, i)
                        || !testGridCellSpawnValid(level, j, i)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * D2Common.0x6FD7EB20
     * 添加 Act1/2/4 次要边界（填充 D2UnkOutdoorStrc 并调用 DrlgTileSub.addSecondaryBorder）
     */
    public static void addAct124SecondaryBorder(D2DrlgLevel level, int nLvlSubId, int nLevelPrestId) {
        if (level == null || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();

        D2UnkOutdoorStrc a1 = new D2UnkOutdoorStrc();
        a1.setPLevel(level);
        // C++ field_4 = &pOutdoors->nWidth，后续用 field_4[2]/[3] 作网格宽高
        a1.setField_4(new int[] { outdoors.getNWidth(), outdoors.getNHeight(), outdoors.getNGridWidth(), outdoors.getNGridHeight() });
        a1.setPGrid1(outdoors.getPGrid(0));
        a1.setPGrid2(outdoors.getPGrid(2));
        a1.setNLevelPrestId(nLevelPrestId);
        a1.setField_14(-1);
        a1.setNLvlSubId(nLvlSubId);
        a1.setField_1C(DrlgOutdoors::testGridCellNonLvlLink);
        a1.setField_20(DrlgOutdoors::testOutdoorLevelPreset);
        a1.setField_2C(DrlgOutdoors::alterAdjacentPresetGridCells);
        a1.setField_30(DrlgOutdoors::setBlankGridCell);
        a1.setField_34((lvl, x, y, levelPrestId, rand, a6) -> {
            spawnOutdoorLevelPresetEx(lvl, x, y, levelPrestId, rand, a6);
        });

        DrlgTileSub.addSecondaryBorder(a1);
    }

    /**
     * D2Common.0x6FD7E6D0
     * 生成 Act1/2 传送点
     */
    public static void spawnAct12Waypoint(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;

        // 冷原（Cold Plains）有特殊逻辑：优先在通往血荒地的连接处生成
        if (level.getLevelId() == D2LevelIds.LEVEL_COLDPLAINS) {
            int[] visArray = DrlgDrlgRoom.getVisArrayFromLevelId(level.getDrlg(),
                    D2LevelIds.LEVEL_COLDPLAINS);
            int flags = 0;
            for (int i = 0; i < 8 && visArray != null; ++i) {
                if (visArray[i] == D2LevelIds.LEVEL_BLOODMOOR) {
                    flags = 1 << (i + 4);
                    break;
                }
            }

            int gridHeight = outdoors.getNGridHeight();
            int gridWidth = outdoors.getNGridWidth();
            for (int y = 0; y < gridHeight; ++y) {
                for (int x = 0; x < gridWidth; ++x) {
                    if ((DrlgDrlgGrid.getGridEntry(outdoors.getPGrid(1), x, y) & flags) != 0
                            && DrlgOutdoors.testGridCellNonLvlLink(level, x, y) == 0) {
                        // 避免落在边界上
                        if (x == 0) {
                            x = 1;
                        }
                        if (y == 0) {
                            y = 1;
                        }
                        if (x == gridWidth - 1) {
                            --x;
                        }
                        if (y == gridHeight - 1) {
                            --y;
                        }

                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(1), x, y, 0x20000,
                                DrlgDrlgGrid.FlagOperation.OR);
                        D2DrlgOutdoorPackedGrid2InfoStrc packedInfo =
                                new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                        packedInfo.setNUnkb11(true);
                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), x, y,
                                packedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                        return;
                    }
                }
            }
        }

        // 一般情况：在可用的网格中打乱坐标后随机选择
        int nWidth = outdoors.getNGridWidth() - 2;
        int nHeight = outdoors.getNGridHeight() - 2;
        int nArea = nWidth * nHeight;
        if (nArea <= 0) {
            return;
        }

        D2Coord[] coords = new D2Coord[nArea];
        for (int i = 0; i < nArea; ++i) {
            coords[i] = new D2Coord(i % nWidth, i / nWidth);
        }

        // 洗牌
        for (int i = 0; i < nArea; ++i) {
            int nRand1 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nArea);
            int nRand2 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nArea);

            D2Coord c1 = coords[nRand1];
            D2Coord c2 = coords[nRand2];
            coords[nRand1] = c2;
            coords[nRand2] = c1;
        }

        for (int i = 0; i < nArea; ++i) {
            int x = coords[i].getX() + 1;
            int y = coords[i].getY() + 1;

            if (DrlgOutdoors.testGridCellSpawnValid(level, x, y)) {
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(1), x, y, 0x10000,
                        DrlgDrlgGrid.FlagOperation.OR);
                D2DrlgOutdoorPackedGrid2InfoStrc packedInfo =
                        new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                packedInfo.setNUnkb11(true);
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), x, y,
                        packedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                break;
            }
        }
    }

    /**
     * D2Common.0x6FD7E940
     * 生成 Act1/2 神殿
     */
    public static void spawnAct12Shrines(D2DrlgLevel level, int nShrines) {
        if (level == null || nShrines <= 0) {
            return;
        }
        Object outdoorsObj = level.getPresetOrOutdoorsOrMaze();
        if (!(outdoorsObj instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) outdoorsObj;

        // 对应 C++ 的 dword_6FDCF948
        final int[] shrineFlags = { 0x1000, 0x2000, 0x4000, 0x8000 };

        int nWidth = outdoors.getNGridWidth() - 2;
        int nHeight = outdoors.getNGridHeight() - 2;
        int nArea = nWidth * nHeight;
        if (nArea <= 0) {
            return;
        }

        int nIndex = (int) (com.d2moo.common.seed.Seed.rollRandomNumber(level.getSeed()) & 3);

        D2Coord[] coords = new D2Coord[nArea];
        for (int i = 0; i < nArea; ++i) {
            coords[i] = new D2Coord(i % nWidth, i / nWidth);
        }

        // 洗牌
        for (int i = 0; i < nArea; ++i) {
            int nRand1 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nArea);
            int nRand2 = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(level.getSeed(), nArea);

            D2Coord c1 = coords[nRand1];
            D2Coord c2 = coords[nRand2];
            coords[nRand1] = c2;
            coords[nRand2] = c1;
        }

        for (int i = 0; i < nArea && nShrines > 0; ++i) {
            int x = coords[i].getX() + 1;
            int y = coords[i].getY() + 1;

            if (DrlgOutdoors.testGridCellSpawnValid(level, x, y)) {
                // 在 grid[1] 上打标志位
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(1), x, y,
                        shrineFlags[nIndex], DrlgDrlgGrid.FlagOperation.OR);

                // 在 grid[2] 上设置 nUnkb12
                D2DrlgOutdoorPackedGrid2InfoStrc packedInfo =
                        new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                packedInfo.setNUnkb12(true);
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), x, y,
                        packedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);

                nIndex = (nIndex + 1) % shrineFlags.length;
                --nShrines;
            }
        }
    }

    /**
     * 帮助函数：从户外信息的第 2 号网格中获取打包的 Grid2 信息
     * 对应 C++ 内联函数：DRLGOUTDOORS_GetPackedGrid2Info
     */
    public static D2DrlgOutdoorPackedGrid2InfoStrc getPackedGrid2Info(D2DrlgOutdoorInfoStrc outdoors, int x, int y) {
        if (outdoors == null) {
            return new D2DrlgOutdoorPackedGrid2InfoStrc(0);
        }
        D2DrlgGridStrc grid2 = outdoors.getPGrid(2);
        int packedValue = grid2 != null ? DrlgDrlgGrid.getGridEntry(grid2, x, y) : 0;
        return new D2DrlgOutdoorPackedGrid2InfoStrc(packedValue);
    }
    
    private static final int ALTDIR_WEST = 0;
    private static final int ALTDIR_NORTH = 1;
    private static final int ALTDIR_EAST = 2;
    private static final int ALTDIR_SOUTH = 3;
    private static final int ALTDIR_CENTER = 4;
    private static final int MAX_ACT1_DIRT_PATHS = 6;

    private static final byte[] ACT1_DIRT_PATH_TILES = {
        0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
        0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
        0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
        0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
        0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
        0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
        0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
        0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
        0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
        0x04, 0x04, 0x0B, 0x25, 0x04, 0x04, 0x0B, 0x2B,
        0x03, 0x03, 0x0C, 0x0C, 0x03, 0x03, 0x27, 0x27,
        0x09, 0x09, 0x02, 0x2B, 0x09, 0x09, 0x2C, 0x1A,
        0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
        0x17, 0x17, 0x29, 0x11, 0x17, 0x17, 0x29, 0x11,
        0x03, 0x03, 0x0C, 0x0C, 0x03, 0x03, 0x27, 0x27,
        0x2A, 0x2A, 0x2E, 0x2A, 0x2A, 0x2A, 0x21, 0x1F,
        0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
        0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
        0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
        0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x23, 0x14,
        0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
        0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
        0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
        0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
        0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
        0x04, 0x04, 0x0B, 0x25, 0x04, 0x04, 0x0B, 0x25,
        0x12, 0x12, 0x23, 0x23, 0x12, 0x12, 0x16, 0x16,
        0x24, 0x24, 0x2D, 0x22, 0x24, 0x24, 0x1C, 0x1D,
        0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
        0x17, 0x17, 0x29, 0x11, 0x17, 0x17, 0x29, 0x11,
        0x12, 0x12, 0x23, 0x23, 0x12, 0x12, 0x16, 0x16,
        0x18, 0x18, 0x19, 0x20, 0x18, 0x18, 0x1E, 0x01,
    };

    /** D2Common.0x6FD7F250. */
    public static void spawnAct1DirtPaths(D2DrlgLevel level) {
        if (level == null || level.getOutdoors() == null || level.getLevelCoords() == null) return;
        D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
        outdoors.setNVertices(0);
        for (int i = 0; i < MAX_ACT1_DIRT_PATHS; i++) outdoors.setPPathStarts(i, null);
        for (int i = 0; i < outdoors.getPVertices().length; i++) resetVertex(outdoors.getPVertices(i));

        for (D2DrlgOrth roomData = outdoors.getPRoomData(); roomData != null;
                roomData = roomData.getPNext()) {
            D2DrlgLevel linked = roomData.getPLevel();
            if (linked == null || linked.getLevelCoords() == null) continue;
            if (linked.getLevelId() == D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
                D2DrlgVertexStrc vertex = nextDirtPathVertex(level, outdoors);
                if (vertex == null) break;
                vertex.setNDirection(roomData.getNDirection());
                int x = linked.getLevelCoords().getNPosX();
                int y = linked.getLevelCoords().getNPosY();
                switch (roomData.getNDirection()) {
                    case ALTDIR_WEST: vertex.setNPosX(x + 59); vertex.setNPosY(y + 19); break;
                    case ALTDIR_NORTH: vertex.setNPosX(x + 29); vertex.setNPosY(y + 35); break;
                    case ALTDIR_EAST: vertex.setNPosX(x + 4); vertex.setNPosY(y + 22); break;
                    case ALTDIR_SOUTH: vertex.setNPosX(x + 29); vertex.setNPosY(y + 3); break;
                    default: break;
                }
            } else if (linked.getLevelId() == D2LevelIds.LEVEL_MONASTERYGATE) {
                D2DrlgVertexStrc vertex = nextDirtPathVertex(level, outdoors);
                if (vertex == null) break;
                vertex.setNPosX(linked.getLevelCoords().getNPosX() + 27);
                vertex.setNPosY(linked.getLevelCoords().getNPosY() + 13);
                vertex.setNDirection((byte) ALTDIR_NORTH);
            }
        }

        int levelX = level.getLevelCoords().getNPosX();
        int levelY = level.getLevelCoords().getNPosY();
        outer:
        for (int x = 0; x < outdoors.getNGridWidth(); x++) {
            for (int y = 0; y < outdoors.getNGridHeight(); y++) {
                int preset = DrlgDrlgGrid.getGridEntry(outdoors.getPGrid(0), x, y);
                D2DrlgOutdoorPackedGrid2InfoStrc packed = getPackedGrid2Info(outdoors, x, y);
                int direction = ALTDIR_CENTER;
                switch (preset) {
                    case 4: if (packed.getNPickedFile() == 3) direction = ALTDIR_SOUTH; break;
                    case 5: if (packed.getNPickedFile() == 3) direction = ALTDIR_WEST; break;
                    case 6: if (packed.getNPickedFile() == 3) direction = ALTDIR_NORTH; break;
                    case 7: if (packed.getNPickedFile() == 3) direction = ALTDIR_EAST; break;
                    case 24: direction = ALTDIR_NORTH; break;
                    case 25: direction = ALTDIR_WEST; break;
                    case 28:
                        if (packed.getNPickedFile() == 1 && x == outdoors.getNGridWidth() - 2) {
                            direction = ALTDIR_EAST;
                        }
                        break;
                    case 51:
                    case 52: direction = packed.getNPickedFile() != 0 ? 1 : 0; break;
                    default: break;
                }
                if (direction != ALTDIR_CENTER) {
                    D2DrlgVertexStrc vertex = nextDirtPathVertex(level, outdoors);
                    if (vertex == null) break outer;
                    vertex.setNPosX(levelX + 8 * x + 3);
                    vertex.setNPosY(levelY + 8 * y + 3);
                    vertex.setNDirection((byte) direction);
                }
            }
        }

        for (int i = 0; i < outdoors.getNVertices(); i++) {
            calculatePathCoordinates(level, outdoors.getPVertices(i), outdoors.getPVertices(6 + i));
        }
        calculateDirtPathHub(level);

        int built = 0;
        for (int i = 0; i < outdoors.getNVertices(); i++) {
            if (DrlgOutPlace.buildAct1DirtPath(level, i)) {
                D2DrlgOutdoorPackedGrid2InfoStrc pathInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                pathInfo.setNUnkb07(true);
                DrlgDrlgGrid.setVertexGridFlags(
                        outdoors.getPGrid(2), outdoors.getPPathStarts(i), pathInfo.getNPackedValue());
                finalizeDirtPath(level, i);
                logDirtPath(level, i);
                built++;
            }
        }
        D2Log.debug("DRLG_DIRTPATH topology level=%d endpoints=%d built=%d",
                level.getLevelId(), outdoors.getNVertices(), built);
    }

    private static D2DrlgVertexStrc nextDirtPathVertex(
            D2DrlgLevel level, D2DrlgOutdoorInfoStrc outdoors) {
        int index = outdoors.getNVertices();
        if (index >= MAX_ACT1_DIRT_PATHS) {
            D2Log.warning("DRLG_DIRTPATH endpoint overflow level=%d max=%d",
                    level.getLevelId(), MAX_ACT1_DIRT_PATHS);
            return null;
        }
        D2DrlgVertexStrc vertex = outdoors.getPVertices(index);
        resetVertex(vertex);
        outdoors.setNVertices(index + 1);
        return vertex;
    }

    private static void resetVertex(D2DrlgVertexStrc vertex) {
        vertex.setNPosX(0);
        vertex.setNPosY(0);
        vertex.setNDirection((byte) 0);
        vertex.setDwFlags(0);
        vertex.setPNext(null);
    }

    static void calculatePathCoordinates(
            D2DrlgLevel level, D2DrlgVertexStrc source, D2DrlgVertexStrc target) {
        int levelX = level.getLevelCoords().getNPosX();
        int levelY = level.getLevelCoords().getNPosY();
        int x = source.getNPosX() - levelX;
        int y = source.getNPosY() - levelY;
        switch (source.getNDirection()) {
            case ALTDIR_WEST: x = 8 * (x / 8) + 11; break;
            case ALTDIR_NORTH: y = 8 * (y / 8) + 11; break;
            case ALTDIR_EAST: x = 8 * (x / 8) - 5; break;
            case ALTDIR_SOUTH: y = 8 * (y / 8) - 5; break;
            default: break;
        }
        target.setNPosX(x + levelX);
        target.setNPosY(y + levelY);
    }

    private static void calculateDirtPathHub(D2DrlgLevel level) {
        D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
        int levelX = level.getLevelCoords().getNPosX();
        int levelY = level.getLevelCoords().getNPosY();
        int[] bridgeX = {-1};
        int[] bridgeY = {-1};
        if ((outdoors.getDwFlags() & OUTDOOR_RIVER) != 0) {
            DrlgOutWild.getBridgeCoords(level, bridgeX, bridgeY);
        }
        if (bridgeX[0] != -1) {
            int hubX = levelX + 8 * bridgeX[0] + 3;
            int hubY = levelY + 8 * bridgeY[0] + 3;
            for (int i = 0; i < outdoors.getNVertices(); i++) {
                D2DrlgVertexStrc rawHub = outdoors.getPVertices(18 + i);
                rawHub.setNPosY(hubY);
                if (outdoors.getPVertices(i).getNPosX() <= hubX) {
                    rawHub.setNPosX(hubX);
                    rawHub.setNDirection((byte) ALTDIR_EAST);
                } else {
                    rawHub.setNPosX(hubX + 8);
                    rawHub.setNDirection((byte) ALTDIR_WEST);
                }
            }
        } else {
            int gridX;
            int gridY;
            if (outdoors.getNVertices() == 1) {
                gridX = outdoors.getNGridWidth() / 2;
                gridY = outdoors.getNGridHeight() / 2;
            } else {
                int sumX = 0;
                int sumY = 0;
                for (int i = 0; i < outdoors.getNVertices(); i++) {
                    sumX += outdoors.getPVertices(i).getNPosX() - levelX;
                    sumY += outdoors.getPVertices(i).getNPosY() - levelY;
                }
                gridX = sumX / (8 * outdoors.getNVertices());
                gridY = sumY / (8 * outdoors.getNVertices());
            }
            int[] xOffsets = {-1, 0, 0, 1};
            int[] yOffsets = {0, 1, -1, 0};
            boolean found = false;
            for (int radius = 0; radius < 8 && !found; radius++) {
                for (int direction = 0; direction < 4; direction++) {
                    int x = gridX + radius * xOffsets[direction];
                    int y = gridY + radius * yOffsets[direction];
                    if (x >= 0 && x < outdoors.getNGridWidth()
                            && y >= 0 && y < outdoors.getNGridHeight()
                            && testGridCellSpawnValid(level, x, y)) {
                        gridX = x;
                        gridY = y;
                        found = true;
                        break;
                    }
                }
            }
            D2DrlgVertexStrc firstHub = outdoors.getPVertices(18);
            firstHub.setNPosX(levelX + 8 * gridX + 3);
            firstHub.setNPosY(levelY + 8 * gridY + 3);
            firstHub.setNDirection((byte) ALTDIR_CENTER);
            for (int i = 1; i < outdoors.getNVertices(); i++) {
                D2DrlgVertexStrc rawHub = outdoors.getPVertices(18 + i);
                rawHub.setNPosX(firstHub.getNPosX());
                rawHub.setNPosY(firstHub.getNPosY());
                rawHub.setNDirection((byte) ALTDIR_CENTER);
            }
        }
        for (int i = 0; i < outdoors.getNVertices(); i++) {
            calculatePathCoordinates(
                    level, outdoors.getPVertices(18 + i), outdoors.getPVertices(12 + i));
        }
    }

    private static void finalizeDirtPath(D2DrlgLevel level, int vertexId) {
        D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
        D2DrlgVertexStrc path = outdoors.getPPathStarts(vertexId);
        int directionIndex = (int) (Seed.rollRandomNumber(level.getSeed()) & 3);
        if (path == null) return;
        D2DrlgVertexStrc rawHub = outdoors.getPVertices(18 + vertexId);
        if (rawHub.getNDirection() != ALTDIR_CENTER) {
            D2DrlgVertexStrc newHead = new D2DrlgVertexStrc(
                    rawHub.getNPosX(), rawHub.getNPosY(), (byte) 0);
            newHead.setPNext(path);
            outdoors.setPPathStarts(vertexId, newHead);
        }
        path.setNPosX(outdoors.getPVertices(12 + vertexId).getNPosX());
        path.setNPosY(outdoors.getPVertices(12 + vertexId).getNPosY());
        D2DrlgVertexStrc vertex = path.getPNext();
        int[] xOffsets = {1, 0, -1, 0};
        int[] yOffsets = {0, 1, 0, -1};
        if (vertex != null) {
            while (vertex.getPNext() != null) {
                int offsetX = (((int) Seed.rollRandomNumber(level.getSeed()) & 1) + 2)
                        * xOffsets[directionIndex];
                int offsetY = (((int) Seed.rollRandomNumber(level.getSeed()) & 1) + 2)
                        * yOffsets[directionIndex];
                directionIndex = (directionIndex + 1) & 3;
                vertex.setNPosX(8 * vertex.getNPosX()
                        + level.getLevelCoords().getNPosX() + offsetX + 3);
                vertex.setNPosY(8 * vertex.getNPosY()
                        + level.getLevelCoords().getNPosY() + offsetY + 3);
                vertex = vertex.getPNext();
            }
            vertex.setNPosX(outdoors.getPVertices(6 + vertexId).getNPosX());
            vertex.setNPosY(outdoors.getPVertices(6 + vertexId).getNPosY());
            vertex.setPNext(new D2DrlgVertexStrc(
                    outdoors.getPVertices(vertexId).getNPosX(),
                    outdoors.getPVertices(vertexId).getNPosY(), (byte) 0));
        }
    }

    private static void logDirtPath(D2DrlgLevel level, int vertexId) {
        D2DrlgVertexStrc path = level.getOutdoors().getPPathStarts(vertexId);
        if (path == null) return;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int vertices = 0;
        for (D2DrlgVertexStrc vertex = path; vertex != null && vertices < 1000;
                vertex = vertex.getPNext()) {
            minX = Math.min(minX, vertex.getNPosX());
            minY = Math.min(minY, vertex.getNPosY());
            maxX = Math.max(maxX, vertex.getNPosX());
            maxY = Math.max(maxY, vertex.getNPosY());
            vertices++;
        }
        D2Log.debug("DRLG_DIRTPATH path level=%d endpoint=%d vertices=%d bounds=(%d,%d)-(%d,%d)",
                level.getLevelId(), vertexId, vertices, minX, minY, maxX, maxY);
    }

    /** D2Common.0x6FD7EFE0 - writes the native dirt-path floor flags for one RoomEx. */
    public static void generateDirtPath(D2DrlgLevel level, D2DrlgRoom drlgRoom) {
        if (level == null || drlgRoom == null || level.getOutdoors() == null
                || !(drlgRoom.getMazeOrOutdoor() instanceof D2DrlgOutdoorRoomStrc)) return;
        D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
        D2DrlgOutdoorRoomStrc room = (D2DrlgOutdoorRoomStrc) drlgRoom.getMazeOrOutdoor();
        D2DrlgGridStrc dirt = room.getPDirtPathGrid();
        DrlgDrlgGrid.initializeGridCells(level.getDrlg().getMempool(), dirt,
                drlgRoom.getNTileWidth() + 3, drlgRoom.getNTileHeight() + 3);
        D2DrlgCoord roomBox = new D2DrlgCoord();
        roomBox.setNPosX(drlgRoom.getNTileXPos() - 1);
        roomBox.setNPosY(drlgRoom.getNTileYPos() - 1);
        roomBox.setNWidth(drlgRoom.getNTileWidth() + 3);
        roomBox.setNHeight(drlgRoom.getNTileHeight() + 3);
        for (int i = 0; i < outdoors.getNVertices(); i++) {
            for (D2DrlgVertexStrc vertex = outdoors.getPPathStarts(i);
                    vertex != null; vertex = vertex.getPNext()) {
                if (vertex.getPNext() != null) {
                    DrlgDrlgGrid.sub_6FD75F60(
                            dirt, vertex, roomBox, 1, DrlgDrlgGrid.FlagOperation.OR, 2);
                }
            }
        }

        int rasterCells = 0;
        if (dirt.getPCellsFlags() != null) {
            for (int flags : dirt.getPCellsFlags()) {
                if (flags != 0) rasterCells++;
            }
        }
        int changed = 0;
        for (int x = 1; x <= drlgRoom.getNTileWidth(); x++) {
            for (int y = drlgRoom.getNTileHeight() + 1; y >= 1; y--) {
                int center = DrlgDrlgGrid.getGridEntry(dirt, x, y);
                if (center == 0) continue;
                int directions = 0;
                for (int boxIndex = 8; boxIndex >= 0; boxIndex--) {
                    if (boxIndex == 4) continue;
                    directions <<= 1;
                    int boxX = boxIndex / 3;
                    int boxY = boxIndex % 3;
                    int offsetX = boxX - 1;
                    int offsetY = 1 - boxY;
                    if (DrlgDrlgGrid.getGridEntry(dirt, x + offsetX, y + offsetY) != 0) {
                        directions |= 1;
                    }
                }
                if (directions != 0) {
                    int tile = ACT1_DIRT_PATH_TILES[directions] & 0xFF;
                    if (tile != 0) {
                        DrlgDrlgGrid.alterGridFlag(room.getPFloorGrid(), x - 1, y - 1,
                                (tile << 8) | 0x82, DrlgDrlgGrid.FlagOperation.OVERWRITE);
                        changed++;
                    }
                }
            }
        }
        if (changed != 0) {
            D2Log.debug("DRLG_DIRTPATH room level=%d pos=(%d,%d) rasterCells=%d floorCells=%d",
                    level.getLevelId(), drlgRoom.getNTileXPos(), drlgRoom.getNTileYPos(),
                    rasterCells, changed);
        }
    }
}
