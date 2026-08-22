package com.d2moo.common.drlg;

/**
 * 房间瓦片结构
 * 对应 C++ 结构：D2RoomTileStrc
 */
public class D2RoomTile {
    private D2DrlgRoom pDrlgRoom;                // 0x00
    private Object pLvlWarpTxtRecord;            // 0x04 D2LvlWarpTxt*
    private boolean bEnabled;                   // 0x08
    private Object unk0x0C;                      // 0x0C D2DrlgTileDataStrc*
    private Object unk0x10;                      // 0x10 D2DrlgTileDataStrc*
    private D2RoomTile pNext;                    // 0x14
    
    public D2DrlgRoom getPDrlgRoom() { return pDrlgRoom; }
    public void setPDrlgRoom(D2DrlgRoom pDrlgRoom) { this.pDrlgRoom = pDrlgRoom; }
    
    public Object getPLvlWarpTxtRecord() { return pLvlWarpTxtRecord; }
    public void setPLvlWarpTxtRecord(Object pLvlWarpTxtRecord) { this.pLvlWarpTxtRecord = pLvlWarpTxtRecord; }
    
    public boolean isBEnabled() { return bEnabled; }
    public void setBEnabled(boolean bEnabled) { this.bEnabled = bEnabled; }
    
    public Object getUnk0x0C() { return unk0x0C; }
    public void setUnk0x0C(Object unk0x0C) { this.unk0x0C = unk0x0C; }
    
    public Object getUnk0x10() { return unk0x10; }
    public void setUnk0x10(Object unk0x10) { this.unk0x10 = unk0x10; }
    
    public D2RoomTile getPNext() { return pNext; }
    public void setPNext(D2RoomTile pNext) { this.pNext = pNext; }
}
