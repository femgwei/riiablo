package com.d2moo.common.drlg;

import com.d2moo.common.environment.D2DrlgEnvironment;

/**
 * Drlg Act 结构
 * 对应 C++ 结构：D2DrlgActStrc
 */
public class D2DrlgAct {
    private byte act;                              // 0x00
    private D2ActiveRoom room;                     // 0x04
    private D2DrlgStrc drlg;                       // 0x08
    private int initSeed;                          // 0x0C
    private int townId;                            // 0x10
    private D2DrlgEnvironment environment;         // 0x14 D2DrlgEnvironmentStrc*
    private D2ActCallback pfnActCallBack;          // 0x18 ACTCALLBACKFN
    private boolean client;                        // 0x1C
    private boolean hasPendingRoomsUpdates;        // 0x20
    private boolean hasPendingRoomDeletions;       // 0x24
    private boolean hasPendingUnitListUpdates;     // 0x28
    private D2DrlgTileDataStrc tileData;           // 0x2C embedded D2DrlgTileDataStrc
    private Object memPool;                        // 0x5C

    public D2DrlgAct() {
        tileData = new D2DrlgTileDataStrc();
    }
    
    public byte getAct() { return act; }
    public void setAct(byte act) { this.act = act; }
    
    public D2ActiveRoom getRoom() { return room; }
    public void setRoom(D2ActiveRoom room) { this.room = room; }
    
    public D2DrlgStrc getDrlg() { return drlg; }
    public void setDrlg(D2DrlgStrc drlg) { this.drlg = drlg; }
    
    public int getInitSeed() { return initSeed; }
    public void setInitSeed(int initSeed) { this.initSeed = initSeed; }
    
    public int getTownId() { return townId; }
    public void setTownId(int townId) { this.townId = townId; }
    
    public D2DrlgEnvironment getEnvironment() { return environment; }
    public void setEnvironment(D2DrlgEnvironment environment) { this.environment = environment; }
    
    public D2ActCallback getPfnActCallBack() { return pfnActCallBack; }
    public void setPfnActCallBack(D2ActCallback pfnActCallBack) { this.pfnActCallBack = pfnActCallBack; }
    
    public boolean isClient() { return client; }
    public void setClient(boolean client) { this.client = client; }
    
    public boolean isHasPendingRoomsUpdates() { return hasPendingRoomsUpdates; }
    public void setHasPendingRoomsUpdates(boolean hasPendingRoomsUpdates) { this.hasPendingRoomsUpdates = hasPendingRoomsUpdates; }
    
    public boolean isHasPendingRoomDeletions() { return hasPendingRoomDeletions; }
    public void setHasPendingRoomDeletions(boolean hasPendingRoomDeletions) { this.hasPendingRoomDeletions = hasPendingRoomDeletions; }
    
    public boolean isHasPendingUnitListUpdates() { return hasPendingUnitListUpdates; }
    public void setHasPendingUnitListUpdates(boolean hasPendingUnitListUpdates) { this.hasPendingUnitListUpdates = hasPendingUnitListUpdates; }
    
    public D2DrlgTileDataStrc getTileData() { return tileData; }
    public void setTileData(D2DrlgTileDataStrc tileData) {
        this.tileData = tileData != null ? tileData : new D2DrlgTileDataStrc();
    }
    
    public Object getPMemPool() { return memPool; }
    public void setPMemPool(Object memPool) { this.memPool = memPool; }
}
