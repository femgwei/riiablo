package com.d2moo.common.drlg;

/** Native {@code ACTCALLBACKFN}, invoked after an active room is fully linked. */
@FunctionalInterface
public interface D2ActCallback {
    void onRoomAllocated(D2ActiveRoom room);
}
