package com.d2moo.common.drlg;

import com.d2moo.common.seed.Seed;

/**
 * Drlg 关卡链接数据结构
 * 对应 C++ 中的 D2DrlgLevelLinkDataStrc 结构
 * 
 * 用于关卡之间的链接和坐标计算
 */
public class D2DrlgLevelLinkDataStrc {
    private D2Seed pSeed;                    // 随机数种子
    private D2DrlgCoord[] pLevelCoord;        // 关卡坐标数组 [15]
    private D2DrlgLink[] pLink;               // 关卡链接数组
    private int[][] nRand;                    // 随机数数组 [4][15] 或 [2][15]
    private int nIteration;                   // 迭代次数
    private int nCurrentLevel;                // 当前关卡ID
    
    /**
     * 构造函数
     */
    public D2DrlgLevelLinkDataStrc() {
        this.pSeed = new D2Seed();
        this.nCurrentLevel = 0;
        this.nIteration = 0;
        // The C++ structure embeds fixed-size arrays. Java must instantiate
        // the elements as well as the arrays before link placement starts.
        this.pLevelCoord = new D2DrlgCoord[15];
        this.pLink = new D2DrlgLink[15];
        this.nRand = new int[4][15];
        for (int i = 0; i < 15; i++) {
            this.pLevelCoord[i] = new D2DrlgCoord();
            this.pLink[i] = new D2DrlgLink();
            for (int j = 0; j < this.nRand.length; j++) this.nRand[j][i] = -1;
        }
    }
    
    /**
     * 构造函数
     * @param seed 随机数种子
     * @param currentLevel 当前关卡ID
     * @param iteration 迭代次数
     * @param levelCoordCount 关卡坐标数组大小
     * @param linkCount 链接数组大小
     */
    public D2DrlgLevelLinkDataStrc(D2Seed seed, int currentLevel, int iteration, 
            int levelCoordCount, int linkCount) {
        this.pSeed = seed != null ? seed : new D2Seed();
        this.nCurrentLevel = currentLevel;
        this.nIteration = iteration;
        this.pLevelCoord = new D2DrlgCoord[levelCoordCount];
        this.pLink = new D2DrlgLink[linkCount];
        // nRand 可以是 [4][15] 或 [2][15]，根据 C++ 定义使用 [4][15]
        this.nRand = new int[4][levelCoordCount];
        
        // 初始化数组
        for (int i = 0; i < levelCoordCount; i++) {
            this.pLevelCoord[i] = new D2DrlgCoord();
            this.nRand[0][i] = -1;
            this.nRand[1][i] = -1;
        }
        
        for (int i = 0; i < linkCount; i++) {
            this.pLink[i] = new D2DrlgLink();
        }
    }
    
    // Getters and Setters
    public D2Seed getPSeed() {
        return pSeed;
    }
    
    public void setPSeed(D2Seed pSeed) {
        this.pSeed = pSeed;
    }
    
    public int getNCurrentLevel() {
        return nCurrentLevel;
    }
    
    public void setNCurrentLevel(int nCurrentLevel) {
        this.nCurrentLevel = nCurrentLevel;
    }
    
    public int getNIteration() {
        return nIteration;
    }
    
    public void setNIteration(int nIteration) {
        this.nIteration = nIteration;
    }
    
    public D2DrlgCoord[] getPLevelCoord() {
        return pLevelCoord;
    }
    
    public void setPLevelCoord(D2DrlgCoord[] pLevelCoord) {
        this.pLevelCoord = pLevelCoord;
    }
    
    public D2DrlgCoord getPLevelCoord(int index) {
        if (pLevelCoord == null || index < 0 || index >= pLevelCoord.length) {
            return null;
        }
        return pLevelCoord[index];
    }
    
    public void setPLevelCoord(int index, D2DrlgCoord coord) {
        if (pLevelCoord != null && index >= 0 && index < pLevelCoord.length) {
            pLevelCoord[index] = coord;
        }
    }
    
    public D2DrlgLink[] getPLink() {
        return pLink;
    }
    
    public void setPLink(D2DrlgLink[] pLink) {
        this.pLink = pLink;
    }
    
    public D2DrlgLink getPLink(int index) {
        if (pLink == null || index < 0 || index >= pLink.length) {
            return null;
        }
        return pLink[index];
    }
    
    public void setPLink(int index, D2DrlgLink link) {
        if (pLink != null && index >= 0 && index < pLink.length) {
            pLink[index] = link;
        }
    }
    
    public int[][] getNRand() {
        return nRand;
    }
    
    public void setNRand(int[][] nRand) {
        this.nRand = nRand;
    }
    
    public int getNRand(int arrayIndex, int iteration) {
        if (nRand == null || arrayIndex < 0 || arrayIndex >= nRand.length) {
            return -1;
        }
        if (iteration < 0 || iteration >= nRand[arrayIndex].length) {
            return -1;
        }
        return nRand[arrayIndex][iteration];
    }
    
    public void setNRand(int arrayIndex, int iteration, int value) {
        if (nRand != null && arrayIndex >= 0 && arrayIndex < nRand.length) {
            if (iteration >= 0 && iteration < nRand[arrayIndex].length) {
                nRand[arrayIndex][iteration] = value;
            }
        }
    }
    
    /**
     * 获取 nRand 数组的指定索引（用于兼容 C++ 风格的数组访问）
     * @param arrayIndex 数组索引（0-3）
     * @return 一维数组，如果不存在返回 null
     */
    public int[] getNRand(int arrayIndex) {
        if (nRand == null || arrayIndex < 0 || arrayIndex >= nRand.length) {
            return null;
        }
        return nRand[arrayIndex];
    }
    
    /**
     * 获取 nRand2（一维数组视图，对应 C++ union）
     * @return 一维数组，长度为 60 (4 * 15)
     */
    public int[] getNRand2() {
        if (nRand == null || nRand.length == 0 || nRand[0].length == 0) {
            return null;
        }
        int[] result = new int[nRand.length * nRand[0].length];
        int index = 0;
        for (int i = 0; i < nRand.length; i++) {
            for (int j = 0; j < nRand[i].length; j++) {
                result[index++] = nRand[i][j];
            }
        }
        return result;
    }
}
