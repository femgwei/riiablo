package com.d2moo.common.drlg;

/**
 * 户外替换结构2
 * 对应 C++ 结构：D2UnkOutdoorStrc2
 * 用于 DrlgTileSub 模块的替换操作
 */
public class D2UnkOutdoorStrc2 {
    private D2DrlgRoom pDrlgRoom;                    // 0x00 Drlg 房间指针
    private D2DrlgOutdoorRoomStrc[] pOutdoorRooms;   // 0x04 户外房间数组 [8]
    private D2DrlgGridStrc[] pWallsGrids;            // 0x24 墙壁网格数组 [8]
    private D2DrlgGridStrc pFloorGrid;               // 0x44 地板网格指针
    private int field_28;                            // 0x28 字段28
    private int field_2C;                            // 0x2C 字段2C（层数）
    private int nSubTheme;                           // 0x30 子主题
    private int nSubWaypoint_Shrine;                 // 0x34 传送点/神殿子ID
    private int nSubThemePicked;                     // 0x38 选中的子主题
    
    public D2UnkOutdoorStrc2() {
        this.pOutdoorRooms = new D2DrlgOutdoorRoomStrc[8];
        this.pWallsGrids = new D2DrlgGridStrc[8];
    }
    
    // Getters and Setters
    public D2DrlgRoom getPDrlgRoom() {
        return pDrlgRoom;
    }
    
    public void setPDrlgRoom(D2DrlgRoom pDrlgRoom) {
        this.pDrlgRoom = pDrlgRoom;
    }
    
    public D2DrlgOutdoorRoomStrc[] getPOutdoorRooms() {
        return pOutdoorRooms;
    }
    
    public void setPOutdoorRooms(D2DrlgOutdoorRoomStrc[] pOutdoorRooms) {
        this.pOutdoorRooms = pOutdoorRooms;
    }
    
    public D2DrlgOutdoorRoomStrc getPOutdoorRooms(int index) {
        if (index >= 0 && index < pOutdoorRooms.length) {
            return pOutdoorRooms[index];
        }
        return null;
    }
    
    public void setPOutdoorRooms(int index, D2DrlgOutdoorRoomStrc room) {
        if (index >= 0 && index < pOutdoorRooms.length) {
            pOutdoorRooms[index] = room;
        }
    }
    
    public D2DrlgGridStrc[] getPWallsGrids() {
        return pWallsGrids;
    }
    
    public void setPWallsGrids(D2DrlgGridStrc[] pWallsGrids) {
        this.pWallsGrids = pWallsGrids;
    }
    
    public D2DrlgGridStrc getPWallsGrids(int index) {
        if (index >= 0 && index < pWallsGrids.length) {
            return pWallsGrids[index];
        }
        return null;
    }
    
    public void setPWallsGrids(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pWallsGrids.length) {
            pWallsGrids[index] = grid;
        }
    }
    
    public D2DrlgGridStrc getPFloorGrid() {
        return pFloorGrid;
    }
    
    public void setPFloorGrid(D2DrlgGridStrc pFloorGrid) {
        this.pFloorGrid = pFloorGrid;
    }
    
    public int getField_28() {
        return field_28;
    }
    
    public void setField_28(int field_28) {
        this.field_28 = field_28;
    }
    
    public int getField_2C() {
        return field_2C;
    }
    
    public void setField_2C(int field_2C) {
        this.field_2C = field_2C;
    }
    
    public int getNSubTheme() {
        return nSubTheme;
    }
    
    public void setNSubTheme(int nSubTheme) {
        this.nSubTheme = nSubTheme;
    }
    
    public int getNSubWaypoint_Shrine() {
        return nSubWaypoint_Shrine;
    }
    
    public void setNSubWaypoint_Shrine(int nSubWaypoint_Shrine) {
        this.nSubWaypoint_Shrine = nSubWaypoint_Shrine;
    }
    
    public int getNSubThemePicked() {
        return nSubThemePicked;
    }
    
    public void setNSubThemePicked(int nSubThemePicked) {
        this.nSubThemePicked = nSubThemePicked;
    }
}
