package com.d2moo.common.drlg;

/**
 * Drlg 户外房间结构
 * 对应 C++ 结构：D2DrlgOutdoorRoomStrc
 */
public class D2DrlgOutdoorRoomStrc {
    private D2DrlgGridStrc pTileTypeGrid;   // 0x00 瓦片类型网格（方向网格）
    private D2DrlgGridStrc pWallGrid;       // 0x14 墙壁网格
    private D2DrlgGridStrc pFloorGrid;       // 0x28 地板网格
    private D2DrlgGridStrc pDirtPathGrid;    // 0x3C 土路网格
    private D2DrlgVertexStrc pVertex;       // 0x50 顶点链表
    private int dwFlags;                    // 0x54 标志
    private int dwFlagsEx;                  // 0x58 扩展标志
    private int unk0x5C;                    // 0x5C 未知字段
    private int unk0x60;                    // 0x60 未知字段
    private int nSubType;                   // 0x64 子类型
    private int nSubTheme;                  // 0x68 子主题
    private int nSubThemePicked;           // 0x6C 选中的子主题
    
    public D2DrlgOutdoorRoomStrc() {
        this.pTileTypeGrid = new D2DrlgGridStrc();
        this.pWallGrid = new D2DrlgGridStrc();
        this.pFloorGrid = new D2DrlgGridStrc();
        this.pDirtPathGrid = new D2DrlgGridStrc();
        this.dwFlags = 0;
        this.dwFlagsEx = 0;
        this.nSubType = 0;
        this.nSubTheme = 0;
        this.nSubThemePicked = 0;
    }
    
    // Getters and Setters
    public D2DrlgGridStrc getPTileTypeGrid() {
        return pTileTypeGrid;
    }
    
    public void setPTileTypeGrid(D2DrlgGridStrc pTileTypeGrid) {
        this.pTileTypeGrid = pTileTypeGrid;
    }
    
    public D2DrlgGridStrc getPWallGrid() {
        return pWallGrid;
    }
    
    public void setPWallGrid(D2DrlgGridStrc pWallGrid) {
        this.pWallGrid = pWallGrid;
    }
    
    public D2DrlgGridStrc getPFloorGrid() {
        return pFloorGrid;
    }
    
    public void setPFloorGrid(D2DrlgGridStrc pFloorGrid) {
        this.pFloorGrid = pFloorGrid;
    }
    
    public D2DrlgGridStrc getPDirtPathGrid() {
        return pDirtPathGrid;
    }
    
    public void setPDirtPathGrid(D2DrlgGridStrc pDirtPathGrid) {
        this.pDirtPathGrid = pDirtPathGrid;
    }
    
    public D2DrlgVertexStrc getPVertex() {
        return pVertex;
    }
    
    public void setPVertex(D2DrlgVertexStrc pVertex) {
        this.pVertex = pVertex;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public int getDwFlagsEx() {
        return dwFlagsEx;
    }
    
    public void setDwFlagsEx(int dwFlagsEx) {
        this.dwFlagsEx = dwFlagsEx;
    }
    
    public int getUnk0x5C() {
        return unk0x5C;
    }
    
    public void setUnk0x5C(int unk0x5C) {
        this.unk0x5C = unk0x5C;
    }
    
    public int getUnk0x60() {
        return unk0x60;
    }
    
    public void setUnk0x60(int unk0x60) {
        this.unk0x60 = unk0x60;
    }
    
    public int getNSubType() {
        return nSubType;
    }
    
    public void setNSubType(int nSubType) {
        this.nSubType = nSubType;
    }
    
    public int getNSubTheme() {
        return nSubTheme;
    }
    
    public void setNSubTheme(int nSubTheme) {
        this.nSubTheme = nSubTheme;
    }
    
    public int getNSubThemePicked() {
        return nSubThemePicked;
    }
    
    public void setNSubThemePicked(int nSubThemePicked) {
        this.nSubThemePicked = nSubThemePicked;
    }
}
