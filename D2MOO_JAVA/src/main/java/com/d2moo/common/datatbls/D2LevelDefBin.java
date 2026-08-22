package com.d2moo.common.datatbls;

/**
 * 关卡定义二进制结构
 * 对应 C++ 结构：D2LevelDefBin
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 */
public class D2LevelDefBin {
    private int dwLevelId;               // 关卡ID
    private int dwDrlgType;              // Drlg 类型
    private int dwLevelType;             // 关卡类型
    private int dwPopulate;              // 填充标志
    private int dwLogicals;              // 逻辑标志
    private int dwOutdoors;              // 户外标志
    private int dwAnimate;               // 动画标志
    private int dwKillEdge;              // 边缘清除标志
    private int dwFillBlanks;            // 填充空白标志
    private int[] dwSizeX;               // X 尺寸数组（按难度索引）
    private int[] dwSizeY;               // Y 尺寸数组（按难度索引）
    private int dwAutoMap;               // 自动地图标志
    private int dwScan;                  // 扫描标志
    private int dwPops;                  // 弹出标志
    private int dwPopPad;                // 弹出填充
    private int dwFiles;                 // 文件数量
    private int dwFileId;                // 文件ID
    private int dwDt1Mask;               // DT1 掩码
    private int dwBeta;                  // Beta 标志
    private int dwQuestFlag;             // 任务标志
    private int dwQuestFlagEx;           // 任务标志扩展
    private int dwLayer;                 // 层
    private int dwOffsetX;               // X 偏移
    private int dwOffsetY;               // Y 偏移
    private int dwDepend;                // 依赖
    private int dwSubType;               // 子类型
    private int dwSubTheme;              // 子主题
    private int dwSubWaypoint;           // 子传送点
    private int dwSubShrine;             // 子神殿
    private int[] dwVis;                 // 可见性数组 [8]
    private int dwVisEx;                 // 可见性扩展
    private byte nIntensity;             // 强度
    private byte nRed;                   // 红色
    private byte nGreen;                 // 绿色
    private byte nBlue;                  // 蓝色
    private int dwPortal;                // 传送门
    private int dwPosition;              // 位置
    private int dwSaveMonsters;          // 保存怪物
    private int dwLOSDraw;                // LOS 绘制
    private int[] dwWarp;                // 传送门数组 [8]
    
    public D2LevelDefBin() {
        this.dwWarp = new int[8];
        this.dwSizeX = new int[3];  // 3个难度级别
        this.dwSizeY = new int[3];  // 3个难度级别
        this.dwLevelId = 0;
        this.dwDrlgType = 0;
        this.dwLevelType = 0;
    }
    
    // Getters and Setters
    public int getDwLevelId() {
        return dwLevelId;
    }
    
    public void setDwLevelId(int dwLevelId) {
        this.dwLevelId = dwLevelId;
    }
    
    public int getDwDrlgType() {
        return dwDrlgType;
    }
    
    public void setDwDrlgType(int dwDrlgType) {
        this.dwDrlgType = dwDrlgType;
    }
    
    public int getDwLevelType() {
        return dwLevelType;
    }
    
