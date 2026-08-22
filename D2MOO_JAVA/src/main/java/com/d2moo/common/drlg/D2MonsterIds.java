package com.d2moo.common.drlg;

/**
 * 怪物ID常量
 * 对应 C++ 枚举：D2C_MonsterIds
 * 
 * 注意：这里只定义常用的怪物ID，完整列表需要从数据表获取
 */
public class D2MonsterIds {
    // 常用怪物ID（从枚举位置计算）
    public static final int MONSTER_NAVI = 272; // 从 MonsterIds.h 枚举位置计算
    public static final int MONSTER_ACT2VENDOR1 = 210; // 从 MonsterIds.h 枚举位置计算
    public static final int MONSTER_ACT2VENDOR2 = 211; // 从 MonsterIds.h 枚举位置计算
    public static final int MONSTER_LIGHTNINGSPIRE = 377; // 从 MonsterIds.h 枚举位置计算
    public static final int MONSTER_FIRETOWER = 378; // 从 MonsterIds.h 枚举位置计算
    
    // 路障和监狱门相关怪物
    public static final int MONSTER_BARRICADETOWER = 439; // 需要确认实际值
    public static final int MONSTER_BARRICADEDOOR1 = 440; // 需要确认实际值
    public static final int MONSTER_BARRICADEDOOR2 = 441; // 需要确认实际值
    public static final int MONSTER_PRISONDOOR = 442; // 需要确认实际值
    public static final int MONSTER_BARRICADEWALL1 = 443; // 需要确认实际值
    public static final int MONSTER_BARRICADEWALL2 = 444; // 需要确认实际值
}
