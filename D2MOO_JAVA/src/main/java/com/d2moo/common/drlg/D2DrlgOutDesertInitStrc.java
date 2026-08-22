package com.d2moo.common.drlg;

/**
 * 沙漠初始化结构
 * 对应 C++ 结构：D2DrlgOutDesertInitStrc
 */
public class D2DrlgOutDesertInitStrc {
    private int nLvlPrestId;    // 0x00 关卡预设ID
    private int nRand;          // 0x04 随机值
    private int nX;             // 0x08 X 坐标
    private int nY;             // 0x0C Y 坐标
    
    public D2DrlgOutDesertInitStrc() {
        this.nLvlPrestId = 0;
        this.nRand = 0;
        this.nX = 0;
        this.nY = 0;
    }
    
    public D2DrlgOutDesertInitStrc(int nLvlPrestId, int nRand, int nX, int nY) {
        this.nLvlPrestId = nLvlPrestId;
        this.nRand = nRand;
        this.nX = nX;
        this.nY = nY;
    }
    
    // Getters and Setters
    public int getNLvlPrestId() {
        return nLvlPrestId;
    }
    
    public void setNLvlPrestId(int nLvlPrestId) {
        this.nLvlPrestId = nLvlPrestId;
    }
    
    public int getNRand() {
        return nRand;
    }
    
    public void setNRand(int nRand) {
        this.nRand = nRand;
    }
    
    public int getNX() {
        return nX;
    }
    
    public void setNX(int nX) {
        this.nX = nX;
    }
    
    public int getNY() {
        return nY;
    }
    
    public void setNY(int nY) {
        this.nY = nY;
    }
}
