package com.d2moo.common.drlg;

/**
 * 未知户外结构
 * 对应 C++ 结构：D2UnkOutdoorStrc
 */
public class D2UnkOutdoorStrc {
    private D2DrlgLevel pLevel;         // 0x00 关卡指针
    private int[] field_4;               // 0x04 字段4（int数组指针）
    private D2DrlgGridStrc pGrid1;      // 0x08 网格1
    private D2DrlgGridStrc pGrid2;     // 0x0C 网格2
    private int nLevelPrestId;          // 0x10 关卡预设ID
    private int field_14;                // 0x14 字段14
    private int nLvlSubId;               // 0x18 关卡子ID
    
    // 函数指针（使用 Java 函数式接口）
    @FunctionalInterface
    public interface Field1CFunction {
        int apply(D2DrlgLevel level, int x, int y);
    }
    
    @FunctionalInterface
    public interface Field20Function {
        boolean apply(D2DrlgLevel level, int x, int y, int id, int offset, byte flags);
    }
    
    @FunctionalInterface
    public interface Field24Function {
        boolean apply(D2DrlgLevel level, int x, int y, int a4, int a5, int a6);
    }
    
    @FunctionalInterface
    public interface Field28Function {
        int apply(D2DrlgLevel level, int style, int a3);
    }
    
    @FunctionalInterface
    public interface Field2CFunction {
        void apply(D2DrlgLevel level, int x, int y);
    }
    
    @FunctionalInterface
    public interface Field30Function {
        void apply(D2DrlgLevel level, int x, int y);
    }
    
    @FunctionalInterface
    public interface Field34Function {
        void apply(D2DrlgLevel level, int x, int y, int levelPrestId, int rand, boolean a6);
    }
    
    private Field1CFunction field_1C;    // 0x1C 函数指针
    private Field20Function field_20;   // 0x20 函数指针
    private Field24Function field_24;   // 0x24 函数指针
    private Field28Function field_28;    // 0x28 函数指针
    private Field2CFunction field_2C;   // 0x2C 函数指针
    private Field30Function field_30;   // 0x30 函数指针
    private Field34Function field_34;   // 0x34 函数指针
    
    public D2UnkOutdoorStrc() {
        this.pGrid1 = new D2DrlgGridStrc();
        this.pGrid2 = new D2DrlgGridStrc();
    }
    
    // Getters and Setters
    public D2DrlgLevel getPLevel() {
        return pLevel;
    }
    
    public void setPLevel(D2DrlgLevel pLevel) {
        this.pLevel = pLevel;
    }
    
    public int[] getField_4() {
        return field_4;
    }
    
    public void setField_4(int[] field_4) {
        this.field_4 = field_4;
    }
    
    public D2DrlgGridStrc getPGrid1() {
        return pGrid1;
    }
    
    public void setPGrid1(D2DrlgGridStrc pGrid1) {
        this.pGrid1 = pGrid1;
    }
    
    public D2DrlgGridStrc getPGrid2() {
        return pGrid2;
    }
    
    public void setPGrid2(D2DrlgGridStrc pGrid2) {
        this.pGrid2 = pGrid2;
    }
    
    public int getNLevelPrestId() {
        return nLevelPrestId;
    }
    
    public void setNLevelPrestId(int nLevelPrestId) {
        this.nLevelPrestId = nLevelPrestId;
    }
    
    public int getField_14() {
        return field_14;
    }
    
    public void setField_14(int field_14) {
        this.field_14 = field_14;
    }
    
    public int getNLvlSubId() {
        return nLvlSubId;
    }
    
    public void setNLvlSubId(int nLvlSubId) {
        this.nLvlSubId = nLvlSubId;
    }
    
    public Field1CFunction getField_1C() {
        return field_1C;
    }
    
    public void setField_1C(Field1CFunction field_1C) {
        this.field_1C = field_1C;
    }
    
    public Field20Function getField_20() {
        return field_20;
    }
    
    public void setField_20(Field20Function field_20) {
        this.field_20 = field_20;
    }
    
    public Field24Function getField_24() {
        return field_24;
    }
    
    public void setField_24(Field24Function field_24) {
        this.field_24 = field_24;
    }
    
    public Field28Function getField_28() {
        return field_28;
    }
    
    public void setField_28(Field28Function field_28) {
        this.field_28 = field_28;
    }
    
    public Field2CFunction getField_2C() {
        return field_2C;
    }
    
    public void setField_2C(Field2CFunction field_2C) {
        this.field_2C = field_2C;
    }
    
    public Field30Function getField_30() {
        return field_30;
    }
    
    public void setField_30(Field30Function field_30) {
        this.field_30 = field_30;
    }
    
    public Field34Function getField_34() {
        return field_34;
    }
    
    public void setField_34(Field34Function field_34) {
        this.field_34 = field_34;
    }
}
