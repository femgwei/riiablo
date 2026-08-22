package com.d2moo.common.drlg;

/**
 * Drlg 顶点结构
 * 对应 C++ 结构：D2DrlgVertexStrc
 */
public class D2DrlgVertexStrc {
    private int nPosX;                      // 0x00 X 位置
    private int nPosY;                      // 0x04 Y 位置
    private byte nDirection;                // 0x08 方向
    private byte[] pad0x09 = new byte[3];   // 0x09 填充
    private int dwFlags;                    // 0x0C 标志
    private D2DrlgVertexStrc pNext;         // 0x10 下一个顶点（链表）
    
    public D2DrlgVertexStrc() {
        this.nPosX = 0;
        this.nPosY = 0;
        this.nDirection = 0;
        this.dwFlags = 0;
    }
    
    public D2DrlgVertexStrc(int posX, int posY, byte direction) {
        this.nPosX = posX;
        this.nPosY = posY;
        this.nDirection = direction;
        this.dwFlags = 0;
    }
    
    // Getters and Setters
    public int getNPosX() {
        return nPosX;
    }
    
    public void setNPosX(int nPosX) {
        this.nPosX = nPosX;
    }
    
    public int getNPosY() {
        return nPosY;
    }
    
    public void setNPosY(int nPosY) {
        this.nPosY = nPosY;
    }
    
    public byte getNDirection() {
        return nDirection;
    }
    
    public void setNDirection(byte nDirection) {
        this.nDirection = nDirection;
    }
    
    public int getDwFlags() {
        return dwFlags;
    }
    
    public void setDwFlags(int dwFlags) {
        this.dwFlags = dwFlags;
    }
    
    public D2DrlgVertexStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2DrlgVertexStrc pNext) {
        this.pNext = pNext;
    }
}
