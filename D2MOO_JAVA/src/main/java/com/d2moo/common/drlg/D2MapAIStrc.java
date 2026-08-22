package com.d2moo.common.drlg;

/**
 * 地图 AI 结构
 * 对应 C++ 结构：D2MapAIStrc
 */
public class D2MapAIStrc {
    private int nPathNodes;                              // 0x00 路径节点数量
    private D2MapAIPathPositionStrc[] pPosition;        // 0x04 路径位置数组
    
    public D2MapAIStrc() {
        this.nPathNodes = 0;
        this.pPosition = null;
    }
    
    public D2MapAIStrc(int nPathNodes) {
        this.nPathNodes = nPathNodes;
        this.pPosition = new D2MapAIPathPositionStrc[nPathNodes];
    }
    
    // Getters and Setters
    public int getNPathNodes() {
        return nPathNodes;
    }
    
    public void setNPathNodes(int nPathNodes) {
        this.nPathNodes = nPathNodes;
        if (this.pPosition == null || this.pPosition.length != nPathNodes) {
            this.pPosition = new D2MapAIPathPositionStrc[nPathNodes];
        }
    }
    
    public D2MapAIPathPositionStrc[] getPPosition() {
        return pPosition;
    }
    
    public void setPPosition(D2MapAIPathPositionStrc[] pPosition) {
        this.pPosition = pPosition;
        if (pPosition != null) {
            this.nPathNodes = pPosition.length;
        } else {
            this.nPathNodes = 0;
        }
    }
    
    public D2MapAIPathPositionStrc getPPosition(int index) {
        if (pPosition != null && index >= 0 && index < pPosition.length) {
            return pPosition[index];
        }
        return null;
    }
    
    public void setPPosition(int index, D2MapAIPathPositionStrc position) {
        if (pPosition != null && index >= 0 && index < pPosition.length) {
            pPosition[index] = position;
        }
    }
}
