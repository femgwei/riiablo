package com.d2moo.common.drlg;

/**
 * Drlg 动画瓦片网格结构
 * 对应 C++ 结构：D2DrlgAnimTileGridStrc
 */
public class D2DrlgAnimTileGridStrc {
    private D2DrlgTileDataStrc[] ppMapTileData;  // 0x00 瓦片数据数组（指针数组）
    private int nFrames;                         // 0x04 总帧数（8位定点格式）
    private int nCurrentFrame;                   // 0x08 当前帧（8位定点格式）
    private int nAnimationSpeed;                // 0x0C 动画速度（8位定点格式）
    private D2DrlgAnimTileGridStrc pNext;       // 0x10 下一个动画网格（链表）
    
    public D2DrlgAnimTileGridStrc() {
        this.nFrames = 0;
        this.nCurrentFrame = 0;
        this.nAnimationSpeed = 0;
    }
    
    // Getters and Setters
    public D2DrlgTileDataStrc[] getPpMapTileData() {
        return ppMapTileData;
    }
    
    public void setPpMapTileData(D2DrlgTileDataStrc[] ppMapTileData) {
        this.ppMapTileData = ppMapTileData;
    }
    
    public int getNFrames() {
        return nFrames;
    }
    
    public void setNFrames(int nFrames) {
        this.nFrames = nFrames;
    }
    
    public int getNCurrentFrame() {
        return nCurrentFrame;
    }
    
    public void setNCurrentFrame(int nCurrentFrame) {
        this.nCurrentFrame = nCurrentFrame;
    }
    
    public int getNAnimationSpeed() {
        return nAnimationSpeed;
    }
    
    public void setNAnimationSpeed(int nAnimationSpeed) {
        this.nAnimationSpeed = nAnimationSpeed;
    }
    
    public D2DrlgAnimTileGridStrc getPNext() {
        return pNext;
    }
    
    public void setPNext(D2DrlgAnimTileGridStrc pNext) {
        this.pNext = pNext;
    }
    
    /** Frame count is an integer in native D2Common. */
    public int getActualFrames() {
        return nFrames;
    }
    
    /**
     * 获取实际当前帧（从8位定点格式转换）
     */
    public int getActualCurrentFrame() {
        return nCurrentFrame >> 8;
    }
    
    /** Speed is already expressed in 8-bit fixed-point units per tick. */
    public int getActualAnimationSpeed() {
        return nAnimationSpeed;
    }
    
    /** Frame count is stored without fixed-point conversion. */
    public void setActualFrames(int frames) {
        this.nFrames = frames;
    }
    
    /**
     * 设置实际当前帧（转换为8位定点格式）
     */
    public void setActualCurrentFrame(int currentFrame) {
        this.nCurrentFrame = currentFrame << 8;
    }
    
    /** Speed is already expressed in 8-bit fixed-point units per tick. */
    public void setActualAnimationSpeed(int speed) {
        this.nAnimationSpeed = speed;
    }
}
