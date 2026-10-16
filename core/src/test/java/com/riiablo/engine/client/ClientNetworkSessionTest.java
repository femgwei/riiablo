package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientNetworkSessionTest {
  @Test
  void sessionKeepsItsCharacterAndLivePlayerProviderWhenGlobalsChange() {
    CharData character = mock(CharData.class);
    AtomicInteger player = new AtomicInteger(17);
    ClientNetworkSession session = new ClientNetworkSession(character, player::get);
    CharData previousCharacter = Riiablo.charData;
    try {
      Riiablo.charData = mock(CharData.class);
      assertSame(character, session.character());
      assertEquals(17, session.player());
      player.set(29);
      assertEquals(29, session.player());
      assertNotSame(Riiablo.charData, session.character());
    } finally {
      Riiablo.charData = previousCharacter;
    }
  }

  @Test
  void resultRouterIsOptionalForDirectFixtures() {
    CharData character = mock(CharData.class);
    ClientNetworkSession session = new ClientNetworkSession(character, () -> 1);
    assertNull(session.results());
    ClientNetworkSession.ResultRouter router = mock(ClientNetworkSession.ResultRouter.class);
    assertSame(router, new ClientNetworkSession(character, () -> 1, router).results());
  }
}
