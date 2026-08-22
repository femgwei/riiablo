package com.d2moo.common.drlg;

/**
 * Drlg 预设构建链表节点
 * 对应 C++ 结构：D2DrlgBuildStrc
 * 用于户外关卡按预设类型维护“下一个要用的文件索引”（nRand），
 * 同一 nLevelPrestId 在链表中只占一项，通过 pNext 串在 level.pBuild 上。
 */
public class D2DrlgBuildStrc {
    /** 0x00 预设定义 ID（来自 LvlPrestTxt.dwDef） */
    private int nPreset;
    /** 0x04 该预设的文件数量（用于 nRand % nDivisor） */
    private int nDivisor;
    /** 0x08 当前轮转到的文件索引（0..nDivisor-1） */
    private int nRand;
    /** 0x0C 链表中下一个节点 */
    private D2DrlgBuildStrc pNext;

    public int getNPreset() {
        return nPreset;
    }

    public void setNPreset(int nPreset) {
        this.nPreset = nPreset;
    }

    public int getNDivisor() {
        return nDivisor;
    }

    public void setNDivisor(int nDivisor) {
        this.nDivisor = nDivisor;
    }

    public int getNRand() {
        return nRand;
    }

    public void setNRand(int nRand) {
        this.nRand = nRand;
    }

    public D2DrlgBuildStrc getPNext() {
        return pNext;
    }

    public void setPNext(D2DrlgBuildStrc pNext) {
        this.pNext = pNext;
    }
}
