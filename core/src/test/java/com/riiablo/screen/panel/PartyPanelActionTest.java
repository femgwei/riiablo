package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.net.packet.d2gs.PartyOperation;
import org.junit.jupiter.api.Test;

class PartyPanelActionTest {
  @Test
  void mapsAuthoritativeRelationsToUiActions() {
    assertArrayEquals(new byte[] {PartyOperation.LEAVE},
        PartyPanel.actionsFor(true, 3, 3, PartyRelation.PARTY_MEMBER));
    assertArrayEquals(new byte[0],
        PartyPanel.actionsFor(true, Party.INVALID_ID, Party.INVALID_ID, PartyRelation.NONE));
    assertArrayEquals(new byte[] {PartyOperation.ACCEPT, PartyOperation.DECLINE},
        PartyPanel.actionsFor(false, Party.INVALID_ID, 3, PartyRelation.INVITED));
    assertArrayEquals(new byte[] {PartyOperation.CANCEL},
        PartyPanel.actionsFor(false, 3, Party.INVALID_ID, PartyRelation.INVITER));
    assertArrayEquals(new byte[] {PartyOperation.UNHOSTILE},
        PartyPanel.actionsFor(false, Party.INVALID_ID, Party.INVALID_ID, PartyRelation.HOSTILE));
    assertArrayEquals(new byte[] {PartyOperation.INVITE, PartyOperation.HOSTILE},
        PartyPanel.actionsFor(false, Party.INVALID_ID, Party.INVALID_ID, PartyRelation.NONE));
    assertArrayEquals(new byte[] {PartyOperation.HOSTILE},
        PartyPanel.actionsFor(false, Party.INVALID_ID, 4, PartyRelation.NONE));
    assertArrayEquals(new byte[0],
        PartyPanel.actionsFor(false, 3, 3, PartyRelation.PARTY_MEMBER));
  }
}
