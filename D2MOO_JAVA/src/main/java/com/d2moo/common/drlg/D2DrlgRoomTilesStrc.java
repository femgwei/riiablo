package com.d2moo.common.drlg;

/**
 * Drlg 房间瓦片结构
 * 对应 C++ 结构：D2DrlgRoomTilesStrc
 */
public class D2DrlgRoomTilesStrc {
    private D2DrlgTileDataStrc[] pWallTiles;   // 0x00 墙壁瓦片数组
    private int nWalls;                        // 0x04 墙壁数量
    private D2DrlgTileDataStrc[] pFloorTiles;  // 0x08 地板瓦片数组
    private int nFloors;                       // 0x0C 地板数量
    private D2DrlgTileDataStrc[] pRoofTiles;   // 0x10 屋顶瓦片数组
    private int nRoofs;                        // 0x14 屋顶数量
    
    public D2DrlgRoomTilesStrc() {
        this.nWalls = 0;
        this.nFloors = 0;
        this.nRoofs = 0;
    }
    
    // Getters and Setters
    public D2DrlgTileDataStrc[] getPWallTiles() {
        return pWallTiles;
    }
    
    public void setPWallTiles(D2DrlgTileDataStrc[] pWallTiles) {
        this.pWallTiles = pWallTiles;
        if (pWallTiles != null) {
            this.nWalls = pWallTiles.length;
        } else {
            this.nWalls = 0;
        }
    }
    
    public int getNWalls() {
        return nWalls;
    }
    
    public void setNWalls(int nWalls) {
        this.nWalls = nWalls;
    }
    
    public D2DrlgTileDataStrc[] getPFloorTiles() {
        return pFloorTiles;
    }
    
    public void setPFloorTiles(D2DrlgTileDataStrc[] pFloorTiles) {
        this.pFloorTiles = pFloorTiles;
        if (pFloorTiles != null) {
            this.nFloors = pFloorTiles.length;
        } else {
            this.nFloors = 0;
        }
    }
    
    public int getNFloors() {
        return nFloors;
    }
    
    public void setNFloors(int nFloors) {
        this.nFloors = nFloors;
    }
    
    public D2DrlgTileDataStrc[] getPRoofTiles() {
        return pRoofTiles;
    }
    
    public void setPRoofTiles(D2DrlgTileDataStrc[] pRoofTiles) {
        this.pRoofTiles = pRoofTiles;
        if (pRoofTiles != null) {
            this.nRoofs = pRoofTiles.length;
        } else {
            this.nRoofs = 0;
        }
    }
    
    public int getNRoofs() {
        return nRoofs;
    }
    
    public void setNRoofs(int nRoofs) {
        this.nRoofs = nRoofs;
    }
}
