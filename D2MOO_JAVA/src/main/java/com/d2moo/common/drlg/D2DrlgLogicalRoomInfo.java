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
    // Java replacement for native pointers stored in pIndexY cells.
    private D2RoomCoordListStrc[] coordListCells;
    private int coordListCellsWidth;
    
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

    public void initializeCoordListCells(int width, int height) {
        coordListCellsWidth = Math.max(0, width);
        coordListCells = width > 0 && height > 0
                ? new D2RoomCoordListStrc[width * height] : null;
    }

    public void setCoordListCell(int x, int y, D2RoomCoordListStrc coordList) {
        int index = coordListCellIndex(x, y);
        if (index >= 0) coordListCells[index] = coordList;
    }

    public D2RoomCoordListStrc getCoordListCell(int x, int y) {
        int index = coordListCellIndex(x, y);
        return index >= 0 ? coordListCells[index] : null;
    }

    public void clearCoordListCells() {
        coordListCells = null;
        coordListCellsWidth = 0;
    }

    private int coordListCellIndex(int x, int y) {
        if (coordListCells == null || coordListCellsWidth <= 0 || x < 0 || y < 0
                || x >= coordListCellsWidth) return -1;
        int index = y * coordListCellsWidth + x;
        return index < coordListCells.length ? index : -1;
    }
}
