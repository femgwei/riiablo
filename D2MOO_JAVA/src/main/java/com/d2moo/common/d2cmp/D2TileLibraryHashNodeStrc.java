package com.d2moo.common.d2cmp;

/**
 * 瓦片库哈希节点结构
 * 对应 C++ 结构：D2TileLibraryHashNodeStrc
 */
public class D2TileLibraryHashNodeStrc {
    private int nStyle;                          // 0x00 瓦片样式
    private int nSequence;                        // 0x04 瓦片序列号
    private int nType;                            // 0x08 瓦片类型（方向）
    private D2TileLibraryHashRefStrc pRef;        // 0x0C 引用
    private D2TileLibraryHashNodeStrc pPrev;      // 0x10 前一个节点
    
    public D2TileLibraryHashNodeStrc() {
        this.nStyle = 0;
        this.nSequence = 0;
        this.nType = 0;
    }
    
    // Getters and Setters
    public int getNStyle() {
        return nStyle;
    }
    
    public void setNStyle(int nStyle) {
        this.nStyle = nStyle;
    }
    
    public int getNSequence() {
        return nSequence;
    }
    
    public void setNSequence(int nSequence) {
        this.nSequence = nSequence;
    }
    
    public int getNType() {
        return nType;
    }
    
    public void setNType(int nType) {
        this.nType = nType;
    }
    
    public D2TileLibraryHashRefStrc getPRef() {
        return pRef;
    }
    
    public void setPRef(D2TileLibraryHashRefStrc pRef) {
        this.pRef = pRef;
    }
    
    public D2TileLibraryHashNodeStrc getPPrev() {
        return pPrev;
    }
    
    public void setPPrev(D2TileLibraryHashNodeStrc pPrev) {
        this.pPrev = pPrev;
    }
}
