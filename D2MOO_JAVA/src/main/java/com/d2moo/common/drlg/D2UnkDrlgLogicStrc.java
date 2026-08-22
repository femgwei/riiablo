package com.d2moo.common.drlg;

/**
 * 未知 Drlg 逻辑结构
 * 对应 C++ 结构：D2UnkDrlgLogicStrc
 * 
 * 用于 DRLGLOGIC_SetTileGridFlags 函数的参数传递
 */
public class D2UnkDrlgLogicStrc {
    private D2DrlgRoom pDrlgRoom;              // 0x00
    private D2DrlgGridStrc field_4;            // 0x04 (pIndexX)
    private D2DrlgGridStrc pTileTypeGrid;      // 0x08
    private D2DrlgGridStrc pWallGrid;          // 0x0C
    private D2DrlgGridStrc pFloorGrid;         // 0x10
    private D2DrlgGridStrc field_14;           // 0x14 (pDrlgGrid)
    private int field_18;                      // 0x18 (nLists)
    private int nFlags;                        // 0x1C
    
    // Getters and Setters
    public D2DrlgRoom getPDrlgRoom() {
        return pDrlgRoom;
    }
    
    public void setPDrlgRoom(D2DrlgRoom pDrlgRoom) {
        this.pDrlgRoom = pDrlgRoom;
    }
    
    public D2DrlgGridStrc getField_4() {
        return field_4;
    }
    
    public void setField_4(D2DrlgGridStrc field_4) {
        this.field_4 = field_4;
    }
    
    public D2DrlgGridStrc getPTileTypeGrid() {
        return pTileTypeGrid;
    }
    
    public void setPTileTypeGrid(D2DrlgGridStrc pTileTypeGrid) {
        this.pTileTypeGrid = pTileTypeGrid;
    }
    
    public D2DrlgGridStrc getPWallGrid() {
        return pWallGrid;
    }
    
    public void setPWallGrid(D2DrlgGridStrc pWallGrid) {
        this.pWallGrid = pWallGrid;
    }
    
    public D2DrlgGridStrc getPFloorGrid() {
        return pFloorGrid;
    }
    
    public void setPFloorGrid(D2DrlgGridStrc pFloorGrid) {
        this.pFloorGrid = pFloorGrid;
    }
    
    public D2DrlgGridStrc getField_14() {
        return field_14;
    }
    
    public void setField_14(D2DrlgGridStrc field_14) {
        this.field_14 = field_14;
    }
    
    public int getField_18() {
        return field_18;
    }
    
    public void setField_18(int field_18) {
        this.field_18 = field_18;
    }
    
    public int getNFlags() {
        return nFlags;
    }
    
    public void setNFlags(int nFlags) {
        this.nFlags = nFlags;
    }
}
