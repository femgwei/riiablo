package com.d2moo.common.drlg;

/**
 * Drlg 文件结构
 * 对应 C++ 结构：D2DrlgFileStrc
 * 
 * 注意：此结构用于存储 DS1 文件解析后的数据
 */
public class D2DrlgFileStrc {
    private int nSubstMethod;                    // 0x00 D2C_DrlgTileSubstitionMethod
    private Object pDS1File;                      // 0x04 原始 DS1 文件数据
    private int unk0x08;                         // 0x08 未知字段
    private int nWidth;                          // 0x0C 宽度
    private int nHeight;                         // 0x10 高度
    private int nWallLayers;                     // 0x14 墙壁层数
    private int nFloorLayers;                    // 0x18 地板层数
    private Object[] pTileTypeLayer;             // 0x1C 瓦片类型层数组 [DRLG_MAX_WALL_LAYERS = 4]
    private Object[] pWallLayer;                 // 0x2C 墙壁层数组 [DRLG_MAX_WALL_LAYERS = 4]
    private Object[] pFloorLayer;                // 0x3C 地板层数组 [DRLG_MAX_FLOOR_LAYERS = 2]
    private Object pShadowLayer;                 // 0x44 阴影层
    private Object pSubstGroupTags;              // 0x48 替换组标签
    private int nSubstGroups;                    // 0x4C 替换组数量（原始游戏中称为 nClusters）
    private D2DrlgSubstGroupStrc[] pSubstGroups; // 0x50 D2DrlgSubstGroupStrc* 替换组数组
    private D2PresetUnit pPresetUnit;           // 0x54 预设单位链表
    private D2DrlgFileStrc pNext;                // 0x58 下一个文件
    
    public D2DrlgFileStrc() {
        this.pTileTypeLayer = new Object[4]; // DRLG_MAX_WALL_LAYERS = 4
        this.pWallLayer = new Object[4];     // DRLG_MAX_WALL_LAYERS = 4
        this.pFloorLayer = new Object[2];    // DRLG_MAX_FLOOR_LAYERS = 2
        this.nSubstMethod = 0;
        this.nWidth = 0;
        this.nHeight = 0;
        this.nWallLayers = 0;
        this.nFloorLayers = 0;
        this.nSubstGroups = 0;
    }
    
    // Getters and Setters
    public int getNSubstMethod() {
        return nSubstMethod;
    }
    
    public void setNSubstMethod(int nSubstMethod) {
        this.nSubstMethod = nSubstMethod;
    }
    
    public Object getPDS1File() {
        return pDS1File;
    }
    
    public void setPDS1File(Object pDS1File) {
        this.pDS1File = pDS1File;
    }
    
    public int getUnk0x08() {
        return unk0x08;
    }
    
    public void setUnk0x08(int unk0x08) {
        this.unk0x08 = unk0x08;
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
    
    public int getNWallLayers() {
        return nWallLayers;
    }
    
    public void setNWallLayers(int nWallLayers) {
        this.nWallLayers = nWallLayers;
    }
    
    public int getNFloorLayers() {
        return nFloorLayers;
    }
    
    public void setNFloorLayers(int nFloorLayers) {
        this.nFloorLayers = nFloorLayers;
    }
    
    public Object[] getPTileTypeLayer() {
        return pTileTypeLayer;
    }
    
    public void setPTileTypeLayer(Object[] pTileTypeLayer) {
        this.pTileTypeLayer = pTileTypeLayer;
    }
    
    public Object getPTileTypeLayer(int index) {
        if (pTileTypeLayer != null && index >= 0 && index < pTileTypeLayer.length) {
            return pTileTypeLayer[index];
        }
        return null;
    }
    
    public void setPTileTypeLayer(int index, Object layer) {
        if (pTileTypeLayer != null && index >= 0 && index < pTileTypeLayer.length) {
            pTileTypeLayer[index] = layer;
        }
    }
    
    public Object[] getPWallLayer() {
        return pWallLayer;
    }
    
    public void setPWallLayer(Object[] pWallLayer) {
        this.pWallLayer = pWallLayer;
    }
    
    public Object getPWallLayer(int index) {
        if (pWallLayer != null && index >= 0 && index < pWallLayer.length) {
            return pWallLayer[index];
        }
        return null;
    }
    
    public void setPWallLayer(int index, Object layer) {
        if (pWallLayer != null && index >= 0 && index < pWallLayer.length) {
            pWallLayer[index] = layer;
        }
    }
    
    public Object[] getPFloorLayer() {
        return pFloorLayer;
    }
    
    public void setPFloorLayer(Object[] pFloorLayer) {
        this.pFloorLayer = pFloorLayer;
    }
    
    public Object getPFloorLayer(int index) {
        if (pFloorLayer != null && index >= 0 && index < pFloorLayer.length) {
            return pFloorLayer[index];
        }
        return null;
    }
    
    public void setPFloorLayer(int index, Object layer) {
        if (pFloorLayer != null && index >= 0 && index < pFloorLayer.length) {
            pFloorLayer[index] = layer;
        }
    }
    
    public Object getPShadowLayer() {
        return pShadowLayer;
    }
    
    public void setPShadowLayer(Object pShadowLayer) {
        this.pShadowLayer = pShadowLayer;
    }
    
    public Object getPSubstGroupTags() {
        return pSubstGroupTags;
    }
    
    public void setPSubstGroupTags(Object pSubstGroupTags) {
        this.pSubstGroupTags = pSubstGroupTags;
    }
    
    public int getNSubstGroups() {
        return nSubstGroups;
    }
    
    public void setNSubstGroups(int nSubstGroups) {
        this.nSubstGroups = nSubstGroups;
    }
    
    public D2DrlgSubstGroupStrc[] getPSubstGroups() {
        return pSubstGroups;
    }
    
    public void setPSubstGroups(D2DrlgSubstGroupStrc[] pSubstGroups) {
        this.pSubstGroups = pSubstGroups;
    }
    
    public D2PresetUnit getPPresetUnit() {
        return pPresetUnit;
    }
    
    public void setPPresetUnit(D2PresetUnit pPresetUnit) {
        this.pPresetUnit = pPresetUnit;
    }
    
    public D2DrlgFileStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2DrlgFileStrc pNext) {
        this.pNext = pNext;
    }
}
