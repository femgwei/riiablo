package com.d2moo.common.drlg;

/**
 * Drlg 坐标结构
 * 对应 C++ 结构：D2DrlgCoordStrc
 * 
 * 注意：在 C++ 中，这个结构有时使用 nPosX/nPosY/nWidth/nHeight，
 * 有时使用 nTileXPos/nTileYPos/nTileWidth/nTileHeight
 * 在 Java 中我们提供两套访问方法以兼容不同的使用场景
 */
public class D2DrlgCoord {
    private int nPosX;          // 位置 X（瓦片坐标）
    private int nPosY;          // 位置 Y（瓦片坐标）
    private int nWidth;         // 宽度（瓦片单位）
    private int nHeight;        // 高度（瓦片单位）
    
    // 兼容字段（与上面的字段对应）
    private int nTileXPos;      // 瓦片 X 位置（等同于 nPosX）
    private int nTileYPos;      // 瓦片 Y 位置（等同于 nPosY）
    private int nTileWidth;     // 瓦片宽度（等同于 nWidth）
    private int nTileHeight;    // 瓦片高度（等同于 nHeight）
    
    // nPosX/nPosY/nWidth/nHeight 的访问方法
    public int getNPosX() {
        return nPosX;
    }
    
    public void setNPosX(int nPosX) {
        this.nPosX = nPosX;
        this.nTileXPos = nPosX; // 同步更新
    }
    
    public int getNPosY() {
        return nPosY;
    }
    
    public void setNPosY(int nPosY) {
        this.nPosY = nPosY;
        this.nTileYPos = nPosY; // 同步更新
    }
    
    public int getNWidth() {
        return nWidth;
    }
    
    public void setNWidth(int nWidth) {
        this.nWidth = nWidth;
        this.nTileWidth = nWidth; // 同步更新
    }
    
    public int getNHeight() {
        return nHeight;
    }
    
    public void setNHeight(int nHeight) {
        this.nHeight = nHeight;
        this.nTileHeight = nHeight; // 同步更新
    }
    
    // nTileXPos/nTileYPos/nTileWidth/nTileHeight 的访问方法（兼容性）
    public int getNTileXPos() {
        return nTileXPos != 0 ? nTileXPos : nPosX;
    }
    
    public void setNTileXPos(int nTileXPos) {
        this.nTileXPos = nTileXPos;
        this.nPosX = nTileXPos; // 同步更新
    }
    
    public int getNTileYPos() {
        return nTileYPos != 0 ? nTileYPos : nPosY;
    }
    
    public void setNTileYPos(int nTileYPos) {
        this.nTileYPos = nTileYPos;
        this.nPosY = nTileYPos; // 同步更新
    }
    
    public int getNTileWidth() {
        return nTileWidth != 0 ? nTileWidth : nWidth;
    }
    
    public void setNTileWidth(int nTileWidth) {
        this.nTileWidth = nTileWidth;
        this.nWidth = nTileWidth; // 同步更新
    }
    
    public int getNTileHeight() {
        return nTileHeight != 0 ? nTileHeight : nHeight;
    }
    
    public void setNTileHeight(int nTileHeight) {
        this.nTileHeight = nTileHeight;
        this.nHeight = nTileHeight; // 同步更新
    }
}
