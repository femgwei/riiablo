package com.d2moo.common.datatbls;

/**
 * 关卡预设文本结构
 * 对应 C++ 结构：D2LvlPrestTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 */
public class D2LvlPrestTxt {
    private int dwDef;                  // 定义ID
    private int dwLevelId;               // 关卡ID
    private int dwPopulate;              // 填充标志
    private int dwLogicals;              // 逻辑标志
    private int dwOutdoors;              // 户外标志
    private int dwAnimate;               // 动画标志
    private int dwKillEdge;              // 边缘清除标志
    private int dwFillBlanks;            // 填充空白标志
    private int dwExpansion;             // 0x20 扩展标志
    private int nAnimSpeed;              // 0x24 动画速度
    private int dwSizeX;                 // 0x28 X 尺寸
    private int dwSizeY;                 // 0x2C Y 尺寸
    private int dwAutoMap;               // 0x30 自动地图标志
    private int dwScan;                  // 0x34 扫描标志
    private int dwPops;                  // 0x38 弹出标志
    private int dwPopPad;                // 0x3C 弹出填充
    private int dwFiles;                 // 0x40 文件数量
    private String[] szFile;              // 0x44 文件名数组 [6][60]
    private int dwFileId;                // 文件ID
    private int dwDt1Mask;               // DT1 掩码
    private int dwBeta;                  // Beta 标志
    
    public D2LvlPrestTxt() {
        this.dwDef = 0;
        this.dwLevelId = 0;
        this.szFile = new String[6];
    }
    
    // Getters and Setters
    public int getDwDef() {
        return dwDef;
    }
    
    public void setDwDef(int dwDef) {
        this.dwDef = dwDef;
    }
    
    public int getDwLevelId() {
        return dwLevelId;
    }
    
    public void setDwLevelId(int dwLevelId) {
        this.dwLevelId = dwLevelId;
    }
    
    public int getDwPopulate() {
        return dwPopulate;
    }
    
    public void setDwPopulate(int dwPopulate) {
        this.dwPopulate = dwPopulate;
    }
    
    public int getDwLogicals() {
        return dwLogicals;
    }
    
    public void setDwLogicals(int dwLogicals) {
        this.dwLogicals = dwLogicals;
    }
    
    public int getDwOutdoors() {
        return dwOutdoors;
    }
    
    public void setDwOutdoors(int dwOutdoors) {
        this.dwOutdoors = dwOutdoors;
    }
    
    public int getDwAnimate() {
        return dwAnimate;
    }
    
    public void setDwAnimate(int dwAnimate) {
        this.dwAnimate = dwAnimate;
    }
    
    public int getDwKillEdge() {
        return dwKillEdge;
    }
    
    public void setDwKillEdge(int dwKillEdge) {
        this.dwKillEdge = dwKillEdge;
    }
    
    public int getDwFillBlanks() {
        return dwFillBlanks;
    }
    
    public void setDwFillBlanks(int dwFillBlanks) {
        this.dwFillBlanks = dwFillBlanks;
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
    
    public int getDwAutoMap() {
        return dwAutoMap;
    }
    
    public void setDwAutoMap(int dwAutoMap) {
        this.dwAutoMap = dwAutoMap;
    }
    
    public int getDwScan() {
        return dwScan;
    }
    
    public void setDwScan(int dwScan) {
        this.dwScan = dwScan;
    }
    
    public int getDwPops() {
        return dwPops;
    }
    
    public void setDwPops(int dwPops) {
        this.dwPops = dwPops;
    }
    
    public int getDwPopPad() {
        return dwPopPad;
    }
    
    public void setDwPopPad(int dwPopPad) {
        this.dwPopPad = dwPopPad;
    }
    
    public int getDwFiles() {
        return dwFiles;
    }
    
    public void setDwFiles(int dwFiles) {
        this.dwFiles = dwFiles;
    }
    
    public int getDwFileId() {
        return dwFileId;
    }
    
    public void setDwFileId(int dwFileId) {
        this.dwFileId = dwFileId;
    }
    
    public int getDwDt1Mask() {
        return dwDt1Mask;
    }
    
    public void setDwDt1Mask(int dwDt1Mask) {
        this.dwDt1Mask = dwDt1Mask;
    }
    
    public int getDwBeta() {
        return dwBeta;
    }
    
    public void setDwBeta(int dwBeta) {
        this.dwBeta = dwBeta;
    }
    
    public int getDwExpansion() {
        return dwExpansion;
    }
    
    public void setDwExpansion(int dwExpansion) {
        this.dwExpansion = dwExpansion;
    }
    
    public int getNAnimSpeed() {
        return nAnimSpeed;
    }
    
    public void setNAnimSpeed(int nAnimSpeed) {
        this.nAnimSpeed = nAnimSpeed;
    }
    
    public String[] getSzFile() {
        return szFile;
    }
    
    public void setSzFile(String[] szFile) {
        this.szFile = szFile;
    }
    
    public String getSzFile(int index) {
        if (index >= 0 && index < szFile.length) {
            return szFile[index];
        }
        return null;
    }
    
    public void setSzFile(int index, String fileName) {
        if (index >= 0 && index < szFile.length) {
            szFile[index] = fileName;
        }
    }
}
