package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;
import com.d2moo.common.seed.Seed;

/**
 * Drlg 户外房间模块
 * 对应 C++ 文件：DrlgOutRoom.cpp
 */
public class DrlgOutRoom {
    
    /**
     * D2Common.0x6FD83DE0
     * 分配户外房间
     * 被 DrlgDrlgRoom 依赖
     */
    public static void allocDrlgOutdoorRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getLevel() == null || drlgRoom.getLevel().getDrlg() == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        D2DrlgOutdoorRoomStrc outdoorRoom = D2Pool.callocStrcPool(memPool, D2DrlgOutdoorRoomStrc.class);
        if (outdoorRoom == null) {
            outdoorRoom = new D2DrlgOutdoorRoomStrc();
        }
        drlgRoom.setMazeOrOutdoor(outdoorRoom);
    }
    
    /**
     * D2Common.0x6FD83D90
     * 释放户外房间数据
     * 被 DrlgRoomTile 依赖
     */
    public static void freeDrlgOutdoorRoomData(D2DrlgRoom drlgRoom) {
        Object outdoor = drlgRoom.getMazeOrOutdoor();
        if (outdoor == null || !(outdoor instanceof D2DrlgOutdoorRoomStrc)) {
            return;
        }
        
        D2DrlgOutdoorRoomStrc outdoorRoom = (D2DrlgOutdoorRoomStrc) outdoor;
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 释放网格
        DrlgDrlgGrid.freeGrid(memPool, outdoorRoom.getPTileTypeGrid());
        DrlgDrlgGrid.freeGrid(memPool, outdoorRoom.getPWallGrid());
        DrlgDrlgGrid.freeGrid(memPool, outdoorRoom.getPFloorGrid());
        DrlgDrlgGrid.freeGrid(memPool, outdoorRoom.getPDirtPathGrid());
        
        // 释放顶点
        D2DrlgVertexStrc pVertex = outdoorRoom.getPVertex();
        if (pVertex != null) {
            D2DrlgVertexStrc[] ppVertices = new D2DrlgVertexStrc[1];
            ppVertices[0] = pVertex;
            DrlgDrlgVer.freeVertices(memPool, ppVertices);
        }
    }
    
    /**
     * D2Common.0x6FD83D20
     * 释放户外房间
     * 被 DrlgDrlgRoom 依赖
     */
    public static void freeDrlgOutdoorRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom.getMazeOrOutdoor() != null) {
            freeDrlgOutdoorRoomData(drlgRoom);
            
            // 释放内存池
            D2Pool.freePool(drlgRoom.getLevel().getDrlg().getMempool(), drlgRoom.getMazeOrOutdoor());
            drlgRoom.setMazeOrOutdoor(null);
        }
    }
    
    /**
     * D2Common.0x6FD83E20
     * 初始化户外房间
     * 被 DrlgRoomTile 依赖
     */
    public static void initializeDrlgOutdoorRoom(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        // 分配瓦片网格
        DrlgRoomTile.allocTileGrid(drlgRoom);
        
        Object outdoor = drlgRoom.getMazeOrOutdoor();
        if (outdoor == null || !(outdoor instanceof D2DrlgOutdoorRoomStrc)) {
            return;
        }
        
        D2DrlgOutdoorRoomStrc outdoorRoom = (D2DrlgOutdoorRoomStrc) outdoor;
        
        // 统计瓦片类型
        DrlgRoomTile.countWallWarpTiles(drlgRoom, outdoorRoom.getPWallGrid(), 
            outdoorRoom.getPTileTypeGrid(), false, false);
        DrlgRoomTile.countAllTileTypes(drlgRoom, outdoorRoom.getPWallGrid(), false, false, false);
        DrlgRoomTile.countAllTileTypes(drlgRoom, outdoorRoom.getPFloorGrid(), false, false, false);
        
        // 分配瓦片数据
        DrlgRoomTile.allocTileData(drlgRoom);
        
        // 加载初始化房间瓦片
        DrlgRoomTile.loadInitRoomTiles(drlgRoom, outdoorRoom.getPWallGrid(), 
            outdoorRoom.getPTileTypeGrid(), false, false, false);
        DrlgRoomTile.loadInitRoomTiles(drlgRoom, outdoorRoom.getPFloorGrid(), 
            null, false, false, false);
        
        // 更新瓦片计数
        if (drlgRoom.getTileGrid() != null && drlgRoom.getTileGrid().getPTiles() != null) {
            drlgRoom.getTileGrid().getPTiles().setNWalls(drlgRoom.getTileGrid().getNWalls());
            drlgRoom.getTileGrid().getPTiles().setNFloors(drlgRoom.getTileGrid().getNFloors());
            drlgRoom.getTileGrid().getPTiles().setNRoofs(drlgRoom.getTileGrid().getNShadows());
        }
        
        // 分配坐标列表
        DrlgDrlgLogic.allocCoordLists(drlgRoom);
    }
    
    /**
     * D2Common.0x6FD83EC0
     * 通过关卡坐标链接关卡
     */
    public static boolean linkLevelsByLevelCoords(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData == null) {
            return false;
        }
        
        int nRand = (int) (Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1);
        int nIteration = pLevelLinkData.getNIteration();
        
        pLevelLinkData.setNRand(0, nIteration, nRand);
        pLevelLinkData.setNRand(1, nIteration, nRand);
        
        D2DrlgCoord levelCoord = pLevelLinkData.getPLevelCoord(nIteration);
        if (levelCoord == null) {
            return false;
        }
        
        if (nRand == 0) {
            levelCoord.setNWidth(64);
            levelCoord.setNHeight(160);
        } else if (nRand == 1) {
            levelCoord.setNWidth(160);
            levelCoord.setNHeight(64);
        }
        
        D2DrlgLink link = pLevelLinkData.getPLink(nIteration);
        if (link == null) {
            return false;
        }
        
        int nLevelLink = link.getNLevelLink();
        D2DrlgCoord linkCoord = pLevelLinkData.getPLevelCoord(nLevelLink);
        if (linkCoord == null) {
            return false;
        }
        
        levelCoord.setNPosX(linkCoord.getNPosX() - levelCoord.getNWidth());
        levelCoord.setNPosY(linkCoord.getNHeight() - levelCoord.getNHeight() + 
            linkCoord.getNPosY() - 16);
        
        return true;
    }
    
    /**
     * D2Common.0x6FD83F70
     * 通过关卡定义链接关卡
     */
    public static boolean linkLevelsByLevelDef(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData == null) {
            return false;
        }
        
        int nRand = (int) (Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1);
        int nIteration = pLevelLinkData.getNIteration();
        
        pLevelLinkData.setNRand(0, nIteration, nRand);
        pLevelLinkData.setNRand(1, nIteration, nRand);
        
        D2DrlgCoord levelCoord = pLevelLinkData.getPLevelCoord(nIteration);
        if (levelCoord == null) {
            return false;
        }
        
        if (nRand == 0) {
            levelCoord.setNWidth(64);
            levelCoord.setNHeight(160);
        } else if (nRand == 1) {
            levelCoord.setNWidth(160);
            levelCoord.setNHeight(64);
        }
        
        // 获取关卡定义记录
        com.d2moo.common.datatbls.D2LevelDefBin levelDefBin = 
            com.d2moo.common.datatbls.DataTbls.getLevelDefRecord(pLevelLinkData.getNCurrentLevel());
        
        if (levelDefBin != null) {
            levelCoord.setNPosX(levelDefBin.getDwOffsetX());
            levelCoord.setNPosY(levelDefBin.getDwOffsetY());
        }
        
        return true;
    }
    
    /**
     * D2Common.0x6FD84010
     * 通过偏移坐标链接关卡
     * 对应 C++ 实现：DRLGOUTROOM_LinkLevelsByOffsetCoords
     */
    public static boolean linkLevelsByOffsetCoords(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLevelLinkData == null) {
            return false;
        }
        
        // 偏移坐标常量（对应 C++ 中的静态数组）
        // 注意：这些坐标值对应 C++ 中的 { 0, -160 }, { -96, -64 }, { -64, -96 }, { -160, 0 }
        final D2Coord[] pOffsetCoords = {
            new D2Coord(0, -160),   // 0: 向上
            new D2Coord(-96, -64),  // 1: 向左上
            new D2Coord(-64, -96),  // 2: 向左上（另一种）
            new D2Coord(-160, 0)    // 3: 向左
        };
        
        int nIteration = pLevelLinkData.getNIteration();
        
        // 如果 nRand[1] 为 -1，则随机生成并设置
        if (pLevelLinkData.getNRand(1, nIteration) == -1) {
            int nRand1 = (int) (Seed.rollRandomNumber(pLevelLinkData.getPSeed()) & 1);
            pLevelLinkData.setNRand(1, nIteration, nRand1);
            pLevelLinkData.setNRand(0, nIteration, nRand1);
        } else {
            // 否则检查 nRand[0] 是否为 0，并验证是否与 nRand[1] 匹配
            boolean bRand0IsNull = (pLevelLinkData.getNRand(0, nIteration) == 0);
            int nRand1Value = pLevelLinkData.getNRand(1, nIteration);
            
            // 如果 bRand0IsNull 的整数值等于 nRand[1]，则返回 false
            if ((bRand0IsNull ? 1 : 0) == nRand1Value) {
                return false;
            }
            
            // 设置 nRand[0] 为 bRand0IsNull 的整数值
            pLevelLinkData.setNRand(0, nIteration, bRand0IsNull ? 1 : 0);
        }
        
        D2DrlgCoord levelCoord = pLevelLinkData.getPLevelCoord(nIteration);
        if (levelCoord == null) {
            return false;
        }
        
        // 根据 nRand[0] 设置宽度和高度
        int nRand0 = pLevelLinkData.getNRand(0, nIteration);
        if (nRand0 == 0) {
            levelCoord.setNWidth(64);
            levelCoord.setNHeight(160);
        } else if (nRand0 == 1) {
            levelCoord.setNWidth(160);
            levelCoord.setNHeight(64);
        }
        
        // 计算索引：nIndex = nRand[0] + 2 * nRand[0] of linked level
        D2DrlgLink link = pLevelLinkData.getPLink(nIteration);
        if (link == null) {
            return false;
        }
        
        int nLevelLink = link.getNLevelLink();
        int nIndex = nRand0 + 2 * pLevelLinkData.getNRand(0, nLevelLink);
        
        if (nIndex < 0 || nIndex >= pOffsetCoords.length) {
            return false;
        }
        
        D2DrlgCoord linkCoord = pLevelLinkData.getPLevelCoord(nLevelLink);
        if (linkCoord == null) {
            return false;
        }
        
        // 应用偏移坐标
        D2Coord offset = pOffsetCoords[nIndex];
        levelCoord.setNPosX(linkCoord.getNPosX() + offset.getX());
        levelCoord.setNPosY(linkCoord.getNPosY() + offset.getY());
        
        return true;
    }
}
