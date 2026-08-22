package com.d2moo.common.drlg;

/**
 * 预设单位结构
 * 对应 C++ 结构：D2PresetUnitStrc
 */
public class D2PresetUnit {
    private int nUnitType;                       // 0x00
    private int nIndex;                          // 0x04
    private int nMode;                           // 0x08
    private int nXpos;                           // 0x0C
    private int nYpos;                           // 0x10
    private boolean bSpawned;                    // 0x14
    private D2MapAIStrc pMapAI;                  // 0x18 D2MapAIStrc*
    private D2PresetUnit pNext;                  // 0x1C
    
    public int getNUnitType() { return nUnitType; }
    public void setNUnitType(int nUnitType) { this.nUnitType = nUnitType; }
    
    public int getNIndex() { return nIndex; }
    public void setNIndex(int nIndex) { this.nIndex = nIndex; }
    
    public int getNMode() { return nMode; }
    public void setNMode(int nMode) { this.nMode = nMode; }
    
    public int getNXpos() { return nXpos; }
    public void setNXpos(int nXpos) { this.nXpos = nXpos; }
    
    public int getNYpos() { return nYpos; }
    public void setNYpos(int nYpos) { this.nYpos = nYpos; }
    
    public boolean isBSpawned() { return bSpawned; }
    public void setBSpawned(boolean bSpawned) { this.bSpawned = bSpawned; }
    
    public D2MapAIStrc getPMapAI() { return pMapAI; }
    public void setPMapAI(D2MapAIStrc pMapAI) { this.pMapAI = pMapAI; }
    
    public D2PresetUnit getPNext() { return pNext; }
    public void setPNext(D2PresetUnit pNext) { this.pNext = pNext; }
}
