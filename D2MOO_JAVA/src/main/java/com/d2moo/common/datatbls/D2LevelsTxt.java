package com.d2moo.common.datatbls;

/**
 * 关卡文本结构
 * 对应 C++ 结构：D2LevelsTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 * Levels.txt 文件包含关卡的基本信息，与 Levels.bin 类似但格式为文本
 */
public class D2LevelsTxt {
    private int dwLevelId;                 // 关卡ID
    private String szName;                 // 关卡名称
    private int dwId;                      // ID
    private int dwPal;                     // 调色板
    private int dwAct;                     // Act 编号
    private int dwTeleport;                // 传送标志
    private int dwRain;                    // 下雨标志
    private int dwMud;                     // 泥地标志
    private int dwNoPer;                   // 无透视标志
    private int dwIsInside;                // 室内标志
    private int dwDrawEdges;               // 绘制边缘标志
    private int dwDrlgType;                // DRLG 类型
    private int dwLevelType;                // 关卡类型
    private int dwSubType;                 // 子类型
    private int dwSubTheme;                // 子主题
    private int dwSubWaypoint;             // 子传送点
    private int dwSubShrine;               // 子神殿
    private int dwVis0;                    // 可见性0
    private int dwVis1;                    // 可见性1
    private int dwVis2;                    // 可见性2
    private int dwVis3;                    // 可见性3
    private int dwVis4;                    // 可见性4
    private int dwVis5;                    // 可见性5
    private int dwVis6;                    // 可见性6
    private int dwVis7;                    // 可见性7
    private int dwSizeX;                   // X 尺寸
    private int dwSizeY;                   // Y 尺寸
    private int dwSizeX_N;                 // X 尺寸（归一化）
    private int dwSizeY_N;                 // Y 尺寸（归一化）
    private int dwOffsetX;                 // X 偏移
    private int dwOffsetY;                 // Y 偏移
    private int dwDepend;                  // 依赖
    private int dwQuest;                   // 任务
    private int dwQuestDiff;               // 任务难度
    private int dwLayer;                   // 层
    private int dwMap;                     // 地图
    private int dwMonLvl1;                 // 怪物等级1
    private int dwMonLvl2;                 // 怪物等级2
    private int dwMonLvl3;                 // 怪物等级3
    private int dwMonLvl1Ex;               // 怪物等级1扩展
    private int dwMonLvl2Ex;               // 怪物等级2扩展
    private int dwMonLvl3Ex;               // 怪物等级3扩展
    private int dwMonDen;                  // 怪物密度
    private int dwMonDen_N;                // 怪物密度（归一化）
    private int dwMonDen_H;                // 怪物密度（地狱）
    private int dwMonDen_NH;               // 怪物密度（归一化地狱）
    private int dwMonUMin;                 // 怪物U最小值
    private int dwMonUMax;                 // 怪物U最大值
    private int dwMonWndr;                 // 怪物游荡
    private int dwMonSpcWalk;              // 怪物特殊行走
    private int dwNumMon;                  // 怪物数量
    private int dwBeta;                    // Beta 标志
    
    public D2LevelsTxt() {
        this.dwLevelId = 0;
        this.szName = "";
        this.dwId = 0;
        this.dwAct = 0;
    }
    
    // Getters and Setters
    public int getDwLevelId() {
        return dwLevelId;
    }
    
    public void setDwLevelId(int dwLevelId) {
        this.dwLevelId = dwLevelId;
    }
    
    public String getSzName() {
        return szName;
    }
    
    public void setSzName(String szName) {
        this.szName = szName;
    }
    
    public int getDwId() {
        return dwId;
    }
    
    public void setDwId(int dwId) {
        this.dwId = dwId;
    }
    
    public int getDwPal() {
        return dwPal;
    }
    
    public void setDwPal(int dwPal) {
        this.dwPal = dwPal;
    }
    
    public int getDwAct() {
        return dwAct;
    }
    
    public void setDwAct(int dwAct) {
        this.dwAct = dwAct;
    }
    
    public int getDwTeleport() {
        return dwTeleport;
    }
    
    public void setDwTeleport(int dwTeleport) {
        this.dwTeleport = dwTeleport;
    }
    
    public int getDwRain() {
        return dwRain;
    }
    
    public void setDwRain(int dwRain) {
        this.dwRain = dwRain;
    }
    
    public int getDwMud() {
        return dwMud;
    }
    
    public void setDwMud(int dwMud) {
        this.dwMud = dwMud;
    }
    
    public int getDwNoPer() {
        return dwNoPer;
    }
    
    public void setDwNoPer(int dwNoPer) {
        this.dwNoPer = dwNoPer;
    }
    
    public int getDwIsInside() {
        return dwIsInside;
    }
    
    public void setDwIsInside(int dwIsInside) {
        this.dwIsInside = dwIsInside;
    }
    
    public int getDwDrawEdges() {
        return dwDrawEdges;
    }
    
