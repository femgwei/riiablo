package com.d2moo.common.drlg;

/**
 * 种子结构
 * 对应 C++ 结构：D2SeedStrc
 */
public class D2Seed {
    private long lSeed;        // 0x00 64位种子值（联合体）
    private int nLowSeed;       // 0x00 低32位种子（联合体的一部分）
    private int nHighSeed;     // 0x04 高32位种子（联合体的一部分）
    
    public D2Seed() {
        this.lSeed = 0;
        this.nLowSeed = 0;
        this.nHighSeed = 0;
    }
    
    public D2Seed(int lowSeed, int highSeed) {
        this.nLowSeed = lowSeed;
        this.nHighSeed = highSeed;
        updateLSeed();
    }
    
    /**
     * 更新 64 位种子值（基于低32位和高32位）
     */
    private void updateLSeed() {
        // 将两个 int 组合成 long，注意处理符号扩展
        this.lSeed = ((long) nHighSeed << 32) | (nLowSeed & 0xFFFFFFFFL);
    }
    
    /**
     * 更新低32位和高32位（基于 64 位种子值）
     */
    private void updateSeeds() {
        this.nLowSeed = (int) (lSeed & 0xFFFFFFFFL);
        this.nHighSeed = (int) ((lSeed >> 32) & 0xFFFFFFFFL);
    }
    
    // Getters and Setters
    public long getLSeed() {
        return lSeed;
    }
    
    public void setLSeed(long lSeed) {
        this.lSeed = lSeed;
        updateSeeds();
    }
    
    public int getNLowSeed() {
        return nLowSeed;
    }
    
    public void setNLowSeed(int nLowSeed) {
        this.nLowSeed = nLowSeed;
        updateLSeed();
    }
    
    public int getNHighSeed() {
        return nHighSeed;
    }
    
    public void setNHighSeed(int nHighSeed) {
        this.nHighSeed = nHighSeed;
        updateLSeed();
    }
    
    /**
     * 设置低32位和高32位种子
     */
    public void setSeeds(int lowSeed, int highSeed) {
        this.nLowSeed = lowSeed;
        this.nHighSeed = highSeed;
        updateLSeed();
    }
    
    /**
     * 获取低32位和高32位种子
     */
    public void getSeeds(int[] result) {
        if (result == null || result.length < 2) {
            return;
        }
        result[0] = nLowSeed;
        result[1] = nHighSeed;
    }
}
