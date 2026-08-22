package com.d2moo.common.drlg;

/**
 * Drlg 预设信息结构
 * 对应 C++ 结构：D2DrlgPresetInfoStrc
 */
public class D2DrlgPresetInfoStrc {
    private D2DrlgMapStrc pDrlgMap;      // 0x00 Drlg 地图结构
    private int nDirection;              // 0x04 方向
    
    public D2DrlgPresetInfoStrc() {
        this.nDirection = 0;
    }
    
    // Getters and Setters
    public D2DrlgMapStrc getPDrlgMap() {
        return pDrlgMap;
    }
    
    public void setPDrlgMap(D2DrlgMapStrc pDrlgMap) {
        this.pDrlgMap = pDrlgMap;
    }
    
    public int getNDirection() {
        return nDirection;
    }
    
    public void setNDirection(int nDirection) {
        this.nDirection = nDirection;
    }
}