    public void setDwLevelType(int dwLevelType) {
        this.dwLevelType = dwLevelType;
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
    
    // 移除旧的 int 类型方法，保留数组类型方法
    
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
    
    public int[] getDwWarp() {
        return dwWarp;
    }
    
    public void setDwWarp(int[] dwWarp) {
        this.dwWarp = dwWarp;
    }
    
    public int getDwWarp(int index) {
        if (index >= 0 && index < dwWarp.length) {
            return dwWarp[index];
        }
        return 0;
    }
    
    public void setDwWarp(int index, int value) {
        if (index >= 0 && index < dwWarp.length) {
            dwWarp[index] = value;
        }
    }
    
    // 其他字段的 Getters and Setters
    public int getDwQuestFlag() {
        return dwQuestFlag;
    }
    
    public void setDwQuestFlag(int dwQuestFlag) {
        this.dwQuestFlag = dwQuestFlag;
    }
    
    public int getDwQuestFlagEx() {
        return dwQuestFlagEx;
    }
    
    public void setDwQuestFlagEx(int dwQuestFlagEx) {
        this.dwQuestFlagEx = dwQuestFlagEx;
    }
    
    public int getDwLayer() {
        return dwLayer;
    }
    
    public void setDwLayer(int dwLayer) {
        this.dwLayer = dwLayer;
    }
    
    public int[] getDwSizeX() {
        return dwSizeX;
    }
    
    public void setDwSizeX(int[] dwSizeX) {
        this.dwSizeX = dwSizeX;
    }
    
    public int getDwSizeX(int index) {
        if (index >= 0 && index < dwSizeX.length) {
            return dwSizeX[index];
        }
        return 0;
    }
    
    public void setDwSizeX(int index, int value) {
        if (index >= 0 && index < dwSizeX.length) {
            dwSizeX[index] = value;
        }
    }
    
    public int[] getDwSizeY() {
        return dwSizeY;
    }
    
    public void setDwSizeY(int[] dwSizeY) {
        this.dwSizeY = dwSizeY;
    }
    
    public int getDwSizeY(int index) {
        if (index >= 0 && index < dwSizeY.length) {
            return dwSizeY[index];
        }
        return 0;
    }
    
    public void setDwSizeY(int index, int value) {
        if (index >= 0 && index < dwSizeY.length) {
            dwSizeY[index] = value;
        }
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
    
    public int getDwDepend() {
        return dwDepend;
    }
    
    public void setDwDepend(int dwDepend) {
        this.dwDepend = dwDepend;
    }
    
    public int getDwSubType() {
        return dwSubType;
    }
    
    public void setDwSubType(int dwSubType) {
        this.dwSubType = dwSubType;
    }
    
    public int getDwSubTheme() {
        return dwSubTheme;
    }
    
    public void setDwSubTheme(int dwSubTheme) {
        this.dwSubTheme = dwSubTheme;
    }
    
    public int getDwSubWaypoint() {
        return dwSubWaypoint;
    }
    
    public void setDwSubWaypoint(int dwSubWaypoint) {
        this.dwSubWaypoint = dwSubWaypoint;
    }
    
    public int getDwSubShrine() {
        return dwSubShrine;
    }
    
    public void setDwSubShrine(int dwSubShrine) {
        this.dwSubShrine = dwSubShrine;
    }
    
    public int[] getDwVis() {
        return dwVis;
    }
    
    public void setDwVis(int[] dwVis) {
        this.dwVis = dwVis;
    }
    
    public int getDwVis(int index) {
        if (index >= 0 && index < dwVis.length) {
            return dwVis[index];
        }
        return 0;
    }
    
    public void setDwVis(int index, int value) {
        if (index >= 0 && index < dwVis.length) {
            dwVis[index] = value;
        }
    }
    
    public byte getNIntensity() {
        return nIntensity;
    }
    
    public void setNIntensity(byte nIntensity) {
        this.nIntensity = nIntensity;
    }
    
    public byte getNRed() {
        return nRed;
    }
    
    public void setNRed(byte nRed) {
        this.nRed = nRed;
    }
    
    public byte getNGreen() {
        return nGreen;
    }
    
    public void setNGreen(byte nGreen) {
        this.nGreen = nGreen;
    }
    
    public byte getNBlue() {
        return nBlue;
    }
    
    public void setNBlue(byte nBlue) {
        this.nBlue = nBlue;
    }
    
    public int getDwPortal() {
        return dwPortal;
    }
    
    public void setDwPortal(int dwPortal) {
        this.dwPortal = dwPortal;
    }
    
    public int getDwPosition() {
        return dwPosition;
    }
    
    public void setDwPosition(int dwPosition) {
        this.dwPosition = dwPosition;
    }
    
    public int getDwSaveMonsters() {
        return dwSaveMonsters;
    }
    
    public void setDwSaveMonsters(int dwSaveMonsters) {
        this.dwSaveMonsters = dwSaveMonsters;
    }
    
    public int getDwLOSDraw() {
        return dwLOSDraw;
    }
    
    public void setDwLOSDraw(int dwLOSDraw) {
        this.dwLOSDraw = dwLOSDraw;
    }
}
