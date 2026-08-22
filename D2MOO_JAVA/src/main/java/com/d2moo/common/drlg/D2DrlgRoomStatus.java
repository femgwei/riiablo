package com.d2moo.common.drlg;

/**
 * 房间状态枚举
 * 注意：较低的值具有更高的优先级
 */
public enum D2DrlgRoomStatus {
    /** 客户端在房间内 */
    CLIENT_IN_ROOM(0),
    /** 客户端在视野内 */
    CLIENT_IN_SIGHT(1),
    /** 客户端在视野外 */
    CLIENT_OUT_OF_SIGHT(2),
    /** 卸载状态 */
    UNTILE(3),
    /** 状态数量（用于边界检查） */
    COUNT(4);
    
    private final int value;
    
    D2DrlgRoomStatus(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    /**
     * 从整数值获取枚举
     */
    public static D2DrlgRoomStatus fromValue(int value) {
        for (D2DrlgRoomStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return COUNT; // 默认返回 COUNT
    }
}
