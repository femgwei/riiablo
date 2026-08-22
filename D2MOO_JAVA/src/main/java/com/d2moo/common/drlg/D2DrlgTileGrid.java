package com.d2moo.common.drlg;

/**
 * Drlg 瓦片网格结构
 * 对应 C++ 结构：D2DrlgTileGridStrc
 */
public class D2DrlgTileGrid {
    private D2DrlgTileLinkStrc pMapLinks;      // 0x00 地图链接链表
    private D2DrlgAnimTileGridStrc pAnimTiles; // 0x04 D2DrlgAnimTileGridStrc* 动画瓦片网格
    private int nWalls;                        // 0x08 墙壁数量
    private int nFloors;                       // 0x0C 地板数量
    private int nShadows;                      // 0x10 阴影数量
    private D2DrlgRoomTilesStrc pTiles;        // 0x14 房间瓦片结构
    
    public D2DrlgTileGrid() {
        this.nWalls = 0;
        this.nFloors = 0;
        this.nShadows = 0;
        this.pTiles = new D2DrlgRoomTilesStrc();
    }
    
    // Getters and Setters
    public D2DrlgTileLinkStrc getPMapLinks() {
        return pMapLinks;
    }
    
    public void setPMapLinks(D2DrlgTileLinkStrc pMapLinks) {
        this.pMapLinks = pMapLinks;
    }
    
    public D2DrlgAnimTileGridStrc getPAnimTiles() {
        return pAnimTiles;
    }
    
    public void setPAnimTiles(D2DrlgAnimTileGridStrc pAnimTiles) {
        this.pAnimTiles = pAnimTiles;
    }
    
    public int getNWalls() {
        return nWalls;
    }
    
    public void setNWalls(int nWalls) {
        this.nWalls = nWalls;
    }
    
    public int getNFloors() {
        return nFloors;
    }
    
    public void setNFloors(int nFloors) {
        this.nFloors = nFloors;
    }
    
    public int getNShadows() {
        return nShadows;
    }
    
    public void setNShadows(int nShadows) {
        this.nShadows = nShadows;
    }
    
    public D2DrlgRoomTilesStrc getPTiles() {
        return pTiles;
    }
    
    public void setPTiles(D2DrlgRoomTilesStrc pTiles) {
        this.pTiles = pTiles;
    }
}
