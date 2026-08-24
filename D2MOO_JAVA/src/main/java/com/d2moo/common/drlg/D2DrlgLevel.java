package com.d2moo.common.drlg;

/**
 * Drlg 关卡结构
 * 对应 C++ 结构：D2DrlgLevelStrc
 */
public class D2DrlgLevel {
    private D2DrlgStrc drlg;                      // 0x00
    private int levelId;                          // 0x04
    private int levelType;                        // 0x08
    private int drlgType;                         // 0x0C
    private int flags;                            // 0x10
    private D2Seed seed;                          // 0x14
    private int initSeed;                         // 0x1C
    private D2DrlgCoord levelCoords;              // 0x20
    private D2DrlgRoom firstRoomEx;               // 0x30
    private int rooms;                            // 0x34
    private Object presetOrOutdoorsOrMaze;        // 0x38 union
    private Object currentMap;                    // 0x3C
    private int coordLists;                       // 0x40
    private D2DrlgTileInfoStrc[] pTileInfo;       // 0x44 D2DrlgTileInfoStrc pTileInfo[32]
    private int nTileInfoCount;                   // 0x1C4
    private int[] roomCenterWarpX;                // 0x1C8 int nRoom_Center_Warp_X[9]
    private int[] roomCenterWarpY;                 // 0x1EC int nRoom_Center_Warp_Y[9]
    private int roomCoords;                       // 0x210
    private int[] pJungleDefs;                    // 0x214
    private int nJungleDefsCount;                  // 0x218
    private D2DrlgBuildStrc build;                 // 0x21C D2DrlgBuildStrc*
    private int active;                            // 0x220 native activity reference count
    private int inactiveFrames;                    // 0x224
    private int[] presetMaps;                     // 0x228
    private D2DrlgLevel nextLevel;                // 0x22C
    
    public D2DrlgLevel() {
        // Embedded in the native structure; Java must allocate it eagerly.
        this.levelCoords = new D2DrlgCoord();
        this.pTileInfo = new D2DrlgTileInfoStrc[32];
        for (int i = 0; i < this.pTileInfo.length; i++) this.pTileInfo[i] = new D2DrlgTileInfoStrc();
        this.roomCenterWarpX = new int[9];
        this.roomCenterWarpY = new int[9];
    }
    
    // Getters and Setters
    public D2DrlgStrc getDrlg() { return drlg; }
    public void setDrlg(D2DrlgStrc drlg) { this.drlg = drlg; }
    
    public int getLevelId() { return levelId; }
    public void setLevelId(int levelId) { this.levelId = levelId; }
    
    public int getLevelType() { return levelType; }
    public void setLevelType(int levelType) { this.levelType = levelType; }
    
    public int getDrlgType() { return drlgType; }
    public void setDrlgType(int drlgType) { this.drlgType = drlgType; }
    
    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    
    public D2Seed getSeed() { return seed; }
    public void setSeed(D2Seed seed) { this.seed = seed; }
    
    public int getInitSeed() { return initSeed; }
    public void setInitSeed(int initSeed) { this.initSeed = initSeed; }
    
    public D2DrlgCoord getLevelCoords() { return levelCoords; }
    public void setLevelCoords(D2DrlgCoord levelCoords) { this.levelCoords = levelCoords; }
    
    // 别名方法（对应 C++ 中的 pLevelCoords）
    public D2DrlgCoord getPLevelCoords() { return levelCoords; }
    public void setPLevelCoords(D2DrlgCoord pLevelCoords) { this.levelCoords = pLevelCoords; }
    
    public D2DrlgRoom getFirstRoomEx() { return firstRoomEx; }
    public void setFirstRoomEx(D2DrlgRoom firstRoomEx) { this.firstRoomEx = firstRoomEx; }
    
    public int getRooms() { return rooms; }
    public void setRooms(int rooms) { this.rooms = rooms; }
    
    public Object getPresetOrOutdoorsOrMaze() { return presetOrOutdoorsOrMaze; }
    public void setPresetOrOutdoorsOrMaze(Object presetOrOutdoorsOrMaze) { this.presetOrOutdoorsOrMaze = presetOrOutdoorsOrMaze; }
    
    public Object getCurrentMap() { return currentMap; }
    public void setCurrentMap(Object currentMap) { this.currentMap = currentMap; }
    
    // 别名方法（对应 C++ 中的 pCurrentMap）
    public Object getPCurrentMap() { return currentMap; }
    public void setPCurrentMap(Object currentMap) { this.currentMap = currentMap; }
    
    public int getCoordLists() { return coordLists; }
    public void setCoordLists(int coordLists) { this.coordLists = coordLists; }
    
