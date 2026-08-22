package com.d2moo.common.drlg;

/**
 * D2UnkOutdoorStrc3 结构
 * 用于 sub_6FD82360 中的标志设置
 */
public class D2UnkOutdoorStrc3 {
    private int nLevelId;
    private int nExcludedLevel1;
    private int nExcludedLevel2;
    private int nRand;
    private int nNextRand;
    private int nFlags;
    
    public D2UnkOutdoorStrc3(int nLevelId, int nExcludedLevel1, int nExcludedLevel2, 
                             int nRand, int nNextRand, int nFlags) {
        this.nLevelId = nLevelId;
        this.nExcludedLevel1 = nExcludedLevel1;
        this.nExcludedLevel2 = nExcludedLevel2;
        this.nRand = nRand;
        this.nNextRand = nNextRand;
        this.nFlags = nFlags;
    }
    
    public int getNLevelId() {
        return nLevelId;
    }
    
    public int getNExcludedLevel1() {
        return nExcludedLevel1;
    }
    
    public int getNExcludedLevel2() {
        return nExcludedLevel2;
    }
    
    public int getNRand() {
        return nRand;
    }
    
    public int getNNextRand() {
        return nNextRand;
    }
    
    public int getNFlags() {
        return nFlags;
    }
}
