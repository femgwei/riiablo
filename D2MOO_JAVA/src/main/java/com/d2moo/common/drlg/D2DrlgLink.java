package com.d2moo.common.drlg;

/**
 * Drlg 链接结构
 * 对应 C++ 中的 D2DrlgLinkStrc 结构
 */
public class D2DrlgLink {
    @FunctionalInterface
    public interface LinkFunction {
        boolean link(D2DrlgLevelLinkDataStrc pLevelLinkData);
    }
    
    @FunctionalInterface
    public interface LinkFunctionWithIteration {
        boolean link(D2DrlgLevelLinkDataStrc pLevelLinkData, int nIteration);
    }
    
    private LinkFunction pLinkFunction;          // 链接函数（无迭代参数）
    private LinkFunctionWithIteration pLinkFunctionWithIteration; // 链接函数（有迭代参数）
    private int nLevelId;                        // 关卡ID
    private int nLevelLink;                      // 链接的关卡索引
    private int nLevelLinkEx;                    // 额外链接的关卡索引
    private int nUnk;                            // 未知字段
    
    public D2DrlgLink() {
        this.pLinkFunction = null;
        this.pLinkFunctionWithIteration = null;
        this.nLevelId = 0;
        this.nLevelLink = -1;
        this.nLevelLinkEx = -1;
        this.nUnk = -1;
    }
    
    public D2DrlgLink(LinkFunction pLinkFunction, int nLevelId, int nLevelLink, int nLevelLinkEx) {
        this.pLinkFunction = pLinkFunction;
        this.pLinkFunctionWithIteration = null;
        this.nLevelId = nLevelId;
        this.nLevelLink = nLevelLink;
        this.nLevelLinkEx = nLevelLinkEx;
        this.nUnk = -1;
    }
    
    public D2DrlgLink(LinkFunctionWithIteration pLinkFunctionWithIteration, int nLevelId, int nLevelLink, int nLevelLinkEx) {
        this.pLinkFunction = null;
        this.pLinkFunctionWithIteration = pLinkFunctionWithIteration;
        this.nLevelId = nLevelId;
        this.nLevelLink = nLevelLink;
        this.nLevelLinkEx = nLevelLinkEx;
        this.nUnk = -1;
    }
    
    // Getters and Setters
    public LinkFunction getPLinkFunction() {
        return pLinkFunction;
    }
    
    public void setPLinkFunction(LinkFunction pLinkFunction) {
        this.pLinkFunction = pLinkFunction;
        this.pLinkFunctionWithIteration = null;
    }
    
    public LinkFunctionWithIteration getPLinkFunctionWithIteration() {
        return pLinkFunctionWithIteration;
    }
    
    public void setPLinkFunctionWithIteration(LinkFunctionWithIteration pLinkFunctionWithIteration) {
        this.pLinkFunctionWithIteration = pLinkFunctionWithIteration;
        this.pLinkFunction = null;
    }
    
    public int getNLevel() {
        return nLevelId;
    }
    
    public void setNLevel(int nLevelId) {
        this.nLevelId = nLevelId;
    }
    
    public int getNLevelId() {
        return nLevelId;
    }
    
    public void setNLevelId(int nLevelId) {
        this.nLevelId = nLevelId;
    }
    
    public int getNLevelLink() {
        return nLevelLink;
    }
    
    public void setNLevelLink(int nLevelLink) {
        this.nLevelLink = nLevelLink;
    }
    
    public int getNLevelLinkEx() {
        return nLevelLinkEx;
    }
    
    public void setNLevelLinkEx(int nLevelLinkEx) {
        this.nLevelLinkEx = nLevelLinkEx;
    }
    
    public int getNUnk() {
        return nUnk;
    }
    
    public void setNUnk(int nUnk) {
        this.nUnk = nUnk;
    }
    
    /**
     * 执行链接函数
     * @param pLevelLinkData 关卡链接数据
     * @return 链接是否成功
     */
    public boolean executeLink(D2DrlgLevelLinkDataStrc pLevelLinkData) {
        if (pLinkFunction != null) {
            return pLinkFunction.link(pLevelLinkData);
        } else if (pLinkFunctionWithIteration != null) {
            return pLinkFunctionWithIteration.link(pLevelLinkData, pLevelLinkData.getNIteration());
        }
        return false;
    }
}
