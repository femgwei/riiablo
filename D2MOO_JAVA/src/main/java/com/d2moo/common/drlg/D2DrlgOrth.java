package com.d2moo.common.drlg;

/**
 * Drlg 正交结构
 * 对应 C++ 结构：D2DrlgOrthStrc
 */
public class D2DrlgOrth {
    private Object drlgRoomOrLevel;              // 0x00 union: D2DrlgRoom* 或 D2DrlgLevel*
    private byte nDirection;                     // 0x04
    private boolean bPreset;                     // 0x08
    private boolean bInit;                       // 0x0C
    private D2DrlgCoord pBox;                    // 0x10 D2DrlgCoordStrc*
    private D2DrlgOrth pNext;                    // 0x14
    
    public Object getDrlgRoomOrLevel() { return drlgRoomOrLevel; }
    public void setDrlgRoomOrLevel(Object drlgRoomOrLevel) { this.drlgRoomOrLevel = drlgRoomOrLevel; }
    
    public D2DrlgRoom getPDrlgRoom() { 
        return drlgRoomOrLevel instanceof D2DrlgRoom ? (D2DrlgRoom)drlgRoomOrLevel : null; 
    }
    public void setPDrlgRoom(D2DrlgRoom pDrlgRoom) { this.drlgRoomOrLevel = pDrlgRoom; }
    
    public D2DrlgLevel getPLevel() { 
        return drlgRoomOrLevel instanceof D2DrlgLevel ? (D2DrlgLevel)drlgRoomOrLevel : null; 
    }
    public void setPLevel(D2DrlgLevel pLevel) { this.drlgRoomOrLevel = pLevel; }
    
    public byte getNDirection() { return nDirection; }
    public void setNDirection(byte nDirection) { this.nDirection = nDirection; }
    
    public boolean isBPreset() { return bPreset; }
    public void setBPreset(boolean bPreset) { this.bPreset = bPreset; }
    
    public boolean isBInit() { return bInit; }
    public void setBInit(boolean bInit) { this.bInit = bInit; }
    
    public D2DrlgCoord getPBox() { return pBox; }
    public void setPBox(D2DrlgCoord pBox) { this.pBox = pBox; }
    
    public D2DrlgOrth getPNext() { return pNext; }
    public void setPNext(D2DrlgOrth pNext) { this.pNext = pNext; }
}
