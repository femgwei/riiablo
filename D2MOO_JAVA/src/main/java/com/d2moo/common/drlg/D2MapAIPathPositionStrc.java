package com.d2moo.common.drlg;

/**
 * 地图 AI 路径位置结构
 * 对应 C++ 结构：D2MapAIPathPositionStrc
 */
public class D2MapAIPathPositionStrc {
    private int nMapAIAction;    // 0x00 地图 AI 动作
    private int nX;              // 0x04 X 坐标
    private int nY;              // 0x08 Y 坐标
    
    public D2MapAIPathPositionStrc() {
        this.nMapAIAction = 0;
        this.nX = 0;
        this.nY = 0;
    }
    
    public D2MapAIPathPositionStrc(int nMapAIAction, int nX, int nY) {
        this.nMapAIAction = nMapAIAction;
        this.nX = nX;
        this.nY = nY;
    }
    
    // Getters and Setters
    public int getNMapAIAction() {
        return nMapAIAction;
    }
    
    public void setNMapAIAction(int nMapAIAction) {
        this.nMapAIAction = nMapAIAction;
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
