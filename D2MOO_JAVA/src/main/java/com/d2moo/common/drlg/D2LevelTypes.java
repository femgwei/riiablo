package com.d2moo.common.drlg;

/**
 * 关卡类型常量
 * 对应 C++ 枚举：D2C_LevelTypes
 */
public class D2LevelTypes {
    public static final int LVLTYPE_ACT1_WILDERNESS = 2;
    public static final int LVLTYPE_ACT2_DESERT = 16;
    // These are the 1.10f LvlTypes.txt ids.  The old Java port used 6/7
    // (zero-based positions from an early table dump), which made the native
    // outdoor generator fall through to dt1Mask=0 for Act III Jungle/Kurast.
    public static final int LVLTYPE_ACT3_JUNGLE = 21;
    public static final int LVLTYPE_ACT3_KURAST = 22;
    public static final int LVLTYPE_ACT4_MESA = 27;
    public static final int LVLTYPE_ACT4_LAVA = 28;
    public static final int LVLTYPE_ACT5_SIEGE = 30;
    public static final int LVLTYPE_ACT5_BARRICADE = 31;
}
