package com.d2moo.common.drlg;

/**
 * 关卡文件列表结构
 * 对应 C++ 结构：D2LevelFileListStrc
 * 
 * 用于管理已加载的 DS1 文件，实现引用计数和缓存
 */
public class D2LevelFileListStrc {
    private String szPath;                      // 0x00 文件路径
    private long nRefCount;                     // 0x104 引用计数
    private D2DrlgFileStrc pFile;               // 0x108 文件结构
    private D2LevelFileListStrc pNext;          // 0x10C 下一个文件列表节点
    
    public D2LevelFileListStrc() {
        this.szPath = "";
        this.nRefCount = 0;
        this.pFile = null;
        this.pNext = null;
    }
    
    // Getters and Setters
    public String getSzPath() {
        return szPath;
    }
    
    public void setSzPath(String szPath) {
        this.szPath = szPath;
    }
    
    public long getNRefCount() {
        return nRefCount;
    }
    
    public void setNRefCount(long nRefCount) {
        this.nRefCount = nRefCount;
    }
    
    public void incrementRefCount() {
        this.nRefCount++;
    }
    
    public void decrementRefCount() {
        if (this.nRefCount > 0) {
            this.nRefCount--;
        }
    }
    
    public D2DrlgFileStrc getPFile() {
        return pFile;
    }
    
    public void setPFile(D2DrlgFileStrc pFile) {
        this.pFile = pFile;
    }
    
    public D2LevelFileListStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2LevelFileListStrc pNext) {
        this.pNext = pNext;
    }
}
