package com.d2moo.common.datatbls;

import com.d2moo.common.drlg.D2DrlgFileStrc;
import com.d2moo.common.drlg.D2DrlgGridStrc;

/**
 * 关卡子文本结构
 * 对应 C++ 结构：D2LvlSubTxt
 */
public class D2LvlSubTxt {
    private int dwType;                     // 0x00 D2C_LevelSubstitutionType
    private String szFile;                  // 0x04 文件名（60字符）
    private int dwCheckAll;                 // 0x40 检查所有标志
    private int dwBordType;                 // 0x44 边界类型（控制替换频率）
    private int dwDt1Mask;                  // 0x48 DT1掩码
    private int dwGridSize;                 // 0x4C 网格大小（替换瓦片的簇大小）
    private D2DrlgFileStrc pDrlgFile;       // 0x50 Drlg文件指针
    private D2DrlgGridStrc[] pTileTypeGrid; // 0x54 瓦片类型网格[4]
    private D2DrlgGridStrc[] pWallGrid;     // 0xA4 墙壁网格[4]
    private D2DrlgGridStrc pFloorGrid;      // 0xF4 地板网格
    private D2DrlgGridStrc pShadowGrid;     // 0x108 阴影网格
    private int[] nProb;                    // 0x11C 概率数组[5]
    private int[] nTrials;                  // 0x130 尝试次数数组[5]
    private int[] nMax;                     // 0x144 最大替换次数数组[5]
    private int dwExpansion;                // 0x158 扩展标志
    
    public D2LvlSubTxt() {
        this.pTileTypeGrid = new D2DrlgGridStrc[4];
        this.pWallGrid = new D2DrlgGridStrc[4];
        for (int i = 0; i < 4; i++) {
            this.pTileTypeGrid[i] = new D2DrlgGridStrc();
            this.pWallGrid[i] = new D2DrlgGridStrc();
        }
        this.pFloorGrid = new D2DrlgGridStrc();
        this.pShadowGrid = new D2DrlgGridStrc();
        this.nProb = new int[5];
        this.nTrials = new int[5];
        this.nMax = new int[5];
    }
    
    // Getters and Setters
    public int getDwType() {
        return dwType;
    }
    
    public void setDwType(int dwType) {
        this.dwType = dwType;
    }
    
    public String getSzFile() {
        return szFile;
    }
    
    public void setSzFile(String szFile) {
        this.szFile = szFile;
    }
    
    public int getDwCheckAll() {
        return dwCheckAll;
    }
    
    public void setDwCheckAll(int dwCheckAll) {
        this.dwCheckAll = dwCheckAll;
    }
    
    public int getDwBordType() {
        return dwBordType;
    }
    
    public void setDwBordType(int dwBordType) {
        this.dwBordType = dwBordType;
    }
    
    public int getDwDt1Mask() {
        return dwDt1Mask;
    }
    
    public void setDwDt1Mask(int dwDt1Mask) {
        this.dwDt1Mask = dwDt1Mask;
    }
    
    public int getDwGridSize() {
        return dwGridSize;
    }
    
    public void setDwGridSize(int dwGridSize) {
        this.dwGridSize = dwGridSize;
    }
    
    public D2DrlgFileStrc getPDrlgFile() {
        return pDrlgFile;
    }
    
    public void setPDrlgFile(D2DrlgFileStrc pDrlgFile) {
        this.pDrlgFile = pDrlgFile;
    }
    
    public D2DrlgGridStrc getPTileTypeGrid(int index) {
        if (index < 0 || index >= pTileTypeGrid.length) {
            return null;
        }
        return pTileTypeGrid[index];
    }
    
    public void setPTileTypeGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pTileTypeGrid.length) {
            pTileTypeGrid[index] = grid;
        }
    }
    
    public D2DrlgGridStrc getPWallGrid(int index) {
        if (index < 0 || index >= pWallGrid.length) {
            return null;
        }
        return pWallGrid[index];
    }
    
    public void setPWallGrid(int index, D2DrlgGridStrc grid) {
        if (index >= 0 && index < pWallGrid.length) {
            pWallGrid[index] = grid;
        }
    }
    
    public D2DrlgGridStrc getPFloorGrid() {
        return pFloorGrid;
    }
    
    public void setPFloorGrid(D2DrlgGridStrc pFloorGrid) {
        this.pFloorGrid = pFloorGrid;
    }
    
    public D2DrlgGridStrc getPShadowGrid() {
        return pShadowGrid;
    }
    
    public void setPShadowGrid(D2DrlgGridStrc pShadowGrid) {
        this.pShadowGrid = pShadowGrid;
    }
    
    public int getNProb(int index) {
        if (index < 0 || index >= nProb.length) {
            return 0;
        }
        return nProb[index];
    }
    
    public int[] getNProb() {
        return nProb;
    }
    
    public void setNProb(int index, int value) {
        if (index >= 0 && index < nProb.length) {
            nProb[index] = value;
        }
    }
    
    public int getNTrials(int index) {
        if (index < 0 || index >= nTrials.length) {
            return 0;
        }
        return nTrials[index];
    }
    
    public int[] getNTrials() {
        return nTrials;
    }
    
    public void setNTrials(int index, int value) {
        if (index >= 0 && index < nTrials.length) {
            nTrials[index] = value;
        }
    }
    
    public int getNMax(int index) {
        if (index < 0 || index >= nMax.length) {
            return 0;
        }
        return nMax[index];
    }
    
    public int[] getNMax() {
        return nMax;
    }
    
    public void setNMax(int index, int value) {
        if (index >= 0 && index < nMax.length) {
            nMax[index] = value;
        }
    }
    
    public int getDwExpansion() {
        return dwExpansion;
    }
    
    public void setDwExpansion(int dwExpansion) {
        this.dwExpansion = dwExpansion;
    }
}
