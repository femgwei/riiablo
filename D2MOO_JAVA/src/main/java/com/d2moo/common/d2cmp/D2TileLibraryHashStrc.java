package com.d2moo.common.d2cmp;

/**
 * 瓦片库哈希表结构
 * 对应 C++ 结构：D2TileLibraryHashStrc
 * 
 * 注意：这是一个哈希表结构，用于快速查找瓦片
 */
public class D2TileLibraryHashStrc {
    private D2TileLibraryHashNodeStrc[] pNodes;  // 0x00 哈希节点数组 [128]
    private String fileName;                      // 文件名（用于标识）
    
    public D2TileLibraryHashStrc() {
        this.pNodes = new D2TileLibraryHashNodeStrc[128];
    }
    
    public D2TileLibraryHashStrc(String fileName) {
        this();
        this.fileName = fileName;
    }
    
    // Getters and Setters
    public D2TileLibraryHashNodeStrc[] getPNodes() {
        return pNodes;
    }
    
    public void setPNodes(D2TileLibraryHashNodeStrc[] pNodes) {
        this.pNodes = pNodes;
    }
    
    public D2TileLibraryHashNodeStrc getPNode(int index) {
        if (pNodes == null || index < 0 || index >= pNodes.length) {
            return null;
        }
        return pNodes[index];
    }
    
    public void setPNode(int index, D2TileLibraryHashNodeStrc node) {
        if (pNodes == null) {
            pNodes = new D2TileLibraryHashNodeStrc[128];
        }
        if (index >= 0 && index < pNodes.length) {
            pNodes[index] = node;
        }
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
