package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeWarpSlotResolverTest {
  @Test void reusedJailDefinitionIsDisambiguatedByTheNativeRoomWarpFlags() {
    D2DrlgLevel level = new D2DrlgLevel();
    D2DrlgRoom previous = new D2DrlgRoom(), next = new D2DrlgRoom();
    previous.setFlags(D2DrlgRoomFlags.HAS_WARP_0);
    next.setFlags(D2DrlgRoomFlags.HAS_WARP_0 << 1);
    previous.setDrlgRoomNext(next); level.setFirstRoomEx(previous);
    int[] vis = {30, 32}, warps = {13, 13};
    assertEquals(0, NativeWarpSlotResolver.resolve(level, vis, warps, 0, 13));
    assertEquals(1, NativeWarpSlotResolver.resolve(level, vis, warps, 1, 13));
    next.setFlags(0);
    assertEquals(-1, NativeWarpSlotResolver.resolve(level, vis, warps, 1, 13));
    assertEquals(0, NativeWarpSlotResolver.resolve(level, new int[] {106, 108},
        new int[] {70, -1}, 1, 70));
    assertEquals(-1, NativeWarpSlotResolver.resolve(level, vis, warps, 1, -1));
  }
}
