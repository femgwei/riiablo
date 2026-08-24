package com.d2moo.common.drlg;

/**
 * 活动房间结构
 * 对应 C++ 结构：D2ActiveRoomStrc
 * 
 * 注意：D2ActiveRoom 是游戏运行时使用的房间对象，与 D2DrlgRoom 关联
 * D2DrlgRoom 是关卡生成时使用的房间对象，包含房间的生成信息
 */
public class D2ActiveRoom {
    private final D2DrlgCoords coords = new D2DrlgCoords();
    private D2DrlgRoomTilesStrc pRoomTiles;
    private D2ActiveRoom[] ppRoomList = new D2ActiveRoom[0];
    private int nNumRooms;
    private D2DrlgRoom pDrlgRoom;        // 关联的 Drlg 房间对象
    private D2Seed seed = new D2Seed();
    private D2DrlgAct act;
    private D2ActiveRoom roomNext;
    private int nRoomId;                 // 房间ID
    private int dwFlags;                  // 房间标志
    private Object pUnits;               // 单位列表（占位符）
    private Object pObjects;              // 对象列表（占位符）
    private D2DrlgGridStrc pCollisionGrid; // 碰撞网格（用于存储碰撞标志）
    private int nNumClients;
    private int nMaxClients;
    private int nTileCount;
    
    public D2ActiveRoom() {
        this.nRoomId = 0;
        this.dwFlags = 0;
    }
    
    // Getters and Setters
    public D2DrlgCoords getCoords() {
        return coords;
    }

    public void setCoords(D2DrlgCoords coords) {
        this.coords.set(coords);
    }

    public D2DrlgRoomTilesStrc getPRoomTiles() {
        return pRoomTiles;
    }

    public void setPRoomTiles(D2DrlgRoomTilesStrc pRoomTiles) {
        this.pRoomTiles = pRoomTiles;
    }

    public D2ActiveRoom[] getPpRoomList() {
        return ppRoomList;
    }

    public void setPpRoomList(D2ActiveRoom[] ppRoomList) {
        this.ppRoomList = ppRoomList != null ? ppRoomList : new D2ActiveRoom[0];
        if (nNumRooms > this.ppRoomList.length) nNumRooms = this.ppRoomList.length;
    }

    public int getNNumRooms() {
        return nNumRooms;
    }

    public void setNNumRooms(int nNumRooms) {
        this.nNumRooms = Math.max(0, Math.min(nNumRooms, ppRoomList.length));
    }

    public D2DrlgRoom getPDrlgRoom() {
        return pDrlgRoom;
    }
    
    public void setPDrlgRoom(D2DrlgRoom pDrlgRoom) {
        this.pDrlgRoom = pDrlgRoom;
        // 确保双向关联：如果设置了 DrlgRoom，也设置 DrlgRoom 的 room 字段
        if (pDrlgRoom != null && pDrlgRoom.getRoom() != this) {
            pDrlgRoom.setRoom(this);
        }
    }
    
    public int getNRoomId() {
        return nRoomId;
    }
    
    public void setNRoomId(int nRoomId) {
        this.nRoomId = nRoomId;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }

    public int getNNumClients() {
        return nNumClients;
    }

    public void setNNumClients(int nNumClients) {
        this.nNumClients = Math.max(0, nNumClients);
    }

    public int getNMaxClients() {
        return nMaxClients;
    }

    public void setNMaxClients(int nMaxClients) {
        this.nMaxClients = Math.max(0, nMaxClients);
    }

    public int getNTileCount() {
        return nTileCount;
    }

    public void setNTileCount(int nTileCount) {
        this.nTileCount = Math.max(0, nTileCount);
    }
    
    public Object getPUnits() {
        return pUnits;
    }
    
    public void setPUnits(Object pUnits) {
        this.pUnits = pUnits;
    }
    
    public Object getPObjects() {
        return pObjects;
    }
    
    public void setPObjects(Object pObjects) {
        this.pObjects = pObjects;
    }
    
    public int getNTileXPos() {
        return coords.getNTileXPos();
    }
    
    public void setNTileXPos(int nTileXPos) {
        coords.setNTileXPos(nTileXPos);
        coords.setNSubtileX(nTileXPos * 5);
    }
    
    public int getNTileYPos() {
        return coords.getNTileYPos();
    }
    
    public void setNTileYPos(int nTileYPos) {
        coords.setNTileYPos(nTileYPos);
        coords.setNSubtileY(nTileYPos * 5);
    }
    
    public int getNTileWidth() {
        return coords.getNTileWidth();
    }
    
    public void setNTileWidth(int nTileWidth) {
        coords.setNTileWidth(nTileWidth);
        coords.setNSubtileWidth(nTileWidth * 5);
    }
    
    public int getNTileHeight() {
        return coords.getNTileHeight();
    }
    
    public void setNTileHeight(int nTileHeight) {
        coords.setNTileHeight(nTileHeight);
        coords.setNSubtileHeight(nTileHeight * 5);
    }

    public D2Seed getSeed() {
        return seed;
    }

    public void setSeed(D2Seed seed) {
        this.seed = seed != null ? seed : new D2Seed();
    }

    public D2DrlgAct getAct() {
        return act;
    }

    public void setAct(D2DrlgAct act) {
        this.act = act;
    }

    public D2ActiveRoom getRoomNext() {
        return roomNext;
    }

    public void setRoomNext(D2ActiveRoom roomNext) {
        this.roomNext = roomNext;
    }
    
    public D2DrlgGridStrc getPCollisionGrid() {
        return pCollisionGrid;
    }
    
    public void setPCollisionGrid(D2DrlgGridStrc pCollisionGrid) {
        this.pCollisionGrid = pCollisionGrid;
    }
}
