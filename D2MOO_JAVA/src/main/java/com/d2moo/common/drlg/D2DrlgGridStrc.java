package com.d2moo.common.drlg;

/**
 * Drlg 网格结构
 * 对应 C++ 结构：D2DrlgGridStrc
 */
public class D2DrlgGridStrc {
    private int[] pCellsFlags;          // 0x00 单元格标志数组
    private int[] pCellsRowOffsets;     // 0x04 行偏移数组
    private int nWidth;                 // 0x08 宽度
    private int nHeight;                // 0x0C 高度
    private int unk0x10;                // 0x10 未知字段（可能表示是否未初始化）
    
    public D2DrlgGridStrc() {
    }
    
    public D2DrlgGridStrc(int width, int height) {
        this.nWidth = width;
        this.nHeight = height;
    }
    
    // Getters and Setters
    public int[] getPCellsFlags() {
        return pCellsFlags;
    }
    
    public void setPCellsFlags(int[] pCellsFlags) {
        this.pCellsFlags = pCellsFlags;
    }
    
    public int[] getPCellsRowOffsets() {
        return pCellsRowOffsets;
    }
    
    public void setPCellsRowOffsets(int[] pCellsRowOffsets) {
        this.pCellsRowOffsets = pCellsRowOffsets;
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
    
    public int getUnk0x10() {
        return unk0x10;
    }
    
    public void setUnk0x10(int unk0x10) {
        this.unk0x10 = unk0x10;
    }
    
    /**
     * 获取指定位置的标志值
     */
    public int getFlag(int x, int y) {
        if (pCellsFlags == null || pCellsRowOffsets == null) {
            return 0;
        }
        if (x < 0 || x >= nWidth || y < 0 || y >= nHeight) {
            return 0;
        }
        return pCellsFlags[x + pCellsRowOffsets[y]];
    }
    
    /**
     * 设置指定位置的标志值
     */
    public void setFlag(int x, int y, int flag) {
        if (pCellsFlags == null || pCellsRowOffsets == null) {
            return;
        }
        if (x < 0 || x >= nWidth || y < 0 || y >= nHeight) {
            return;
        }
        pCellsFlags[x + pCellsRowOffsets[y]] = flag;
    }
    
    /**
     * 获取指定位置的标志值引用（用于修改）
     */
    public int[] getFlagRef(int x, int y) {
        if (pCellsFlags == null || pCellsRowOffsets == null) {
            return new int[1];
        }
        if (x < 0 || x >= nWidth || y < 0 || y >= nHeight) {
            return new int[1];
        }
        int index = x + pCellsRowOffsets[y];
        return new int[] { pCellsFlags[index] };
    }
    
    /**
     * 设置指定位置的标志值（通过引用）
     */
    public void setFlagRef(int x, int y, int[] flagRef) {
        if (pCellsFlags == null || pCellsRowOffsets == null || flagRef == null || flagRef.length == 0) {
            return;
        }
        if (x < 0 || x >= nWidth || y < 0 || y >= nHeight) {
            return;
        }
        int index = x + pCellsRowOffsets[y];
        pCellsFlags[index] = flagRef[0];
    }
}
