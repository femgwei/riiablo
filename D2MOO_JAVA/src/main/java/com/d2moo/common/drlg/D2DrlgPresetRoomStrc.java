package com.d2moo.common.drlg;

/**
 * Drlg 预设房间结构
 * 对应 C++ 结构：D2DrlgPresetRoomStrc
 */
public class D2DrlgPresetRoomStrc {
    private int nLevelPrest;                    // 0x00 关卡预设ID
    private int nPickedFile;                     // 0x04 选中的文件
    private D2DrlgMapStrc pMap;                 // 0x08 D2DrlgMapStrc* 地图结构
    private int dwFlags;                        // 0x0C 标志（D2DrlgPresetRoomFlags）
    private D2DrlgGridStrc[] pWallGrid;         // 0x10 墙壁网格数组 [4]
    private D2DrlgGridStrc[] pTileTypeGrid;     // 0x60 瓦片类型网格数组 [4]（方向网格）
    private D2DrlgGridStrc[] pFloorGrid;        // 0xB0 地板网格数组 [2]
    private D2DrlgGridStrc pCellGrid;           // 0xD8 单元格网格
    private D2DrlgGridStrc pMazeGrid;           // 0xEC 迷宫网格（指针）
    private D2Coord[] pTombStoneTiles;          // 0xF0 D2CoordStrc* 墓碑瓦片
    private int nTombStoneTiles;                // 0xF4 墓碑瓦片数量
    
    public D2DrlgPresetRoomStrc() {
        this.pWallGrid = new D2DrlgGridStrc[4];
        this.pTileTypeGrid = new D2DrlgGridStrc[4];
        this.pFloorGrid = new D2DrlgGridStrc[2];
        this.pCellGrid = new D2DrlgGridStrc();
        
        // 初始化数组
        for (int i = 0; i < 4; i++) {
            this.pWallGrid[i] = new D2DrlgGridStrc();
            this.pTileTypeGrid[i] = new D2DrlgGridStrc();
        }
        for (int i = 0; i < 2; i++) {
            this.pFloorGrid[i] = new D2DrlgGridStrc();
        }
        
        this.nLevelPrest = 0;
        this.nPickedFile = 0;
        this.dwFlags = 0;
        this.nTombStoneTiles = 0;
    }
    
    // Getters and Setters
    public int getNLevelPrest() {
        return nLevelPrest;
    }
    
    public void setNLevelPrest(int nLevelPrest) {
        this.nLevelPrest = nLevelPrest;
    }
    
    public int getNPickedFile() {
        return nPickedFile;
    }
    
    public void setNPickedFile(int nPickedFile) {
        this.nPickedFile = nPickedFile;
    }
    
    public D2DrlgMapStrc getPMap() {
        return pMap;
    }
    
    public void setPMap(D2DrlgMapStrc pMap) {
        this.pMap = pMap;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public D2DrlgGridStrc[] getPWallGrid() {
        return pWallGrid;
    }
    
    public void setPWallGrid(D2DrlgGridStrc[] pWallGrid) {
        this.pWallGrid = pWallGrid;
    }
    
    public D2DrlgGridStrc getPWallGrid(int index) {
        if (index >= 0 && index < pWallGrid.length) {
            return pWallGrid[index];
        }
        return null;
    }
    
    public void setPWallGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pWallGrid.length) {
            pWallGrid[index] = grid;
        }
    }
    
    public D2DrlgGridStrc[] getPTileTypeGrid() {
        return pTileTypeGrid;
    }
    
    public void setPTileTypeGrid(D2DrlgGridStrc[] pTileTypeGrid) {
        this.pTileTypeGrid = pTileTypeGrid;
    }
    
    public D2DrlgGridStrc getPTileTypeGrid(int index) {
        if (index >= 0 && index < pTileTypeGrid.length) {
            return pTileTypeGrid[index];
        }
        return null;
    }
    
    public void setPTileTypeGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pTileTypeGrid.length) {
            pTileTypeGrid[index] = grid;
        }
    }
    
    public D2DrlgGridStrc[] getPFloorGrid() {
        return pFloorGrid;
    }
    
    public void setPFloorGrid(D2DrlgGridStrc[] pFloorGrid) {
        this.pFloorGrid = pFloorGrid;
    }
    
    public D2DrlgGridStrc getPFloorGrid(int index) {
        if (index >= 0 && index < pFloorGrid.length) {
            return pFloorGrid[index];
        }
        return null;
    }
    
    public void setPFloorGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pFloorGrid.length) {
            pFloorGrid[index] = grid;
        }
    }
    
    public D2DrlgGridStrc getPCellGrid() {
        return pCellGrid;
    }
    
    public void setPCellGrid(D2DrlgGridStrc pCellGrid) {
        this.pCellGrid = pCellGrid;
    }
    
    public D2DrlgGridStrc getPMazeGrid() {
        return pMazeGrid;
    }
    
    public void setPMazeGrid(D2DrlgGridStrc pMazeGrid) {
        this.pMazeGrid = pMazeGrid;
    }
    
    public D2Coord[] getPTombStoneTiles() {
        return pTombStoneTiles;
    }
    
    public void setPTombStoneTiles(D2Coord[] pTombStoneTiles) {
        this.pTombStoneTiles = pTombStoneTiles;
        if (nTombStoneTiles > (pTombStoneTiles != null ? pTombStoneTiles.length : 0)) {
            nTombStoneTiles = pTombStoneTiles != null ? pTombStoneTiles.length : 0;
        }
    }
    
    public int getNTombStoneTiles() {
        return nTombStoneTiles;
    }
    
    public void setNTombStoneTiles(int nTombStoneTiles) {
        this.nTombStoneTiles = Math.max(0, Math.min(nTombStoneTiles,
                pTombStoneTiles != null ? pTombStoneTiles.length : 0));
    }
}
