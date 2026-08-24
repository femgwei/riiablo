package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 逻辑模块
 * 对应 C++ 文件：DrlgDrlgLogic.cpp
 */
public class DrlgDrlgLogic {
    
    // 逻辑房间信息标志
    public static final int DRLGLOGIC_ROOMINFO_HAS_COORD_LIST = 0x1;
    public static final int DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS = 0x2;
    
    /**
     * D2Common.0x6FD76420
     * 释放 Drlg 坐标列表
     * 被 DrlgRoomTile 和 DrlgDrlgRoom 依赖
     */
    public static void freeDrlgCoordList(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        D2DrlgLogicalRoomInfo logicalRoomInfo = drlgRoom.getLogicalRoomInfo();
        if (logicalRoomInfo == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 如果设置了网格单元格标志，释放索引网格
        if ((logicalRoomInfo.getDwFlags() & DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS) != 0) {
            DrlgDrlgGrid.freeGrid(memPool, logicalRoomInfo.getPIndexX());
            DrlgDrlgGrid.freeGrid(memPool, logicalRoomInfo.getPIndexY());
        }
        logicalRoomInfo.clearCoordListCells();
        
        // 释放坐标列表链表
        D2RoomCoordListStrc pRoomCoordList = logicalRoomInfo.getPCoordList();
        while (pRoomCoordList != null) {
            D2RoomCoordListStrc pNext = pRoomCoordList.getPNext();
            D2Pool.freePool(memPool, pRoomCoordList);
            pRoomCoordList = pNext;
        }
        
        // 释放逻辑房间信息
        D2Pool.freePool(memPool, logicalRoomInfo);
        drlgRoom.setLogicalRoomInfo(null);
    }
    
    /**
     * D2Common.0x6FD764A0
     * 初始化 Drlg 坐标列表
     * 对应 C++ DRLGLOGIC_InitializeDrlgCoordList
     * 
     * 功能：
     * 1. 分配逻辑房间信息
     * 2. 处理树木瓦片（MAPTILE_TREES）
     * 3. 初始化网格单元格
     * 4. 填充临时网格
     * 5. 处理附近房间
     * 6. 设置瓦片网格标志（递归）
     * 7. 分配坐标列表
     */
    public static void initializeDrlgCoordList(D2DrlgRoom drlgRoom, D2DrlgGridStrc pTileTypeGrid, 
            D2DrlgGridStrc pFloorGrid, D2DrlgGridStrc pWallGrid) {
        if (drlgRoom == null || pTileTypeGrid == null || pFloorGrid == null || pWallGrid == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        D2DrlgCoord drlgCoord = drlgRoom.getDrlgCoord();
        
        // 1. 分配逻辑房间信息
        D2DrlgLogicalRoomInfo logicalRoomInfo = D2Pool.callocStrcPool(memPool, D2DrlgLogicalRoomInfo.class);
        if (logicalRoomInfo == null) {
            logicalRoomInfo = new D2DrlgLogicalRoomInfo();
        }
        logicalRoomInfo.setDwFlags(0);
        logicalRoomInfo.setNLists(0);
        drlgRoom.setLogicalRoomInfo(logicalRoomInfo);
        
        // 2. Native iterates the materialized wall array, not map-link chains.
        for (D2DrlgTileDataStrc tile : wallTiles(drlgRoom)) {
            if (tile == null) continue;
            if ((tile.getDwFlags() & 0x000004) != 0 && !hasMapTileLayer(tile.getDwFlags())) {
                DrlgDrlgGrid.alterGridFlag(pWallGrid, tile.getNPosX(), tile.getNPosY(), 8,
                        DrlgDrlgGrid.FlagOperation.OR);
            }
        }
        
        // 设置网格单元格标志
        logicalRoomInfo.setHasGridCells(true);
        
        // 3. 初始化网格单元格（X 索引网格，尺寸为 nTileWidth + 1, nTileHeight + 1）
        int nWidth = drlgRoom.getNTileWidth() + 1;
        int nHeight = drlgRoom.getNTileHeight() + 1;
        
        DrlgDrlgGrid.initializeGridCells(memPool, logicalRoomInfo.getPIndexX(), nWidth, nHeight);
        
        // 初始化关卡坐标列表计数
        if (drlgRoom.getLevel().getCoordLists() == 0) {
            drlgRoom.getLevel().setCoordLists(1);
        }
        
        int nLists = drlgRoom.getLevel().getCoordLists();
        
        // 4. 填充临时网格
        int[] nCellPositions = new int[1024];
        int[] nCellFlags = new int[256];
        D2DrlgGridStrc pDrlgGrid = new D2DrlgGridStrc();
        DrlgDrlgGrid.fillGrid(pDrlgGrid, nWidth, nHeight, nCellPositions, nCellFlags);
        
        // 处理当前房间的墙壁瓦片（第1层但不是屋顶且不是对象墙壁）
        for (D2DrlgTileDataStrc tile : wallTiles(drlgRoom)) {
            if (tile == null) continue;
            if (checkLayer1ButNotWallObject(tile)) {
                DrlgDrlgGrid.alterGridFlag(pDrlgGrid, tile.getNPosX(), tile.getNPosY(), 1,
                        DrlgDrlgGrid.FlagOperation.OR);
            }
        }
        
        // 5. 处理附近房间
        if (drlgRoom.getPpRoomsNear() != null) {
            for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
                D2DrlgRoom pDrlgRoomNear = drlgRoom.getPpRoomsNear()[i];
                if (pDrlgRoomNear != null && pDrlgRoomNear != drlgRoom && pDrlgRoomNear.getTileGrid() != null) {
                    D2DrlgTileLinkStrc pDrlgTileLink = pDrlgRoomNear.getTileGrid().getPMapLinks();
                    while (pDrlgTileLink != null) {
                        if (!pDrlgTileLink.isBFloor()) {
                            D2DrlgTileDataStrc pDrlgTileData = pDrlgTileLink.getPMapTile();
                            while (pDrlgTileData != null) {
                                if (checkLayer1ButNotWallObject(pDrlgTileData)) {
                                    int nX = pDrlgRoomNear.getNTileXPos() + pDrlgTileData.getNPosX();
                                    int nY = pDrlgRoomNear.getNTileYPos() + pDrlgTileData.getNPosY();
                                    if (DrlgDrlgRoom.areXYInsideCoordinatesOrOnBorder(drlgCoord, nX, nY)) {
                                        DrlgDrlgGrid.alterGridFlag(pDrlgGrid, nX - drlgRoom.getNTileXPos(), 
                                                nY - drlgRoom.getNTileYPos(), 1, DrlgDrlgGrid.FlagOperation.OR);
                                    }
                                }
                                pDrlgTileData = pDrlgTileData.getUnk0x20();
                            }
                        }
                        pDrlgTileLink = pDrlgTileLink.getPNext();
                    }
                }
            }
        }
        
        // 6. 设置瓦片网格标志（递归处理）
        D2UnkDrlgLogicStrc tDRLGLogicUnkStrc = new D2UnkDrlgLogicStrc();
        tDRLGLogicUnkStrc.setField_4(logicalRoomInfo.getPIndexX());
        tDRLGLogicUnkStrc.setPTileTypeGrid(pTileTypeGrid);
        tDRLGLogicUnkStrc.setPDrlgRoom(drlgRoom);
        tDRLGLogicUnkStrc.setPWallGrid(pWallGrid);
        tDRLGLogicUnkStrc.setPFloorGrid(pFloorGrid);
        tDRLGLogicUnkStrc.setField_14(pDrlgGrid);
        tDRLGLogicUnkStrc.setField_18(nLists);
        
        for (int j = 0; j <= drlgRoom.getNTileHeight(); ++j) {
            for (int i = 0; i <= drlgRoom.getNTileWidth(); ++i) {
                if ((DrlgDrlgGrid.getGridEntry(tDRLGLogicUnkStrc.getField_4(), i, j) & 0x10000000) == 0) {
                    tDRLGLogicUnkStrc.setField_18(tDRLGLogicUnkStrc.getField_18() + 1);
                    tDRLGLogicUnkStrc.setNFlags((tDRLGLogicUnkStrc.getField_18() & 0xFFFFFFF) | 0x10000000);
                    
                    // 检查地板信息
                    int nFloorEntry = DrlgDrlgGrid.getGridEntry(tDRLGLogicUnkStrc.getPFloorGrid(), i, j);
                    D2C_PackedTileInformation nFloorPackedInfo = new D2C_PackedTileInformation(nFloorEntry);
                    if ((nFloorPackedInfo.getNTileStyle() == 30 && nFloorPackedInfo.getNWallLayer() == 0) 
                            || nFloorPackedInfo.isBHidden()) {
                        tDRLGLogicUnkStrc.setNFlags(tDRLGLogicUnkStrc.getNFlags() | 0x20000000);
                    }
                    
                    setTileGridFlags(tDRLGLogicUnkStrc, i, j, -1);
                }
            }
        }
        
        // 重置临时网格
        DrlgDrlgGrid.resetGrid(pDrlgGrid);
        
        // 7. 计算坐标列表数量并分配
        logicalRoomInfo.setNLists(tDRLGLogicUnkStrc.getField_18() - nLists + 1);
        
        // Java nodes are allocated while their rectangles are discovered.
        logicalRoomInfo.setPCoordList(null);
        
        drlgRoom.getLevel().setCoordLists(drlgRoom.getLevel().getCoordLists() + logicalRoomInfo.getNLists());
        
        // 8. 分配坐标列表到网格
        assignCoordListsForGrids(drlgRoom, logicalRoomInfo, nLists);
        
        // 9. 为瓦片设置坐标列表
        setCoordListForTiles(drlgRoom);
        
        // 10. 处理附近房间的坐标列表
        sub_6FD769B0(drlgRoom);
    }
    
    /**
     * D2Common.0x6FD77110
     * 获取房间坐标列表（未命名函数）
     * 对应 C++ sub_6FD77110
     * 
     * @param drlgRoom 房间
     * @param x X 坐标（subtile 坐标）
     * @param y Y 坐标（subtile 坐标）
     * @return 坐标列表节点，如果不存在返回 null
     */
    public static D2RoomCoordListStrc sub_6FD77110(D2DrlgRoom drlgRoom, int x, int y) {
        if (drlgRoom == null) {
            return null;
        }
        
        D2DrlgLogicalRoomInfo logicalRoomInfo = drlgRoom.getLogicalRoomInfo();
        if (logicalRoomInfo == null) {
            return null;
        }
        
        // 如果设置了 HAS_COORD_LIST 标志，直接返回第一个坐标列表
        if ((logicalRoomInfo.getDwFlags() & DRLGLOGIC_ROOMINFO_HAS_COORD_LIST) != 0) {
            return logicalRoomInfo.getPCoordList();
        } else {
            int relX = x / 5 - drlgRoom.getNTileXPos();
            int relY = y / 5 - drlgRoom.getNTileYPos();
            return logicalRoomInfo.getCoordListCell(relX, relY);
        }
    }
    
    /**
     * D2Common.0x6FD77080
     * 获取房间坐标列表索引
     */
    public static int getRoomCoordListIndex(D2DrlgRoom drlgRoom, int x, int y) {
        D2RoomCoordListStrc roomCoordList = sub_6FD77110(drlgRoom, x, y);
        return roomCoordList != null ? roomCoordList.getNIndex() : -1;
    }
    
    /**
     * D2Common.0x6FD77190
     * 获取房间坐标列表
     */
    public static D2RoomCoordListStrc getRoomCoordList(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) return null;
        D2DrlgLogicalRoomInfo logicalRoomInfo = drlgRoom.getLogicalRoomInfo();
        if (logicalRoomInfo == null) {
            return null;
        }
        return logicalRoomInfo.getPCoordList();
    }
    
