package com.d2moo.common.drlg;

/**
 * 活动房间结构
 * 对应 C++ 结构：D2ActiveRoomStrc
 * 
 * 注意：D2ActiveRoom 是游戏运行时使用的房间对象，与 D2DrlgRoom 关联
 * D2DrlgRoom 是关卡生成时使用的房间对象，包含房间的生成信息
 */
public class D2ActiveRoom {
    private D2DrlgRoom pDrlgRoom;        // 关联的 Drlg 房间对象
    private int nRoomId;                 // 房间ID
    private int dwFlags;                  // 房间标志
    private Object pUnits;               // 单位列表（占位符）
    private Object pObjects;              // 对象列表（占位符）
    private int nTileXPos;               // 瓦片X位置
    private int nTileYPos;               // 瓦片Y位置
    private int nTileWidth;              // 瓦片宽度
    private int nTileHeight;             // 瓦片高度
    private D2DrlgGridStrc pCollisionGrid; // 碰撞网格（用于存储碰撞标志）
    
    public D2ActiveRoom() {
        this.nRoomId = 0;
        this.dwFlags = 0;
    }
    
    // Getters and Setters
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
        return nTileXPos;
    }
    
    public void setNTileXPos(int nTileXPos) {
        this.nTileXPos = nTileXPos;
    }
    
    public int getNTileYPos() {
        return nTileYPos;
    }
    
    public void setNTileYPos(int nTileYPos) {
        this.nTileYPos = nTileYPos;
    }
    
    public int getNTileWidth() {
        return nTileWidth;
    }
    
    public void setNTileWidth(int nTileWidth) {
        this.nTileWidth = nTileWidth;
    }
    
    public int getNTileHeight() {
        return nTileHeight;
    }
    
    public void setNTileHeight(int nTileHeight) {
        this.nTileHeight = nTileHeight;
    }
    
    public D2DrlgGridStrc getPCollisionGrid() {
        return pCollisionGrid;
    }
    
    public void setPCollisionGrid(D2DrlgGridStrc pCollisionGrid) {
        this.pCollisionGrid = pCollisionGrid;
    }
}
