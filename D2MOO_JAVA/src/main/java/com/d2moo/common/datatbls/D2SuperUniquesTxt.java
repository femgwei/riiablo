package com.d2moo.common.datatbls;

/**
 * 超级唯一怪物文本结构
 * 对应 C++ 结构：D2SuperUniquesTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 * SuperUniques.txt 文件包含超级唯一怪物的信息
 */
public class D2SuperUniquesTxt {
    private int dwId;                      // 超级唯一怪物ID
    private String szName;                 // 名称
    private String szClass;                // 类别
    private int dwHcIdx;                   // 硬核索引
    private int dwMonSound;                // 怪物声音
    private int dwMod1;                    // 修改器1
    private int dwMod2;                    // 修改器2
    private int dwMod3;                    // 修改器3
    private int dwMinGrp;                  // 最小组大小
    private int dwMaxGrp;                  // 最大组大小
    private int dwEClass;                  // 精英类别
    private int dwAutoPos;                 // 自动位置
    private int dwStacks;                  // 堆叠
    private int dwStacksPer;               // 每堆叠
    private int dwStacksMin;               // 最小堆叠
    private int dwStacksMax;               // 最大堆叠
    private int dwTC;                      // 财宝等级
    private int dwTCEx;                    // 扩展财宝等级
    private int dwBeta;                    // Beta 标志
    
    public D2SuperUniquesTxt() {
        this.dwId = 0;
        this.szName = "";
        this.szClass = "";
    }
    
    // Getters and Setters
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
    
    public String getSzClass() {
        return szClass;
    }
    
    public void setSzClass(String szClass) {
        this.szClass = szClass;
    }
    
    public int getDwHcIdx() {
        return dwHcIdx;
    }
    
    public void setDwHcIdx(int dwHcIdx) {
        this.dwHcIdx = dwHcIdx;
    }
    
    public int getDwMonSound() {
        return dwMonSound;
    }
    
    public void setDwMonSound(int dwMonSound) {
        this.dwMonSound = dwMonSound;
    }
    
    public int getDwMod1() {
        return dwMod1;
    }
    
    public void setDwMod1(int dwMod1) {
        this.dwMod1 = dwMod1;
    }
    
    public int getDwMod2() {
        return dwMod2;
    }
    
    public void setDwMod2(int dwMod2) {
        this.dwMod2 = dwMod2;
    }
    
    public int getDwMod3() {
        return dwMod3;
    }
    
    public void setDwMod3(int dwMod3) {
        this.dwMod3 = dwMod3;
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
    
    public int getDwEClass() {
        return dwEClass;
    }
    
    public void setDwEClass(int dwEClass) {
        this.dwEClass = dwEClass;
    }
    
    public int getDwAutoPos() {
        return dwAutoPos;
    }
    
    public void setDwAutoPos(int dwAutoPos) {
        this.dwAutoPos = dwAutoPos;
    }
    
    public int getDwStacks() {
        return dwStacks;
    }
    
    public void setDwStacks(int dwStacks) {
        this.dwStacks = dwStacks;
    }
    
    public int getDwStacksPer() {
        return dwStacksPer;
    }
    
    public void setDwStacksPer(int dwStacksPer) {
        this.dwStacksPer = dwStacksPer;
    }
    
    public int getDwStacksMin() {
        return dwStacksMin;
    }
    
    public void setDwStacksMin(int dwStacksMin) {
        this.dwStacksMin = dwStacksMin;
    }
    
    public int getDwStacksMax() {
        return dwStacksMax;
    }
    
    public void setDwStacksMax(int dwStacksMax) {
        this.dwStacksMax = dwStacksMax;
    }
    
    public int getDwTC() {
        return dwTC;
    }
    
    public void setDwTC(int dwTC) {
        this.dwTC = dwTC;
    }
    
    public int getDwTCEx() {
        return dwTCEx;
    }
    
    public void setDwTCEx(int dwTCEx) {
        this.dwTCEx = dwTCEx;
    }
    
    public int getDwBeta() {
        return dwBeta;
    }
    
    public void setDwBeta(int dwBeta) {
        this.dwBeta = dwBeta;
    }
}