    public void setDwDrawEdges(int dwDrawEdges) {
        this.dwDrawEdges = dwDrawEdges;
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
    
    public int getDwVis0() {
        return dwVis0;
    }
    
    public void setDwVis0(int dwVis0) {
        this.dwVis0 = dwVis0;
    }
    
    public int getDwVis1() {
        return dwVis1;
    }
    
    public void setDwVis1(int dwVis1) {
        this.dwVis1 = dwVis1;
    }
    
    public int getDwVis2() {
        return dwVis2;
    }
    
    public void setDwVis2(int dwVis2) {
        this.dwVis2 = dwVis2;
    }
    
    public int getDwVis3() {
        return dwVis3;
    }
    
    public void setDwVis3(int dwVis3) {
        this.dwVis3 = dwVis3;
    }
    
    public int getDwVis4() {
        return dwVis4;
    }
    
    public void setDwVis4(int dwVis4) {
        this.dwVis4 = dwVis4;
    }
    
    public int getDwVis5() {
        return dwVis5;
    }
    
    public void setDwVis5(int dwVis5) {
        this.dwVis5 = dwVis5;
    }
    
    public int getDwVis6() {
        return dwVis6;
    }
    
    public void setDwVis6(int dwVis6) {
        this.dwVis6 = dwVis6;
    }
    
    public int getDwVis7() {
        return dwVis7;
    }
    
    public void setDwVis7(int dwVis7) {
        this.dwVis7 = dwVis7;
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
    
    public int getDwSizeX_N() {
        return dwSizeX_N;
    }
    
    public void setDwSizeX_N(int dwSizeX_N) {
        this.dwSizeX_N = dwSizeX_N;
    }
    
    public int getDwSizeY_N() {
        return dwSizeY_N;
    }
    
    public void setDwSizeY_N(int dwSizeY_N) {
        this.dwSizeY_N = dwSizeY_N;
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
    
    public int getDwQuest() {
        return dwQuest;
    }
    
    public void setDwQuest(int dwQuest) {
        this.dwQuest = dwQuest;
    }
    
    public int getDwQuestDiff() {
        return dwQuestDiff;
    }
    
    public void setDwQuestDiff(int dwQuestDiff) {
        this.dwQuestDiff = dwQuestDiff;
    }
    
    public int getDwLayer() {
        return dwLayer;
    }
    
    public void setDwLayer(int dwLayer) {
        this.dwLayer = dwLayer;
    }
    
    public int getDwMap() {
        return dwMap;
    }
    
    public void setDwMap(int dwMap) {
        this.dwMap = dwMap;
    }
    
    public int getDwMonLvl1() {
        return dwMonLvl1;
    }
    
    public void setDwMonLvl1(int dwMonLvl1) {
        this.dwMonLvl1 = dwMonLvl1;
    }
    
    public int getDwMonLvl2() {
        return dwMonLvl2;
    }
    
    public void setDwMonLvl2(int dwMonLvl2) {
        this.dwMonLvl2 = dwMonLvl2;
    }
    
    public int getDwMonLvl3() {
        return dwMonLvl3;
    }
    
    public void setDwMonLvl3(int dwMonLvl3) {
        this.dwMonLvl3 = dwMonLvl3;
    }
    
    public int getDwMonLvl1Ex() {
        return dwMonLvl1Ex;
    }
    
    public void setDwMonLvl1Ex(int dwMonLvl1Ex) {
        this.dwMonLvl1Ex = dwMonLvl1Ex;
    }
    
    public int getDwMonLvl2Ex() {
        return dwMonLvl2Ex;
    }
    
    public void setDwMonLvl2Ex(int dwMonLvl2Ex) {
        this.dwMonLvl2Ex = dwMonLvl2Ex;
    }
    
    public int getDwMonLvl3Ex() {
        return dwMonLvl3Ex;
    }
    
    public void setDwMonLvl3Ex(int dwMonLvl3Ex) {
        this.dwMonLvl3Ex = dwMonLvl3Ex;
    }
    
    public int getDwMonDen() {
        return dwMonDen;
    }
    
    public void setDwMonDen(int dwMonDen) {
        this.dwMonDen = dwMonDen;
    }
    
    public int getDwMonDen_N() {
        return dwMonDen_N;
    }
    
    public void setDwMonDen_N(int dwMonDen_N) {
        this.dwMonDen_N = dwMonDen_N;
    }
    
    public int getDwMonDen_H() {
        return dwMonDen_H;
    }
    
    public void setDwMonDen_H(int dwMonDen_H) {
        this.dwMonDen_H = dwMonDen_H;
    }
    
    public int getDwMonDen_NH() {
        return dwMonDen_NH;
    }
    
    public void setDwMonDen_NH(int dwMonDen_NH) {
        this.dwMonDen_NH = dwMonDen_NH;
    }
    
    public int getDwMonUMin() {
        return dwMonUMin;
    }
    
    public void setDwMonUMin(int dwMonUMin) {
        this.dwMonUMin = dwMonUMin;
    }
    
    public int getDwMonUMax() {
        return dwMonUMax;
    }
    
    public void setDwMonUMax(int dwMonUMax) {
        this.dwMonUMax = dwMonUMax;
    }
    
    public int getDwMonWndr() {
        return dwMonWndr;
    }
    
    public void setDwMonWndr(int dwMonWndr) {
        this.dwMonWndr = dwMonWndr;
    }
    
    public int getDwMonSpcWalk() {
        return dwMonSpcWalk;
    }
    
    public void setDwMonSpcWalk(int dwMonSpcWalk) {
        this.dwMonSpcWalk = dwMonSpcWalk;
    }
    
    public int getDwNumMon() {
        return dwNumMon;
    }
    
    public void setDwNumMon(int dwNumMon) {
        this.dwNumMon = dwNumMon;
    }
    
    public int getDwBeta() {
        return dwBeta;
    }
    
    public void setDwBeta(int dwBeta) {
        this.dwBeta = dwBeta;
    }
}
