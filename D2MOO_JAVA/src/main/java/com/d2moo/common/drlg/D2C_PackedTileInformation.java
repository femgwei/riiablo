package com.d2moo.common.drlg;

/**
 * 打包瓦片信息结构
 * 对应 C++ 联合体：D2C_PackedTileInformation
 * 
 * 注意：C++ 中使用位域（bit field），Java 中使用位操作来访问各个字段
 */
public class D2C_PackedTileInformation {
    private int nPackedValue;  // 打包的 32 位值
    
    // 位域定义（从 C++ 结构）
    // BIT(0)     bIsWall           : 1
    // BIT(1)     bIsFloor          : 1
    // BIT(2)     bLOS              : 1
    // BIT(3)     bEnclosed         : 1
    // BIT(4)     bExit             : 1
    // BIT(5)     bUnk0x20          : 1
    // BIT(6)     bLayerBelow       : 1
    // BIT(7)     bLayerAbove       : 1
    // BIT(8-15)  nTileSequence     : 8
    // BIT(16)    bFillLOS          : 1
    // BIT(17)    bUnwalkable       : 1
    // BIT(18-19) nWallLayer        : 2
    // BIT(20-25) nTileStyle        : 6
    // BIT(26)    bRevealHidden     : 1
    // BIT(27)    bShadow           : 1
    // BIT(28)    bLinkage          : 1
    // BIT(29)    bObjectWall       : 1
    // BIT(30)    bUnk0x40000000    : 1
    // BIT(31)    bHidden           : 1
    
    public D2C_PackedTileInformation() {
        this.nPackedValue = 0;
    }
    
    public D2C_PackedTileInformation(int packedValue) {
        this.nPackedValue = packedValue;
    }
    
    public int getNPackedValue() {
        return nPackedValue;
    }
    
    public void setNPackedValue(int nPackedValue) {
        this.nPackedValue = nPackedValue;
    }
    
    // 位域访问方法
    public boolean isBIsWall() {
        return (nPackedValue & 0x00000001) != 0;
    }
    
    public void setBIsWall(boolean value) {
        if (value) {
            nPackedValue |= 0x00000001;
        } else {
            nPackedValue &= ~0x00000001;
        }
    }
    
    public boolean isBIsFloor() {
        return (nPackedValue & 0x00000002) != 0;
    }
    
    public void setBIsFloor(boolean value) {
        if (value) {
            nPackedValue |= 0x00000002;
        } else {
            nPackedValue &= ~0x00000002;
        }
    }
    
    public boolean isBLOS() {
        return (nPackedValue & 0x00000004) != 0;
    }
    
    public void setBLOS(boolean value) {
        if (value) {
            nPackedValue |= 0x00000004;
        } else {
            nPackedValue &= ~0x00000004;
        }
    }
    
    public boolean isBEnclosed() {
        return (nPackedValue & 0x00000008) != 0;
    }
    
    public void setBEnclosed(boolean value) {
        if (value) {
            nPackedValue |= 0x00000008;
        } else {
            nPackedValue &= ~0x00000008;
        }
    }
    
    public boolean isBExit() {
        return (nPackedValue & 0x00000010) != 0;
    }
    
    public void setBExit(boolean value) {
        if (value) {
            nPackedValue |= 0x00000010;
        } else {
            nPackedValue &= ~0x00000010;
        }
    }
    
    public boolean isBUnk0x20() {
        return (nPackedValue & 0x00000020) != 0;
    }
    
    public void setBUnk0x20(boolean value) {
        if (value) {
            nPackedValue |= 0x00000020;
        } else {
            nPackedValue &= ~0x00000020;
        }
    }
    
    public boolean isBLayerBelow() {
        return (nPackedValue & 0x00000040) != 0;
    }
    
    public void setBLayerBelow(boolean value) {
        if (value) {
            nPackedValue |= 0x00000040;
        } else {
            nPackedValue &= ~0x00000040;
        }
    }
    
    public boolean isBLayerAbove() {
        return (nPackedValue & 0x00000080) != 0;
    }
    
    public void setBLayerAbove(boolean value) {
        if (value) {
            nPackedValue |= 0x00000080;
        } else {
            nPackedValue &= ~0x00000080;
        }
    }
    
    public int getNTileSequence() {
        return (nPackedValue >> 8) & 0xFF;
    }
    
    public void setNTileSequence(int value) {
        nPackedValue = (nPackedValue & ~0x0000FF00) | ((value & 0xFF) << 8);
    }
    
    public boolean isBFillLOS() {
        return (nPackedValue & 0x00010000) != 0;
    }
    
    public void setBFillLOS(boolean value) {
        if (value) {
            nPackedValue |= 0x00010000;
        } else {
            nPackedValue &= ~0x00010000;
        }
    }
    
    public boolean isBUnwalkable() {
        return (nPackedValue & 0x00020000) != 0;
    }
    
    public void setBUnwalkable(boolean value) {
        if (value) {
            nPackedValue |= 0x00020000;
        } else {
            nPackedValue &= ~0x00020000;
        }
    }
    
    public int getNWallLayer() {
        return (nPackedValue >> 18) & 0x03;
    }
    
    public void setNWallLayer(int value) {
        nPackedValue = (nPackedValue & ~0x000C0000) | ((value & 0x03) << 18);
    }
    
    public int getNTileStyle() {
        return (nPackedValue >> 20) & 0x3F;
    }
    
    public void setNTileStyle(int value) {
        nPackedValue = (nPackedValue & ~0x03F00000) | ((value & 0x3F) << 20);
    }
    
    public boolean isBRevealHidden() {
        return (nPackedValue & 0x04000000) != 0;
    }
    
    public void setBRevealHidden(boolean value) {
        if (value) {
            nPackedValue |= 0x04000000;
        } else {
            nPackedValue &= ~0x04000000;
        }
    }
    
    public boolean isBShadow() {
        return (nPackedValue & 0x08000000) != 0;
    }
    
    public void setBShadow(boolean value) {
        if (value) {
            nPackedValue |= 0x08000000;
        } else {
            nPackedValue &= ~0x08000000;
        }
    }
    
    public boolean isBLinkage() {
        return (nPackedValue & 0x10000000) != 0;
    }
    
    public void setBLinkage(boolean value) {
        if (value) {
            nPackedValue |= 0x10000000;
        } else {
            nPackedValue &= ~0x10000000;
        }
    }
    
    public boolean isBObjectWall() {
        return (nPackedValue & 0x20000000) != 0;
    }
    
    public void setBObjectWall(boolean value) {
        if (value) {
            nPackedValue |= 0x20000000;
        } else {
            nPackedValue &= ~0x20000000;
        }
    }
    
    public boolean isBUnk0x40000000() {
        return (nPackedValue & 0x40000000) != 0;
    }
    
    public void setBUnk0x40000000(boolean value) {
        if (value) {
            nPackedValue |= 0x40000000;
        } else {
            nPackedValue &= ~0x40000000;
        }
    }
    
    public boolean isBHidden() {
        return (nPackedValue & 0x80000000) != 0;
    }
    
    public void setBHidden(boolean value) {
        if (value) {
            nPackedValue |= 0x80000000;
        } else {
            nPackedValue &= ~0x80000000;
        }
    }
}
