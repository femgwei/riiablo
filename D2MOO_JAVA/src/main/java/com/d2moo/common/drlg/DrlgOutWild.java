package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.seed.Seed;

/**
 * Drlg 户外荒野模块
 * 对应 C++ 文件：DrlgOutWild.cpp
 */
public class DrlgOutWild {

    // Native D2C_LvlSubIds values used by Act 1 wilderness borders.
    static final int LVLSUB_ACT1_BORDER_CLIFFS = 0;
    static final int LVLSUB_ACT1_BORDER_MIDDLE = 1;
    static final int LVLSUB_ACT1_BORDER_CORNER = 2;
    static final int LVLSUB_ACT1_BORDER_BORDER = 3;
    
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
     * Port of DRLGOUTWILD_InitAct1OutdoorLevel.  Keep the native stage order:
     * vertex/link flags, primary border, four secondary borders with river and
     * cave stages interleaved, transitions, waypoint/shrines, special presets.
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
        D2DrlgVertexStrc head = outdoors.getPVertex();
        if (head == null) {
            D2Log.warning("DRLG_OUTWILD no vertex level=%d", level.getLevelId());
            return;
        }
        D2Log.debug("DRLG_OUTWILD begin level=%d grid=%dx%d flags=0x%X", level.getLevelId(),
                outdoors.getNGridWidth(), outdoors.getNGridHeight(), outdoors.getDwFlags());
        
        applyNativeCliffDirections(level, outdoors, head);
        logVertices(level, head, "directions");
        
        // 2. 设置网格链接标志
        D2Log.debug("DRLG_OUTWILD linkFlags begin level=%d", level.getLevelId());
        DrlgOutPlace.setOutGridLinkFlags(level);
        D2Log.debug("DRLG_OUTWILD linkFlags end level=%d", level.getLevelId());
        logGrid2Flags(level, outdoors, "linkFlags");
        
        D2Log.debug("DRLG_OUTWILD borders begin level=%d", level.getLevelId());
        DrlgOutPlace.placeAct1245OutdoorBorders(level);
        D2Log.debug("DRLG_OUTWILD borders end level=%d", level.getLevelId());
        logGrid2Flags(level, outdoors, "borders");
        
