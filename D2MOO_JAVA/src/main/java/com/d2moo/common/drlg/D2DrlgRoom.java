package com.d2moo.common.drlg;

/**
 * Drlg 房间结构
 * 对应 C++ 结构：D2DrlgRoomStrc
 */
public class D2DrlgRoom {
    private D2DrlgLevel level;                    // 0x00
    private D2DrlgCoord drlgCoord;                // 0x04 坐标信息
    private int flags;                            // 0x14 D2DrlgRoomFlags
    private int otherFlags;                       // 0x18
    private int type;                             // 0x1C
    private Object mazeOrOutdoor;                 // 0x20 union: D2DrlgPresetRoomStrc* 或 D2DrlgOutdoorRoomStrc*
    private int dt1Mask;                         // 0x24 - tile caching mask
    private Object[] tiles;                       // 0x28 D2TileLibraryHashStrc* pTiles[32]
    private D2DrlgTileGrid tileGrid;             // 0xA8
    private D2DrlgRoomStatus roomStatus;          // 0xAC D2DrlgRoomStatus
    private byte unk0xAD;                        // 0xAD
    private short[] roomsInList;                 // 0xAE uint16_t wRoomsInList[ROOMSTATUS_COUNT + 1]
    private D2DrlgRoom statusNext;               // 0xB8
    private D2DrlgRoom statusPrev;               // 0xBC
    private D2DrlgRoom[] ppRoomsNear;             // 0xC0 ppRoomsNear
    private int nRoomsNear;                      // 0xC4 nRoomsNear
    private D2RoomTile roomTiles;                 // 0xC8
    private D2PresetUnit presetUnits;            // 0xCC
    private D2DrlgOrth drlgOrth;                  // 0xD0
    private D2Seed seed;                          // 0xD4
    private int initSeed;                         // 0xDC
    private D2DrlgLogicalRoomInfo logicalRoomInfo; // 0xE0
    private D2ActiveRoom room;                   // 0xE4
    private D2DrlgRoom drlgRoomNext;              // 0xE8
    
    public D2DrlgRoom() {
        this.roomsInList = new short[D2DrlgRoomStatus.COUNT.getValue() + 1];
        this.tiles = new Object[32];
    }
    
    // Getters and Setters
    public D2DrlgLevel getLevel() { return level; }
    public void setLevel(D2DrlgLevel level) { this.level = level; }
    
    public D2DrlgCoord getDrlgCoord() { 
        if (drlgCoord == null) {
            drlgCoord = new D2DrlgCoord();
            drlgCoord.setNTileXPos(getNTileXPos());
            drlgCoord.setNTileYPos(getNTileYPos());
            drlgCoord.setNTileWidth(getNTileWidth());
            drlgCoord.setNTileHeight(getNTileHeight());
        }
        return drlgCoord; 
    }
    public void setDrlgCoord(D2DrlgCoord drlgCoord) { this.drlgCoord = drlgCoord; }
    
    // 便捷方法：直接访问坐标字段（对应 C++ union）
    public int getNTileXPos() { return drlgCoord != null ? drlgCoord.getNTileXPos() : 0; }
    public void setNTileXPos(int nTileXPos) { 
        if (drlgCoord == null) drlgCoord = new D2DrlgCoord();
        drlgCoord.setNTileXPos(nTileXPos); 
    }
    public int getNTileYPos() { return drlgCoord != null ? drlgCoord.getNTileYPos() : 0; }
    public void setNTileYPos(int nTileYPos) { 
        if (drlgCoord == null) drlgCoord = new D2DrlgCoord();
        drlgCoord.setNTileYPos(nTileYPos); 
    }
    public int getNTileWidth() { return drlgCoord != null ? drlgCoord.getNTileWidth() : 0; }
    public void setNTileWidth(int nTileWidth) { 
        if (drlgCoord == null) drlgCoord = new D2DrlgCoord();
        drlgCoord.setNTileWidth(nTileWidth); 
    }
    public int getNTileHeight() { return drlgCoord != null ? drlgCoord.getNTileHeight() : 0; }
    public void setNTileHeight(int nTileHeight) { 
        if (drlgCoord == null) drlgCoord = new D2DrlgCoord();
        drlgCoord.setNTileHeight(nTileHeight); 
    }
    
    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    
    public int getOtherFlags() { return otherFlags; }
    public void setOtherFlags(int otherFlags) { this.otherFlags = otherFlags; }
    
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    
    public Object getMazeOrOutdoor() { return mazeOrOutdoor; }
    public void setMazeOrOutdoor(Object mazeOrOutdoor) { this.mazeOrOutdoor = mazeOrOutdoor; }
    
