package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartyServiceProtocolTest {
  @Test
  void inviteAcceptAndLeaveUseAuthenticatedSource() {
    PartyManager parties = new PartyManager();
    java.util.function.IntPredicate online = id -> id == 10 || id == 11;

    PartyServiceProtocol.Result invite = PartyServiceProtocol.execute(
        parties, 10, com.riiablo.net.packet.d2gs.PartyOperation.INVITE, 11, online);
    assertTrue(invite.success);
    assertEquals(10, parties.getInviter(11));

    PartyServiceProtocol.Result accept = PartyServiceProtocol.execute(
        parties, 11, com.riiablo.net.packet.d2gs.PartyOperation.ACCEPT, 10, online);
    assertTrue(accept.success);
    assertTrue(parties.areInSameParty(10, 11));

    PartyServiceProtocol.Result leave = PartyServiceProtocol.execute(
        parties, 11, com.riiablo.net.packet.d2gs.PartyOperation.LEAVE, -1, online);
    assertTrue(leave.success);
    assertEquals(Party.INVALID_ID, parties.getPartyId(11));
  }

  @Test
  void rejectsOfflineTargetAndPartyFriendlyHostility() {
    PartyManager parties = new PartyManager();
    java.util.function.IntPredicate online = id -> id == 1 || id == 2;
    assertEquals("TARGET_OFFLINE", PartyServiceProtocol.execute(
        parties, 1, com.riiablo.net.packet.d2gs.PartyOperation.INVITE, 3, online).reason);
    assertTrue(PartyServiceProtocol.execute(
        parties, 1, com.riiablo.net.packet.d2gs.PartyOperation.INVITE, 2, online).success);
    assertTrue(PartyServiceProtocol.execute(
        parties, 2, com.riiablo.net.packet.d2gs.PartyOperation.ACCEPT, 1, online).success);
    assertEquals("HOSTILE_REJECTED", PartyServiceProtocol.execute(
        parties, 1, com.riiablo.net.packet.d2gs.PartyOperation.HOSTILE, 2, online).reason);
  }
}
