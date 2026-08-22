package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.drlg.D2DrlgLink.LinkFunctionWithIteration;

/**
 * Drlg 户外放置模块
 * 对应 C++ 文件：DrlgOutPlace.cpp
 */
public class DrlgOutPlace {
    /**
     * 全局标志变量（对应 C++ 中的 dword_6FDEA6FC）
     * 用于在 sub_6FD81CA0 中设置，并在 createLevelConnections 中应用到 LEVEL_OUTERSTEPPES
     */
    private static int dword_6FDEA6FC = 0;
    
    // 常量数组：Act1 荒野链接
    private static final D2DrlgLink[] gAct1WildernessDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_STONYFIELD, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_COLDPLAINS, 0, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81950, D2LevelIds.LEVEL_BLOODMOOR, 1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81720, D2LevelIds.LEVEL_ROGUEENCAMPMENT, 2, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_BURIALGROUNDS, 1, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act1 修道院链接
    private static final D2DrlgLink[] gAct1MonasteryDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_MOOMOOFARM, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_MONASTERYGATE, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81AD0, D2LevelIds.LEVEL_TAMOEHIGHLAND, 1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_BLACKMARSH, 2, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_DARKWOOD, 3, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act2 户外链接
    private static final D2DrlgLink[] gAct2OutdoorDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_LUTGHOLEIN, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81B30, D2LevelIds.LEVEL_ROCKYWASTE, 0, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81530, D2LevelIds.LEVEL_DRYHILLS, 1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81530, D2LevelIds.LEVEL_FAROASIS, 2, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81530, D2LevelIds.LEVEL_LOSTCITY, 3, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81BF0, D2LevelIds.LEVEL_VALLEYOFSNAKES, 4, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act2 峡谷链接
    private static final D2DrlgLink[] gAct2CanyonDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_CANYONOFTHEMAGI, -1, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act4 户外链接
    private static final D2DrlgLink[] gAct4OutdoorDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81CA0, D2LevelIds.LEVEL_OUTERSTEPPES, 0, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_PLAINSOFDESPAIR, 1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81380, D2LevelIds.LEVEL_CITYOFTHEDAMNED, 2, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act4 混沌圣域链接
    private static final D2DrlgLink[] gAct4ChaosSanctumDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_CHAOSSANCTUM, -1, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act5 户外链接
    private static final D2DrlgLink[] gAct5OutdoorDrlgLink = {
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_HARROGATH, -1, -1),
        new D2DrlgLink(DrlgOutPlace::sub_6FD81330, D2LevelIds.LEVEL_BLOODYFOOTHILLS, 0, -1),
        new D2DrlgLink(DrlgOutRoom::linkLevelsByLevelCoords, D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, 1, -1),
        new D2DrlgLink(DrlgOutRoom::linkLevelsByOffsetCoords, D2LevelIds.LEVEL_ARREATPLATEAU, 2, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // 常量数组：Act5 苔原链接
    private static final D2DrlgLink[] gAct5TundraDrlgLink = {
        new D2DrlgLink(DrlgOutRoom::linkLevelsByLevelDef, D2LevelIds.LEVEL_TUNDRAWASTELANDS, -1, -1),
        new D2DrlgLink((D2DrlgLink.LinkFunction)null, 0, -1, -1),
    };
    
    // Act1 荒野链接验证表
    private static final boolean[] dword_6FDD05C0 = {
        true, true, false, false, false, false, false, false, false, true, false, false, false, false, false, false,
        false, false, false, true, false, true, false, false, true, false, false, false, false, false, true, true,
        false, true, false, false, false, false, false, true, false, true, false, false, false, false, false, false,
        false, false, false, false, false, true, true, false, true, false, false, false, false, false, true, true,
    };
    
    /**
     * D2Common.0x6FD81D60
     * 创建关卡连接
     * 被 DrlgDrlg 依赖
     */
    public static void createLevelConnections(D2DrlgStrc drlg, byte actNo) {
        D2Log.debug("DRLG_LINKS begin act=%d seed=(%d,%d) difficulty=%d", actNo,
                drlg != null && drlg.getSeed() != null ? drlg.getSeed().getNLowSeed() : 0,
                drlg != null && drlg.getSeed() != null ? drlg.getSeed().getNHighSeed() : 0,
                drlg != null ? drlg.getDifficulty() : -1);
        D2LevelDefBin pLevelDefBinRecord;
        D2DrlgLevel pAdjacentLevel;
        D2DrlgLevel pLevel;
        int nSizeX;
        int nSizeY;
        int nPosY;
        
        switch (actNo) {
            case D2C_Acts.ACT_I:
                sub_6FD823C0(drlg, gAct1WildernessDrlgLink, DrlgOutPlace::sub_6FD82050, DrlgOutPlace::sub_6FD82360);
                sub_6FD823C0(drlg, gAct1MonasteryDrlgLink, DrlgOutPlace::sub_6FD82130, DrlgOutPlace::sub_6FD82360);
                
                sub_6FD82750(drlg, D2LevelIds.LEVEL_ROGUEENCAMPMENT, D2LevelIds.LEVEL_BURIALGROUNDS);
                break;
                
            case D2C_Acts.ACT_II:
                sub_6FD823C0(drlg, gAct2OutdoorDrlgLink, DrlgOutPlace::linkAct2Outdoors, null);
                sub_6FD823C0(drlg, gAct2CanyonDrlgLink, DrlgOutPlace::linkAct2Canyon, null);
                
                sub_6FD82750(drlg, D2LevelIds.LEVEL_LUTGHOLEIN, D2LevelIds.LEVEL_CANYONOFTHEMAGI);
                break;
                
            case D2C_Acts.ACT_III:
                pLevel = DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_KURASTDOCKTOWN);
                pLevelDefBinRecord = DataTbls.getLevelDefRecord(D2LevelIds.LEVEL_KURASTDOCKTOWN);
                
                pLevel.getLevelCoords().setNPosX(pLevelDefBinRecord.getDwOffsetX());
                pLevel.getLevelCoords().setNPosY(pLevelDefBinRecord.getDwOffsetY());
                pLevel.getLevelCoords().setNWidth(pLevelDefBinRecord.getDwSizeX(drlg.getDifficulty()));
                pLevel.getLevelCoords().setNHeight(pLevelDefBinRecord.getDwSizeY(drlg.getDifficulty()));
                
                // 生成丛林（简化版本，完整实现需要多个辅助函数）
                pLevel = DrlgOutPlace.generateJungles(pLevel);
                
                nPosY = 0;
                for (int i = D2LevelIds.LEVEL_LOWERKURAST; i <= D2LevelIds.LEVEL_TRAVINCAL; ++i) {
                    pLevelDefBinRecord = DataTbls.getLevelDefRecord(i);
                    
                    nSizeX = pLevelDefBinRecord.getDwSizeX(pLevel.getDrlg().getDifficulty());
                    nSizeY = pLevelDefBinRecord.getDwSizeY(pLevel.getDrlg().getDifficulty());
                    
                    nPosY -= nSizeY;
                    
                    pAdjacentLevel = DrlgDrlg.getLevel(pLevel.getDrlg(), i);
                    
                    pAdjacentLevel.getLevelCoords().setNPosX(pLevel.getLevelCoords().getNWidth() / 2 + pLevel.getLevelCoords().getNPosX() - nSizeX / 2);
                    pAdjacentLevel.getLevelCoords().setNWidth(nSizeX);
                    pAdjacentLevel.getLevelCoords().setNPosY(nPosY + pLevel.getLevelCoords().getNPosY());
                    pAdjacentLevel.getLevelCoords().setNHeight(nSizeY);
                }
                
                sub_6FD826D0(drlg, D2LevelIds.LEVEL_KURASTDOCKTOWN, D2LevelIds.LEVEL_TRAVINCAL);
                sub_6FD82750(drlg, D2LevelIds.LEVEL_KURASTDOCKTOWN, D2LevelIds.LEVEL_TRAVINCAL);
                break;
                
            case D2C_Acts.ACT_IV:
                sub_6FD823C0(drlg, gAct4OutdoorDrlgLink, DrlgOutPlace::linkAct4Outdoors, null);
                sub_6FD823C0(drlg, gAct4ChaosSanctumDrlgLink, DrlgOutPlace::linkAct4ChaosSanctum, null);
                
                // 应用全局标志到 LEVEL_OUTERSTEPPES（对应 C++ 中的 pLevel->pOutdoors->dwFlags |= dword_6FDEA6FC）
                pLevel = DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_OUTERSTEPPES);
                if (pLevel != null && pLevel.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc) {
                    D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) pLevel.getPresetOrOutdoorsOrMaze();
                    outdoors.setDwFlags(outdoors.getDwFlags() | dword_6FDEA6FC);
                    // 重置全局标志
                    dword_6FDEA6FC = 0;
                    outdoors.setDwFlags(outdoors.getDwFlags() | 0x800000); // dword_6FDEA6FC
                }
                
                sub_6FD82750(drlg, D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS, D2LevelIds.LEVEL_CITYOFTHEDAMNED);
                break;
                
            case D2C_Acts.ACT_V:
                sub_6FD823C0(drlg, gAct5OutdoorDrlgLink, null, null);
                sub_6FD823C0(drlg, gAct5TundraDrlgLink, null, null);
                
                sub_6FD826D0(drlg, D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, D2LevelIds.LEVEL_ARREATPLATEAU);
                sub_6FD82750(drlg, D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1, D2LevelIds.LEVEL_ARREATPLATEAU);
                
                sub_6FD826D0(drlg, D2LevelIds.LEVEL_BLOODYFOOTHILLS, D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1);
                
                sub_6FD826D0(drlg, D2LevelIds.LEVEL_HARROGATH, D2LevelIds.LEVEL_BLOODYFOOTHILLS);
                break;
                
