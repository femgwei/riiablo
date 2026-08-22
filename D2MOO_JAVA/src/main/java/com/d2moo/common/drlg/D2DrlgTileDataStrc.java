package com.d2moo.common.drlg;

/**
 * Drlg 瓦片数据结构
 * 对应 C++ 结构：D2DrlgTileDataStrc
 */
public class D2DrlgTileDataStrc {
    private int nWidth;                    // 0x00 宽度
    private int nHeight;                   // 0x04 高度
    private int nPosX;                     // 0x08 X 位置
    private int nPosY;                     // 0x0C Y 位置
    private int unk0x10;                   // 0x10 未知字段
    private int dwFlags;                   // 0x14 D2MapTileFlags
    private Object pTile;                  // 0x18 D2TileLibraryEntryStrc* 瓦片库条目
    private int nTileType;                 // 0x1C 瓦片类型
    private D2DrlgTileDataStrc unk0x20;    // 0x20 未知字段（可能是下一个瓦片数据）
    private int unk0x24;                   // 0x24 未知字段
    private byte nRed;                     // 0x28 红色分量
    private byte nGreen;                   // 0x29 绿色分量
    private byte nBlue;                    // 0x2A 蓝色分量
    private byte nIntensity;               // 0x2B 强度
    private int unk0x2C;                   // 0x2C 未知字段
    
    public D2DrlgTileDataStrc() {
        this.nWidth = 0;
        this.nHeight = 0;
        this.nPosX = 0;
        this.nPosY = 0;
        this.dwFlags = 0;
        this.nTileType = 0;
        this.nRed = 0;
        this.nGreen = 0;
        this.nBlue = 0;
        this.nIntensity = 0;
    }
    
    // Getters and Setters
    public int getNWidth() {
        return nWidth;
    }
    
    public void setNWidth(int nWidth) {
        this.nWidth = nWidth;
    }
    
    public int getNHeight() {
        return nHeight;
    }
    
    public void setNHeight(int nHeight) {
        this.nHeight = nHeight;
    }
    
    public int getNPosX() {
        return nPosX;
    }
    
    public void setNPosX(int nPosX) {
        this.nPosX = nPosX;
    }
    
    public int getNPosY() {
        return nPosY;
    }
    
    public void setNPosY(int nPosY) {
        this.nPosY = nPosY;
    }
    
    public int getUnk0x10() {
        return unk0x10;
    }
    
    public void setUnk0x10(int unk0x10) {
        this.unk0x10 = unk0x10;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public Object getPTile() {
        return pTile;
    }
    
    public void setPTile(Object pTile) {
        this.pTile = pTile;
    }
    
    public int getNTileType() {
        return nTileType;
    }
    
    public void setNTileType(int nTileType) {
        this.nTileType = nTileType;
    }
    
    public D2DrlgTileDataStrc getUnk0x20() {
        return unk0x20;
    }
    
    public void setUnk0x20(D2DrlgTileDataStrc unk0x20) {
        this.unk0x20 = unk0x20;
    }
    
    public int getUnk0x24() {
        return unk0x24;
    }
    
    public void setUnk0x24(int unk0x24) {
        this.unk0x24 = unk0x24;
    }
    
    public byte getNRed() {
        return nRed;
    }
    
    public void setNRed(byte nRed) {
        this.nRed = nRed;
    }
    
    public byte getNGreen() {
        return nGreen;
    }
    
    public void setNGreen(byte nGreen) {
        this.nGreen = nGreen;
    }
    
    public byte getNBlue() {
        return nBlue;
    }
    
    public void setNBlue(byte nBlue) {
        this.nBlue = nBlue;
    }
    
    public byte getNIntensity() {
        return nIntensity;
    }
    
    public void setNIntensity(byte nIntensity) {
        this.nIntensity = nIntensity;
    }
    
    public int getUnk0x2C() {
        return unk0x2C;
    }
    
    public void setUnk0x2C(int unk0x2C) {
        this.unk0x2C = unk0x2C;
    }
}
