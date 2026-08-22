package com.d2moo.common.d2cmp;

/**
 * 瓦片库结构
 * 对应 C++ 结构：D2TileLibrary（可能是 D2CmpTileLibraryStrc）
 * 
 * 注意：这是一个瓦片库结构，包含瓦片数据数组
 */
public class D2TileLibrary {
    private int nTileLibraryId;         // 瓦片库ID
    private int nSlot;                  // 槽位
    private D2TileData[] pTiles;        // 瓦片数据数组
    private int nTiles;                 // 瓦片数量
    private Object pData;               // 原始数据指针（占位符）
    private String fileName;            // 文件名（用于标识）
    
    public D2TileLibrary() {
        this.nTileLibraryId = 0;
        this.nSlot = 0;
        this.nTiles = 0;
    }
    
    // Getters and Setters
    public int getNTileLibraryId() {
        return nTileLibraryId;
    }
    
    public void setNTileLibraryId(int nTileLibraryId) {
        this.nTileLibraryId = nTileLibraryId;
    }
    
    public int getNSlot() {
        return nSlot;
    }
    
    public void setNSlot(int nSlot) {
        this.nSlot = nSlot;
    }
    
    public D2TileData[] getPTiles() {
        return pTiles;
    }
    
    public void setPTiles(D2TileData[] pTiles) {
        this.pTiles = pTiles;
        if (pTiles != null) {
            this.nTiles = pTiles.length;
        } else {
            this.nTiles = 0;
        }
    }
    
    public int getNTiles() {
        return nTiles;
    }
    
    public void setNTiles(int nTiles) {
        this.nTiles = nTiles;
    }
    
    public Object getPData() {
        return pData;
    }
    
    public void setPData(Object pData) {
        this.pData = pData;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