            default:
                D2Log.warning("DRLG_LINKS unsupported act=%d", actNo);
                return;
        }
        D2Log.debug("DRLG_LINKS end act=%d", actNo);
    }
    
    // 房间标志常量
    private static final int DRLGROOMFLAG_HAS_WAYPOINT_MASK = 0x1F0000;
    private static final int DRLGROOMFLAG_HAS_WAYPOINT_FIRST_BIT = 16;
    private static final int DRLGROOMFLAG_SUBSHRINE_ROWS_MASK = 0x1F000000;
    private static final int DRLGROOMFLAG_SUBSHRINE_ROWS_FIRST_BIT = 24;
    
    /**
     * D2Common.0x6FD83A20
     * 初始化户外房间网格
     * 被 DrlgRoomTile 依赖
     */
    public static void initOutdoorRoomGrids(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        Object outdoor = drlgRoom.getMazeOrOutdoor();
        if (outdoor == null || !(outdoor instanceof D2DrlgOutdoorRoomStrc)) {
            return;
        }
        
        D2DrlgOutdoorRoomStrc outdoorRoom = (D2DrlgOutdoorRoomStrc) outdoor;
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 获取关卡定义记录
        D2LevelDefBin pLevelDefBinRecord = DataTbls.getLevelDefRecord(drlgRoom.getLevel().getLevelId());
        
        // 提取传送点和神殿子主题
        int nWaypointSubTheme = (drlgRoom.getFlags() & DRLGROOMFLAG_HAS_WAYPOINT_MASK) >> DRLGROOMFLAG_HAS_WAYPOINT_FIRST_BIT;
        int nShrineSubTheme = (drlgRoom.getFlags() & DRLGROOMFLAG_SUBSHRINE_ROWS_MASK) >> DRLGROOMFLAG_SUBSHRINE_ROWS_FIRST_BIT;
        
        // 网格大小（宽度和高度各加1）
        int nWidth = drlgRoom.getNTileWidth() + 1;
        int nHeight = drlgRoom.getNTileHeight() + 1;
        
        // 初始化网格单元格
        DrlgDrlgGrid.initializeGridCells(memPool, outdoorRoom.getPTileTypeGrid(), nWidth, nHeight);
        DrlgDrlgGrid.initializeGridCells(memPool, outdoorRoom.getPWallGrid(), nWidth, nHeight);
        DrlgDrlgGrid.initializeGridCells(memPool, outdoorRoom.getPFloorGrid(), nWidth, nHeight);
        
        // 初始化前8x8区域的地板标志（0x40002）
        for (int i = 0; i < 8 && i < nHeight; ++i) {
            for (int j = 0; j < 8 && j < nWidth; ++j) {
                DrlgDrlgGrid.alterGridFlag(outdoorRoom.getPFloorGrid(), j, i, 0x40002,
                        DrlgDrlgGrid.FlagOperation.OVERWRITE);
            }
        }
        
        // Act1 生成泥土路径
        byte nAct = DrlgDrlg.getActNoFromLevelId(drlgRoom.getLevel().getLevelId());
        if (nAct == D2C_Acts.ACT_I) {
            DrlgOutdoors.generateDirtPath(drlgRoom.getLevel(), drlgRoom);
        }
        
        // 分配瓦片网格
        DrlgRoomTile.allocTileGrid(drlgRoom);
        
        // 初始化 D2UnkOutdoorStrc2 结构
        D2UnkOutdoorStrc2 a1 = new D2UnkOutdoorStrc2();
        a1.setPDrlgRoom(drlgRoom);
        a1.setPOutdoorRooms(0, outdoorRoom);
        a1.setPWallsGrids(0, outdoorRoom.getPWallGrid());
        a1.setPFloorGrid(outdoorRoom.getPFloorGrid());
        a1.setField_28(0);
        a1.setField_2C(1);
        
        // 处理传送点替换
        if (nWaypointSubTheme != 0 && pLevelDefBinRecord != null) {
            a1.setNSubTheme(0);
            a1.setNSubWaypoint_Shrine(pLevelDefBinRecord.getDwSubWaypoint());
            a1.setNSubThemePicked(nWaypointSubTheme);
            DrlgTileSub.sub_6FD8AA80(a1);
        }
        
        // 处理神殿替换
        if (nShrineSubTheme != 0 && pLevelDefBinRecord != null) {
            a1.setNSubTheme(0);
            a1.setNSubWaypoint_Shrine(pLevelDefBinRecord.getDwSubShrine());
            a1.setNSubThemePicked(nShrineSubTheme);
            DrlgTileSub.sub_6FD8AA80(a1);
        }
        
        // 处理普通子主题替换
        a1.setNSubWaypoint_Shrine(outdoorRoom.getNSubType());
        a1.setNSubTheme(outdoorRoom.getNSubTheme());
        a1.setNSubThemePicked(outdoorRoom.getNSubThemePicked());
        DrlgTileSub.sub_6FD8AA80(a1);
        
        // 根据关卡类型设置地板标志
        int nFlags = 0;
        int nLevelType = drlgRoom.getLevel().getLevelType();
        switch (nLevelType) {
            case LVLTYPE_ACT2_DESERT:
                nFlags = 0x100;
                break;
            case LVLTYPE_ACT3_JUNGLE:
                nFlags = 0x120000;
                break;
            case LVLTYPE_ACT3_KURAST:
                nFlags = 0x100000;
                break;
            case LVLTYPE_ACT4_MESA:
                nFlags = 0xA00000;
                break;
            case LVLTYPE_ACT4_LAVA:
                nFlags = 0x1600000;
                break;
            case LVLTYPE_ACT5_BARRICADE:
                // 只有 TUNDRAWASTELANDS 关卡设置标志
                if (drlgRoom.getLevel().getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) {
                    nFlags = 0x600000;
                }
                break;
            default:
                break;
        }
        
        // 为没有特定标志的地板单元格设置标志
        for (int nY = 0; nY < nHeight; ++nY) {
            for (int nX = 0; nX < nWidth; ++nX) {
                int nFloorFlags = DrlgDrlgGrid.getGridEntry(outdoorRoom.getPFloorGrid(), nX, nY);
                if ((nFloorFlags & 0x3F0FF80) == 0) {
                    DrlgDrlgGrid.alterGridFlag(outdoorRoom.getPFloorGrid(), nX, nY, nFlags,
                            DrlgDrlgGrid.FlagOperation.OR);
                }
            }
        }
        
        // 设置边缘网格标志
        DrlgDrlgGrid.alterEdgeGridFlags(outdoorRoom.getPWallGrid(), 4, DrlgDrlgGrid.FlagOperation.OR);
        DrlgDrlgGrid.alterEdgeGridFlags(outdoorRoom.getPFloorGrid(), 4, DrlgDrlgGrid.FlagOperation.OR);
    }
    
    /**
     * D2Common.0x6FD83C90
     * 创建户外房间
     */
    public static void createOutdoorRoomEx(D2DrlgLevel level, int x, int y, 
            int width, int height, int dwRoomFlags, int dwOutdoorFlags, 
            int dwOutdoorFlagsEx, int dwDT1Mask) {
        if (level == null || level.getDrlg() == null) {
            return;
        }

        if (level.getRooms() < 2) {
            D2Log.debug("DRLG_ROOM create level=%d pos=(%d,%d) size=%dx%d flags=0x%X outdoor=0x%X ex=0x%X",
                    level.getLevelId(), x, y, width, height, dwRoomFlags, dwOutdoorFlags, dwOutdoorFlagsEx);
        }
        
        // 分配房间（类型为 MAZE）
        D2DrlgRoom pDrlgRoom = DrlgDrlgRoom.allocRoomEx(level, D2DrlgTypes.DRLGTYPE_MAZE);
        if (pDrlgRoom == null) {
            D2Log.warning("DRLGOUTPLACE_CreateOutdoorRoomEx: Failed to allocate room");
            return;
        }
        
        // 设置房间尺寸和位置
        pDrlgRoom.setNTileWidth(width);
        pDrlgRoom.setNTileHeight(height);
        pDrlgRoom.setNTileXPos(x);
        pDrlgRoom.setNTileYPos(y);
        
        // 添加到关卡
        DrlgDrlgRoom.addRoomExToLevel(level, pDrlgRoom);
        
        // 设置 DT1 掩码和房间标志
        pDrlgRoom.setDt1Mask(dwDT1Mask);
        pDrlgRoom.setFlags(pDrlgRoom.getFlags() | dwRoomFlags | D2DrlgRoomFlags.NO_LOS_DRAW);
        
        // 获取户外房间数据
        Object outdoorObj = pDrlgRoom.getMazeOrOutdoor();
        if (!(outdoorObj instanceof D2DrlgOutdoorRoomStrc)) {
            D2Log.warning("DRLGOUTPLACE_CreateOutdoorRoomEx: Invalid outdoor room data");
            return;
        }
        
        D2DrlgOutdoorRoomStrc outdoor = (D2DrlgOutdoorRoomStrc) outdoorObj;
        
        // 设置户外标志
        outdoor.setDwFlags(dwOutdoorFlags);
        outdoor.setDwFlagsEx(dwOutdoorFlagsEx);
        
        // 获取关卡定义记录并设置子类型和子主题
        D2LevelDefBin pLevelDefBinRecord = DataTbls.getLevelDefRecord(level.getLevelId());
        if (pLevelDefBinRecord != null) {
            outdoor.setNSubType(pLevelDefBinRecord.getDwSubType());
            outdoor.setNSubTheme(pLevelDefBinRecord.getDwSubTheme());
            outdoor.setNSubThemePicked(DrlgTileSub.pickSubThemes(
                pDrlgRoom, pLevelDefBinRecord.getDwSubType(), pLevelDefBinRecord.getDwSubTheme()));
        }
    }
    
    /**
     * D2Common.0x6FD80DA0
     * 设置户外网格链接标志
     * 
     * C++ 原始代码：
     * D2DrlgVertexStrc* pVertex = pLevel->pOutdoors->pVertex;
     * D2DrlgVertexStrc* pNext = pVertex->pNext;
     * 
     * do {
     *     if (pVertex->dwFlags & 1) {
     *         sub_6FD75DE0(&pLevel->pOutdoors->pGrid[1], pVertex, 
     *             DRLGOUTDOORS_GetOutLinkVisFlag(pLevel, pVertex), FLAG_OPERATION_OR, 1);
     *         D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo{ 0 };
     *         tPackedInfo.nUnkb00 = true;
     *         tPackedInfo.bHasDirection = pVertex->nDirection != 0;
     *         sub_6FD75DE0(&pLevel->pOutdoors->pGrid[2], pVertex, 
     *             tPackedInfo.nPackedValue, FLAG_OPERATION_OR, 1);
     *     }
     *     pVertex = pNext;
     *     pNext = pVertex->pNext;
     * } while (pVertex != pLevel->pOutdoors->pVertex);
     */
    public static void setOutGridLinkFlags(D2DrlgLevel level) {
        if (level == null || level.getPresetOrOutdoorsOrMaze() == null 
                || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        D2DrlgVertexStrc pVertex = outdoors.getPVertex();
        
        if (pVertex == null) {
            return;
        }
        
        D2DrlgVertexStrc pNext = pVertex.getPNext();
        if (pNext == null) {
            return;
        }
        
        // 遍历顶点链表
        do {
            // 检查顶点标志（dwFlags & 1）
            if ((pVertex.getDwFlags() & 1) != 0) {
                int nOutLinkVisFlag = DrlgOutdoors.getOutLinkVisFlag(level, pVertex);
                DrlgDrlgGrid.sub_6FD75DE0(outdoors.getPGrid(1), pVertex,
                        nOutLinkVisFlag, DrlgDrlgGrid.FlagOperation.OR, true);

                D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                tPackedInfo.setNUnkb00(true);
                tPackedInfo.setBHasDirection(pVertex.getNDirection() != 0);
                DrlgDrlgGrid.sub_6FD75DE0(outdoors.getPGrid(2), pVertex,
                        tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR, true);
            }

            pVertex = pNext;
            pNext = pVertex.getPNext();
        } while (pVertex != outdoors.getPVertex());
    }
    
    // 边界索引查找表（对应 C++ nBorderIndices）
    private static final int[] nBorderIndices = {
        -1,  1, -1,  0, -1,  2, -1,  3, -1,  0,
         1,  9,  9, -1, -1,  1,  8, -1, -1, 12,
        -1, -1, -1, -1, 12,  4, -1, -1,  5,  2,
         2, 10, -1, -1, -1, -1, 10,  1,  9,  9,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        // Second array
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 11, 11,  3, 12, -1, -1, -1, -1, 12,
         4,  4,  7, -1, -1,  2, 10, -1, -1, -1,
        -1, 10, -1, -1,  6,  3, -1, -1, 11, 11,
         3,
    };
    
    private static final int nBorderIndicesOffset_sub_6FD80BE0 = 4;
    private static final int nBorderIndicesOffset_sub_6FD80C10 = 50;
    
    // 边界预设ID查找表（对应 C++ levelPrestBorder）
    private static final int[][] levelPrestBorder = {
        // Row 0: 无效数据（C++ 注释说明）
        { 0x2010203, 0x1020300, 0x0FF0001, 0x0FF000100 },
        // Row 1-12: 有效的边界预设
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_1, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_1, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_1 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_2, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_2, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_2, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_2 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_3, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_3, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_3, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_3 },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_4, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_4, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_4 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_5, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_5, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_5, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_5 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_6A, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_6, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_6, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_6 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_7, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_7, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_7, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_7 },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_8, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_8, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_8 },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_9, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_9, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_9 },
        { D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_10, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_10, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_10, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_10 },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_11, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_11, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_11 },
        { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_ACT1_WILD_BORDER_12, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_12, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_12 },
    };
    
    // Act5 障碍物悬崖边界ID查找表（对应 C++ gnBarricadeCliffBorderIds）
    private static final int[][] gnBarricadeCliffBorderIds = {
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_2, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_2_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_3, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_3_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_4, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_4_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_5, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_5_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_6, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_6_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_7, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_7_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_8, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_8_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_9, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_9_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_10, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_10_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_11, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_11_SNOW },
        { D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_12, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_12_SNOW },
        { -1, 1 },
        { -1, 0 },
    };
    
    /**
     * D2Common.0x6FD80BE0
     * 根据坐标差值和关卡类型计算边界预设ID
     * @param a1 当前坐标差X（-1, 0, 1）
     * @param a2 当前坐标差Y（-1, 0, 1）
     * @param a3 关卡类型索引（0=Act1, 2=Act2, 3=Act4, 4+=Act5）
     * @return 边界预设ID
     */
    public static int sub_6FD80BE0(int a1, int a2, int a3) {
        int lutIndex = a1 + 3 * a2 + nBorderIndicesOffset_sub_6FD80BE0;
        if (lutIndex < 0 || lutIndex >= nBorderIndices.length) {
            return D2LvlPrestIds.LVLPREST_NONE;
        }
        
        int nIndex = nBorderIndices[lutIndex];
        
        if (a3 < 4) {
            if (nIndex < 0 || nIndex >= levelPrestBorder.length - 1) {
                return D2LvlPrestIds.LVLPREST_NONE;
            }
            return levelPrestBorder[nIndex + 1][a3];
        } else {
            if (nIndex < 0 || nIndex >= gnBarricadeCliffBorderIds.length) {
                return D2LvlPrestIds.LVLPREST_NONE;
            }
            return gnBarricadeCliffBorderIds[nIndex][a3 - 4];
        }
    }
    
    /**
     * D2Common.0x6FD80C10
     * 根据两个坐标差值和关卡类型计算边界预设ID（用于顶点连接）
     * @param a1 当前坐标差X（归一化后）
     * @param a2 当前坐标差Y（归一化后）
     * @param a3 下一个坐标差X（归一化后）
     * @param a4 下一个坐标差Y（归一化后）
     * @param a5 关卡类型索引（0=Act1, 2=Act2, 3=Act4, 4+=Act5）
     * @return 边界预设ID，如果为 -1 则返回 0
     */
    public static int sub_6FD80C10(int a1, int a2, int a3, int a4, int a5) {
        // 归一化 a1
        if (a1 >= 0) {
            if (a1 > 0) {
                a1 += 2;
            }
        } else {
            a1 -= 2;
        }
        
        // 归一化 a3
        if (a3 >= 0) {
            if (a3 > 0) {
                a3 += 2;
            }
        } else {
            a3 -= 2;
        }
        
        int lutIndex = a2 + a1 + 9 * (a4 + a3) + nBorderIndicesOffset_sub_6FD80C10;
        if (lutIndex < 0 || lutIndex >= nBorderIndices.length) {
            return 0;
        }
        
        int v6 = nBorderIndices[lutIndex];
        
        if (v6 == -1) {
            return 0;
        } else if (a5 < 4) {
            if (v6 < 0 || v6 >= levelPrestBorder.length) {
                return 0;
            }
            return levelPrestBorder[v6][a5];
        } else {
            if (v6 - 1 < 0 || v6 - 1 >= gnBarricadeCliffBorderIds.length) {
                return 0;
            }
            return gnBarricadeCliffBorderIds[v6 - 1][a5 - 4];
        }
    }
    
    /**
     * D2Common.0x6FD84100
     * 获取 Act5 关卡类型索引
     * @param level 关卡
     * @return 关卡类型索引（4 或 5）
     */
    private static int sub_6FD84100(D2DrlgLevel level) {
        if (level == null) {
            return 4;
        }
        return (level.getLevelId() == D2LevelIds.LEVEL_TUNDRAWASTELANDS) ? 5 : 4;
    }
    
    /**
     * D2Common.0x6FD80C80
     * 设置空白边界网格单元格
     */
    public static void setBlankBorderGridCells(D2DrlgLevel level) {
        if (level == null || level.getPresetOrOutdoorsOrMaze() == null 
                || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        
        // 四个角的偏移和方向
        final int[][] aOffsets = {
            { 0, 0, 1, 1 },
            { 1, 0, -1, 1 },
            { 0, 1, 1, -1 },
            { 1, 1, -1, -1 }
        };
        
        for (int k = 0; k < 4; ++k) {
            int tStartX = aOffsets[k][0];
            int tStartY = aOffsets[k][1];
            int nDirX = aOffsets[k][2];
            int nDirY = aOffsets[k][3];
            
            int nX = tStartX;
            if (nX != 0) {
                nX = outdoors.getNGridWidth() - 1;
            }
            
            int nY = tStartY;
            if (nY != 0) {
                nY = outdoors.getNGridHeight() - 1;
            }
            
            // 遍历直到遇到 nUnkb00 标志
            for (int j = nY; j >= 0 && j < outdoors.getNGridHeight(); j += nDirY) {
                D2DrlgOutdoorPackedGrid2InfoStrc packedInfo = DrlgOutdoors.getPackedGrid2Info(outdoors, nX, j);
                if (packedInfo.isNUnkb00()) {
                    break;
                }
                
                for (int i = nX; i >= 0 && i < outdoors.getNGridWidth(); i += nDirX) {
                    D2DrlgOutdoorPackedGrid2InfoStrc packedInfo2 = DrlgOutdoors.getPackedGrid2Info(outdoors, i, j);
                    // Match DRLGOUTPLACE_SetBlankBorderGridCells: nUnkb00 is
                    // the boundary sentinel.  nUnkb07 has different semantics
                    // and must not change the fill topology.
                    if (packedInfo2.isNUnkb00()) {
                        break;
                    }
                    
                    D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                    tPackedInfo.setNUnkb08(true);
                    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), i, j, 
                            tPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                }
            }
        }
    }
    
    // 关卡类型常量（对应 C++ D2C_LvlTypes 枚举）
    private static final int LVLTYPE_ACT1_WILDERNESS = 2;
    private static final int LVLTYPE_ACT2_DESERT = 5;
    private static final int LVLTYPE_ACT3_JUNGLE = 6;
    private static final int LVLTYPE_ACT3_KURAST = 7;
    private static final int LVLTYPE_ACT4_MESA = 10;
    private static final int LVLTYPE_ACT4_LAVA = 11;
    private static final int LVLTYPE_ACT5_BARRICADE = 12;
    
    // Act 常量（对应 C++ D2C_Acts 枚举）
    private static final byte ACT_I = 0;
    private static final byte ACT_II = 1;
    private static final byte ACT_IV = 3;
    private static final byte ACT_V = 4;
    
    /**
     * D2Common.0x6FD80E10
     * 放置 Act1/2/4/5 户外边界
     * 
     * C++ 原始代码非常复杂，涉及：
     * 1. 遍历顶点链表
     * 2. 计算坐标差值
     * 3. 根据关卡类型选择边界预设ID
     * 4. 放置边界预设
     * 5. 设置网格标志
     */
    public static void placeAct1245OutdoorBorders(D2DrlgLevel level) {
        if (level == null || level.getPresetOrOutdoorsOrMaze() == null 
                || !(level.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) level.getPresetOrOutdoorsOrMaze();
        D2DrlgVertexStrc pDrlgVertex = outdoors.getPVertex();
        
        if (pDrlgVertex == null) {
            return;
        }
        
        D2DrlgVertexStrc pNextVertex = pDrlgVertex.getPNext();
        if (pNextVertex == null) {
            return;
        }
        
        // 沙漠边界ID数组（用于Act2）
        final int[][] nDesertBorderIds = {
            { D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_10, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_9 },
            { D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_9, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_12 },
            { D2LvlPrestIds.LVLPREST_NONE, D2LvlPrestIds.LVLPREST_NONE },
            { D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_10, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_11 },
            { D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_11, D2LvlPrestIds.LVLPREST_ACT2_DESERT_BORDER_12 }
        };
        
        byte nAct = DrlgDrlg.getActNoFromLevelId(level.getLevelId());
        int nLevelType = level.getLevelType();
        
        // 初始化打包信息
        D2DrlgOutdoorPackedGrid2InfoStrc tLvlPrestPackedInfo = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
        tLvlPrestPackedInfo.setNUnkb00(true);
        tLvlPrestPackedInfo.setBHasDirection(pDrlgVertex.getNDirection() != 0);
        
        // 遍历顶点链表
        do {
            int[] pCurrentDiffX = new int[1];
            int[] pCurrentDiffY = new int[1];
            int[] pNextDiffX = new int[1];
            int[] pNextDiffY = new int[1];
            
            DrlgDrlgVer.getCoordDiff(pDrlgVertex, pCurrentDiffX, pCurrentDiffY);
            DrlgDrlgVer.getCoordDiff(pNextVertex, pNextDiffX, pNextDiffY);
            
            int nCurrentDiffX = pCurrentDiffX[0];
            int nCurrentDiffY = pCurrentDiffY[0];
            int nNextDiffX = pNextDiffX[0];
            int nNextDiffY = pNextDiffY[0];
            
            int nCurrentDiffXAbs = (nCurrentDiffX < 0) ? -nCurrentDiffX : nCurrentDiffX;
            int nCurrentDiffYAbs = (nCurrentDiffY < 0) ? -nCurrentDiffY : nCurrentDiffY;
            
            int nCurrentX = pDrlgVertex.getNPosX();
            int nCurrentY = pDrlgVertex.getNPosY();
            int nNextX = pNextVertex.getNPosX();
            int nNextY = pNextVertex.getNPosY();

            if (nCurrentDiffX == 0 && nCurrentDiffY == 0
                    && (nCurrentX != nNextX || nCurrentY != nNextY)) {
                D2Log.warning("DRLG_BORDER zero direction edge level=%d current=(%d,%d) next=(%d,%d)",
                        level.getLevelId(), nCurrentX, nCurrentY, nNextX, nNextY);
            }
            
            int nDiff = (nCurrentDiffXAbs != 0) ? (nCurrentX - nNextX) : (nCurrentY - nNextY);
            if (nDiff < 0) {
                nDiff = -nDiff;
            }
            
            // 根据关卡类型选择边界预设ID
            int nLevelPrestId = 0;
            switch (nLevelType) {
                case LVLTYPE_ACT1_WILDERNESS:
                    // Native passes the BOOL expression (nDirection == 0): true is 1.
                    // Reversing these values selects cliff presets for ordinary borders;
                    // level-link marker file 3 is then outside those presets' file count.
                    nLevelPrestId = sub_6FD80BE0(nCurrentDiffX, nCurrentDiffY,
                            (pDrlgVertex.getNDirection() == 0) ? 1 : 0);
                    break;
                case LVLTYPE_ACT2_DESERT:
                    nLevelPrestId = sub_6FD80BE0(nCurrentDiffX, nCurrentDiffY, 2);
                    break;
                case LVLTYPE_ACT4_MESA:
                    nLevelPrestId = sub_6FD80BE0(nCurrentDiffX, nCurrentDiffY, 3);
                    break;
                case LVLTYPE_ACT5_BARRICADE:
                    nLevelPrestId = sub_6FD80BE0(nCurrentDiffX, nCurrentDiffY, sub_6FD84100(level));
                    break;
                default:
                    nLevelPrestId = sub_6FD80BE0(nCurrentDiffX, nCurrentDiffY, -1);
                    break;
            }
            
            // 如果顶点没有标志2，则沿路径放置边界预设
            if ((pDrlgVertex.getDwFlags() & 2) == 0) {
                int nX = nCurrentX;
                int nY = nCurrentY;
                int borderSteps = 0;
                while (nX != nNextX || nY != nNextY) {
                    if (++borderSteps > 4096) {
                        throw new IllegalStateException(String.format(
                                "DRLG border edge did not converge level=%d current=(%d,%d) next=(%d,%d) diff=(%d,%d)",
                                level.getLevelId(), nCurrentX, nCurrentY, nNextX, nNextY,
                                nCurrentDiffX, nCurrentDiffY));
                    }
                    nX += nCurrentDiffX;
                    nY += nCurrentDiffY;
                    DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, nLevelPrestId, -1, false);
                    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nX, nY, 
                            tLvlPrestPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                }
            }
            
            // 如果顶点有标志1但没有标志2，处理关卡链接
            if ((pDrlgVertex.getDwFlags() & 1) != 0 && (pDrlgVertex.getDwFlags() & 2) == 0) {
                int nX, nY;
                switch (nAct) {
                    case ACT_I:
                    case ACT_V: {
                        nX = (pDrlgVertex.getNPosX() < pNextVertex.getNPosX()) 
                                ? pDrlgVertex.getNPosX() : pNextVertex.getNPosX();
                        nX += nCurrentDiffXAbs * nDiff / 2;
                        
                        nY = (pDrlgVertex.getNPosY() < pNextVertex.getNPosY()) 
                                ? pDrlgVertex.getNPosY() : pNextVertex.getNPosY();
                        nY += nCurrentDiffYAbs * nDiff / 2;
                        
                        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfoToAdd = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                        tPackedInfoToAdd.setBLvlLink(true);
                        tPackedInfoToAdd.setNPickedFile((level.getLevelId() == D2LevelIds.LEVEL_BURIALGROUNDS) ? 4 : 3);
                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nX, nY, 0xF0000, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nX, nY, 
                                tPackedInfoToAdd.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                        break;
                    }
                    case ACT_IV: {
                        nX = (pDrlgVertex.getNPosX() < pNextVertex.getNPosX()) 
                                ? pDrlgVertex.getNPosX() : pNextVertex.getNPosX();
                        nX += nCurrentDiffXAbs * nDiff / 2;
                        
                        nY = (pDrlgVertex.getNPosY() < pNextVertex.getNPosY()) 
                                ? pDrlgVertex.getNPosY() : pNextVertex.getNPosY();
                        nY += nCurrentDiffYAbs * nDiff / 2;
                        
                        D2DrlgOutdoorPackedGrid2InfoStrc tPackedInfoToAdd = new D2DrlgOutdoorPackedGrid2InfoStrc(0);
                        tPackedInfoToAdd.setBLvlLink(true);
                        tPackedInfoToAdd.setNPickedFile(3);
                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nX, nY, 0xF0000, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                        DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nX, nY, 
                                tPackedInfoToAdd.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
                        break;
                    }
                    case ACT_II: {
                        nX = (pDrlgVertex.getNPosX() < pNextVertex.getNPosX()) 
                                ? pDrlgVertex.getNPosX() : pNextVertex.getNPosX();
                        nX += nCurrentDiffXAbs * nDiff / 2;
                        
                        nY = (pDrlgVertex.getNPosY() < pNextVertex.getNPosY()) 
                                ? pDrlgVertex.getNPosY() : pNextVertex.getNPosY();
                        nY += nCurrentDiffYAbs * nDiff / 2;
                        
                        int nIndex = nCurrentDiffX + 2 * nCurrentDiffY + 2;
                        if (nIndex >= 0 && nIndex < nDesertBorderIds.length) {
                            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX, nY, nDesertBorderIds[nIndex][0], -1, false);
                            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nX + nCurrentDiffXAbs, nY + nCurrentDiffYAbs, 
                                    nDesertBorderIds[nIndex][1], -1, false);
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            
            // 更新方向标志
            byte nDirection = pDrlgVertex.getNDirection();
            if (nDirection != 0) {
                tLvlPrestPackedInfo.setBHasDirection(true);
            } else {
                nDirection = pNextVertex.getNDirection();
                tLvlPrestPackedInfo.setBHasDirection(true);
            }
            
            // 根据关卡类型确定 v41
            int v41;
            switch (nLevelType) {
                case LVLTYPE_ACT1_WILDERNESS:
                    v41 = (nDirection == 0) ? 0 : 1;
                    break;
                case LVLTYPE_ACT2_DESERT:
                    v41 = 2;
                    break;
                case LVLTYPE_ACT4_MESA:
                    v41 = 3;
                    break;
                case LVLTYPE_ACT5_BARRICADE:
                    v41 = sub_6FD84100(level);
                    break;
                default:
                    v41 = -1;
                    break;
            }
            
            // 根据顶点标志计算边界预设ID
            if ((pDrlgVertex.getDwFlags() & 2) != 0) {
                if ((pNextVertex.getDwFlags() & 2) != 0) {
                    nLevelPrestId = sub_6FD80C10(nCurrentDiffX, nCurrentDiffY, nNextDiffX, nNextDiffY, v41);
                } else {
                    nLevelPrestId = sub_6FD80C10(nCurrentDiffX, nCurrentDiffY, 2 * nNextDiffX, 2 * nNextDiffY, v41);
                }
            } else {
                if ((pNextVertex.getDwFlags() & 2) != 0) {
                    nLevelPrestId = sub_6FD80C10(2 * nCurrentDiffX, 2 * nCurrentDiffY, nNextDiffX, nNextDiffY, v41);
                } else {
                    nLevelPrestId = sub_6FD80C10(2 * nCurrentDiffX, 2 * nCurrentDiffY, 2 * nNextDiffX, 2 * nNextDiffY, v41);
                }
            }
            
            // 处理特殊的边界预设ID
            if (nLevelPrestId == D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_6A) {
                if (pDrlgVertex.getNDirection() == 1) {
                    if (pNextVertex.getNDirection() != 1) {
                        nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_6B;
                    }
                } else {
                    nLevelPrestId = D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_BORDER_6C;
                }
                
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nNextX, nNextY, nLevelPrestId, -1, false);
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nNextX, nNextY, 
                        tLvlPrestPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
            } else if (nLevelPrestId != D2LvlPrestIds.LVLPREST_NONE) {
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, nNextX, nNextY, nLevelPrestId, -1, false);
                DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), nNextX, nNextY, 
                        tLvlPrestPackedInfo.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OR);
            }
            
            pDrlgVertex = pNextVertex;
            pNextVertex = pNextVertex.getPNext();
        } while (pDrlgVertex != outdoors.getPVertex());
        
        // 最后设置空白边界网格单元格
        setBlankBorderGridCells(level);
    }
    
    /**
     * D2Common.0x6FD80480
     * 构建库拉斯特
     */
    public static void buildKurast(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 根据关卡 ID 调用不同的构建函数
        switch (level.getLevelId()) {
            case D2LevelIds.LEVEL_LOWERKURAST:
                DrlgOutJung.buildLowerKurast(level);
                break;
                
            case D2LevelIds.LEVEL_KURASTBAZAAR:
                DrlgOutJung.buildKurastBazaar(level);
                break;
                
            case D2LevelIds.LEVEL_UPPERKURAST:
                DrlgOutJung.buildUpperKurast(level);
                break;
                
            default:
                break;
        }
        
        // 根据关卡 ID 生成预设
        switch (level.getLevelId()) {
            case D2LevelIds.LEVEL_LOWERKURAST:
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_WAYPOINT, 0, 0, (char)15);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_SLUMS_16X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_SLUMS_16X16, 4);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_SLUMS_08X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_SLUMS_16X08, 0);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_SLUMS_08X08, 
                        D2LvlPrestIds.LVLPREST_ACT3_SLUMS_08X08, 0);
                break;
                
            case D2LevelIds.LEVEL_KURASTBAZAAR:
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 3, 3, D2LvlPrestIds.LVLPREST_ACT3_BURBS_SEWER, 0, false);
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, level.getLevelCoords().getNWidth() / 8 - 4, 3, 
                        D2LvlPrestIds.LVLPREST_ACT3_BURBS_SEWER, 1, false);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_TEMPLE, 0, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_TEMPLE, 1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_WAYPOINT, 0, 0, (char)15);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_16X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_BURBS_16X16, 4);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_08X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_BURBS_16X08, 0);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_08X08, 
                        D2LvlPrestIds.LVLPREST_ACT3_BURBS_08X08, 0);
                break;
                
            case D2LevelIds.LEVEL_UPPERKURAST:
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 3, level.getLevelCoords().getNHeight() / 8 - 4, 
                        D2LvlPrestIds.LVLPREST_ACT3_METRO_SEWER, 0, false);
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, level.getLevelCoords().getNWidth() / 8 - 4, 
                        level.getLevelCoords().getNHeight() / 8 - 4, D2LvlPrestIds.LVLPREST_ACT3_METRO_SEWER, 1, false);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_METROTEMPLE, 0, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_METROTEMPLE, 1, 0, (char)15);
                DrlgOutdoors.spawnOutdoorLevelPreset(level, D2LvlPrestIds.LVLPREST_ACT3_BURBS_WAYPOINT, 0, 0, (char)15);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_METRO_16X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_METRO_16X16, 4);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_METRO_08X16, 
                        D2LvlPrestIds.LVLPREST_ACT3_METRO_16X08, 0);
                DrlgOutJung.spawnRandomPreset(level, D2LvlPrestIds.LVLPREST_ACT3_METRO_08X08, 
                        D2LvlPrestIds.LVLPREST_ACT3_METRO_08X08, 0);
                break;
                
            case D2LevelIds.LEVEL_KURASTCAUSEWAY:
                DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, D2LvlPrestIds.LVLPREST_ACT3_BRIDGE, 0, false);
                break;
                
            default:
                return;
        }
    }
    
    /**
     * D2Common.0x6FD806A0
     * 初始化 Act3 户外关卡
     */
    public static void initAct3OutdoorLevel(D2DrlgLevel level) {
        if (level == null) {
            return;
        }
        
        // 设置户外网格链接标志
        setOutGridLinkFlags(level);
        
        // 构建丛林
        DrlgOutJung.buildJungle(level);
        
        // 构建库拉斯特
        buildKurast(level);
        
        // 处理 TRAVINCAL 关卡的特殊预设
        if (level.getLevelId() == D2LevelIds.LEVEL_TRAVINCAL) {
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 0, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_NW, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 2, 0, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_N, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 6, 0, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_NE, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 0, 4, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_SW, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 2, 4, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_S, -1, false);
            DrlgOutdoors.spawnOutdoorLevelPresetEx(level, 6, 4, D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_SE, -1, false);
        }
    }
    
    // ========== createLevelConnections 的辅助函数 ==========
    
    /**
     * D2Common.0x6FD823C0
     * 处理链接数据
     */
    @FunctionalInterface
    private interface FlagSetterFunction {
        void accept(D2DrlgLevel pLevel, int nIteration, int[] pRand);
    }
    
    private static void sub_6FD823C0(D2DrlgStrc pDrlg, D2DrlgLink[] pDrlgLink, 
            LinkFunctionWithIteration a3, FlagSetterFunction a4) {
        D2DrlgLevelLinkDataStrc pLevelLinkData = new D2DrlgLevelLinkDataStrc();
        D2LevelDefBin pLevelDefBinRecord;
        D2DrlgWarp pDrlgWarp1;
        D2DrlgWarp pDrlgWarp2;
        D2DrlgLevel pLevel;
        int nCounter = 0;
        int nVisEx = 0;
        int nVis = 0;
        long v28;
        
        // 初始化随机数数组
        int[][] nRand = pLevelLinkData.getNRand();
        if (nRand != null) {
            for (int i = 0; i < 15 && i < nRand[0].length; ++i) {
                if (nRand.length > 0) nRand[0][i] = -1;
                if (nRand.length > 1) nRand[1][i] = -1;
                if (nRand.length > 2) nRand[2][i] = -1;
                if (nRand.length > 3) nRand[3][i] = -1;
            }
        }
        
        pLevelLinkData.getPSeed().setNLowSeed(pDrlg.getSeed().getNLowSeed());
        pLevelLinkData.getPSeed().setNHighSeed(pDrlg.getSeed().getNHighSeed());
        pLevelLinkData.setPLink(pDrlgLink);

        D2Log.debug("DRLG_LINK_LAYOUT begin links=%d validator=%s flagSetter=%s",
                countLinks(pDrlgLink), a3 != null ? a3.getClass().getName() : "none",
                a4 != null ? a4.getClass().getName() : "none");
        
        // 初始化关卡坐标的宽度和高度
        for (int i = 0; i < 15 && pDrlgLink[i].getNLevel() != 0; ++i) {
            pLevelDefBinRecord = DataTbls.getLevelDefRecord(pDrlgLink[i].getNLevel());
            if (pLevelDefBinRecord == null) {
                D2Log.warning("DRLG_LINK_LAYOUT missing LevelDef index=%d level=%d", i,
                        pDrlgLink[i].getNLevel());
                continue;
            }
            pLevelLinkData.getPLevelCoord(i).setNWidth(pLevelDefBinRecord.getDwSizeX(pDrlg.getDifficulty()));
            pLevelLinkData.getPLevelCoord(i).setNHeight(pLevelDefBinRecord.getDwSizeY(pDrlg.getDifficulty()));
            D2Log.debug("DRLG_LINK_LAYOUT seedCoord index=%d level=%d link=%d width=%d height=%d offset=(%d,%d)",
                    i, pDrlgLink[i].getNLevel(), pDrlgLink[i].getNLevelLink(),
                    pLevelLinkData.getPLevelCoord(i).getNWidth(),
                    pLevelLinkData.getPLevelCoord(i).getNHeight(),
                    pLevelDefBinRecord.getDwOffsetX(), pLevelDefBinRecord.getDwOffsetY());
        }
        
        // 执行链接算法
        nCounter = 0;
        int layoutAttempts = 0;
        while (pDrlgLink[nCounter].getNLevel() != 0) {
            if (++layoutAttempts > 100000) {
                throw new IllegalStateException(String.format(
                        "DRLG link layout exceeded attempt limit: links=%d index=%d level=%d coord=%s",
                        countLinks(pDrlgLink), nCounter, pDrlgLink[nCounter].getNLevel(),
                        coordSummary(pLevelLinkData.getPLevelCoord(nCounter))));
            }
            pLevelLinkData.setNIteration(nCounter);
            pLevelLinkData.setNCurrentLevel(pDrlgLink[nCounter].getNLevel());
            D2Log.debug("DRLG_LINK_LAYOUT try index=%d level=%d parent=%d before=%s rand=(%d,%d,%d,%d)",
                    nCounter, pDrlgLink[nCounter].getNLevel(), pDrlgLink[nCounter].getNLevelLink(),
                    coordSummary(pLevelLinkData.getPLevelCoord(nCounter)),
                    pLevelLinkData.getNRand(0, nCounter), pLevelLinkData.getNRand(1, nCounter),
                    pLevelLinkData.getNRand(2, nCounter), pLevelLinkData.getNRand(3, nCounter));
            boolean linkResult = pDrlgLink[nCounter].executeLink(pLevelLinkData);
            D2Log.debug("DRLG_LINK_LAYOUT link index=%d level=%d result=%s after=%s rand=(%d,%d,%d,%d)",
                    nCounter, pDrlgLink[nCounter].getNLevel(), linkResult,
                    coordSummary(pLevelLinkData.getPLevelCoord(nCounter)),
                    pLevelLinkData.getNRand(0, nCounter), pLevelLinkData.getNRand(1, nCounter),
                    pLevelLinkData.getNRand(2, nCounter), pLevelLinkData.getNRand(3, nCounter));
            if (linkResult) {
                boolean accepted = a3 == null || a3.link(pLevelLinkData, nCounter);
                D2Log.debug("DRLG_LINK_LAYOUT validate index=%d level=%d accepted=%s coord=%s",
                        nCounter, pDrlgLink[nCounter].getNLevel(), accepted,
                        coordSummary(pLevelLinkData.getPLevelCoord(nCounter)));
                if (accepted) {
                    ++nCounter;
                }
            } else {
                clearRand(pLevelLinkData, nCounter);
                --nCounter;
            }
            if (nCounter < 0) {
                D2Log.warning("DRLG_LINK_LAYOUT backtracked before first link; aborting links");
                break;
            }
        }
        
        // 应用链接结果
        for (int i = 0; i < 15 && pDrlgLink[i].getNLevel() != 0; ++i) {
            if (pDrlgLink[i].getNLevelLink() != -1) {
                nVis = pDrlgLink[pDrlgLink[i].getNLevelLink()].getNLevel();
            } else {
                nVis = 0;
            }
            
            if (pDrlgLink[i].getNLevelLinkEx() != -1) {
                nVisEx = pDrlgLink[pDrlgLink[i].getNLevelLinkEx()].getNLevel();
            } else {
                nVisEx = 0;
            }
            
            pLevel = DrlgDrlg.getLevel(pDrlg, pDrlgLink[i].getNLevel());
            
            pLevel.getLevelCoords().setNPosX(pLevelLinkData.getPLevelCoord(i).getNPosX());
            pLevel.getLevelCoords().setNPosY(pLevelLinkData.getPLevelCoord(i).getNPosY());
            pLevel.getLevelCoords().setNWidth(pLevelLinkData.getPLevelCoord(i).getNWidth());
            pLevel.getLevelCoords().setNHeight(pLevelLinkData.getPLevelCoord(i).getNHeight());
            D2Log.debug("DRLG_LINK_LAYOUT apply index=%d level=%d coord=%s vis=%d visEx=%d",
                    i, pDrlgLink[i].getNLevel(), coordSummary(pLevelLinkData.getPLevelCoord(i)), nVis, nVisEx);
            
            // 处理预设关卡的特殊逻辑
            if (pLevel.getDrlgType() == D2DrlgTypes.DRLGTYPE_PRESET) {
                D2DrlgPresetInfoStrc pPreset = pLevel.getPreset();
                if (pPreset != null) {
                    if (pLevel.getLevelId() == D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
                        int[] nRand0Array = pLevelLinkData.getNRand(0);
                        if (nRand0Array != null && i < nRand0Array.length) {
                            pPreset.setNDirection(nRand0Array[i]);
                        }
                    } else if (pLevel.getLevelId() == D2LevelIds.LEVEL_LUTGHOLEIN) {
                        int[] nRand0Array = pLevelLinkData.getNRand(0);
                        if (nRand0Array != null && (i + 1) < nRand0Array.length) {
                            pPreset.setNDirection(nRand0Array[i + 1]);
                        }
                    }
                }
            }
            
            // 处理 BLACKMARSH 的特殊逻辑
            if (pLevel.getLevelId() == D2LevelIds.LEVEL_BLACKMARSH) {
                D2DrlgLevel pOuterCloisterLevel = DrlgDrlg.getLevel(pDrlg, D2LevelIds.LEVEL_OUTERCLOISTER);
                if (pOuterCloisterLevel != null) {
                    D2DrlgPresetInfoStrc pPresetInfo = pOuterCloisterLevel.getPreset();
                    if (pPresetInfo != null) {
                        int[] nRand0Array = pLevelLinkData.getNRand(0);
                        int nRand0 = (nRand0Array != null && i < nRand0Array.length) ? nRand0Array[i] : -1;
                        D2Seed pSeed = pDrlg.getSeed();
                        
                        if (nRand0 == 1) {
                            // v28 = pDrlg->pSeed.nHighSeed + 1791398085 * pDrlg->pSeed.nLowSeed;
                            long v28Local = pSeed.getNHighSeed() + 1791398085L * pSeed.getNLowSeed();
                            pSeed.setLSeed(v28Local);
                            // pPresetInfo->nDirection = 2 - ((v28 & 1) != 0);
                            pPresetInfo.setNDirection(2 - ((v28Local & 1) != 0 ? 1 : 0));
                        } else if (nRand0 == 3) {
                            // v28 = 1791398085 * pDrlg->pSeed.nLowSeed + pDrlg->pSeed.nHighSeed;
                            long v28Local = 1791398085L * pSeed.getNLowSeed() + pSeed.getNHighSeed();
                            pSeed.setLSeed(v28Local);
                            // pPresetInfo->nDirection = ~(uint8_t)v28 & 1;
                            pPresetInfo.setNDirection((int)((~(byte)v28Local) & 1));
                        }
                    }
                }
            }
            
            // 调用回调函数
            if (a4 != null) {
                // 传递 nRand[0] 数组和迭代索引
                // 在 sub_6FD82360 中，pRand 是 pLevelLinkData.nRand[0]，nIteration 是 i
                a4.accept(pLevel, i, pLevelLinkData.getNRand(0));
            }
            
            // 设置传送点
            if (pDrlg.getActNo() != D2C_Acts.ACT_V) {
                if (nVis != 0) {
                    pDrlgWarp1 = DrlgDrlg.getDrlgWarpFromLevelId(pDrlg, pDrlgLink[i].getNLevel());
                    pDrlgWarp2 = DrlgDrlg.getDrlgWarpFromLevelId(pDrlg, nVis);
                    DrlgDrlg.setWarpId(pDrlgWarp1, nVis, -1, -1);
                    DrlgDrlg.setWarpId(pDrlgWarp2, pDrlgLink[i].getNLevel(), -1, -1);
                }
                
                if (nVisEx != 0) {
                    pDrlgWarp1 = DrlgDrlg.getDrlgWarpFromLevelId(pDrlg, pDrlgLink[i].getNLevel());
                    pDrlgWarp2 = DrlgDrlg.getDrlgWarpFromLevelId(pDrlg, nVisEx);
                    DrlgDrlg.setWarpId(pDrlgWarp1, nVisEx, -1, -1);
                    DrlgDrlg.setWarpId(pDrlgWarp2, pDrlgLink[i].getNLevel(), -1, -1);
                }
            }
        }
        D2Log.debug("DRLG_LINK_LAYOUT end links=%d attempts=%d", countLinks(pDrlgLink), layoutAttempts);
    }

    private static int countLinks(D2DrlgLink[] links) {
        if (links == null) return 0;
        int count = 0;
        while (count < links.length && links[count] != null && links[count].getNLevel() != 0) count++;
        return count;
    }

    private static void clearRand(D2DrlgLevelLinkDataStrc data, int index) {
        for (int r = 0; r < 4; r++) {
            int[] values = data.getNRand(r);
            if (values != null && index >= 0 && index < values.length) values[index] = -1;
        }
    }

    private static String coordSummary(D2DrlgCoord coord) {
        return coord == null ? "null" : String.format("(%d,%d %dx%d)", coord.getNPosX(),
                coord.getNPosY(), coord.getNWidth(), coord.getNHeight());
    }
    
    /**
     * D2Common.0x6FD826D0
     * 链接关卡（基于重叠检查）
     */
    private static void sub_6FD826D0(D2DrlgStrc pDrlg, int nStartId, int nEndId) {
        D2DrlgWarp pDrlgWarp;
        D2DrlgLevel pLevel;
        
        for (int i = nStartId; i <= nEndId; ++i) {
            pLevel = DrlgDrlg.getLevel(pDrlg, i);
            pDrlgWarp = DrlgDrlg.getDrlgWarpFromLevelId(pDrlg, i);
            
            for (int j = nStartId; j <= nEndId; ++j) {
                if (i != j && DrlgDrlg.checkOverlappingWithOrthogonalMargin(
                        pLevel.getLevelCoords(), 
                        DrlgDrlg.getLevel(pDrlg, j).getLevelCoords(), 
                        -1)) {
                    DrlgDrlg.setWarpId(pDrlgWarp, j, -1, -1);
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD82750
     * 链接户外关卡
     */
    private static void sub_6FD82750(D2DrlgStrc pDrlg, int nStartId, int nEndId) {
        D2DrlgLevel pWarpLevel;
        D2DrlgLevel pLevel;
        int[] pWarpIdArray;
        int[] pVisArray;
        
        for (int i = nStartId; i <= nEndId; ++i) {
            pLevel = DrlgDrlg.getLevel(pDrlg, i);
            
            if (pLevel.getDrlgType() == D2DrlgTypes.DRLGTYPE_OUTDOOR) {
                pVisArray = DrlgDrlgRoom.getVisArrayFromLevelId(pDrlg, i);
                pWarpIdArray = DrlgDrlgWarp.getWarpIdArrayFromLevelId(pDrlg, i);
                
                for (int j = 0; j < 8; ++j) {
                    if (pVisArray[j] != 0 && pWarpIdArray[j] == -1) {
                        pWarpLevel = DrlgDrlg.getLevel(pLevel.getDrlg(), pVisArray[j]);
                        if (pWarpLevel != null && pLevel.getPresetOrOutdoorsOrMaze() instanceof D2DrlgOutdoorInfoStrc) {
                            D2DrlgOutdoorInfoStrc outdoors = (D2DrlgOutdoorInfoStrc) pLevel.getPresetOrOutdoorsOrMaze();
                            int nDirection = DrlgDrlg.getDirectionFromCoordinates(
                                pLevel.getLevelCoords(), pWarpLevel.getLevelCoords());
                            boolean bIsPreset = pWarpLevel.getDrlgType() == D2DrlgTypes.DRLGTYPE_PRESET;
                            
                            // 使用数组包装来模拟 D2DrlgOrth**
                            D2DrlgOrth[] ppDrlgOrth = new D2DrlgOrth[1];
                            ppDrlgOrth[0] = outdoors.getPRoomData();
                            
                            DrlgDrlgRoom.addOrth(ppDrlgOrth, pWarpLevel, nDirection, bIsPreset);
                            
                            // 更新 pRoomData
                            outdoors.setPRoomData(ppDrlgOrth[0]);
                        }
                    }
                }
            }
        }
    }
    
    // ========== 链接函数 ==========
    
    /**
     * D2Common.0x6FD81330
     * 基础链接函数（使用偏移量）
     */
    public static boolean sub_6FD81330(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        D2LevelDefBin pLevelDefBinRecord;
        
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = -1;
        }
        
        pLevelDefBinRecord = DataTbls.getLevelDefRecord(pLevelLinkData.getNCurrentLevel());
        pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()).setNPosX(pLevelDefBinRecord.getDwOffsetX());
        pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()).setNPosY(pLevelDefBinRecord.getDwOffsetY());
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81380
     * 链接函数（4方向）
     */
    public static boolean sub_6FD81380(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 3L);
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
        } else {
            if (((pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] + 1) % 4) == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = (pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] + 1) % 4;
        }
        
        sub_6FD81430(
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
            1
        );
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81430
     * 计算坐标（4方向）
     */
    private static void sub_6FD81430(D2DrlgCoord pDrlgCoord1, D2DrlgCoord pDrlgCoord2, int a3, int a4) {
        switch (a3) {
            case 0:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() - 16);
                }
                break;
                
            case 1:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - 16);
                } else if (a4 == 2) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + 8);
                }
                break;
                
            case 2:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() - pDrlgCoord2.getNHeight());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() + 16);
                }
                break;
                
            case 3:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight() - pDrlgCoord2.getNHeight());
                
                switch (a4) {
                    case 1:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + 16);
                        break;
                    case 2:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - 8);
                        break;
                    case 3:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + 8);
                        break;
                    default:
                        break;
                }
                break;
                
            default:
                return;
        }
    }
    
    /**
     * D2Common.0x6FD81530
     * 链接函数（8方向）
     */
    public static boolean sub_6FD81530(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 7L);
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
        } else {
            if (((pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] + 1) % 8) == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = ((pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] + 1) % 8);
        }
        
        sub_6FD815E0(
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
            1
        );
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81850
     * 计算坐标（4方向，变体）
     */
    private static void sub_6FD81850(D2DrlgCoord pDrlgCoord1, D2DrlgCoord pDrlgCoord2, int a3, int a4) {
        switch (a3) {
            case 0:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() + 16);
                }
                break;
                
            case 1:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight() - pDrlgCoord2.getNHeight());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + 16);
                } else if (a4 == 2) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - 8);
                }
                break;
                
            case 2:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() - pDrlgCoord2.getNHeight());
                
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() - 16);
                }
                break;
                
            case 3:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                
                switch (a4) {
                    case 1:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - 16);
                        break;
                    case 2:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + 8);
                        break;
                    case 3:
                        pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - 8);
                        break;
                    default:
                        break;
                }
                break;
                
            default:
                return;
        }
    }
    
    /**
     * D2Common.0x6FD815E0
     * 计算坐标（8方向）
     */
    private static void sub_6FD815E0(D2DrlgCoord pDrlgCoord1, D2DrlgCoord pDrlgCoord2, int a3, int a4) {
        switch (a3) {
            case 0:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() - pDrlgCoord2.getNWidth() / 2 - 8);
                }
                break;
            case 1:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() + pDrlgCoord1.getNHeight());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() + pDrlgCoord2.getNWidth() / 2 + 8);
                }
                break;
            case 2:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - pDrlgCoord2.getNHeight() / 2 - 8);
                }
                break;
            case 3:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() - pDrlgCoord2.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + pDrlgCoord2.getNHeight() / 2 + 8);
                }
                break;
            case 4:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() - pDrlgCoord2.getNHeight());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() - pDrlgCoord2.getNWidth() / 2 - 8);
                }
                break;
            case 5:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY() - pDrlgCoord2.getNHeight());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosX(pDrlgCoord2.getNPosX() + pDrlgCoord2.getNWidth() / 2 + 8);
                }
                break;
            case 6:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() - pDrlgCoord2.getNHeight() / 2 - 8);
                }
                break;
            case 7:
                pDrlgCoord2.setNPosX(pDrlgCoord1.getNPosX() + pDrlgCoord1.getNWidth());
                pDrlgCoord2.setNPosY(pDrlgCoord1.getNPosY());
                if (a4 == 1) {
                    pDrlgCoord2.setNPosY(pDrlgCoord2.getNPosY() + pDrlgCoord2.getNHeight() / 2 + 8);
                }
                break;
            default:
                return;
        }
    }
    
    /**
     * D2Common.0x6FD81720
     * 链接函数（ROGUEENCAMPMENT）
     */
    public static boolean sub_6FD81720(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        int nRand2 = 0;
        int nRand0 = 0;
        
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 3L);
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
            pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1L);
            
            nRand2 = pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()];
        } else {
            nRand0 = (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] + pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()]) % 4;
            nRand2 = (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] + 1) % 2;
            
            if (nRand0 == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] && nRand2 == pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = nRand0;
        }
        pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] = nRand2;
        
        if (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] == 1) {
            sub_6FD81430(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                2
            );
        } else {
            sub_6FD81850(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                2
            );
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81950
     * 链接函数（BLOODMOOR）
     */
    public static boolean sub_6FD81950(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        int nRand2 = 0;
        int nRand0 = 0;
        
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 3L);
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
            pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()] = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1L);
            
            nRand2 = pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()];
        } else {
            nRand0 = (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] + pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()]) % 4;
            nRand2 = (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] + 1) % 2;
            
            if (nRand0 == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] && nRand2 == pLevelLinkData.getNRand(3)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = nRand0;
        }
        pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] = nRand2;
        
        // 设置宽度和高度
        pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()).setNWidth(
            (pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] % 2 != 0) ? 96 : 56
        );
        pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()).setNHeight(
            (pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] % 2 != 0) ? 56 : 96
        );
        
        if (pLevelLinkData.getNRand(2)[pLevelLinkData.getNIteration()] == 1) {
            sub_6FD81430(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                1
            );
        } else {
            sub_6FD81850(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                1
            );
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81AD0
     * 链接函数（TAMOEHIGHLAND）
     */
    public static boolean sub_6FD81AD0(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData == null) {
            return false;
        }
        
        // 设置随机数为 0（北方向）
        pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = 0;
        pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
        
        // 使用 4 方向坐标计算（方向 0，偏移 0）
        sub_6FD81430(
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
            0
        );
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81B30
     * 链接函数（ROCKYWASTE）
     */
    public static boolean sub_6FD81B30(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        int nRand = 0;
        
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = (int)((Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1L) + 1);
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
        } else {
            nRand = 2 - ((pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] != 1) ? 1 : 0);
            
            if (nRand == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = nRand;
        }
        
        if (pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] == 1) {
            sub_6FD81430(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                1,
                0
            );
        } else {
            sub_6FD81850(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                0
            );
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81BF0
     * 链接函数（VALLEYOFSNAKES）
     */
    public static boolean sub_6FD81BF0(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData == null) {
            return false;
        }
        
        int nRand;
        
        if (pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] == -1) {
            // 初始化随机数（0-7）
            int nRand1 = (int)(Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 7);
            pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = nRand1;
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = nRand1;
        } else {
            // 递增随机数（模 8）
            nRand = (pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] + 1) % 8;
            
            // 如果回到初始值，返回 false
            if (nRand == pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()]) {
                return false;
            }
            
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = nRand;
        }
        
        // 使用 8 方向坐标计算
        sub_6FD815E0(
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
            pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
            pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
            0
        );
        
        return true;
    }
    
    /**
     * D2Common.0x6FD81CA0
     * 链接函数（OUTERSTEPPES）
     */
    public static boolean sub_6FD81CA0(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()] = 3;
        pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()] = pLevelLinkData.getNRand(1)[pLevelLinkData.getNIteration()];
        
        if ((Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1) == 0) {
            sub_6FD81850(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                3
            );
            // 设置全局标志（对应 C++ 中的 dword_6FDEA6FC = 0x400000）
            dword_6FDEA6FC = 0x400000;
        } else {
            sub_6FD81430(
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getPLink()[pLevelLinkData.getNIteration()].getNLevelLink()),
                pLevelLinkData.getPLevelCoord(pLevelLinkData.getNIteration()),
                pLevelLinkData.getNRand(0)[pLevelLinkData.getNIteration()],
                3
            );
            // 设置全局标志（对应 C++ 中的 dword_6FDEA6FC = 0x800000）
            dword_6FDEA6FC = 0x800000;
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD82050
     * Act1 荒野链接验证函数
     */
    public static boolean sub_6FD82050(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        int nLevelLink = gAct1WildernessDrlgLink[nIteration].getNLevelLink();
        
        for (int i = 0; i < nIteration; ++i) {
            if (i != nLevelLink) {
                if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                        pLevelLinkData.getPLevelCoord(nIteration),
                        pLevelLinkData.getPLevelCoord(i),
                        0)) {
                    return false;
                }
            }
        }
        
        if (gAct1WildernessDrlgLink[nIteration].getNLevel() != D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
            if (gAct1WildernessDrlgLink[nIteration].getNLevel() == D2LevelIds.LEVEL_BURIALGROUNDS) {
                // The native table is embedded in a larger structure, but the
                // Java representation is only the six Act-I entries (including
                // the sentinel). Never walk past the actual array.
                for (int i = 0; i < gAct1WildernessDrlgLink.length; ++i) {
                    if (i != nIteration && gAct1WildernessDrlgLink[i].getNLevelLink() == nLevelLink 
                            && pLevelLinkData.getNRand(2)[nIteration] == pLevelLinkData.getNRand(2)[i]) {
                        return false;
                    }
                }
            }
            
            return true;
        } else {
            int index = pLevelLinkData.getNRand(0)[nIteration] + 4 * (pLevelLinkData.getNRand(2)[nIteration] + 2 * (pLevelLinkData.getNRand(0)[nLevelLink] + 4 * pLevelLinkData.getNRand(2)[nLevelLink]));
            if (index >= 0 && index < dword_6FDD05C0.length) {
                return dword_6FDD05C0[index];
            }
            return false;
        }
    }
    
    /**
     * D2Common.0x6FD82130
     * Act1 修道院链接验证函数
     */
    public static boolean sub_6FD82130(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        boolean bResult = false;
        int nCounter = 0;
        
        nCounter = 0;
        
        while (nCounter < nIteration) {
            if (nCounter != gAct1MonasteryDrlgLink[nIteration].getNLevelLink() 
                    && !DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                            pLevelLinkData.getPLevelCoord(nIteration),
                            pLevelLinkData.getPLevelCoord(nCounter),
                            0)) {
                return false;
            }
            
            ++nCounter;
        }
        
        bResult = true;
        if (nIteration != 0) {
            pLevelLinkData.getPLevelCoord(0).setNHeight(pLevelLinkData.getPLevelCoord(0).getNHeight() + 200);
            pLevelLinkData.getPLevelCoord(0).setNPosY(pLevelLinkData.getPLevelCoord(0).getNPosY() - 200);
            
            bResult = DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    pLevelLinkData.getPLevelCoord(0),
                    pLevelLinkData.getPLevelCoord(nCounter),
                    0);
            
            pLevelLinkData.getPLevelCoord(0).setNHeight(pLevelLinkData.getPLevelCoord(0).getNHeight() - 200);
            pLevelLinkData.getPLevelCoord(0).setNPosY(pLevelLinkData.getPLevelCoord(0).getNPosY() + 200);
        }
        
        return bResult;
    }
    
    /**
     * D2Common.0x6FD821E0
     * Act2 户外链接函数
     */
    public static boolean linkAct2Outdoors(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        int nLevelLink = gAct2OutdoorDrlgLink[nIteration].getNLevelLink();
        
        for (int i = 0; i < nIteration; ++i) {
            if (i != nLevelLink && !DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    pLevelLinkData.getPLevelCoord(nIteration),
                    pLevelLinkData.getPLevelCoord(i),
                    0)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD82240
     * Act2 峡谷链接函数
     */
    public static boolean linkAct2Canyon(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        int nLevelLink = gAct2CanyonDrlgLink[nIteration].getNLevelLink();
        
        for (int i = 0; i < nIteration; ++i) {
            if (i != nLevelLink && !DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    pLevelLinkData.getPLevelCoord(nIteration),
                    pLevelLinkData.getPLevelCoord(i),
                    0)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD822A0
     * Act4 户外链接函数
     */
    public static boolean linkAct4Outdoors(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        int nLevelLink = gAct4OutdoorDrlgLink[nIteration].getNLevelLink();
        
        for (int i = 0; i < nIteration; ++i) {
            if (i != nLevelLink && !DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    pLevelLinkData.getPLevelCoord(nIteration),
                    pLevelLinkData.getPLevelCoord(i),
                    0)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD82300
     * Act4 混沌圣域链接函数
     */
    public static boolean linkAct4ChaosSanctum(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration) {
        int nLevelLink = gAct4ChaosSanctumDrlgLink[nIteration].getNLevelLink();
        
        for (int i = 0; i < nIteration; ++i) {
            if (i != nLevelLink && !DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                    pLevelLinkData.getPLevelCoord(nIteration),
                    pLevelLinkData.getPLevelCoord(i),
                    0)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD82360
     * 设置关卡标志（基于随机数）
     * @param pLevel 关卡
     * @param nIteration 迭代索引
     * @param pRand 随机数数组（pLevelLinkData.nRand[0]）
     */
    public static void sub_6FD82360(D2DrlgLevel pLevel, int nIteration, int[] pRand) {
        if (pLevel == null || pRand == null || nIteration < 0 || nIteration >= pRand.length) {
            return;
        }
        
        // 静态常量数组，对应 C++ 中的 stru_6FDD06C0
        final D2UnkOutdoorStrc3[] stru_6FDD06C0 = new D2UnkOutdoorStrc3[] {
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_BLOODMOOR, D2LevelIds.LEVEL_COLDPLAINS, 1, 0, 0x04),
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_BLOODMOOR, D2LevelIds.LEVEL_COLDPLAINS, 2, 3, 0x04),
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_COLDPLAINS, D2LevelIds.LEVEL_BURIALGROUNDS, 2, 1, 0x08),
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_COLDPLAINS, D2LevelIds.LEVEL_BURIALGROUNDS, 3, 0, 0x08),
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_COLDPLAINS, D2LevelIds.LEVEL_BURIALGROUNDS, 1, 1, 0x10),
            new D2UnkOutdoorStrc3(0, D2LevelIds.LEVEL_COLDPLAINS, D2LevelIds.LEVEL_BURIALGROUNDS, 3, 3, 0x10),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 0, 0, 0x08),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 2, 2, 0x08),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 3, 0, 0x08),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 3, 2, 0x08),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 0, 1, 0x400),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 1, 1, 0x400),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 2, 1, 0x200),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 2, 2, 0x80),
            new D2UnkOutdoorStrc3(D2LevelIds.LEVEL_BLOODMOOR, 0, 0, 3, 2, 0x100),
        };
        
        // 检查是否为户外类型
        if (pLevel.getDrlgType() != D2DrlgTypes.DRLGTYPE_OUTDOOR) {
            return;
        }
        
        // 获取户外信息
        Object presetOrOutdoorsOrMaze = pLevel.getPresetOrOutdoorsOrMaze();
        if (!(presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc)) {
            return;
        }
        
        D2DrlgOutdoorInfoStrc pOutdoors = (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        
        // 遍历所有标志设置规则
        for (int i = 0; i < stru_6FDD06C0.length; ++i) {
            D2UnkOutdoorStrc3 rule = stru_6FDD06C0[i];
            
            // 检查关卡 ID 匹配（0 表示匹配所有关卡）
            if (pLevel.getLevelId() == rule.getNLevelId() || rule.getNLevelId() == 0) {
                // 检查排除的关卡
                if (pLevel.getLevelId() != rule.getNExcludedLevel1() 
                        && pLevel.getLevelId() != rule.getNExcludedLevel2()) {
                    // 检查随机数匹配（pRand[nIteration] 和 pRand[nIteration + 1]）
                    if (nIteration < pRand.length && pRand[nIteration] == rule.getNRand()) {
                        if (nIteration + 1 < pRand.length && pRand[nIteration + 1] == rule.getNNextRand()) {
                            // 设置标志
                            pOutdoors.setDwFlags(pOutdoors.getDwFlags() | rule.getNFlags());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD83970
     * 设置丛林坐标
     * 根据随机数方向计算丛林的坐标偏移量
     * @param pDrlgCoord 基础坐标
     * @param pJungle 丛林结构
     * @param nRand 随机数方向（0-4）
     * @param nSizeX X 尺寸
     * @param nSizeY Y 尺寸
     */
    public static void sub_6FD83970(D2DrlgCoord pDrlgCoord, D2JungleStrc pJungle, 
            int nRand, int nSizeX, int nSizeY) {
        if (pDrlgCoord == null || pJungle == null) {
            return;
        }
        
        int nX = 0;
        int nY = 0;
        
        switch (nRand) {
            case 0:
                // 北方向
                nX = 0;
                nY = -nSizeY;
                break;
            
            case 1:
                // 西方向（偏上）
                nX = -nSizeX;
                // 计算 nY = nSizeY / 3（使用固定点乘法模拟）
                long temp1 = ((long)nSizeY * 0x55555555L) >>> 32;
                int nTemp = (int)((temp1 - nSizeY) >> 1);
                nY = (nTemp >> 31) + nTemp; // 符号扩展
                break;
            
            case 2:
                // 东方向（偏上）
                nX = nSizeX;
                // 计算 nY = nSizeY / 3（使用固定点乘法模拟）
                long temp2 = ((long)nSizeY * 0x55555555L) >>> 32;
                int nTemp2 = (int)((temp2 - nSizeY) >> 1);
                nY = (nTemp2 >> 31) + nTemp2; // 符号扩展
                break;
            
            case 3:
                // 西方向（偏下）
                nX = -nSizeX;
                // 计算 nY = -nSizeY / 3（使用固定点乘法模拟）
                long temp3 = (0xFFFFFFFF55555554L * (long)nSizeY) >>> 32;
                int nTemp3 = (int)temp3;
                nY = (nTemp3 >> 31) + nTemp3; // 符号扩展
                break;
            
            case 4:
                // 东方向（偏下）
                nX = nSizeX;
                // 计算 nY = -nSizeY / 3（使用固定点乘法模拟）
                long temp4 = (0xFFFFFFFF55555554L * (long)nSizeY) >>> 32;
                int nTemp4 = (int)temp4;
                nY = (nTemp4 >> 31) + nTemp4; // 符号扩展
                break;
            
            default:
                break;
        }
        
        // 设置丛林坐标
        pJungle.getPDrlgCoord().setNPosX(nX + pDrlgCoord.getNPosX());
        pJungle.getPDrlgCoord().setNHeight(nSizeY);
        pJungle.getPDrlgCoord().setNWidth(nSizeX);
        pJungle.getPDrlgCoord().setNPosY(nY + pDrlgCoord.getNPosY());
        pJungle.setField_10(nRand);
    }
    
    // 预设块维度常量
    private static final int nPresetBlocksDimensions = 32;
    
    // 方向常量
    private static final int DIRECTION_SOUTHWEST = 0;
    private static final int DIRECTION_NORTHWEST = 1;
    private static final int DIRECTION_SOUTHEAST = 2;
    private static final int DIRECTION_NORTHEAST = 3;
    private static final int DIRECTION_COUNT = 4;
    
    // 丛林预设数组（对应 C++ 中的 gJunglePresets）
    // 注意：这些预设ID需要根据实际的 D2LvlPrestIds 值进行调整
    private static final int[] gJunglePresets = {
        D2LvlPrestIds.LVLPREST_NONE, 0, 0, 0,  // 占位符，需要实际的预设ID
        // 这里需要添加完整的64个预设ID值
        // 由于预设ID值未知，暂时使用占位符
    };
    
    // 蜘蛛森林预设数组（对应 C++ 中的 gSpiderForestPresets）
    private static final int[] gSpiderForestPresets = {
        D2LvlPrestIds.LVLPREST_NONE, 0, 0, 0,  // 占位符，需要实际的预设ID
        // 这里需要添加完整的17个预设ID值
    };
    
    /**
     * D2Common.0x6FD7F1B0 (DRLG_GenerateJunglesAttachPoints)
     * 生成丛林附着点
     * 为每个丛林生成附着点，用于连接不同的丛林区域
     * 
     * @param pDrlg Drlg 结构
     * @param tJungles 丛林数组
     * @param nMinX 最小X坐标
     * @param nMinY 最小Y坐标
     * @param nSpiderForestLevelSizeX 蜘蛛森林关卡尺寸X
     * @param nSpiderForestLevelSizeY 蜘蛛森林关卡尺寸Y
     * @param nPresetsWidth 预设宽度
     * @param nPresetsHeight 预设高度
     * @param pPreset0 预设数组0（丛林索引）
     * @param pPreset1 预设数组1（距离/值）
     * @param pPreset2 预设数组2（附着点标记）
     * @param pLevelPresetId 关卡预设ID数组
     */
    private static void generateJunglesAttachPoints(
            D2DrlgStrc pDrlg,
            D2JungleStrc[] tJungles,
            int nMinX, int nMinY,
            int nSpiderForestLevelSizeX, int nSpiderForestLevelSizeY,
            int nPresetsWidth, int nPresetsHeight,
            int[] pPreset0, int[] pPreset1, int[] pPreset2, int[] pLevelPresetId) {
        
        int nPresets = nPresetsWidth * nPresetsHeight;
        
        // 初始化数组
        java.util.Arrays.fill(pPreset0, 0);
        java.util.Arrays.fill(pPreset1, 0);
        java.util.Arrays.fill(pPreset2, 0);
        java.util.Arrays.fill(pLevelPresetId, 0);
        
        int nSpiderForestLevelPresetsBlocksSizeX = nSpiderForestLevelSizeX / nPresetBlocksDimensions;
        int nSpiderForestLevelPresetsBlocksSizeY = nSpiderForestLevelSizeY / nPresetBlocksDimensions;
        
        for (int nJungleIdx = 0; nJungleIdx < D2JungleStrc.JUNGLE_MAX_ATTACH; nJungleIdx++) {
            D2JungleStrc tCurrentJungle = tJungles[nJungleIdx];
            int nJungleOffsetX = tCurrentJungle.getPDrlgCoord().getNPosX() - nMinX;
            int nJungleOffsetY = tCurrentJungle.getPDrlgCoord().getNPosY() - nMinY;
            
            tCurrentJungle.setNPresetsBlocksX((((nJungleOffsetX & 0x1F) + nJungleOffsetX) >> 5) + 1);
            tCurrentJungle.setNPresetsBlocksY((((nJungleOffsetY & 0x1F) + nJungleOffsetY) >> 5) + 1);
            
            // 释放旧的丛林定义数组
            if (tCurrentJungle.getPJungleDefs() != null) {
                // Java 中不需要手动释放，GC 会自动处理
                tCurrentJungle.setPJungleDefs(null);
            }
            
            // 分配新的丛林定义数组
            int[] pJungleDefs = new int[nSpiderForestLevelPresetsBlocksSizeX * nSpiderForestLevelPresetsBlocksSizeY];
            tCurrentJungle.setPJungleDefs(pJungleDefs);
            
            // 确定行起始偏移
            boolean bLineStartWithOffset;
            if (nJungleIdx != 0) {
                bLineStartWithOffset = (tCurrentJungle.getField_10() % 2) != 0;
            } else {
                bLineStartWithOffset = (Seed.rollLimitedRandomNumber(pDrlg.getSeed(), 2) != 0);
            }
            
            // 生成潜在的附着点
            int nJungleAttachPoints = 0;
            while (nJungleAttachPoints < 2) {
                // 初始化数据
                int nPresetBlockRowOffset = tCurrentJungle.getNPresetsBlocksX() + nPresetsWidth * tCurrentJungle.getNPresetsBlocksY();
                for (int nPresetBlockY = 0; nPresetBlockY < nSpiderForestLevelPresetsBlocksSizeY; nPresetBlockY++) {
                    for (int nPresetBlockX = 0; nPresetBlockX < nSpiderForestLevelPresetsBlocksSizeX; nPresetBlockX++) {
                        int nPresetBlockIndex = nPresetBlockRowOffset + nPresetBlockX;
                        pPreset0[nPresetBlockIndex] = nJungleIdx + 1;
                        pPreset1[nPresetBlockIndex] = 0;
                        pPreset2[nPresetBlockIndex] = 0;
                        pLevelPresetId[nPresetBlockIndex] = 0;
                    }
                    nPresetBlockRowOffset += nPresetsWidth;
                }
                
                int nFirstPresetsBlockY = tCurrentJungle.getNPresetsBlocksY();
                int nLastPresetsBlockY = nFirstPresetsBlockY + nSpiderForestLevelPresetsBlocksSizeY - 1;
                
                if (nJungleIdx != 0) {
                    int nFirstPresetsBlockIndex = nLastPresetsBlockY * nPresetsWidth + tCurrentJungle.getNPresetsBlocksX();
                    pPreset2[nFirstPresetsBlockIndex + (bLineStartWithOffset ? 1 : 0)] = 1;
                    pPreset2[nFirstPresetsBlockIndex + (bLineStartWithOffset ? 0 : 1)] = D2JungleStrc.JUNGLE_PRESET2_ATTACH_POINT;
                }
                
                // 处理分支
                for (int nBranchIdx = 0; nBranchIdx < tCurrentJungle.getNBranch(); nBranchIdx++) {
                    D2JungleStrc pJungleBranch = tCurrentJungle.getPJungleBranches(nBranchIdx);
                    if (pJungleBranch == null) continue;
                    
                    int nPresetOffsetX = 0;
                    int nPresetOffsetY = 0;
                    switch (pJungleBranch.getField_10()) {
                        case 0:
                            break;
                        case 1:
                            nPresetOffsetY = 3;
                            break;
                        case 2:
                            nPresetOffsetX = 1;
                            nPresetOffsetY = 3;
                            break;
                        case 3:
                            nPresetOffsetY = 1;
                            break;
                        case 4:
                            nPresetOffsetX = 1;
                            nPresetOffsetY = 1;
                            break;
                    }
                    int nBranchIndex = tCurrentJungle.getNPresetsBlocksX() + nPresetOffsetX + 
                            nPresetsWidth * (tCurrentJungle.getNPresetsBlocksY() + nPresetOffsetY);
                    pPreset2[nBranchIndex] = 1;
                }
                
                nJungleAttachPoints = (nJungleIdx != 0) ? 1 : 0;
                
                // 在列中追踪波形，生成附着点
                int nCurrentPresetsBlockX = tCurrentJungle.getNPresetsBlocksX();
                int i = 20 * (5 * nJungleIdx + 5);
                int nColumnSize = 0;
                for (int nCurrentPresetsBlockY = nLastPresetsBlockY; nCurrentPresetsBlockY >= nFirstPresetsBlockY; ) {
                    int nCurrentPresetsBlockRowOffset = nCurrentPresetsBlockY * nPresetsWidth;
                    
                    pPreset1[nCurrentPresetsBlockRowOffset + nCurrentPresetsBlockX + (bLineStartWithOffset ? 1 : 0)] = i;
                    i++;
                    
                    if (nColumnSize == 0
                            || nCurrentPresetsBlockY == nFirstPresetsBlockY
                            || (Seed.rollLimitedRandomNumber(pDrlg.getSeed(), 3) != 0)) {
                        nColumnSize++;
                        
                        // 当列至少有2个像素时，标记相对单元格为附着点
                        if (nColumnSize >= 2 && nCurrentPresetsBlockY > 1) {
                            int nNextPresetIdx = nCurrentPresetsBlockRowOffset + nCurrentPresetsBlockX + (bLineStartWithOffset ? 0 : 1);
                            
                            if (pPreset2[nNextPresetIdx] == 0) {
                                pPreset2[nNextPresetIdx] = D2JungleStrc.JUNGLE_PRESET2_ATTACH_POINT;
                                nJungleAttachPoints++;
                            }
                        }
                        nCurrentPresetsBlockY--;
                    } else {
                        nColumnSize = 0;
                        bLineStartWithOffset = !bLineStartWithOffset;
                    }
                }
            }
            
            // 减少附着点数量到3个
            while (nJungleAttachPoints > 3) {
                boolean bFinished = false;
                int nAttachPointsFound = 0;
                int nAttachPointToRemove = Seed.rollLimitedRandomNumber(pDrlg.getSeed(), nJungleAttachPoints);
                
                for (int nCurrentPresetsBlockY = 0; nCurrentPresetsBlockY < nSpiderForestLevelPresetsBlocksSizeY && !bFinished; nCurrentPresetsBlockY++) {
                    int nCurrentPresetsBlockOffset = tCurrentJungle.getNPresetsBlocksX() + 
                            (tCurrentJungle.getNPresetsBlocksY() + nCurrentPresetsBlockY) * nPresetsWidth;
                    
                    for (int nCurrentPresetsBlockX = 0; nCurrentPresetsBlockX < nSpiderForestLevelPresetsBlocksSizeX && !bFinished; nCurrentPresetsBlockX++) {
                        int nCurrentPresetBlockIdx = nCurrentPresetsBlockOffset + nCurrentPresetsBlockX;
                        if (pPreset2[nCurrentPresetBlockIdx] == D2JungleStrc.JUNGLE_PRESET2_ATTACH_POINT) {
                            if (nAttachPointsFound == nAttachPointToRemove) {
                                pPreset2[nCurrentPresetBlockIdx] = 0;
                                bFinished = true;
                                nJungleAttachPoints--;
                            }
                            nAttachPointsFound++;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7F3A0 (DRLG_JungleComputeConnexity)
     * 计算丛林连通性
     * 计算预设之间的连通性关系，建立连接图
     * 
     * @param pDrlg Drlg 结构
     * @param nPresetsWidth 预设宽度
     * @param nPresetsHeight 预设高度
     * @param pPreset0 预设数组0（丛林索引）
     * @param pPreset1 预设数组1（距离/值）
     * @param pPreset2 预设数组2（附着点标记）
     * @param pLevelPresetId 关卡预设ID数组
     */
    private static void jungleComputeConnexity(
            D2DrlgStrc pDrlg,
            int nPresetsWidth,
            int nPresetsHeight,
            int[] pPreset0,
            int[] pPreset1,
            int[] pPreset2,
            int[] pLevelPresetId) {
        
        for (int nCurrentPresetY = 0; nCurrentPresetY < nPresetsHeight; nCurrentPresetY++) {
            for (int nCurrentPresetX = 0; nCurrentPresetX < nPresetsWidth; nCurrentPresetX++) {
                int nCurrentPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX;
                int nRightPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX + 1;
                int nLeftPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX - 1;
                int nTopPresetIndex = (nCurrentPresetY - 1) * nPresetsWidth + nCurrentPresetX;
                int nBottomPresetIndex = (nCurrentPresetY + 1) * nPresetsWidth + nCurrentPresetX;
                
                if (pPreset2[nCurrentPresetIndex] == 1) {
                    int nPreset0CurrentValue = pPreset0[nCurrentPresetIndex];
                    
                    int nFlags = 0;
                    int nLastDirPreset1Value = Integer.MAX_VALUE;
                    
                    // 更新标志：根据相邻预设的 pPreset1 值
                    if (nTopPresetIndex >= 0 && nTopPresetIndex < pPreset1.length) {
                        int nPreset1Value = pPreset1[nTopPresetIndex];
                        if (nPreset1Value != 0 && nPreset1Value < nLastDirPreset1Value
                                && pPreset0[nTopPresetIndex] == nPreset0CurrentValue) {
                            nFlags = D2JungleStrc.JUNGLE_FLAG_TOP;
                            nLastDirPreset1Value = nPreset1Value;
                        }
                    }
                    
                    if (nBottomPresetIndex >= 0 && nBottomPresetIndex < pPreset1.length) {
                        int nPreset1Value = pPreset1[nBottomPresetIndex];
                        if (nPreset1Value != 0 && nPreset1Value < nLastDirPreset1Value
                                && pPreset0[nBottomPresetIndex] == nPreset0CurrentValue) {
                            nFlags = D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                            nLastDirPreset1Value = nPreset1Value;
                        }
                    }
                    
                    if (nRightPresetIndex >= 0 && nRightPresetIndex < pPreset1.length) {
                        int nPreset1Value = pPreset1[nRightPresetIndex];
                        if (nPreset1Value != 0 && nPreset1Value < nLastDirPreset1Value
                                && pPreset0[nRightPresetIndex] == nPreset0CurrentValue) {
                            nFlags = D2JungleStrc.JUNGLE_FLAG_RIGHT;
                            nLastDirPreset1Value = nPreset1Value;
                        }
                    }
                    
                    if (nLeftPresetIndex >= 0 && nLeftPresetIndex < pPreset1.length) {
                        int nPreset1Value = pPreset1[nLeftPresetIndex];
                        if (nPreset1Value != 0 && nPreset1Value < nLastDirPreset1Value
                                && pPreset0[nLeftPresetIndex] == nPreset0CurrentValue) {
                            nFlags = D2JungleStrc.JUNGLE_FLAG_LEFT;
                            nLastDirPreset1Value = nPreset1Value;
                        }
                    }
                    
                    // 更新相邻预设的标志
                    if ((nFlags & D2JungleStrc.JUNGLE_FLAG_TOP) != 0 && nTopPresetIndex >= 0 && nTopPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nTopPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                    }
                    if ((nFlags & D2JungleStrc.JUNGLE_FLAG_BOTTOM) != 0 && nBottomPresetIndex >= 0 && nBottomPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nBottomPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_TOP;
                    }
                    if ((nFlags & D2JungleStrc.JUNGLE_FLAG_RIGHT) != 0 && nRightPresetIndex >= 0 && nRightPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nRightPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_LEFT;
                    }
                    if ((nFlags & D2JungleStrc.JUNGLE_FLAG_LEFT) != 0 && nLeftPresetIndex >= 0 && nLeftPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nLeftPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_RIGHT;
                    }
                    
                    // 检查不同丛林之间的连接
                    if (nTopPresetIndex >= 0 && nTopPresetIndex < pPreset2.length
                            && pPreset2[nTopPresetIndex] == 1
                            && (nTopPresetIndex >= pPreset0.length || pPreset0[nTopPresetIndex] != nPreset0CurrentValue)) {
                        nFlags |= D2JungleStrc.JUNGLE_FLAG_TOP;
                    }
                    if (nBottomPresetIndex >= 0 && nBottomPresetIndex < pPreset2.length
                            && pPreset2[nBottomPresetIndex] == 1
                            && (nBottomPresetIndex >= pPreset0.length || pPreset0[nBottomPresetIndex] != nPreset0CurrentValue)) {
                        nFlags |= D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                    }
                    if (nRightPresetIndex >= 0 && nRightPresetIndex < pPreset2.length
                            && pPreset2[nRightPresetIndex] == 1
                            && (nRightPresetIndex >= pPreset0.length || pPreset0[nRightPresetIndex] != nPreset0CurrentValue)) {
                        nFlags |= D2JungleStrc.JUNGLE_FLAG_RIGHT;
                    }
                    if (nLeftPresetIndex >= 0 && nLeftPresetIndex < pPreset2.length
                            && pPreset2[nLeftPresetIndex] == 1
                            && (nLeftPresetIndex >= pPreset0.length || pPreset0[nLeftPresetIndex] != nPreset0CurrentValue)) {
                        nFlags |= D2JungleStrc.JUNGLE_FLAG_LEFT;
                    }
                }
                
                // 根据 pPreset1 的值更新标志
                int nPreset1CurrentValue = pPreset1[nCurrentPresetIndex];
                if (nPreset1CurrentValue != 0) {
                    if (nTopPresetIndex >= 0 && nTopPresetIndex < pPreset1.length
                            && Math.abs(pPreset1[nTopPresetIndex] - nPreset1CurrentValue) == 1) {
                        pLevelPresetId[nCurrentPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_TOP;
                    }
                    if (nBottomPresetIndex >= 0 && nBottomPresetIndex < pPreset1.length
                            && Math.abs(pPreset1[nBottomPresetIndex] - nPreset1CurrentValue) == 1) {
                        pLevelPresetId[nCurrentPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                    }
                    if (nRightPresetIndex >= 0 && nRightPresetIndex < pPreset1.length
                            && Math.abs(pPreset1[nRightPresetIndex] - nPreset1CurrentValue) == 1) {
                        pLevelPresetId[nCurrentPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_RIGHT;
                    }
                    if (nLeftPresetIndex >= 0 && nLeftPresetIndex < pPreset1.length
                            && Math.abs(pPreset1[nLeftPresetIndex] - nPreset1CurrentValue) == 1) {
                        pLevelPresetId[nCurrentPresetIndex] |= D2JungleStrc.JUNGLE_FLAG_LEFT;
                    }
                }
                
                // 应用标志到当前预设
                if (pPreset2[nCurrentPresetIndex] == 1) {
                    int nFlags = 0;
                    int nPreset0CurrentValue = pPreset0[nCurrentPresetIndex];
                    int nLastDirPreset1Value = Integer.MAX_VALUE;
                    
                    // 重新计算标志（简化版本，完整版本需要更复杂的逻辑）
                    if (nTopPresetIndex >= 0 && nTopPresetIndex < pPreset1.length) {
                        int nPreset1Value = pPreset1[nTopPresetIndex];
                        if (nPreset1Value != 0 && nPreset1Value < nLastDirPreset1Value
                                && pPreset0[nTopPresetIndex] == nPreset0CurrentValue) {
                            nFlags = D2JungleStrc.JUNGLE_FLAG_TOP;
                            nLastDirPreset1Value = nPreset1Value;
                        }
                    }
                    // ... 其他方向的类似逻辑
                    
                    pLevelPresetId[nCurrentPresetIndex] |= nFlags;
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD7F4E0 (DRLG_JungleUpdateAttachPointsDirections)
     * 更新附着点方向
     * 为附着点确定连接方向，如果无法找到有效方向则返回 false（需要重新生成）
     * 
     * @param pDrlg Drlg 结构
     * @param nPresetsWidth 预设宽度
     * @param nPresetsHeight 预设高度
     * @param pPreset0 预设数组0（丛林索引）
     * @param pPreset2 预设数组2（附着点标记）
     * @param pLevelPresetId 关卡预设ID数组
     * @return 如果成功更新所有附着点方向返回 true，否则返回 false（需要重新生成）
     */
    private static boolean jungleUpdateAttachPointsDirections(
            D2DrlgStrc pDrlg,
            int nPresetsWidth,
            int nPresetsHeight,
            int[] pPreset0,
            int[] pPreset2,
            int[] pLevelPresetId) {
        
        for (int nCurrentPresetY = 0; nCurrentPresetY < nPresetsHeight; nCurrentPresetY++) {
            for (int nCurrentPresetX = 0; nCurrentPresetX < nPresetsWidth; nCurrentPresetX++) {
                int nCurrentPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX;
                
                int nDirectionBase = Seed.rollLimitedRandomNumber(pDrlg.getSeed(), DIRECTION_COUNT);
                
                if (pPreset2[nCurrentPresetIndex] == D2JungleStrc.JUNGLE_PRESET2_ATTACH_POINT) {
                    int nRightPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX + 1;
                    int nLeftPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX - 1;
                    int nTopPresetIndex = (nCurrentPresetY - 1) * nPresetsWidth + nCurrentPresetX;
                    int nBottomPresetIndex = (nCurrentPresetY + 1) * nPresetsWidth + nCurrentPresetX;
                    
                    int nPreset0CurrentValue = pPreset0[nCurrentPresetIndex];
                    
                    int nCurrentPresetLevelId = pLevelPresetId[nCurrentPresetIndex];
                    boolean bHadNoPresetLevelId = (nCurrentPresetLevelId == 0);
                    
                    // 查找方向：在同一丛林内查找有效的连接
                    boolean bLookAtNextDirection = true;
                    boolean bFoundDirection = false;
                    
                    for (int i = 0; i < DIRECTION_COUNT && bLookAtNextDirection; i++) {
                        int nDirection = (nDirectionBase + i) % DIRECTION_COUNT;
                        int nCheckPresetIndex = -1;
                        int nFlagToAdd = 0;
                        
                        switch (nDirection) {
                            case DIRECTION_SOUTHWEST:
                                nCheckPresetIndex = nTopPresetIndex;
                                nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_TOP;
                                break;
                            case DIRECTION_NORTHWEST:
                                nCheckPresetIndex = nBottomPresetIndex;
                                nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                                break;
                            case DIRECTION_SOUTHEAST:
                                nCheckPresetIndex = nRightPresetIndex;
                                nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_RIGHT;
                                break;
                            case DIRECTION_NORTHEAST:
                                nCheckPresetIndex = nLeftPresetIndex;
                                nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_LEFT;
                                break;
                        }
                        
                        if (nCheckPresetIndex >= 0 && nCheckPresetIndex < pPreset0.length
                                && nCheckPresetIndex < pLevelPresetId.length) {
                            if (pPreset0[nCheckPresetIndex] == nPreset0CurrentValue
                                    && pLevelPresetId[nCheckPresetIndex] != 0
                                    && pLevelPresetId[nCheckPresetIndex] < 15) {
                                nCurrentPresetLevelId |= (nFlagToAdd << 4);
                                bLookAtNextDirection = false;
                                bFoundDirection = true;
                            }
                        }
                    }
                    
                    // 如果没找到方向，需要重新生成
                    if (!bFoundDirection) {
                        return false;
                    }
                    
                    // 如果之前没有预设级别ID，查找不同丛林之间的连接
                    if (bHadNoPresetLevelId) {
                        bLookAtNextDirection = true;
                        for (int i = 0; i < DIRECTION_COUNT && bLookAtNextDirection; i++) {
                            int nDirection = (nDirectionBase + i) % DIRECTION_COUNT;
                            int nCheckPresetIndex = -1;
                            int nFlagToAdd = 0;
                            
                            switch (nDirection) {
                                case DIRECTION_SOUTHWEST:
                                    nCheckPresetIndex = nTopPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_TOP;
                                    break;
                                case DIRECTION_NORTHWEST:
                                    nCheckPresetIndex = nBottomPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                                    break;
                                case DIRECTION_SOUTHEAST:
                                    nCheckPresetIndex = nRightPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_RIGHT;
                                    break;
                                case DIRECTION_NORTHEAST:
                                    nCheckPresetIndex = nLeftPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_LEFT;
                                    break;
                            }
                            
                            if (nCheckPresetIndex >= 0 && nCheckPresetIndex < pPreset0.length
                                    && nCheckPresetIndex < pPreset2.length) {
                                if (pPreset0[nCheckPresetIndex] != nPreset0CurrentValue
                                        && pPreset2[nCheckPresetIndex] == D2JungleStrc.JUNGLE_PRESET2_ATTACH_POINT) {
                                    nCurrentPresetLevelId |= (nFlagToAdd << 4);
                                    bLookAtNextDirection = false;
                                }
                            }
                        }
                        
                        // 随机决定是否继续查找
                        bLookAtNextDirection = (Seed.rollLimitedRandomNumber(pDrlg.getSeed(), 2) != 0) && bLookAtNextDirection;
                        
                        // 重新随机方向
                        nDirectionBase = Seed.rollLimitedRandomNumber(pDrlg.getSeed(), DIRECTION_COUNT);
                        
                        // 查找其他有效方向
                        for (int i = 0; i < DIRECTION_COUNT && bLookAtNextDirection; i++) {
                            int nDirection = (nDirectionBase + i) % DIRECTION_COUNT;
                            int nCheckPresetIndex = -1;
                            int nFlagToAdd = 0;
                            
                            switch (nDirection) {
                                case DIRECTION_SOUTHWEST:
                                    nCheckPresetIndex = nTopPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_TOP;
                                    break;
                                case DIRECTION_NORTHWEST:
                                    nCheckPresetIndex = nBottomPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_BOTTOM;
                                    break;
                                case DIRECTION_SOUTHEAST:
                                    nCheckPresetIndex = nRightPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_RIGHT;
                                    break;
                                case DIRECTION_NORTHEAST:
                                    nCheckPresetIndex = nLeftPresetIndex;
                                    nFlagToAdd = D2JungleStrc.JUNGLE_FLAG_LEFT;
                                    break;
                            }
                            
                            if (nCheckPresetIndex >= 0 && nCheckPresetIndex < pLevelPresetId.length) {
                                if (pLevelPresetId[nCheckPresetIndex] != 0
                                        && pLevelPresetId[nCheckPresetIndex] < 15
                                        && ((nCurrentPresetLevelId & (nFlagToAdd << 4)) == 0)) {
                                    nCurrentPresetLevelId |= (nFlagToAdd << 4);
                                    bLookAtNextDirection = false;
                                }
                            }
                        }
                    }
                    
                    // 更新相邻预设的标志
                    if ((nCurrentPresetLevelId & (D2JungleStrc.JUNGLE_FLAG_TOP << 4)) != 0
                            && nTopPresetIndex >= 0 && nTopPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nTopPresetIndex] |= (D2JungleStrc.JUNGLE_FLAG_BOTTOM << 4);
                    }
                    if ((nCurrentPresetLevelId & (D2JungleStrc.JUNGLE_FLAG_BOTTOM << 4)) != 0
                            && nBottomPresetIndex >= 0 && nBottomPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nBottomPresetIndex] |= (D2JungleStrc.JUNGLE_FLAG_TOP << 4);
                    }
                    if ((nCurrentPresetLevelId & (D2JungleStrc.JUNGLE_FLAG_RIGHT << 4)) != 0
                            && nRightPresetIndex >= 0 && nRightPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nRightPresetIndex] |= (D2JungleStrc.JUNGLE_FLAG_LEFT << 4);
                    }
                    if ((nCurrentPresetLevelId & (D2JungleStrc.JUNGLE_FLAG_LEFT << 4)) != 0
                            && nLeftPresetIndex >= 0 && nLeftPresetIndex < pLevelPresetId.length) {
                        pLevelPresetId[nLeftPresetIndex] |= (D2JungleStrc.JUNGLE_FLAG_RIGHT << 4);
                    }
                    
                    // 清除附着点标记，应用预设级别ID
                    pPreset2[nCurrentPresetIndex] = 0;
                    pLevelPresetId[nCurrentPresetIndex] |= nCurrentPresetLevelId;
                }
            }
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD7F6A0 (DRLG_JungleNormalizeLevelPresetId)
     * 规范化关卡预设ID
     * 将计算出的预设ID转换为实际的预设ID值
     * 
     * @param nLevelPresetId 关卡预设ID
     * @return 规范化后的预设ID
     */
    private static int jungleNormalizeLevelPresetId(int nLevelPresetId) {
        int nbDirections = 16;
        int nPDef = nLevelPresetId % nbDirections;
        
        if (nPDef != 0) {
            if (nLevelPresetId < nbDirections) {
                // 小于16的预设ID，加上城镇基础ID
                // 注意：LVLPREST_ACT3_TOWN 需要在实际使用时定义
                nPDef += 700; // 占位符，需要实际的 LVLPREST_ACT3_TOWN 值
            } else {
                // 大于等于16的预设ID，从 gJunglePresets 数组中查找
                int nPresetDefinitionOffset = 4 * (nPDef - 1); // 注意：nPDef > 0
                
                if ((nLevelPresetId & (D2JungleStrc.JUNGLE_FLAG_LEFT << 4)) != 0) {
                    if (nPresetDefinitionOffset < gJunglePresets.length) {
                        nPDef = gJunglePresets[nPresetDefinitionOffset];
                    }
                }
                if ((nLevelPresetId & (D2JungleStrc.JUNGLE_FLAG_RIGHT << 4)) != 0) {
                    if (nPresetDefinitionOffset + 1 < gJunglePresets.length) {
                        nPDef = gJunglePresets[nPresetDefinitionOffset + 1];
                    }
                }
                if ((nLevelPresetId & (D2JungleStrc.JUNGLE_FLAG_BOTTOM << 4)) != 0) {
                    if (nPresetDefinitionOffset + 2 < gJunglePresets.length) {
                        nPDef = gJunglePresets[nPresetDefinitionOffset + 2];
                    }
                }
                if ((nLevelPresetId & (D2JungleStrc.JUNGLE_FLAG_TOP << 4)) != 0) {
                    if (nPresetDefinitionOffset + 3 < gJunglePresets.length) {
                        nPDef = gJunglePresets[nPresetDefinitionOffset + 3];
                    }
                }
                
                if (nPDef == D2LvlPrestIds.LVLPREST_NONE) {
                    D2Log.warning("DRLG_JungleNormalizeLevelPresetId: nPDef == LVLPREST_NONE for nLevelPresetId: " + nLevelPresetId);
                }
            }
        } else if (nLevelPresetId >= nbDirections) {
            // 如果 nPDef == 0 但 nLevelPresetId >= 16，从 gSpiderForestPresets 中查找
            int nSpiderForestIndex = nLevelPresetId >> 4;
            if (nSpiderForestIndex < gSpiderForestPresets.length) {
                nPDef = gSpiderForestPresets[nSpiderForestIndex];
            }
            if (nPDef == D2LvlPrestIds.LVLPREST_NONE) {
                D2Log.warning("DRLG_JungleNormalizeLevelPresetId: nPDef == LVLPREST_NONE for nLevelPresetId: " + nLevelPresetId);
            }
        }
        
        return nPDef;
    }
    
    /**
     * D2Common.0x6FD82820
     * 生成丛林
     * 这是一个复杂的函数，需要多个辅助函数支持
     * 
     * @param pLevel 关卡（LEVEL_SPIDERFOREST）
     * @return 返回最后一个丛林关卡
     */
    public static D2DrlgLevel generateJungles(D2DrlgLevel pLevel) {
        if (pLevel == null || pLevel.getDrlg() == null) {
            return null;
        }
        
        // 获取 LEVEL_SPIDERFOREST 的关卡定义
        D2LevelDefBin pSpiderForestLevelDef = DataTbls.getLevelDefRecord(D2LevelIds.LEVEL_SPIDERFOREST);
        if (pSpiderForestLevelDef == null) {
            return null;
        }
        
        D2DrlgStrc pDrlg = pLevel.getDrlg();
        byte nDifficulty = pDrlg.getDifficulty();
        int nSpiderForestLevelSizeX = pSpiderForestLevelDef.getDwSizeX(nDifficulty);
        int nSpiderForestLevelSizeY = pSpiderForestLevelDef.getDwSizeY(nDifficulty);
        
        // 创建丛林数组
        D2JungleStrc[] tJungles = new D2JungleStrc[D2JungleStrc.JUNGLE_MAX_ATTACH];
        for (int i = 0; i < tJungles.length; ++i) {
            tJungles[i] = new D2JungleStrc();
        }
        
        // 初始化第一个丛林（基于关卡坐标）
        sub_6FD83970(pLevel.getLevelCoords(), tJungles[0], 0, nSpiderForestLevelSizeX, nSpiderForestLevelSizeY);
        
        // 生成其他丛林（简化版本：仅生成基本结构，不进行复杂的连接和预设计算）
        int nMinX = pLevel.getLevelCoords().getNPosX();
        int nMaxX = nSpiderForestLevelSizeX + nMinX;
        int nMaxY = pLevel.getLevelCoords().getNPosY();
        int nMinY = nMaxY - nSpiderForestLevelSizeY;
        
        for (int nJungleAttachIdx = 1; nJungleAttachIdx < D2JungleStrc.JUNGLE_MAX_ATTACH; nJungleAttachIdx++) {
            int nBaseOn = Seed.rollLimitedRandomNumber(pDrlg.getSeed(), nJungleAttachIdx);
            
            D2JungleStrc pCurrentJungle = tJungles[nJungleAttachIdx];
            sub_6FD83970(tJungles[nBaseOn].getPDrlgCoord(), pCurrentJungle, 
                    Seed.rollLimitedRandomNumber(pDrlg.getSeed(), 5), 
                    nSpiderForestLevelSizeX, nSpiderForestLevelSizeY);
            
            // 检查重叠（简化版本：仅检查基本重叠）
            boolean bOverlaps = false;
            for (int j = 0; j < nJungleAttachIdx; ++j) {
                if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(
                        tJungles[j].getPDrlgCoord(), pCurrentJungle.getPDrlgCoord(), 0)) {
                    bOverlaps = true;
                    break;
                }
            }
            
            if (bOverlaps) {
                // 重试
                nJungleAttachIdx--;
                continue;
            }
            
            // 链接丛林
            pCurrentJungle.setPBasedOnJungle(tJungles[nBaseOn]);
            if (tJungles[nBaseOn].getNBranch() < D2JungleStrc.JUNGLE_MAX_ATTACH) {
                tJungles[nBaseOn].setPJungleBranches(tJungles[nBaseOn].getNBranch(), pCurrentJungle);
                tJungles[nBaseOn].setNBranch(tJungles[nBaseOn].getNBranch() + 1);
            }
            
            // 更新边界
            if (nMinX > pCurrentJungle.getPDrlgCoord().getNPosX()) {
                nMinX = pCurrentJungle.getPDrlgCoord().getNPosX();
            }
            if (nMinY > pCurrentJungle.getPDrlgCoord().getNPosY()) {
                nMinY = pCurrentJungle.getPDrlgCoord().getNPosY();
            }
            if (nMaxX < pCurrentJungle.getPDrlgCoord().getNWidth() + pCurrentJungle.getPDrlgCoord().getNPosX()) {
                nMaxX = pCurrentJungle.getPDrlgCoord().getNWidth() + pCurrentJungle.getPDrlgCoord().getNPosX();
            }
        }
        
        // 验证边界对齐
        if ((nMaxX - nMinX) % nPresetBlocksDimensions != 0) {
            D2Log.warning("DRLG_GenerateJungles: (nMaxX - nMinX) % nPresetBlocksDimensions != 0");
        }
        if ((nMaxY - nMinY) % nPresetBlocksDimensions != 0) {
            D2Log.warning("DRLG_GenerateJungles: (nMaxY - nMinY) % nPresetBlocksDimensions != 0");
        }
        
        // 计算预设网格尺寸
        int nPresetsWidth = (nMaxX - nMinX) / nPresetBlocksDimensions + 2;
        int nPresetsHeight = (nMaxY - nMinY) / nPresetBlocksDimensions + 2;
        int nPresets = nPresetsWidth * nPresetsHeight;
        
        // 分配预设数组
        int[] pPreset0 = new int[nPresets];
        int[] pPreset1 = new int[nPresets];
        int[] pPreset2 = new int[nPresets];
        int[] pLevelPresetId = new int[nPresets];
        
        // 生成附着点、计算连通性、更新方向（可能需要多次尝试）
        do {
            generateJunglesAttachPoints(pDrlg, tJungles,
                    nMinX, nMinY,
                    nSpiderForestLevelSizeX, nSpiderForestLevelSizeY,
                    nPresetsWidth, nPresetsHeight,
                    pPreset0, pPreset1, pPreset2, pLevelPresetId);
            jungleComputeConnexity(pDrlg, nPresetsWidth, nPresetsHeight,
                    pPreset0, pPreset1, pPreset2, pLevelPresetId);
        } while (!jungleUpdateAttachPointsDirections(pDrlg, nPresetsWidth, nPresetsHeight,
                pPreset0, pPreset2, pLevelPresetId));
        
        // 规范化所有预设ID
        for (int nCurrentPresetY = 0; nCurrentPresetY < nPresetsHeight; nCurrentPresetY++) {
            for (int nCurrentPresetX = 0; nCurrentPresetX < nPresetsWidth; nCurrentPresetX++) {
                int nCurrentPresetIndex = nCurrentPresetY * nPresetsWidth + nCurrentPresetX;
                pLevelPresetId[nCurrentPresetIndex] = jungleNormalizeLevelPresetId(pLevelPresetId[nCurrentPresetIndex]);
            }
        }
        
        // 将预设ID复制到丛林定义数组
        for (int nJungleIdx = 0; nJungleIdx < D2JungleStrc.JUNGLE_MAX_ATTACH; ++nJungleIdx) {
            D2JungleStrc tCurrentJungle = tJungles[nJungleIdx];
            int nSpiderForestLevelPresetsBlocksSizeX = nSpiderForestLevelSizeX / nPresetBlocksDimensions;
            int nSpiderForestLevelPresetsBlocksSizeY = nSpiderForestLevelSizeY / nPresetBlocksDimensions;
            
            int nJungleDefsIndex = 0;
            for (int nBlockY = 0; nBlockY < nSpiderForestLevelPresetsBlocksSizeY; ++nBlockY) {
                for (int nBlockX = 0; nBlockX < nSpiderForestLevelPresetsBlocksSizeX; ++nBlockX) {
                    int nCurrentPresetIndex = nBlockX + tCurrentJungle.getNPresetsBlocksX() + 
                            nPresetsWidth * (nBlockY + tCurrentJungle.getNPresetsBlocksY());
                    if (nCurrentPresetIndex >= 0 && nCurrentPresetIndex < pLevelPresetId.length) {
                        int nLevelPresetId = pLevelPresetId[nCurrentPresetIndex];
                        if (tCurrentJungle.getPJungleDefs() != null && 
                                nJungleDefsIndex < tCurrentJungle.getPJungleDefs().length) {
                            tCurrentJungle.getPJungleDefs()[nJungleDefsIndex] = nLevelPresetId;
                            if (nLevelPresetId > D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_TAIL) {
                                tCurrentJungle.setNJungleDefs(tCurrentJungle.getNJungleDefs() + 1);
                            }
                        }
                        nJungleDefsIndex++;
                    }
                }
            }
        }
        
        // 按 nPosY 排序（从高到低）
        java.util.Arrays.sort(tJungles, (lhs, rhs) -> 
            Integer.compare(rhs.getPDrlgCoord().getNPosY(), lhs.getPDrlgCoord().getNPosY()));
        
        // 设置关卡信息
        D2DrlgLevel pJungleLevel = null;
        for (int i = 0; i < D2JungleStrc.JUNGLE_MAX_ATTACH; ++i) {
            pJungleLevel = DrlgDrlg.getLevel(pDrlg, i + D2LevelIds.LEVEL_SPIDERFOREST);
            if (pJungleLevel != null) {
                // 设置丛林定义数组
                // 注意：需要根据实际的 D2DrlgLevel 结构来设置这些字段
                // pJungleLevel.setPJungleDefs(tJungles[i].getPJungleDefs());
                // pJungleLevel.setNJungleDefsCount(tJungles[i].getNJungleDefs());
                pJungleLevel.getLevelCoords().setNPosX(tJungles[i].getPDrlgCoord().getNPosX());
                pJungleLevel.getLevelCoords().setNPosY(tJungles[i].getPDrlgCoord().getNPosY());
                pJungleLevel.getLevelCoords().setNWidth(tJungles[i].getPDrlgCoord().getNWidth());
                pJungleLevel.getLevelCoords().setNHeight(tJungles[i].getPDrlgCoord().getNHeight());
            }
        }
        
        D2Log.debug("DRLG_GenerateJungles: Jungle generation completed successfully");
        return pJungleLevel;
    }
}
