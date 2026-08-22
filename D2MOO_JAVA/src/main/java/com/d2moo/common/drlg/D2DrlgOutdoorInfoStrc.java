package com.d2moo.common.drlg;

/**
 * Drlg 户外信息结构
 * 对应 C++ 结构：D2DrlgOutdoorInfoStrc
 */
public class D2DrlgOutdoorInfoStrc {
    private int dwFlags;                        // 0x00 D2C_OutDoorInfoFlags
    private D2DrlgGridStrc[] pGrid;             // 0x04 网格数组 [4]
    private int nWidth;                          // 0x54 宽度
    private int nHeight;                        // 0x58 高度
    private int nGridWidth;                     // 0x5C 网格宽度
    private int nGridHeight;                    // 0x60 网格高度
    private D2DrlgVertexStrc pVertex;           // 0x64 顶点链表
    private D2DrlgVertexStrc[] pPathStarts;    // 0x68 路径起点数组 [6]
    private D2DrlgVertexStrc[] pVertices;      // 0x80 顶点数组 [24]
    private int nVertices;                      // 0x260 顶点数量
    private D2DrlgOrth pRoomData;               // 0x264 房间数据
    
    public D2DrlgOutdoorInfoStrc() {
        this.pGrid = new D2DrlgGridStrc[4];
        this.pPathStarts = new D2DrlgVertexStrc[6];
        this.pVertices = new D2DrlgVertexStrc[24];
        
        // 初始化网格数组
        for (int i = 0; i < 4; i++) {
            this.pGrid[i] = new D2DrlgGridStrc();
        }
        
        // 初始化顶点数组
        for (int i = 0; i < 24; i++) {
            this.pVertices[i] = new D2DrlgVertexStrc();
        }
        
        this.dwFlags = 0;
        this.nVertices = 0;
    }
    
    // Getters and Setters
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public D2DrlgGridStrc[] getPGrid() {
        return pGrid;
    }
    
    public void setPGrid(D2DrlgGridStrc[] pGrid) {
        this.pGrid = pGrid;
    }
    
    public D2DrlgGridStrc getPGrid(int index) {
        if (index >= 0 && index < pGrid.length) {
            return pGrid[index];
        }
        return null;
    }
    
    public void setPGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pGrid.length) {
            pGrid[index] = grid;
        }
    }
    
    public int getNWidth() {
        return nWidth;
    }
    
    public void setNWidth(int nWidth) {
        this.nWidth = nWidth;
    }
    
    public int getNHeight() {
        return nHeight;
    }
    
    public void setNHeight(int nHeight) {
        this.nHeight = nHeight;
    }
    
    public int getNGridWidth() {
        return nGridWidth;
    }
    
    public void setNGridWidth(int nGridWidth) {
        this.nGridWidth = nGridWidth;
    }
    
    public int getNGridHeight() {
        return nGridHeight;
    }
    
    public void setNGridHeight(int nGridHeight) {
        this.nGridHeight = nGridHeight;
    }
    
    public D2DrlgVertexStrc getPVertex() {
        return pVertex;
    }
    
    public void setPVertex(D2DrlgVertexStrc pVertex) {
        this.pVertex = pVertex;
    }
    
    public D2DrlgVertexStrc[] getPPathStarts() {
        return pPathStarts;
    }
    
    public void setPPathStarts(D2DrlgVertexStrc[] pPathStarts) {
        this.pPathStarts = pPathStarts;
    }
    
    public D2DrlgVertexStrc getPPathStarts(int index) {
        if (index >= 0 && index < pPathStarts.length) {
            return pPathStarts[index];
        }
        return null;
    }
    
    public void setPPathStarts(int index, D2DrlgVertexStrc vertex) {
        if (index >= 0 && index < pPathStarts.length) {
            pPathStarts[index] = vertex;
        }
    }
    
    public D2DrlgVertexStrc[] getPVertices() {
        return pVertices;
    }
    
    public void setPVertices(D2DrlgVertexStrc[] pVertices) {
        this.pVertices = pVertices;
    }
    
    public D2DrlgVertexStrc getPVertices(int index) {
        if (index >= 0 && index < pVertices.length) {
            return pVertices[index];
        }
        return null;
    }
    
    public void setPVertices(int index, D2DrlgVertexStrc vertex) {
        if (index >= 0 && index < pVertices.length) {
            pVertices[index] = vertex;
        }
    }
    
    public int getNVertices() {
        return nVertices;
    }
    
    public void setNVertices(int nVertices) {
        this.nVertices = nVertices;
    }
    
    public D2DrlgOrth getPRoomData() {
        return pRoomData;
    }
    
    public void setPRoomData(D2DrlgOrth pRoomData) {
        this.pRoomData = pRoomData;
    }
}