    /**
     * D2Common.0x6FD76F90
     * 分配坐标列表
     * 
     * Native non-logical rooms receive one coordinate list covering the room.
     */
    public static void allocCoordLists(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        D2DrlgLogicalRoomInfo logicalRoomInfo =
                D2Pool.callocStrcPool(memPool, D2DrlgLogicalRoomInfo.class);
        if (logicalRoomInfo == null) logicalRoomInfo = new D2DrlgLogicalRoomInfo();
        logicalRoomInfo.setHasCoordList(true);
        logicalRoomInfo.setNLists(1);
        drlgRoom.setLogicalRoomInfo(logicalRoomInfo);
        drlgRoom.getLevel().setCoordLists(1);

        D2RoomCoordListStrc list =
                D2Pool.callocStrcPool(memPool, D2RoomCoordListStrc.class);
        if (list == null) list = new D2RoomCoordListStrc();
        list.setNIndex(1);
        setBox(list.getPBox(0), drlgRoom);
        setBox(list.getPBox(1), drlgRoom);
        logicalRoomInfo.setPCoordList(list);
    }
    
    /**
     * D2Common.0x6FD76CF0
     * 为网格分配坐标列表
     * 对应 C++ DRLGLOGIC_AssignCoordListsForGrids
     * 
     * 功能：
     * 1. 初始化 Y 索引网格
     * 2. 遍历网格单元格，为每个未分配的单元格创建坐标列表节点
     * 3. 计算坐标框的宽度和高度
     * 4. 在 Y 索引网格中存储坐标列表指针（Java 中存储对象引用）
     * 5. 调整坐标框为绝对坐标
     */
    public static void assignCoordListsForGrids(D2DrlgRoom drlgRoom, 
            D2DrlgLogicalRoomInfo pDrlgCoordList, int nLists) {
        if (drlgRoom == null || pDrlgCoordList == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel().getDrlg().getMempool();
        
        // 1. 初始化 Y 索引网格
        DrlgDrlgGrid.initializeGridCells(memPool, pDrlgCoordList.getPIndexY(), 
                drlgRoom.getNTileWidth() + 1, drlgRoom.getNTileHeight() + 1);
        
        int nWidth = drlgRoom.getNTileWidth() + 1;
        int nHeight = drlgRoom.getNTileHeight() + 1;
        pDrlgCoordList.initializeCoordListCells(nWidth, nHeight);
        
        // 2. 遍历网格单元格
        for (int nY = 0; nY < nHeight; ++nY) {
            for (int nX = 0; nX < nWidth; ++nX) {
                int nFlags = DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexX(), nX, nY);
                int nIndex = nFlags & 0xFFFFFFF;
                
                // 如果 Y 索引网格中还没有分配坐标列表
                if (DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexY(), nX, nY) == 0) {
                    // 分配新的坐标列表节点
                    D2RoomCoordListStrc pRoomCoordList = D2Pool.callocStrcPool(memPool, D2RoomCoordListStrc.class);
                    if (pRoomCoordList == null) {
                        pRoomCoordList = new D2RoomCoordListStrc();
                    }
                    
                    pRoomCoordList.setNIndex(nIndex);
                    pRoomCoordList.setBNode((nFlags & 0x20000000) == 0x20000000);
                    // 将新节点插入到链表头部
                    pRoomCoordList.setPNext(pDrlgCoordList.getPCoordList());
                    pDrlgCoordList.setPCoordList(pRoomCoordList);
                    
                    // 设置坐标框的起始位置（相对坐标）
                    D2DrlgCoord coord0 = new D2DrlgCoord();
                    coord0.setNTileXPos(nX);
                    coord0.setNTileYPos(nY);
                    pRoomCoordList.setPBox(0, coord0);
                    
                    // 计算宽度（向右扩展直到索引改变或已分配）
                    int nTmpWidth;
                    for (nTmpWidth = nX; nTmpWidth < nWidth; ++nTmpWidth) {
                        if (nIndex != (DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexX(), nTmpWidth, nY) & 0xFFFFFFF)) {
                            break;
                        }
                        if (DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexY(), nTmpWidth, nY) != 0) {
                            break;
                        }
                    }
                    coord0.setNTileWidth(nTmpWidth);
                    
                    // 计算高度（向下扩展直到索引改变或已分配）
                    boolean bBreak = false;
                    int nTmpHeight;
                    for (nTmpHeight = nY; nTmpHeight < nHeight; ++nTmpHeight) {
                        for (int i = nX; i < coord0.getNTileWidth(); ++i) {
                            if (nIndex != (DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexX(), i, nTmpHeight) & 0xFFFFFFF)
                                    || DrlgDrlgGrid.getGridEntry(pDrlgCoordList.getPIndexY(), i, nTmpHeight) != 0) {
                                bBreak = true;
                                break;
                            }
                        }
                        if (bBreak) {
                            break;
                        }
                    }
                    coord0.setNTileHeight(nTmpHeight);
                    