    public int getDt1Mask() { return dt1Mask; }
    public void setDt1Mask(int dt1Mask) { this.dt1Mask = dt1Mask; }
    
    public Object[] getTiles() { return tiles; }
    public void setTiles(Object[] tiles) { this.tiles = tiles; }
    
    public D2DrlgTileGrid getTileGrid() { return tileGrid; }
    public void setTileGrid(D2DrlgTileGrid tileGrid) { this.tileGrid = tileGrid; }
    
    public D2DrlgRoomStatus getRoomStatus() { return roomStatus; }
    public void setRoomStatus(D2DrlgRoomStatus roomStatus) { this.roomStatus = roomStatus; }
    
    public byte getUnk0xAD() { return unk0xAD; }
    public void setUnk0xAD(byte unk0xAD) { this.unk0xAD = unk0xAD; }
    
    public short[] getRoomsInList() { return roomsInList; }
    public void setRoomsInList(short[] roomsInList) { this.roomsInList = roomsInList; }
    
    public D2DrlgRoom getStatusNext() { return statusNext; }
    public void setStatusNext(D2DrlgRoom statusNext) { this.statusNext = statusNext; }
    
    public D2DrlgRoom getStatusPrev() { return statusPrev; }
    public void setStatusPrev(D2DrlgRoom statusPrev) { this.statusPrev = statusPrev; }
    
    public D2DrlgRoom[] getPpRoomsNear() { return ppRoomsNear; }
    public void setPpRoomsNear(D2DrlgRoom[] ppRoomsNear) { this.ppRoomsNear = ppRoomsNear; }
    
    public int getNRoomsNear() { return nRoomsNear; }
    public void setNRoomsNear(int nRoomsNear) { this.nRoomsNear = nRoomsNear; }
    
    public D2RoomTile getRoomTiles() { return roomTiles; }
    public void setRoomTiles(D2RoomTile roomTiles) { this.roomTiles = roomTiles; }
    
    public D2PresetUnit getPresetUnits() { return presetUnits; }
    public void setPresetUnits(D2PresetUnit presetUnits) { this.presetUnits = presetUnits; }
    
    public D2DrlgOrth getDrlgOrth() { return drlgOrth; }
    public void setDrlgOrth(D2DrlgOrth drlgOrth) { this.drlgOrth = drlgOrth; }
    
    public D2Seed getSeed() { return seed; }
    public void setSeed(D2Seed seed) { this.seed = seed; }
    
    public int getInitSeed() { return initSeed; }
    public void setInitSeed(int initSeed) { this.initSeed = initSeed; }
    
    public D2DrlgLogicalRoomInfo getLogicalRoomInfo() { return logicalRoomInfo; }
    public void setLogicalRoomInfo(D2DrlgLogicalRoomInfo logicalRoomInfo) { this.logicalRoomInfo = logicalRoomInfo; }
    
    public D2ActiveRoom getRoom() { return room; }
    public void setRoom(D2ActiveRoom room) { 
        this.room = room;
        // 确保双向关联：如果设置了 ActiveRoom，也设置 ActiveRoom 的 pDrlgRoom 字段
        if (room != null && room.getPDrlgRoom() != this) {
            room.setPDrlgRoom(this);
        }
    }
    
    public D2DrlgRoom getDrlgRoomNext() { return drlgRoomNext; }
    public void setDrlgRoomNext(D2DrlgRoom drlgRoomNext) { this.drlgRoomNext = drlgRoomNext; }
}
