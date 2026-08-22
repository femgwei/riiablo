package com.d2moo.common.drlg;

/**
 * 迷宫记录结构
 * 对应 C++ 结构：D2LvlMazeTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 * 这里提供基础结构定义，实际数据需要从数据表加载
 */
public class D2MazeRecord {
    private int dwLevelId;              // 0x00 关卡ID
    private int[] dwRooms;               // 0x04 房间数量数组 [3]（普通、噩梦、地狱）
    private int dwSizeX;                // 0x10 X 尺寸
    private int dwSizeY;                // 0x14 Y 尺寸
    private int dwMerge;                // 0x18 合并标志
    
    public D2MazeRecord() {
        this.dwRooms = new int[3];
    }
    
    // Getters and Setters
    public int getDwLevelId() {
        return dwLevelId;
    }
    
    public void setDwLevelId(int dwLevelId) {
        this.dwLevelId = dwLevelId;
    }
    
    public int[] getDwRooms() {
        return dwRooms;
    }
    
    public void setDwRooms(int[] dwRooms) {
        this.dwRooms = dwRooms;
    }
    
    public int getDwRooms(int index) {
        if (index >= 0 && index < dwRooms.length) {
            return dwRooms[index];
        }
        return 0;
    }
    
    public void setDwRooms(int index, int value) {
        if (index >= 0 && index < dwRooms.length) {
            dwRooms[index] = value;
        }
    }
    
    public int getDwSizeX() {
        return dwSizeX;
    }
    
    public void setDwSizeX(int dwSizeX) {
        this.dwSizeX = dwSizeX;
    }
    
    public int getDwSizeY() {
        return dwSizeY;
    }
    
    public void setDwSizeY(int dwSizeY) {
        this.dwSizeY = dwSizeY;
    }
    
    public int getDwMerge() {
        return dwMerge;
    }
    
    public void setDwMerge(int dwMerge) {
        this.dwMerge = dwMerge;
    }
}