                    // 在 Y 索引网格中存储坐标列表对象（Java 中使用对象引用）
                    // 注意：C++ 中存储指针值，Java 中需要特殊处理
                    // 这里使用一个映射来存储对象引用，或者使用索引
                    for (int j = coord0.getNTileYPos(); j < coord0.getNTileHeight(); ++j) {
                        for (int i = coord0.getNTileXPos(); i < coord0.getNTileWidth(); ++i) {
                            pDrlgCoordList.setCoordListCell(i, j, pRoomCoordList);
                            DrlgDrlgGrid.alterGridFlag(pDrlgCoordList.getPIndexY(), i, j,
                                    pRoomCoordList.getNIndex(),
                                    DrlgDrlgGrid.FlagOperation.OVERWRITE);
                        }
                    }
                    
                    // 调整坐标框为绝对坐标
                    coord0.setNTileYPos(coord0.getNTileYPos() + drlgRoom.getNTileYPos());
                    coord0.setNTileHeight(coord0.getNTileHeight() + drlgRoom.getNTileYPos());
                    coord0.setNTileXPos(coord0.getNTileXPos() + drlgRoom.getNTileXPos());
                    coord0.setNTileWidth(coord0.getNTileWidth() + drlgRoom.getNTileXPos());
                    
                    // 设置第二个坐标框（与第一个相同）
                    D2DrlgCoord coord1 = new D2DrlgCoord();
                    coord1.setNTileXPos(coord0.getNTileXPos());
                    coord1.setNTileYPos(coord0.getNTileYPos());
                    coord1.setNTileWidth(coord0.getNTileWidth());
                    coord1.setNTileHeight(coord0.getNTileHeight());
                    pRoomCoordList.setPBox(1, coord1);
                    
