package com.d2moo.common.drlg;

/**
 * Drlg 替换组结构
 * 对应 C++ 结构：D2DrlgSubstGroupStrc
 */
public class D2DrlgSubstGroupStrc {
    private D2DrlgCoord tBox;      // 0x00 坐标框
    private int field_10;           // 0x10 字段10
    private int field_14;           // 0x14 字段14（替换组中的变体数量）
    
    public D2DrlgSubstGroupStrc() {
        this.tBox = new D2DrlgCoord();
    }
    
    // Getters and Setters
    public D2DrlgCoord getTBox() {
        return tBox;
    }
    
    public void setTBox(D2DrlgCoord tBox) {
        this.tBox = tBox;
    }
    
    public int getField_10() {
        return field_10;
    }
    
    public void setField_10(int field_10) {
        this.field_10 = field_10;
    }
    
    public int getField_14() {
        return field_14;
    }
    
    public void setField_14(int field_14) {
        this.field_14 = field_14;
    }
}
