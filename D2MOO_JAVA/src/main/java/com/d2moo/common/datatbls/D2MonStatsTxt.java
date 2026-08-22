package com.d2moo.common.datatbls;

/**
 * 怪物统计文本结构
 * 对应 C++ 结构：D2MonStatsTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 * MonStats.txt 文件包含怪物的基本统计信息
 */
public class D2MonStatsTxt {
    private int dwId;                      // 怪物ID
    private String szName;                 // 怪物名称
    private String szBase;                 // 基础类型
    private int dwMinGrp;                  // 最小组大小
    private int dwMaxGrp;                  // 最大组大小
    private int dwAIParam1;                // AI参数1
    private int dwAIParam2;                // AI参数2
    private int dwAIParam3;                // AI参数3
    private int dwAIParam4;                // AI参数4
    private int dwAIParam5;                // AI参数5
    private int dwAIParam6;                // AI参数6
    private int dwAIParam7;                // AI参数7
    private int dwAIParam8;                // AI参数8
    private int dwLevel;                   // 等级
    private int dwLevelEx;                 // 扩展等级
    private int dwMinHP;                   // 最小生命值
    private int dwMaxHP;                   // 最大生命值
    private int dwAC;                      // 护甲等级
    private int dwExp;                     // 经验值
    private int dwA1MinD;                  // 攻击1最小伤害
    private int dwA1MaxD;                  // 攻击1最大伤害
    private int dwA1TH;                    // 攻击1命中率
    private int dwA2MinD;                  // 攻击2最小伤害
    private int dwA2MaxD;                  // 攻击2最大伤害
    private int dwA2TH;                    // 攻击2命中率
    private int dwS1MinD;                  // 技能1最小伤害
    private int dwS1MaxD;                  // 技能1最大伤害
    private int dwS1TH;                    // 技能1命中率
    private int dwEl1Mode;                 // 元素1模式
    private int dwEl1Type;                 // 元素1类型
    private int dwEl1Pct;                  // 元素1百分比
    private int dwEl1MinD;                 // 元素1最小伤害
    private int dwEl1MaxD;                 // 元素1最大伤害
    private int dwEl2Mode;                 // 元素2模式
    private int dwEl2Type;                 // 元素2类型
    private int dwEl2Pct;                  // 元素2百分比
    private int dwEl2MinD;                 // 元素2最小伤害
    private int dwEl2MaxD;                 // 元素2最大伤害
    private int dwEl3Mode;                 // 元素3模式
    private int dwEl3Type;                  // 元素3类型
    private int dwEl3Pct;                  // 元素3百分比
    private int dwEl3MinD;                 // 元素3最小伤害
    private int dwEl3MaxD;                 // 元素3最大伤害
    private int dwResist1;                 // 抗性1
    private int dwResist2;                 // 抗性2
    private int dwResist3;                 // 抗性3
    private int dwResist4;                 // 抗性4
    private int dwResist5;                 // 抗性5
    private int dwResist6;                 // 抗性6
    private int dwResist7;                 // 抗性7
    private int dwResist8;                 // 抗性8
    private int dwResist9;                 // 抗性9
    private int dwResist10;                // 抗性10
    private int dwResist11;                // 抗性11
    private int dwResist12;                // 抗性12
    private int dwResist13;                // 抗性13
    private int dwResist14;                // 抗性14
    private int dwResist15;                // 抗性15
    private int dwResist16;                // 抗性16
    private int dwTC;                      // 财宝等级
    private int dwTCEx;                    // 扩展财宝等级
    private int dwBeta;                    // Beta 标志
    
    public D2MonStatsTxt() {
        this.dwId = 0;
        this.szName = "";
        this.szBase = "";
    }
    
    // Getters and Setters (只包含主要字段，其他字段类似)
    public int getDwId() {
        return dwId;
    }
    
    public void setDwId(int dwId) {
        this.dwId = dwId;
    }
    
    public String getSzName() {
        return szName;
    }
    
    public void setSzName(String szName) {
        this.szName = szName;
    }
    
    public String getSzBase() {
        return szBase;
    }
    
    public void setSzBase(String szBase) {
        this.szBase = szBase;
    }
    
    public int getDwMinGrp() {
        return dwMinGrp;
    }
    
    public void setDwMinGrp(int dwMinGrp) {
        this.dwMinGrp = dwMinGrp;
    }
    
    public int getDwMaxGrp() {
        return dwMaxGrp;
    }
    
    public void setDwMaxGrp(int dwMaxGrp) {
        this.dwMaxGrp = dwMaxGrp;
    }
    
    public int getDwLevel() {
        return dwLevel;
    }
    
    public void setDwLevel(int dwLevel) {
        this.dwLevel = dwLevel;
    }
    
    public int getDwLevelEx() {
        return dwLevelEx;
    }
    
    public void setDwLevelEx(int dwLevelEx) {
        this.dwLevelEx = dwLevelEx;
    }
    
    public int getDwMinHP() {
        return dwMinHP;
    }
    
    public void setDwMinHP(int dwMinHP) {
        this.dwMinHP = dwMinHP;
    }
    
    public int getDwMaxHP() {
        return dwMaxHP;
    }
    
    public void setDwMaxHP(int dwMaxHP) {
        this.dwMaxHP = dwMaxHP;
    }
    
    public int getDwAC() {
        return dwAC;
    }
    
    public void setDwAC(int dwAC) {
        this.dwAC = dwAC;
    }
    
    public int getDwExp() {
        return dwExp;
    }
    
    public void setDwExp(int dwExp) {
        this.dwExp = dwExp;
    }
    
    public int getDwBeta() {
        return dwBeta;
    }
    
    public void setDwBeta(int dwBeta) {
        this.dwBeta = dwBeta;
    }
    
    // 其他字段的 Getters and Setters（简化实现，只包含主要字段）
    // 如果需要完整实现，可以添加所有字段的 getter/setter
}
