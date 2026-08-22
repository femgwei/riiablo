package com.d2moo.common.d2cmp;

/**
 * 瓦片数据结构
 * 对应 C++ 结构：D2TileData（可能是 D2CmpTileDataStrc）
 * 
 * 注意：这是一个瓦片数据结构，包含瓦片的图像和属性信息
 */
public class D2TileData {
    private int nTileId;                // 瓦片ID
    private int nSequence;               // 序列号
    private int nWidth;                  // 宽度
    private int nHeight;                 // 高度
    private int nPosX;                   // X 位置
    private int nPosY;                   // Y 位置
    private byte[] pImageData;          // 图像数据
    private int nImageSize;              // 图像大小
    private int dwFlags;                 // 标志
    private int nOrientation;            // 方向
    private int nSubTileFlags;           // 子瓦片标志
    private int nRarity;                 // 稀有度（动画瓦片时为帧号）
    
    public D2TileData() {
        this.nTileId = 0;
        this.nSequence = 0;
        this.nWidth = 0;
        this.nHeight = 0;
    }
    
    // Getters and Setters
    public int getNTileId() {
        return nTileId;
    }
    
    public void setNTileId(int nTileId) {
        this.nTileId = nTileId;
    }
    
    public int getNSequence() {
        return nSequence;
    }
    
    public void setNSequence(int nSequence) {
        this.nSequence = nSequence;
    }
    
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
    
    public byte[] getPImageData() {
        return pImageData;
    }
    
    public void setPImageData(byte[] pImageData) {
        this.pImageData = pImageData;
        if (pImageData != null) {
            this.nImageSize = pImageData.length;
        } else {
            this.nImageSize = 0;
        }
    }
    
    public int getNImageSize() {
        return nImageSize;
    }
    
    public void setNImageSize(int nImageSize) {
        this.nImageSize = nImageSize;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public int getNOrientation() {
        return nOrientation;
    }
    
    public void setNOrientation(int nOrientation) {
        this.nOrientation = nOrientation;
    }
    
    public int getNSubTileFlags() {
        return nSubTileFlags;
    }
    
    public void setNSubTileFlags(int nSubTileFlags) {
        this.nSubTileFlags = nSubTileFlags;
    }

    public int getNRarity() {
        return nRarity;
    }

    public void setNRarity(int nRarity) {
        this.nRarity = nRarity;
    }
}
