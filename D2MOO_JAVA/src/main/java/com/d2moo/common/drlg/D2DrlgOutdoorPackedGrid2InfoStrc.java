package com.d2moo.common.drlg;

/**
 * 户外打包网格2信息结构
 * 对应 C++ 联合体：D2DrlgOutdoorPackedGrid2InfoStrc
 *
 * C++ 中使用位域（bit field），Java 中使用一个 32 位整型并通过位运算访问各个字段。
 *
 * 位布局（参考 C++ 结构体注释）：
 * BIT(0)      nUnkb00          : 1   // Mask 0x00000001
 * BIT(1)      bHasDirection    : 1   // Mask 0x00000002
 * BIT(2-6)    nUnkb02          : 5   // Mask 0x0000007C
 * BIT(7)      nUnkb07          : 1   // Mask 0x00000080  spawn preset level here ?
 * BIT(8)      nUnkb08          : 1   // Mask 0x00000100  related to being a blank grid cell?
 * BIT(9)      bHasPickedFile   : 1   // Mask 0x00000200
 * BIT(10)     bLvlLink         : 1   // Mask 0x00000400
 * BIT(11)     nUnkb11          : 1   // Mask 0x00000800
 * BIT(12)     nUnkb12          : 1   // Mask 0x00001000
 * BIT(13-15)  nUnkb13          : 3   // Mask 0x0000E000
 * BIT(16-19)  nPickedFile      : 4   // Mask 0x000F0000
 * BIT(20-31)  nUnkb20          : 12  // Mask 0xFFF00000
 */
public class D2DrlgOutdoorPackedGrid2InfoStrc {

    private int nPackedValue; // 打包的 32 位值

    public D2DrlgOutdoorPackedGrid2InfoStrc() {
        this.nPackedValue = 0;
    }

    public D2DrlgOutdoorPackedGrid2InfoStrc(int packedValue) {
        this.nPackedValue = packedValue;
    }

    public int getNPackedValue() {
        return nPackedValue;
    }

    public void setNPackedValue(int nPackedValue) {
        this.nPackedValue = nPackedValue;
    }

    // --- 单比特布尔字段访问 ---

    /** BIT(0) - 未知标志 nUnkb00 */
    public boolean isNUnkb00() {
        return (nPackedValue & 0x00000001) != 0;
    }

    public void setNUnkb00(boolean value) {
        if (value) {
            nPackedValue |= 0x00000001;
        } else {
            nPackedValue &= ~0x00000001;
        }
    }

    /** BIT(1) - 是否有方向 bHasDirection */
    public boolean isBHasDirection() {
        return (nPackedValue & 0x00000002) != 0;
    }

    public void setBHasDirection(boolean value) {
        if (value) {
            nPackedValue |= 0x00000002;
        } else {
            nPackedValue &= ~0x00000002;
        }
    }

    /** BIT(7) - 未知标志 nUnkb07 */
    public boolean isNUnkb07() {
        return (nPackedValue & 0x00000080) != 0;
    }

    public void setNUnkb07(boolean value) {
        if (value) {
            nPackedValue |= 0x00000080;
        } else {
            nPackedValue &= ~0x00000080;
        }
    }

    /** BIT(8) - 未知标志 nUnkb08（与空白网格单元有关） */
    public boolean isNUnkb08() {
        return (nPackedValue & 0x00000100) != 0;
    }

    public void setNUnkb08(boolean value) {
        if (value) {
            nPackedValue |= 0x00000100;
        } else {
            nPackedValue &= ~0x00000100;
        }
    }

    /** BIT(9) - 是否已经选择了文件 bHasPickedFile */
    public boolean isBHasPickedFile() {
        return (nPackedValue & 0x00000200) != 0;
    }

    public void setBHasPickedFile(boolean value) {
        if (value) {
            nPackedValue |= 0x00000200;
        } else {
            nPackedValue &= ~0x00000200;
        }
    }

    /** BIT(10) - 是否为关卡链接 bLvlLink */
    public boolean isBLvlLink() {
        return (nPackedValue & 0x00000400) != 0;
    }

    public void setBLvlLink(boolean value) {
        if (value) {
            nPackedValue |= 0x00000400;
        } else {
            nPackedValue &= ~0x00000400;
        }
    }

    /** BIT(11) - 未知标志 nUnkb11 */
    public boolean isNUnkb11() {
        return (nPackedValue & 0x00000800) != 0;
    }

    public void setNUnkb11(boolean value) {
        if (value) {
            nPackedValue |= 0x00000800;
        } else {
            nPackedValue &= ~0x00000800;
        }
    }

    /** BIT(12) - 未知标志 nUnkb12 */
    public boolean isNUnkb12() {
        return (nPackedValue & 0x00001000) != 0;
    }

    public void setNUnkb12(boolean value) {
        if (value) {
            nPackedValue |= 0x00001000;
        } else {
            nPackedValue &= ~0x00001000;
        }
    }

    // --- 多比特整数字段访问 ---

    /** BIT(2-6) - 未知字段 nUnkb02（5 bit） */
    public int getNUnkb02() {
        return (nPackedValue >> 2) & 0x1F;
    }

    public void setNUnkb02(int value) {
        nPackedValue = (nPackedValue & ~0x0000007C) | ((value & 0x1F) << 2);
    }

    /** BIT(13-15) - 未知字段 nUnkb13（3 bit） */
    public int getNUnkb13() {
        return (nPackedValue >> 13) & 0x07;
    }

    public void setNUnkb13(int value) {
        nPackedValue = (nPackedValue & ~0x0000E000) | ((value & 0x07) << 13);
    }

    /** BIT(16-19) - 已选择的文件索引 nPickedFile（4 bit） */
    public int getNPickedFile() {
        return (nPackedValue >> 16) & 0x0F;
    }

    public void setNPickedFile(int value) {
        nPackedValue = (nPackedValue & ~0x000F0000) | ((value & 0x0F) << 16);
    }

    /** BIT(20-31) - 未知字段 nUnkb20（12 bit） */
    public int getNUnkb20() {
        return (nPackedValue >> 20) & 0x0FFF;
    }

    public void setNUnkb20(int value) {
        nPackedValue = (nPackedValue & ~0xFFF00000) | ((value & 0x0FFF) << 20);
    }
}

