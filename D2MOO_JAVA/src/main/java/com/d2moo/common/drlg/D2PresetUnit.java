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
    /** True when nIndex still uses the DS1 Obj/MonPreset index contract. */
    private boolean ds1Raw;
    /**
     * Java bridge flag: whether riiablo should create an external ECS entity
     * for this native preset unit.
     *
     * <p>D2Common also copies objects from decorative LvlSub files (trees,
     * borders, puddles, and similar substitutions). Those copies remain part
     * of the native RoomEx data, but riiablo must not reinterpret their DS1
     * preset index as an Objects.txt class id. Waypoint substitutions are the
     * only LvlSub objects currently exposed to the external entity bridge.</p>
     */
    private boolean externalEntity = true;
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

    public boolean isDs1Raw() { return ds1Raw; }
    public void setDs1Raw(boolean ds1Raw) { this.ds1Raw = ds1Raw; }

    public boolean isExternalEntity() { return externalEntity; }
    public void setExternalEntity(boolean externalEntity) {
        this.externalEntity = externalEntity;
    }
    
    public D2MapAIStrc getPMapAI() { return pMapAI; }
    public void setPMapAI(D2MapAIStrc pMapAI) { this.pMapAI = pMapAI; }
    
    public D2PresetUnit getPNext() { return pNext; }
    public void setPNext(D2PresetUnit pNext) { this.pNext = pNext; }
}
