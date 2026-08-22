package com.d2moo.common.drlg;

import com.d2moo.common.datatbls.D2LvlPrestTxt;

/**
 * Drlg 地图结构
 * 对应 C++ 结构：D2DrlgMapStrc
 */
public class D2DrlgMapStrc {
    private int nLevelPrest;                    // 0x00 关卡预设ID
    private int nPickedFile;                    // 0x04 选中的文件索引
    private D2LvlPrestTxt pLvlPrestTxtRecord;   // 0x08 D2LvlPrestTxt* 关卡预设文本记录
    private D2DrlgFileStrc pFile;                // 0x0C D2DrlgFileStrc* 文件结构
    private D2DrlgCoord pDrlgCoord;             // 0x10 坐标信息
    private boolean bHasInfo;                    // 0x20 是否有信息
    private D2DrlgGridStrc pMapGrid;            // 0x24 地图网格
    private D2PresetUnit pPresetUnit;           // 0x38 预设单位链表
    private boolean bInited;                    // 0x3C 是否已初始化
    private int nPops;                          // 0x40 人口数量
    private int[] pPopsIndex;                   // 0x44 人口索引数组
    private int[] pPopsSubIndex;                // 0x48 人口子索引数组
    private int[] pPopsOrientation;             // 0x4C 人口方向数组
    private D2DrlgCoord[] pPopsLocation;        // 0x50 人口位置数组
    private D2DrlgMapStrc pNext;                // 0x54 下一个地图
    
    public D2DrlgMapStrc() {
        this.pDrlgCoord = new D2DrlgCoord();
        this.pMapGrid = new D2DrlgGridStrc();
        this.nLevelPrest = 0;
        this.nPickedFile = 0;
        this.bHasInfo = false;
        this.bInited = false;
        this.nPops = 0;
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
    
    public D2LvlPrestTxt getPLvlPrestTxtRecord() {
        return pLvlPrestTxtRecord;
    }
    
    public void setPLvlPrestTxtRecord(D2LvlPrestTxt pLvlPrestTxtRecord) {
        this.pLvlPrestTxtRecord = pLvlPrestTxtRecord;
    }
    
    public D2DrlgFileStrc getPFile() {
        return pFile;
    }
    
    public void setPFile(D2DrlgFileStrc pFile) {
        this.pFile = pFile;
    }
    
    public D2DrlgCoord getPDrlgCoord() {
        return pDrlgCoord;
    }
    
    public void setPDrlgCoord(D2DrlgCoord pDrlgCoord) {
        this.pDrlgCoord = pDrlgCoord;
    }
    
    public boolean isBHasInfo() {
        return bHasInfo;
    }
    
    public void setBHasInfo(boolean bHasInfo) {
        this.bHasInfo = bHasInfo;
    }
    
    public D2DrlgGridStrc getPMapGrid() {
        return pMapGrid;
    }
    
    public void setPMapGrid(D2DrlgGridStrc pMapGrid) {
        this.pMapGrid = pMapGrid;
    }
    
    public D2PresetUnit getPPresetUnit() {
        return pPresetUnit;
    }
    
    public void setPPresetUnit(D2PresetUnit pPresetUnit) {
        this.pPresetUnit = pPresetUnit;
    }
    
    public boolean isBInited() {
        return bInited;
    }
    
    public void setBInited(boolean bInited) {
        this.bInited = bInited;
    }
    
    public int getNPops() {
        return nPops;
    }
    
    public void setNPops(int nPops) {
        this.nPops = nPops;
    }
    
    public int[] getPPopsIndex() {
        return pPopsIndex;
    }
    
    public void setPPopsIndex(int[] pPopsIndex) {
        this.pPopsIndex = pPopsIndex;
    }
    
    public int[] getPPopsSubIndex() {
        return pPopsSubIndex;
    }
    
    public void setPPopsSubIndex(int[] pPopsSubIndex) {
        this.pPopsSubIndex = pPopsSubIndex;
    }
    
    public int[] getPPopsOrientation() {
        return pPopsOrientation;
    }
    
    public void setPPopsOrientation(int[] pPopsOrientation) {
        this.pPopsOrientation = pPopsOrientation;
    }
    
    public D2DrlgCoord[] getPPopsLocation() {
        return pPopsLocation;
    }
    
    public void setPPopsLocation(D2DrlgCoord[] pPopsLocation) {
        this.pPopsLocation = pPopsLocation;
    }
    
    public D2DrlgMapStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2DrlgMapStrc pNext) {
        this.pNext = pNext;
    }
}
