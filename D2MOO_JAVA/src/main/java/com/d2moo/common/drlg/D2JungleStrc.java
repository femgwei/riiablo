package com.d2moo.common.drlg;

/**
 * Drlg 丛林结构
 * 对应 C++ 结构：D2JungleStrc
 */
public class D2JungleStrc {
    public static final int JUNGLE_MAX_ATTACH = 3;
    public static final int JUNGLE_PRESET2_ATTACH_POINT = 2;
    
    // 丛林预设标志
    public static final int JUNGLE_FLAG_LEFT = 0b0001;   // West : deltaX < 0
    public static final int JUNGLE_FLAG_RIGHT = 0b0010;  // East : deltaX > 0
    public static final int JUNGLE_FLAG_BOTTOM = 0b0100; // South: deltaY > 0
    public static final int JUNGLE_FLAG_TOP = 0b1000;    // North: deltaY < 0
    
    private D2DrlgCoord pDrlgCoord;              // 0x00 坐标信息
    private int field_10;                        // 0x10 方向/方向标识
    private int nBranch;                         // 0x14 分支数量
    private D2JungleStrc pBasedOnJungle;         // 0x18 基于的丛林
    private D2JungleStrc[] pJungleBranches;      // 0x1C 分支丛林数组 [JUNGLE_MAX_ATTACH]
    private int nPresetsBlocksX;                 // 0x28 预设块X坐标
    private int nPresetsBlocksY;                 // 0x2C 预设块Y坐标
    private int[] pJungleDefs;                   // 0x30 丛林定义数组
    private int nJungleDefs;                     // 0x34 丛林定义数量
    
    public D2JungleStrc() {
        this.pDrlgCoord = new D2DrlgCoord();
        this.pJungleBranches = new D2JungleStrc[JUNGLE_MAX_ATTACH];
        this.field_10 = 0;
        this.nBranch = 0;
        this.nPresetsBlocksX = 0;
        this.nPresetsBlocksY = 0;
        this.nJungleDefs = 0;
    }
    
    // Getters and Setters
    public D2DrlgCoord getPDrlgCoord() {
        return pDrlgCoord;
    }
    
    public void setPDrlgCoord(D2DrlgCoord pDrlgCoord) {
        this.pDrlgCoord = pDrlgCoord;
    }
    
    public int getField_10() {
        return field_10;
    }
    
    public void setField_10(int field_10) {
        this.field_10 = field_10;
    }
    
    public int getNBranch() {
        return nBranch;
    }
    
    public void setNBranch(int nBranch) {
        this.nBranch = nBranch;
    }
    
    public D2JungleStrc getPBasedOnJungle() {
        return pBasedOnJungle;
    }
    
    public void setPBasedOnJungle(D2JungleStrc pBasedOnJungle) {
        this.pBasedOnJungle = pBasedOnJungle;
    }
    
    public D2JungleStrc[] getPJungleBranches() {
        return pJungleBranches;
    }
    
    public void setPJungleBranches(D2JungleStrc[] pJungleBranches) {
        this.pJungleBranches = pJungleBranches;
    }
    
    public D2JungleStrc getPJungleBranches(int index) {
        if (index >= 0 && index < pJungleBranches.length) {
            return pJungleBranches[index];
        }
        return null;
    }
    
    public void setPJungleBranches(int index, D2JungleStrc jungle) {
        if (index >= 0 && index < pJungleBranches.length) {
            pJungleBranches[index] = jungle;
        }
    }
    
    public int getNPresetsBlocksX() {
        return nPresetsBlocksX;
    }
    
    public void setNPresetsBlocksX(int nPresetsBlocksX) {
        this.nPresetsBlocksX = nPresetsBlocksX;
    }
    
    public int getNPresetsBlocksY() {
        return nPresetsBlocksY;
    }
    
    public void setNPresetsBlocksY(int nPresetsBlocksY) {
        this.nPresetsBlocksY = nPresetsBlocksY;
    }
    
    public int[] getPJungleDefs() {
        return pJungleDefs;
    }
    
    public void setPJungleDefs(int[] pJungleDefs) {
        this.pJungleDefs = pJungleDefs;
    }
    
    public int getNJungleDefs() {
        return nJungleDefs;
    }
    
    public void setNJungleDefs(int nJungleDefs) {
        this.nJungleDefs = nJungleDefs;
    }
}
