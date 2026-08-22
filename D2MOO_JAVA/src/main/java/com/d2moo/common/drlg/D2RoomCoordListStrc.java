package com.d2moo.common.drlg;

/**
 * 房间坐标列表结构
 * 对应 C++ 结构：D2RoomCoordListStrc
 */
public class D2RoomCoordListStrc {
    private D2DrlgCoord[] pBox;             // 0x00 坐标框数组 [2]
    private boolean bNode;                   // 0x20 是否为节点
    private boolean bRoomActive;            // 0x24 房间是否激活
    private int nIndex;                      // 0x28 索引
    private D2RoomCoordListStrc pNext;       // 0x2C 下一个坐标列表（链表）
    
    public D2RoomCoordListStrc() {
        this.pBox = new D2DrlgCoord[2];
        this.pBox[0] = new D2DrlgCoord();
        this.pBox[1] = new D2DrlgCoord();
        this.bNode = false;
        this.bRoomActive = false;
        this.nIndex = 0;
    }
    
    // Getters and Setters
    public D2DrlgCoord[] getPBox() {
        return pBox;
    }
    
    public void setPBox(D2DrlgCoord[] pBox) {
        this.pBox = pBox;
    }
    
    public D2DrlgCoord getPBox(int index) {
        if (index >= 0 && index < pBox.length) {
            return pBox[index];
        }
        return null;
    }
    
    public void setPBox(int index, D2DrlgCoord coord) {
        if (index >= 0 && index < pBox.length) {
            pBox[index] = coord;
        }
    }
    
    public boolean isBNode() {
        return bNode;
    }
    
    public void setBNode(boolean bNode) {
        this.bNode = bNode;
    }
    
    public boolean isBRoomActive() {
        return bRoomActive;
    }
    
    public void setBRoomActive(boolean bRoomActive) {
        this.bRoomActive = bRoomActive;
    }
    
    public int getNIndex() {
        return nIndex;
    }
    
    public void setNIndex(int nIndex) {
        this.nIndex = nIndex;
    }
    
    public D2RoomCoordListStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2RoomCoordListStrc pNext) {
        this.pNext = pNext;
    }
}
