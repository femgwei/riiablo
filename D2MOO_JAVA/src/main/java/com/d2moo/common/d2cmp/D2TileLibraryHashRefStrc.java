package com.d2moo.common.d2cmp;

/**
 * 瓦片库哈希引用结构
 * 对应 C++ 结构：D2TileLibraryHashRefStrc
 */
public class D2TileLibraryHashRefStrc {
    private Object pTile;                        // 0x00 D2TileLibraryEntryStrc* 瓦片条目
    private D2TileLibraryHashRefStrc pPrev;     // 0x04 前一个引用
    
    public D2TileLibraryHashRefStrc() {
    }
    
    // Getters and Setters
    public Object getPTile() {
        return pTile;
    }
    
    public void setPTile(Object pTile) {
        this.pTile = pTile;
    }
    
    public D2TileLibraryHashRefStrc getPPrev() {
        return pPrev;
    }
    
    public void setPPrev(D2TileLibraryHashRefStrc pPrev) {
        this.pPrev = pPrev;
    }
}
