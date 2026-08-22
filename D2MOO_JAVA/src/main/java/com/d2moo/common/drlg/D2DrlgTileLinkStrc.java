package com.d2moo.common.drlg;

/**
 * Drlg 瓦片链接结构
 * 对应 C++ 结构：D2DrlgTileLinkStrc
 */
public class D2DrlgTileLinkStrc {
    private boolean bFloor;                    // 0x00 是否为地板
    private D2DrlgTileDataStrc pMapTile;      // 0x04 地图瓦片数据
    private D2DrlgTileLinkStrc pNext;         // 0x08 下一个链接
    
    public D2DrlgTileLinkStrc() {
        this.bFloor = false;
    }
    
    // Getters and Setters
    public boolean isBFloor() {
        return bFloor;
    }
    
    public void setBFloor(boolean bFloor) {
        this.bFloor = bFloor;
    }
    
    public D2DrlgTileDataStrc getPMapTile() {
        return pMapTile;
    }
    
    public void setPMapTile(D2DrlgTileDataStrc pMapTile) {
        this.pMapTile = pMapTile;
    }
    
    public D2DrlgTileLinkStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2DrlgTileLinkStrc pNext) {
        this.pNext = pNext;
    }
}
