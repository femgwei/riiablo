package com.d2moo.common.drlg;

/**
 * 房间标志位定义
 */
public class D2DrlgRoomFlags {
    public static final int INACTIVE = 0x00000002;
    public static final int HAS_WARP_0 = 0x00000010;
    public static final int HAS_WARP_1 = 0x00000020;
    public static final int HAS_WARP_2 = 0x00000040;
    public static final int HAS_WARP_3 = 0x00000080;
    public static final int HAS_WARP_4 = 0x00000100;
    public static final int HAS_WARP_5 = 0x00000200;
    public static final int HAS_WARP_6 = 0x00000400;
    public static final int HAS_WARP_7 = 0x00000800;
    public static final int SUBSHRINE_ROW1 = 0x00001000;
    public static final int SUBSHRINE_ROW2 = 0x00002000;
    public static final int SUBSHRINE_ROW3 = 0x00004000;
    public static final int SUBSHRINE_ROW4 = 0x00008000;
    public static final int HAS_WAYPOINT = 0x00010000;              // 带有子主题和子传送点的户外区域
    public static final int HAS_WAYPOINT_SMALL = 0x00020000;       // 小型传送点
    public static final int AUTOMAP_REVEAL = 0x00040000;
    public static final int NO_LOS_DRAW = 0x00080000;
    public static final int HAS_ROOM = 0x00100000;                  // 附加了活动的 pRoom 结构
    public static final int ROOM_FREED_SRV = 0x00200000;           // 释放 pRoom 后设置
    public static final int HASPORTAL = 0x00400000;                 // 防止房间删除
    public static final int POPULATION_ZERO = 0x00800000;           // 城镇设置，如果 ds1 有 populate=0 也设置 // 无生成区域
    public static final int TILELIB_LOADED = 0x01000000;
    public static final int PRESET_UNITS_ADDED = 0x02000000;        // 指 DRLGMap 或 DRLGFile；添加硬编码的预设单位
    public static final int PRESET_UNITS_SPAWNED = 0x04000000;      // 在 RoomEx 预设单位生成后设置 / 防止在房间重新激活时重新生成
    public static final int ANIMATED_FLOOR = 0x08000000;             // 动画地板（火焰之河，地狱第5幕）
    
    public static final int HAS_WARP_MASK = HAS_WARP_0 | HAS_WARP_1 | HAS_WARP_2 | HAS_WARP_3 
                                         | HAS_WARP_4 | HAS_WARP_5 | HAS_WARP_6 | HAS_WARP_7;
    public static final int SUBSHRINE_ROWS_MASK = SUBSHRINE_ROW1 | SUBSHRINE_ROW2 | SUBSHRINE_ROW3 | SUBSHRINE_ROW4;
    public static final int HAS_WAYPOINT_MASK = HAS_WAYPOINT | HAS_WAYPOINT_SMALL;
    
    public static final int HAS_WARP_FIRST_BIT = 4;
    public static final int SUBSHRINE_ROWS_FIRST_BIT = 12;
    public static final int HAS_WAYPOINT_FIRST_BIT = 16;
}
