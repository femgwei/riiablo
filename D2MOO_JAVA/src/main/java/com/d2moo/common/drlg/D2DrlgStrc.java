package com.d2moo.common.drlg;

/**
 * Drlg 主结构
 * 对应 C++ 结构：D2DrlgStrc
 */
public class D2DrlgStrc {
    private D2DrlgLevel level;                    // 0x00 Latest added level
    private Object mempool;                        // 0x04
    private Object archive;                       // 0x08 HD2ARCHIVE - Always null in the game
    private D2DrlgAct pAct;                       // 0x0C
    private byte actNo;                           // 0x10
    private D2Seed seed;                          // 0x14
    private int startSeed;                        // 0x1C
    private int gameLowSeed;                      // 0x20
    private int flags;                            // 0x24 D2DrlgFlags
    private D2DrlgRoom[] statusRoomsLists;        // 0x28 D2DrlgRoomStrc tStatusRoomsLists[ROOMSTATUS_COUNT]
    private D2DrlgRoom drlgRoom;                   // 0x3D8
    private byte roomsInitSinceLastUpdate;        // 0x3DC
    private byte roomsInitTimeout;                // 0x3DD
    private int allocatedRooms;                  // 0x3E0
    private int freedRooms;                       // 0x3E4
    private Object game;                          // 0x3E8 D2GameStrc*
    private byte difficulty;                      // 0x3EC
    private Object pfAutomap;                      // 0x3F0 AUTOMAPFN
    private Object pfTownAutomap;                 // 0x3F4 TOWNAUTOMAPFN
    private int staffTombLevel;                   // 0x3F8
    private int bossTombLevel;                     // 0x3FC
    private Object[] tiles;                       // 0x400 D2TileLibraryHashStrc* pTiles[32]
    private int jungleInterlink;                  // 0x480
    private D2DrlgWarp warp;                       // 0x484
    
    public D2DrlgStrc() {
        this.statusRoomsLists = new D2DrlgRoom[D2DrlgRoomStatus.COUNT.getValue()];
        // C++ embeds sentinel D2DrlgRoomStrc values in the main structure.
        // Java arrays contain null until explicitly populated, which caused
        // DRLG activation to fail before the first room was generated.
        for (int i = 0; i < this.statusRoomsLists.length; i++) {
            this.statusRoomsLists[i] = new D2DrlgRoom();
        }
        this.tiles = new Object[32];
    }
    
    // Getters and Setters
    public D2DrlgLevel getLevel() { return level; }
    public void setLevel(D2DrlgLevel level) { this.level = level; }
    
    public Object getMempool() { return mempool; }
    public void setMempool(Object mempool) { this.mempool = mempool; }
    
    public Object getArchive() { return archive; }
    public void setArchive(Object archive) { this.archive = archive; }
    
    public D2DrlgAct getAct() { return pAct; }
    public void setAct(D2DrlgAct pAct) { this.pAct = pAct; }
    
    public byte getActNo() { return actNo; }
    public void setActNo(byte actNo) { this.actNo = actNo; }
    
    public D2Seed getSeed() { return seed; }
    public void setSeed(D2Seed seed) { this.seed = seed; }
    
    public int getStartSeed() { return startSeed; }
    public void setStartSeed(int startSeed) { this.startSeed = startSeed; }
    
    public int getGameLowSeed() { return gameLowSeed; }
    public void setGameLowSeed(int gameLowSeed) { this.gameLowSeed = gameLowSeed; }
    
    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    
    public D2DrlgRoom[] getStatusRoomsLists() { return statusRoomsLists; }
    public void setStatusRoomsLists(D2DrlgRoom[] statusRoomsLists) { this.statusRoomsLists = statusRoomsLists; }
    
    public D2DrlgRoom getDrlgRoom() { return drlgRoom; }
    public void setDrlgRoom(D2DrlgRoom drlgRoom) { this.drlgRoom = drlgRoom; }
    
    public byte getRoomsInitSinceLastUpdate() { return roomsInitSinceLastUpdate; }
    public void setRoomsInitSinceLastUpdate(byte roomsInitSinceLastUpdate) { this.roomsInitSinceLastUpdate = roomsInitSinceLastUpdate; }
    
    public byte getRoomsInitTimeout() { return roomsInitTimeout; }
    public void setRoomsInitTimeout(byte roomsInitTimeout) { this.roomsInitTimeout = roomsInitTimeout; }
    
    public int getAllocatedRooms() { return allocatedRooms; }
    public void setAllocatedRooms(int allocatedRooms) { this.allocatedRooms = allocatedRooms; }
    
    public int getFreedRooms() { return freedRooms; }
    public void setFreedRooms(int freedRooms) { this.freedRooms = freedRooms; }
    
    public int getNFreedRooms() { return freedRooms; }
    public void setNFreedRooms(int freedRooms) { this.freedRooms = freedRooms; }
    
    public Object getGame() { return game; }
    public void setGame(Object game) { this.game = game; }
    
    public byte getDifficulty() { return difficulty; }
    public void setDifficulty(byte difficulty) { this.difficulty = difficulty; }
    
    public Object getPfAutomap() { return pfAutomap; }
    public void setPfAutomap(Object pfAutomap) { this.pfAutomap = pfAutomap; }
    
    public Object getPfTownAutomap() { return pfTownAutomap; }
    public void setPfTownAutomap(Object pfTownAutomap) { this.pfTownAutomap = pfTownAutomap; }
    
    public int getStaffTombLevel() { return staffTombLevel; }
    public void setStaffTombLevel(int staffTombLevel) { this.staffTombLevel = staffTombLevel; }
    
    public int getBossTombLevel() { return bossTombLevel; }
    public void setBossTombLevel(int bossTombLevel) { this.bossTombLevel = bossTombLevel; }
    
    public Object[] getTiles() { return tiles; }
    public void setTiles(Object[] tiles) { this.tiles = tiles; }
    
    public int getJungleInterlink() { return jungleInterlink; }
    public void setJungleInterlink(int jungleInterlink) { this.jungleInterlink = jungleInterlink; }
    
    public D2DrlgWarp getWarp() { return warp; }
    public void setWarp(D2DrlgWarp warp) { this.warp = warp; }
}
