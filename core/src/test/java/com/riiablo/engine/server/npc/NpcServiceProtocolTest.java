package com.riiablo.engine.server.npc;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.Vector2;

class NpcServiceProtocolTest {
  @Test void rangeAndServiceMatrixAreDeterministic() {
    assertTrue(NpcServiceProtocol.inRange(new Vector2(0, 0), new Vector2(8, 0)));
    assertFalse(NpcServiceProtocol.inRange(new Vector2(0, 0), new Vector2(9, 0)));
    assertTrue(NpcServiceProtocol.supports(NpcServiceProtocol.Service.TRADE, NpcServiceProtocol.Operation.BUY));
    assertFalse(NpcServiceProtocol.supports(NpcServiceProtocol.Service.REPAIR, NpcServiceProtocol.Operation.BUY));
  }

  @Test void rejectReasonsAreOrderedForDiagnostics() {
    assertEquals("NOT_CONNECTED", NpcServiceProtocol.rejectReason(false, true, true, true, true));
    assertEquals("UNKNOWN_NPC", NpcServiceProtocol.rejectReason(true, false, true, true, true));
    assertEquals("STALE_STOCK", NpcServiceProtocol.rejectReason(true, true, true, true, false));
    assertNull(NpcServiceProtocol.rejectReason(true, true, true, true, true));
  }
}
