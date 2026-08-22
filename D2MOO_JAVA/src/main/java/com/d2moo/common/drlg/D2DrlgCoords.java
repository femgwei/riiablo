package com.d2moo.common.drlg;

/**
 * D2DrlgCoordsStrc 结构
 * 对应 C++ 结构：D2DrlgCoordsStrc
 */
public class D2DrlgCoords {
    private int nSubtileX;          // 0x00 子瓦片X坐标
    private int nSubtileY;          // 0x04 子瓦片Y坐标
    private int nSubtileWidth;      // 0x08 子瓦片宽度
    private int nSubtileHeight;     // 0x0C 子瓦片高度
    private int nTileXPos;          // 0x10 瓦片X坐标
    private int nTileYPos;          // 0x14 瓦片Y坐标
    private int nTileWidth;         // 0x18 瓦片宽度
    private int nTileHeight;        // 0x1C 瓦片高度
    
    public int getNSubtileX() {
        return nSubtileX;
    }
    
    public void setNSubtileX(int nSubtileX) {
        this.nSubtileX = nSubtileX;
    }
    
    public int getNSubtileY() {
        return nSubtileY;
    }
    
    public void setNSubtileY(int nSubtileY) {
        this.nSubtileY = nSubtileY;
    }
    
    public int getNSubtileWidth() {
        return nSubtileWidth;
    }
    
    public void setNSubtileWidth(int nSubtileWidth) {
        this.nSubtileWidth = nSubtileWidth;
    }
    
    public int getNSubtileHeight() {
        return nSubtileHeight;
    }
    
    public void setNSubtileHeight(int nSubtileHeight) {
        this.nSubtileHeight = nSubtileHeight;
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
}