                    // 限制坐标框在房间范围内
                    int nTmpWidth2 = drlgRoom.getNTileXPos() + drlgRoom.getNTileWidth();
                    if (coord1.getNTileWidth() >= nTmpWidth2) {
                        coord1.setNTileWidth(nTmpWidth2);
                    }
                    
                    int nTmpHeight2 = drlgRoom.getNTileYPos() + drlgRoom.getNTileHeight();
                    if (coord1.getNTileHeight() >= nTmpHeight2) {
                        coord1.setNTileHeight(nTmpHeight2);
                    }
                    
                    // 检查坐标框是否有效
                    if (coord1.getNTileXPos() >= nTmpWidth2 || coord1.getNTileYPos() >= nTmpHeight2) {
                        coord1.setNTileXPos(0);
                        coord1.setNTileYPos(0);
                        coord1.setNTileWidth(0);
                        coord1.setNTileHeight(0);
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD76C50
     * 为瓦片设置坐标列表
     * 对应 C++ DRLGLOGIC_SetCoordListForTiles
     * 
     * 功能：
     * 1. 遍历房间的所有墙壁瓦片
     * 2. 根据瓦片的坐标，从 Y 索引网格中获取坐标列表值
     * 3. 将坐标列表值存储到瓦片数据的 unk0x10 字段中
     */
    public static void setCoordListForTiles(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        D2DrlgLogicalRoomInfo logicalRoomInfo = drlgRoom.getLogicalRoomInfo();
        if (logicalRoomInfo == null) {
            return;
        }
        
        // 获取墙壁瓦片数组
        D2DrlgRoomTilesStrc pTiles = drlgRoom.getTileGrid().getPTiles();
        if (pTiles == null || pTiles.getNWalls() == 0) {
            return;
        }
        
        D2DrlgTileDataStrc[] pWallTiles = pTiles.getPWallTiles();
        if (pWallTiles == null) {
            return;
        }
        
        // 遍历所有墙壁瓦片
        for (int i = 0; i < pTiles.getNWalls(); ++i) {
            D2DrlgTileDataStrc pWallTile = pWallTiles[i];
            if (pWallTile == null) {
                continue;
            }
            
            // 如果设置了 HAS_COORD_LIST 标志，则跳过（使用简单的坐标列表）
            if ((logicalRoomInfo.getDwFlags() & DRLGLOGIC_ROOMINFO_HAS_COORD_LIST) == 0) {
                // 从 Y 索引网格中获取坐标列表值
                int nGridEntry = DrlgDrlgGrid.getGridEntry(logicalRoomInfo.getPIndexY(), 
                        pWallTile.getNPosX(), pWallTile.getNPosY());
                // 存储到瓦片数据的 unk0x10 字段
                pWallTile.setUnk0x10(nGridEntry);
            } else {
                // 如果设置了 HAS_COORD_LIST 标志，设置为 0
                pWallTile.setUnk0x10(0);
            }
        }
    }
    
    /**
     * D2Common.0x6FD76C20
     * 检查是否为第1层但不是墙壁对象
     * 对应 C++ DRLG_CheckLayer1ButNotWallObject
     * 
     * @param pTileData 瓦片数据
     * @return 如果是第1层但不是墙壁对象返回 true，否则返回 false
     */
    public static boolean checkLayer1ButNotWallObject(D2DrlgTileDataStrc pTileData) {
        if (pTileData == null) {
            return false;
        }
        
        // 获取瓦片层（从 dwFlags 中提取）
        int nLayer = getMapTileLayer(pTileData.getDwFlags());
        if (nLayer == 1) {
            // 检查不是屋顶类型
            if (pTileData.getNTileType() != DrlgRoomTile.TILETYPE_ROOF) {
                return (pTileData.getDwFlags() & 0x000800) == 0;
            }
        }
        
        return false;
    }
    
    /**
     * 获取地图瓦片层
     * 对应 C++ GetMapTileLayer
     * 
     * @param dwFlags 瓦片标志
     * @return 瓦片层（0-3）
     */
    private static int getMapTileLayer(int dwFlags) {
        return ((dwFlags & 0x01C000) >>> 14) - 1;
    }
    
    /**
     * 检查是否有地图瓦片层
     * 对应 C++ HasMapTileLayer
     * 
     * @param dwFlags 瓦片标志
     * @return 如果有层信息返回 true，否则返回 false
     */
    private static boolean hasMapTileLayer(int dwFlags) {
        return (dwFlags & 0x01C000) != 0;
    }
    
    /**
     * D2Common.0x6FD76830
     * 设置瓦片网格标志
     * 对应 C++ DRLGLOGIC_SetTileGridFlags
     * 
     * @param a1 逻辑结构
     * @param nX X 坐标
     * @param nY Y 坐标
     * @param a4 方向（-1 表示初始调用）
     */
    public static void setTileGridFlags(D2UnkDrlgLogicStrc a1, int nX, int nY, int a4) {
        if (a1 == null || a1.getPDrlgRoom() == null) {
            return;
        }
        
        // 方向偏移数组
        final int[][] stru_6FDCE5C8 = {
            { 1, 0 },   // 东
            { 0, 1 },   // 南
            { -1, 0 },  // 西
            { 0, -1 },  // 北
        };
        
        // 标志数组（根据方向和瓦片类型）
        final int[] dword_6FDCE5EC = {
            23, 0, 5, 21, 17, 15, 3, 0, 9, 7, 39, 0, 0, 5, 3, 31, 31, 31, 31, 31, 31, 31, 31, 31, 31, 0
        };
        
        // 索引数组
        final int[] dword_6FDCE650 = {
            -1, 0, 1, 2, 2, 0, 1, 3, 0, 1, 0, 1, 4, -1, 4, 0, 0, 0, 0, 0
        };
        
        int nPosX = nX;
        int nPosY = nY;
        D2DrlgRoom pDrlgRoom = a1.getPDrlgRoom();
        D2DrlgCoord drlgCoord = pDrlgRoom.getDrlgCoord();
        
        // 循环直到超出房间边界
        while (DrlgDrlgRoom.areXYInsideCoordinatesOrOnBorder(drlgCoord, 
                nPosX + pDrlgRoom.getNTileXPos(), nPosY + pDrlgRoom.getNTileYPos())) {
            
            // 检查是否已处理（0x10000000 位）
            if ((DrlgDrlgGrid.getGridEntry(a1.getField_4(), nPosX, nPosY) & 0x10000000) != 0) {
                return;
            }
            
            // 检查网格单元格是否为空
            if (DrlgDrlgGrid.getGridEntry(a1.getField_14(), nPosX, nPosY) == 0) {
                // 设置标志
                DrlgDrlgGrid.alterGridFlag(a1.getField_4(), nPosX, nPosY, a1.getNFlags(), 
                        DrlgDrlgGrid.FlagOperation.OR);
                
                // 递归处理四个方向
                for (int i = 0; i < 4; ++i) {
                    setTileGridFlags(a1, nPosX + stru_6FDCE5C8[i][0], nPosY + stru_6FDCE5C8[i][1], i);
                }
                return;
            }
            
            // 获取瓦片类型索引
            int nIndex = 0;
            if (DrlgDrlgGrid.isGridValid(a1.getPTileTypeGrid())) {
                nIndex = DrlgDrlgGrid.getGridEntry(a1.getPTileTypeGrid(), nPosX, nPosY);
            }
            
            // 计算标志索引
            int nFlagsIndex = a4 + 5 * dword_6FDCE650[nIndex] + 1;
            if (nFlagsIndex < 0 || nFlagsIndex >= dword_6FDCE5EC.length) {
                return;
            }
            
            int nFlags = dword_6FDCE5EC[nFlagsIndex];
            
            // 根据标志位处理
            if ((nFlags & 1) != 0) {
                DrlgDrlgGrid.alterGridFlag(a1.getField_4(), nPosX, nPosY, a1.getNFlags(), 
                        DrlgDrlgGrid.FlagOperation.OR);
            }
            
            if ((nFlags & 2) != 0 && a4 != 2) {
                setTileGridFlags(a1, nPosX + 1, nPosY, 0);
            }
            
            if ((nFlags & 4) != 0 && a4 != 3) {
                setTileGridFlags(a1, nPosX, nPosY + 1, 1);
            }
            
            if ((nFlags & 8) != 0 && a4 != 0) {
                setTileGridFlags(a1, nPosX - 1, nPosY, 2);
            }
            
            if ((nFlags & 16) != 0 && a4 != 1) {
                setTileGridFlags(a1, nPosX, nPosY - 1, 3);
            }
            
            if ((nFlags & 32) == 0) {
                return;
            }
            
            // 继续到下一个单元格
            ++nPosY;
            ++nPosX;
            a4 = -1;
        }
    }
    
    /**
     * D2Common.0x6FD769B0
     * 处理附近房间的坐标列表
     * 对应 C++ sub_6FD769B0
     * 
     * @param pDrlgRoom 房间
     */
    public static void sub_6FD769B0(D2DrlgRoom pDrlgRoom) {
        if (pDrlgRoom == null) {
            return;
        }
        
        D2DrlgCoord drlgCoord = pDrlgRoom.getDrlgCoord();
        
        // 遍历附近房间
        if (pDrlgRoom.getPpRoomsNear() != null) {
            for (int i = 0; i < pDrlgRoom.getNRoomsNear(); ++i) {
                D2DrlgRoom pCurrentRoomEx = pDrlgRoom.getPpRoomsNear()[i];
                if (pCurrentRoomEx != null && pCurrentRoomEx != pDrlgRoom) {
                    if (pCurrentRoomEx.getLogicalRoomInfo() != null) {
                        // 检查是否重叠（使用曼哈顿距离）
                        if (!DrlgDrlg.checkNotOverlappingUsingManhattanDistance(drlgCoord, 
                                pCurrentRoomEx.getDrlgCoord(), 1)) {
                            
                            // 处理上边界和下边界
                            for (int j = drlgCoord.getNTileXPos(); 
                                    j <= drlgCoord.getNTileXPos() + pDrlgRoom.getNTileWidth(); ++j) {
                                sub_6FD76A90(pDrlgRoom, pCurrentRoomEx, j, drlgCoord.getNTileYPos());
                                sub_6FD76A90(pDrlgRoom, pCurrentRoomEx, j, 
                                        pDrlgRoom.getNTileHeight() + drlgCoord.getNTileYPos());
                            }
                            
                            // 处理左边界和右边界
                            for (int j = drlgCoord.getNTileYPos(); 
                                    j <= drlgCoord.getNTileYPos() + pDrlgRoom.getNTileHeight(); ++j) {
                                sub_6FD76A90(pDrlgRoom, pCurrentRoomEx, drlgCoord.getNTileXPos(), j);
                                sub_6FD76A90(pDrlgRoom, pCurrentRoomEx, 
                                        drlgCoord.getNTileXPos() + pDrlgRoom.getNTileWidth(), j);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD76A90
     * 处理两个房间的坐标列表
     * 对应 C++ sub_6FD76A90
     * 
     * @param pDrlgRoom1 房间1
     * @param pDrlgRoom2 房间2
     * @param nX X 坐标
     * @param nY Y 坐标
     */
    public static void sub_6FD76A90(D2DrlgRoom pDrlgRoom1, D2DrlgRoom pDrlgRoom2, int nX, int nY) {
        if (pDrlgRoom1 == null || pDrlgRoom2 == null) {
            return;
        }
        
        // 检查坐标是否在房间2内
        if (DrlgDrlgRoom.areXYInsideCoordinatesOrOnBorder(pDrlgRoom2.getDrlgCoord(), nX, nY)) {
            // 获取坐标列表（注意：坐标需要乘以 5，因为使用的是 subtile 坐标）
            D2RoomCoordListStrc pRoomCoordList1 = sub_6FD77110(pDrlgRoom1, nX * 5, nY * 5);
            D2RoomCoordListStrc pRoomCoordList2 = sub_6FD77110(pDrlgRoom2, nX * 5, nY * 5);
            
            if (pRoomCoordList1 != null && pRoomCoordList2 != null) {
                int nIndex1 = pRoomCoordList1.getNIndex();
                int nIndex2 = pRoomCoordList2.getNIndex();
                
                // 检查索引是否有效且不同
                if (nIndex2 != 0 && nIndex1 != 0) {
                    // 检查是否在同一关卡且节点类型相同
                    if (pDrlgRoom2.getLevel().getLevelId() == pDrlgRoom1.getLevel().getLevelId() 
                            && nIndex2 != nIndex1 
                            && pRoomCoordList2.isBNode() == pRoomCoordList1.isBNode()) {
                        sub_6FD76B90(pDrlgRoom1, nIndex1, nIndex2, pRoomCoordList2.isBNode());
                    }
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD76B90
     * 合并坐标列表索引
     * 对应 C++ sub_6FD76B90
     * 
     * @param pDrlgRoom 房间
     * @param nIndex1 索引1
     * @param nIndex2 索引2
     * @param bNode 节点标志
     */
    public static void sub_6FD76B90(D2DrlgRoom pDrlgRoom, int nIndex1, int nIndex2, boolean bNode) {
        if (pDrlgRoom == null) {
            return;
        }
        
        D2DrlgLogicalRoomInfo logicalRoomInfo = pDrlgRoom.getLogicalRoomInfo();
        if (logicalRoomInfo == null 
                || (logicalRoomInfo.getDwFlags() & DRLGLOGIC_ROOMINFO_HAS_COORD_LIST) != 0) {
            return;
        }
        
        // 遍历坐标列表，将 nIndex1 替换为 nIndex2
        D2RoomCoordListStrc pRoomCoordList = logicalRoomInfo.getPCoordList();
        boolean bContinue = false;
        
        while (pRoomCoordList != null) {
            if (pRoomCoordList.getNIndex() == nIndex1) {
                bContinue = true;
                pRoomCoordList.setNIndex(nIndex2);
            }
            pRoomCoordList = pRoomCoordList.getPNext();
        }
        
        // 如果进行了替换，递归处理附近房间
        if (bContinue) {
            if (pDrlgRoom.getPpRoomsNear() != null) {
                for (int i = 0; i < pDrlgRoom.getNRoomsNear(); ++i) {
                    D2DrlgRoom pNearRoom = pDrlgRoom.getPpRoomsNear()[i];
                    if (pNearRoom != null && pNearRoom != pDrlgRoom 
                            && pNearRoom.getLevel().getLevelId() == pDrlgRoom.getLevel().getLevelId()) {
                        sub_6FD76B90(pNearRoom, nIndex1, nIndex2, bNode);
                    }
                }
            }
        }
    }

    private static D2DrlgTileDataStrc[] wallTiles(D2DrlgRoom room) {
        D2DrlgRoomTilesStrc tiles = room != null && room.getTileGrid() != null
                ? room.getTileGrid().getPTiles() : null;
        if (tiles == null || tiles.getPWallTiles() == null || tiles.getNWalls() <= 0) {
            return new D2DrlgTileDataStrc[0];
        }
        int count = Math.min(tiles.getNWalls(), tiles.getPWallTiles().length);
        D2DrlgTileDataStrc[] walls = new D2DrlgTileDataStrc[count];
        System.arraycopy(tiles.getPWallTiles(), 0, walls, 0, count);
        return walls;
    }

    private static void setBox(D2DrlgCoord box, D2DrlgRoom room) {
        box.setNPosX(room.getNTileXPos());
        box.setNPosY(room.getNTileYPos());
        box.setNWidth(room.getNTileXPos() + room.getNTileWidth());
        box.setNHeight(room.getNTileYPos() + room.getNTileHeight());
    }
}