    public D2DrlgTileInfoStrc[] getPTileInfo() { return pTileInfo; }
    public void setPTileInfo(D2DrlgTileInfoStrc[] pTileInfo) { this.pTileInfo = pTileInfo; }
    public D2DrlgTileInfoStrc getPTileInfo(int index) {
        return pTileInfo != null && index >= 0 && index < pTileInfo.length ? pTileInfo[index] : null;
    }
    
    public int getNTileInfoCount() { return nTileInfoCount; }
    public void setNTileInfoCount(int nTileInfoCount) { this.nTileInfoCount = nTileInfoCount; }
    
    // 别名方法（对应 C++ 中的 nTileInfo）
    public int getNTileInfo() { return nTileInfoCount; }
    public void setNTileInfo(int nTileInfo) { this.nTileInfoCount = nTileInfo; }
    
    public int[] getRoomCenterWarpX() { return roomCenterWarpX; }
    public void setRoomCenterWarpX(int[] roomCenterWarpX) { this.roomCenterWarpX = roomCenterWarpX; }
    
    // 别名方法（对应 C++ 中的 nRoom_Center_Warp_X）
    public int[] getNRoomCenterWarpX() { return roomCenterWarpX; }
    public void setNRoomCenterWarpX(int[] nRoomCenterWarpX) { this.roomCenterWarpX = nRoomCenterWarpX; }
    
    public int[] getRoomCenterWarpY() { return roomCenterWarpY; }
    public void setRoomCenterWarpY(int[] roomCenterWarpY) { this.roomCenterWarpY = roomCenterWarpY; }
    
    // 别名方法（对应 C++ 中的 nRoom_Center_Warp_Y）
    public int[] getNRoomCenterWarpY() { return roomCenterWarpY; }
    public void setNRoomCenterWarpY(int[] nRoomCenterWarpY) { this.roomCenterWarpY = nRoomCenterWarpY; }
    
    public int getRoomCoords() { return roomCoords; }
    public void setRoomCoords(int roomCoords) { this.roomCoords = roomCoords; }
    
    // 别名方法（对应 C++ 中的 nRoomCoords）
    public int getNRoomCoords() { return roomCoords; }
    public void setNRoomCoords(int nRoomCoords) { this.roomCoords = nRoomCoords; }
    
    public int[] getPJungleDefs() { return pJungleDefs; }
    public void setPJungleDefs(int[] pJungleDefs) { this.pJungleDefs = pJungleDefs; }
    
    public int getNJungleDefsCount() { return nJungleDefsCount; }
    public void setNJungleDefsCount(int nJungleDefsCount) { this.nJungleDefsCount = nJungleDefsCount; }
    
    public D2DrlgBuildStrc getBuild() { return build; }
    public void setBuild(D2DrlgBuildStrc build) { this.build = build; }
    
    // 别名方法（对应 C++ 中的 pBuild）
    public D2DrlgBuildStrc getPBuild() { return build; }
    public void setPBuild(D2DrlgBuildStrc pBuild) { this.build = pBuild; }
    
    public boolean isActive() { return active > 0; }
    public void setActive(boolean active) { this.active = active ? 1 : 0; }
    public int getActive() { return active; }
    public void setActive(int active) { this.active = Math.max(0, active); }
    
    public int getInactiveFrames() { return inactiveFrames; }
    public void setInactiveFrames(int inactiveFrames) { this.inactiveFrames = inactiveFrames; }
    
    public int[] getPresetMaps() { return presetMaps; }
    public void setPresetMaps(int[] presetMaps) { this.presetMaps = presetMaps; }
    
    public D2DrlgLevel getPNextLevel() { return nextLevel; }
    public void setPNextLevel(D2DrlgLevel nextLevel) { this.nextLevel = nextLevel; }
    
    // 便捷方法：获取预设信息（对应 C++ 中的 pLevel->pPreset）
    public D2DrlgPresetInfoStrc getPreset() {
        if (presetOrOutdoorsOrMaze instanceof D2DrlgPresetInfoStrc) {
            return (D2DrlgPresetInfoStrc) presetOrOutdoorsOrMaze;
        }
        return null;
    }
    
    public void setPreset(D2DrlgPresetInfoStrc preset) {
        this.presetOrOutdoorsOrMaze = preset;
    }
    
    // 便捷方法：获取户外信息（对应 C++ 中的 pLevel->pOutdoors）
    public D2DrlgOutdoorInfoStrc getOutdoors() {
        if (presetOrOutdoorsOrMaze instanceof D2DrlgOutdoorInfoStrc) {
            return (D2DrlgOutdoorInfoStrc) presetOrOutdoorsOrMaze;
        }
        return null;
    }
    
    public void setOutdoors(D2DrlgOutdoorInfoStrc outdoors) {
        this.presetOrOutdoorsOrMaze = outdoors;
    }
}
