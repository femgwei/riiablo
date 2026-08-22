package com.d2moo.common.drlg;

/**
 * Drlg 逻辑房间信息结构
 * 对应 C++ 结构：D2DrlgLogicalRoomInfoStrc（aka D2DrlgCoordListStrc）
 */
public class D2DrlgLogicalRoomInfo {
    private int dwFlags;                    // 0x00 D2DrlgLogicalRoomInfoFlags
    private int nLists;                     // 0x04 列表数量
    private D2DrlgGridStrc pIndexX;         // 0x08 X 索引网格
    private D2DrlgGridStrc pIndexY;         // 0x1C Y 索引网格
    private D2RoomCoordListStrc pCoordList; // 0x30 坐标列表链表
    
    // 标志常量
    public static final int DRLGLOGIC_ROOMINFO_HAS_COORD_LIST = 0x1;
    public static final int DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS = 0x2;
    
    public D2DrlgLogicalRoomInfo() {
        this.dwFlags = 0;
        this.nLists = 0;
        this.pIndexX = new D2DrlgGridStrc();
        this.pIndexY = new D2DrlgGridStrc();
    }
    
    // Getters and Setters
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public boolean hasCoordList() {
        return (dwFlags & DRLGLOGIC_ROOMINFO_HAS_COORD_LIST) != 0;
    }
    
    public boolean hasGridCells() {
        return (dwFlags & DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS) != 0;
    }
    
    public void setHasCoordList(boolean has) {
        if (has) {
            dwFlags |= DRLGLOGIC_ROOMINFO_HAS_COORD_LIST;
        } else {
            dwFlags &= ~DRLGLOGIC_ROOMINFO_HAS_COORD_LIST;
        }
    }
    
    public void setHasGridCells(boolean has) {
        if (has) {
            dwFlags |= DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS;
        } else {
            dwFlags &= ~DRLGLOGIC_ROOMINFO_HAS_GRID_CELLS;
        }
    }
    
    public int getNLists() {
        return nLists;
    }
    
    public void setNLists(int nLists) {
        this.nLists = nLists;
    }
    
    public D2DrlgGridStrc getPIndexX() {
        return pIndexX;
    }
    
    public void setPIndexX(D2DrlgGridStrc pIndexX) {
        this.pIndexX = pIndexX;
    }
    
    public D2DrlgGridStrc getPIndexY() {
        return pIndexY;
    }
    
    public void setPIndexY(D2DrlgGridStrc pIndexY) {
        this.pIndexY = pIndexY;
    }
    
    public D2RoomCoordListStrc getPCoordList() {
        return pCoordList;
    }
    
    public void setPCoordList(D2RoomCoordListStrc pCoordList) {
        this.pCoordList = pCoordList;
    }
}