        int levelId = level.getLevelId();
        if (isAct1Wilderness(levelId)) {
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_CLIFFS, "cliffs");
            spawnNativeRiversAndCliffCaves(level, outdoors);
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_MIDDLE, "middle");
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_CORNER, "corner");
            spawnTownTransitionsAndCaves(level);
            logGrid2Flags(level, outdoors, "transitionsCaves");
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_BORDER, "border");

            // Native order: the road topology is fixed after all borders,
            // transitions and caves, but before waypoint/shrine presets can
            // consume the remaining free cells.
            DrlgOutdoors.spawnAct1DirtPaths(level);
            logGrid2Flags(level, outdoors, "dirtPaths");
        } else if (levelId == D2LevelIds.LEVEL_MOOMOOFARM) {
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_CLIFFS, "cliffs");
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_MIDDLE, "middle");
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_CORNER, "corner");
            addSecondaryBorder(level, outdoors, LVLSUB_ACT1_BORDER_BORDER, "border");
        }

        if (levelId >= D2LevelIds.LEVEL_COLDPLAINS && levelId <= D2LevelIds.LEVEL_BLACKMARSH) {
            DrlgOutdoors.spawnAct12Waypoint(level);
            logGrid2Flags(level, outdoors, "waypoint");
        }
        if (isAct1Wilderness(levelId)) {
            DrlgOutdoors.spawnAct12Shrines(level, 5);
            logGrid2Flags(level, outdoors, "shrines");
        }
        spawnSpecialPresets(level);
        logGrid2Flags(level, outdoors, "specialPresets");
        D2Log.debug("DRLG_OUTWILD end level=%d flags=0x%X", levelId, outdoors.getDwFlags());
    }

    static boolean isAct1Wilderness(int levelId) {
        return levelId >= D2LevelIds.LEVEL_BLOODMOOR
                && levelId <= D2LevelIds.LEVEL_TAMOEHIGHLAND;
    }

    static boolean preservesInitialDirections(int levelId) {
        return levelId == D2LevelIds.LEVEL_BLOODMOOR
                || levelId == D2LevelIds.LEVEL_COLDPLAINS
                || levelId == D2LevelIds.LEVEL_BURIALGROUNDS;
    }

    private static void addSecondaryBorder(D2DrlgLevel level, D2DrlgOutdoorInfoStrc outdoors,
            int lvlSubId, String stage) {
        DrlgOutdoors.addAct124SecondaryBorder(level, lvlSubId,
                D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1);
        logGrid2Flags(level, outdoors, "secondary-" + stage);
    }

    private static void applyNativeCliffDirections(D2DrlgLevel level,
            D2DrlgOutdoorInfoStrc outdoors, D2DrlgVertexStrc head) {
        if (preservesInitialDirections(level.getLevelId())) {
            return;
        }

        D2DrlgVertexStrc previous = head;
        int guard = 0;
        while (previous.getPNext() != head) {
            previous = previous.getPNext();
            if (previous == null || ++guard > 1024) {
                throw new IllegalStateException("Invalid Act1 vertex ring for level " + level.getLevelId());
            }
        }

        D2DrlgVertexStrc vertex = head;
        boolean breakOuter = false;
        guard = 0;
        do {
            D2DrlgVertexStrc next = vertex.getPNext();
            boolean corner = vertex.getNPosX() < next.getNPosX()
                    && previous.getNPosY() > vertex.getNPosY()
                    && (vertex.getDwFlags() & 1) == 0 && (previous.getDwFlags() & 1) == 0;
            corner |= vertex.getNPosY() > next.getNPosY()
                    && previous.getNPosX() > vertex.getNPosX()
                    && (vertex.getDwFlags() & 1) == 0 && (previous.getDwFlags() & 1) == 0;
            if (corner) {
                D2DrlgVertexStrc first = vertex;
                D2DrlgVertexStrc special = null;
                do {
                    if (vertex == head) breakOuter = true;
                    if (stopsCliffDirectionRun(vertex)) break;
                    if (isCliffDirectionCandidate(vertex)) special = vertex;
                    vertex = vertex.getPNext();
                } while (vertex != first);

                if (special != null) {
                    D2DrlgVertexStrc marked = first;
                    while (marked != special) {
                        marked.setNDirection((byte) 1); // ALTDIR_NORTH
                        marked = marked.getPNext();
                    }
                    special.setNDirection((byte) 1);
                    outdoors.setDwFlags(outdoors.getDwFlags() | DrlgOutdoors.OUTDOOR_CLIFFS);
                }
            }
            previous = vertex;
            vertex = vertex.getPNext();
            if (++guard > 2048) {
                throw new IllegalStateException("Act1 cliff-direction walk did not converge for level "
                        + level.getLevelId());
            }
        } while (!breakOuter && vertex != head);
    }

    static boolean isCliffDirectionCandidate(D2DrlgVertexStrc vertex) {
        D2DrlgVertexStrc next = vertex != null ? vertex.getPNext() : null;
        D2DrlgVertexStrc next2 = next != null ? next.getPNext() : null;
        if (next == null || next2 == null) return false;
        if (vertex.getNPosX() >= next.getNPosX() || next.getNPosY() >= next2.getNPosY()
                || (vertex.getDwFlags() & 1) != 0 || (next.getDwFlags() & 1) != 0) {
            if (vertex.getNPosY() <= next.getNPosY() || next.getNPosX() >= next2.getNPosX()
                    || (vertex.getDwFlags() & 1) != 0 || (next.getDwFlags() & 1) != 0) {
                return false;
            }
        }
        return true;
    }

    static boolean stopsCliffDirectionRun(D2DrlgVertexStrc vertex) {
        D2DrlgVertexStrc next = vertex != null ? vertex.getPNext() : null;
        if (next == null) return true;
        if (vertex.getNPosY() >= next.getNPosY() && vertex.getNPosX() <= next.getNPosX()) {
            if ((vertex.getDwFlags() & 1) == 0) {
                return (next.getDwFlags() & 1) != 0;
            }
        }
        return true;
    }

    private static void spawnNativeRiversAndCliffCaves(D2DrlgLevel level,
            D2DrlgOutdoorInfoStrc outdoors) {
        if ((outdoors.getDwFlags() & (DrlgOutdoors.OUTDOOR_BRIDGE
                | DrlgOutdoors.OUTDOOR_RIVER_OTHER)) != 0) {
            int riverX = outdoors.getNGridWidth() - 2;
            if (testSpawnRiver(level, riverX)) spawnRiver(level, riverX);
        }

        if ((outdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_CLIFFS) != 0
                && (outdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_OUT_CAVES) == 0) {
            boolean transpose = (Seed.rollRandomNumber(level.getSeed()) & 1) != 0;
            boolean added = false;
            for (int y = 0; y < outdoors.getNGridHeight() && !added; y++) {
                for (int x = 0; x < outdoors.getNGridWidth() && !added; x++) {
                    int caveX = transpose ? y : x;
                    int caveY = transpose ? x : y;
                    if (caveX < outdoors.getNGridWidth() && caveY < outdoors.getNGridHeight()) {
                        added = spawnCliffCaves(level, caveX, caveY);
                    }
                }
            }
            if (!added) D2Log.warning("DRLG_OUTWILD cliff cave not placed level=%d", level.getLevelId());
        }

        if ((outdoors.getDwFlags() & 0x1C) != 0
                && (outdoors.getDwFlags() & DrlgOutdoors.OUTDOOR_OUT_CAVES) == 0) {
            int y = outdoors.getNGridHeight() - 4;
            int x = outdoors.getNGridWidth()
                    - (((~outdoors.getDwFlags() & 0x10) | 0x40) >> 4);
            int random = (int) (Seed.rollRandomNumber(level.getSeed()) & 3);
            if ((random & 1) != 0) x = 3;
            if (random / 2 != 0) y = 3;
            int preset = level.getLevelId() == D2LevelIds.LEVEL_BLOODMOOR
                    ? D2LvlPrestIds.LVLPREST_ACT1_DOE_ENTRANCE
                    : D2LvlPrestIds.LVLPREST_ACT1_CAVE_ENTRANCE;
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, x, y, preset, -1, false);
            outdoors.setDwFlags(outdoors.getDwFlags() | DrlgOutdoors.OUTDOOR_OUT_CAVES);
        }
    }

    private static void logVertices(D2DrlgLevel level, D2DrlgVertexStrc head, String stage) {
        StringBuilder trace = new StringBuilder();
        D2DrlgVertexStrc vertex = head;
        int count = 0;
        do {
            if (count > 0) trace.append(' ');
            trace.append('(').append(vertex.getNPosX()).append(',').append(vertex.getNPosY())
                    .append(" d=").append(vertex.getNDirection())
                    .append(" f=0x").append(Integer.toHexString(vertex.getDwFlags())).append(')');
            vertex = vertex.getPNext();
        } while (vertex != null && vertex != head && ++count < 32);
        D2Log.debug("DRLG_OUTWILD vertices stage=%s level=%d %s", stage,
                level.getLevelId(), trace.toString());
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
     * Locate the already-picked Act 1 bridge cell.  This is a lookup, not a
     * placement-validity test; native D2MOO searches preset id 28 on the two
     * central river columns and requires picked file 1.
     */
    public static void getBridgeCoords(D2DrlgLevel level, int[] x, int[] y) {
        if (level == null || x == null || x.length == 0 || y == null || y.length == 0) {
            if (x != null && x.length > 0) x[0] = -1;
            if (y != null && y.length > 0) y[0] = -1;
            return;
        }
        
        x[0] = -1;
        y[0] = -1;
        Object presetOrOutdoors = level.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoors instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoors;
        int bridgeX = outdoors.getNGridWidth() / 2 - 1;
        // D2MOO 1.10f uses nGridWidth as the upper Y bound. Clamp it for
        // malformed/non-square inputs while preserving the native scan range.
        int yEnd = Math.min(outdoors.getNGridWidth() - 1, outdoors.getNGridHeight());
        for (int bridgeY = 1; bridgeY < yEnd; bridgeY++) {
            if (DrlgDrlgGrid.getGridEntry(outdoors.getPGrid(0), bridgeX, bridgeY)
                    == D2LvlPrestIds.LVLPREST_ACT1_BRIDGE
                    && DrlgOutdoors.getPackedGrid2Info(outdoors, bridgeX, bridgeY).getNPickedFile() == 1) {
                x[0] = bridgeX;
                y[0] = bridgeY;
                return;
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
