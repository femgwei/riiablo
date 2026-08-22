package com.d2moo.common.drlg;

/**
 * Drlg 类型枚举
 */
public enum D2DrlgType {
    /** 迷宫类型 */
    MAZE(0x01),
    /** 预设类型 */
    PRESET(0x02),
    /** 户外类型 */
    OUTDOOR(0x03);
    
    private final int value;
    
    D2DrlgType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    /**
     * 从整数值获取枚举
     */
    public static D2DrlgType fromValue(int value) {
        for (D2DrlgType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return PRESET; // 默认返回 PRESET
    }
}
