package com.d2moo.common.datatbls;

/**
 * 关卡类型文本结构
 * 对应 C++ 结构：D2LvlTypesTxt
 * 
 * 注意：这是一个数据表结构，通常从 DATATBLS 模块加载
 */
public class D2LevelTypesTxt {
    private int dwLevelType;            // 关卡类型ID
    private String[] szFile;            // 文件名数组 [32]，每个最多 60 字符
    private int dwAct;                  // Act 编号
    private int dwExpansion;            // 扩展标志
    private int dwBeta;                 // Beta 标志
    
    public D2LevelTypesTxt() {
        this.dwLevelType = 0;
        this.dwAct = 0;
        this.szFile = new String[32]; // 32 个文件
    }
    
    // Getters and Setters
    public int getDwLevelType() {
        return dwLevelType;
    }
    
    public void setDwLevelType(int dwLevelType) {
        this.dwLevelType = dwLevelType;
    }
    
    public String[] getSzFile() {
        return szFile;
    }
    
    public void setSzFile(String[] szFile) {
        this.szFile = szFile;
    }
    
    /**
     * 获取指定索引的文件名
     * @param index 文件索引（0-31）
     * @return 文件名，如果索引无效或文件名为空返回 null
     */
    public String getSzFile(int index) {
        if (szFile == null || index < 0 || index >= szFile.length) {
            return null;
        }
        return szFile[index];
    }
    
    /**
     * 设置指定索引的文件名
     * @param index 文件索引（0-31）
     * @param fileName 文件名
     */
    public void setSzFile(int index, String fileName) {
        if (szFile == null) {
            szFile = new String[32];
        }
        if (index >= 0 && index < szFile.length) {
            szFile[index] = fileName;
        }
    }
    
    public int getDwAct() {
        return dwAct;
    }
    
    public void setDwAct(int dwAct) {
        this.dwAct = dwAct;
    }
    
    public int getDwExpansion() {
        return dwExpansion;
    }
    
    public void setDwExpansion(int dwExpansion) {
        this.dwExpansion = dwExpansion;
    }
    
    public int getDwBeta() {
        return dwBeta;
    }
    
    public void setDwBeta(int dwBeta) {
        this.dwBeta = dwBeta;
    }
}
