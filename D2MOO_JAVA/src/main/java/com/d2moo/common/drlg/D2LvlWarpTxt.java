package com.d2moo.common.drlg;

/**
 * 关卡传送点文本结构
 * 对应 C++ 结构：D2LvlWarpTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 * 这里提供基础结构定义，实际数据需要从数据表加载
 */
public class D2LvlWarpTxt {
    private int dwLevelId;              // 0x00 关卡ID
    private int dwSelectX;               // 0x04 选择X坐标
    private int dwSelectY;               // 0x08 选择Y坐标
    private int dwSelectDX;              // 0x0C 选择宽度
    private int dwSelectDY;              // 0x10 选择高度
    private int dwExitWalkX;             // 0x14 出口行走X坐标
    private int dwExitWalkY;             // 0x18 出口行走Y坐标
    private int dwOffsetX;               // 0x1C 偏移X
    private int dwOffsetY;               // 0x20 偏移Y
    private int dwLitVersion;            // 0x24 光照版本
    private int dwTiles;                 // 0x28 瓦片数量
    private String szDirection;          // 0x30 方向字符串 [4]
    
    public D2LvlWarpTxt() {
        this.dwLevelId = 0;
        this.szDirection = "";
    }
    
    // Getters and Setters
    public int getDwLevelId() {
        return dwLevelId;
    }
    
    public void setDwLevelId(int dwLevelId) {
        this.dwLevelId = dwLevelId;
    }
    
    public int getDwSelectX() {
        return dwSelectX;
    }
    
    public void setDwSelectX(int dwSelectX) {
        this.dwSelectX = dwSelectX;
    }
    
    public int getDwSelectY() {
        return dwSelectY;
    }
    
    public void setDwSelectY(int dwSelectY) {
        this.dwSelectY = dwSelectY;
    }
    
    public int getDwSelectDX() {
        return dwSelectDX;
    }
    
    public void setDwSelectDX(int dwSelectDX) {
        this.dwSelectDX = dwSelectDX;
    }
    
    public int getDwSelectDY() {
        return dwSelectDY;
    }
    
    public void setDwSelectDY(int dwSelectDY) {
        this.dwSelectDY = dwSelectDY;
    }
    
    public int getDwExitWalkX() {
        return dwExitWalkX;
    }
    
    public void setDwExitWalkX(int dwExitWalkX) {
        this.dwExitWalkX = dwExitWalkX;
    }
    
    public int getDwExitWalkY() {
        return dwExitWalkY;
    }
    
    public void setDwExitWalkY(int dwExitWalkY) {
        this.dwExitWalkY = dwExitWalkY;
    }
    
    public int getDwOffsetX() {
        return dwOffsetX;
    }
    
    public void setDwOffsetX(int dwOffsetX) {
        this.dwOffsetX = dwOffsetX;
    }
    
    public int getDwOffsetY() {
        return dwOffsetY;
    }
    
    public void setDwOffsetY(int dwOffsetY) {
        this.dwOffsetY = dwOffsetY;
    }
    
    public int getDwLitVersion() {
        return dwLitVersion;
    }
    
    public void setDwLitVersion(int dwLitVersion) {
        this.dwLitVersion = dwLitVersion;
    }
    
    public int getDwTiles() {
        return dwTiles;
    }
    
    public void setDwTiles(int dwTiles) {
        this.dwTiles = dwTiles;
    }
    
    public String getSzDirection() {
        return szDirection;
    }
    
    public void setSzDirection(String szDirection) {
        this.szDirection = szDirection;
    }
}
