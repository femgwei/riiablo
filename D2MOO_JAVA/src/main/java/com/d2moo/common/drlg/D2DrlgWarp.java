package com.d2moo.common.drlg;

/**
 * Drlg 传送门结构
 * 对应 C++ 结构：D2DrlgWarpStrc
 */
public class D2DrlgWarp {
    private int nLevel;                           // 0x00
    private int[] nVis;                           // 0x04 int nVis[8]
    private int[] nWarp;                           // 0x24 int nWarp[8]
    private D2DrlgWarp pNext;                      // 0x44
    
    public D2DrlgWarp() {
        this.nVis = new int[8];
        this.nWarp = new int[8];
    }
    
    public int getNLevel() { return nLevel; }
    public void setNLevel(int nLevel) { this.nLevel = nLevel; }
    
    public int[] getNVis() { return nVis; }
    public void setNVis(int[] nVis) { this.nVis = nVis; }
    
    public int[] getNWarp() { return nWarp; }
    public void setNWarp(int[] nWarp) { this.nWarp = nWarp; }
    
    public D2DrlgWarp getPNext() { return pNext; }
    public void setPNext(D2DrlgWarp pNext) { this.pNext = pNext; }
}
