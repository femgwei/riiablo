package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Npc;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.screen.GameScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ClientNpcSessionTest {
  @Test void healerAndGreetingAudioStayInTheirOwningSession() throws Exception {
    Audio first = mock(Audio.class), second = mock(Audio.class), global = mock(Audio.class);
    Audio previous = Riiablo.audio;
    try {
      ClientNpc a = npc(first, 1), b = npc(second, 2);
      when(first.play("akara_greeting_1", false)).thenReturn(mock(Audio.Instance.class));
      Riiablo.audio = global;
      a.presentInteraction();
      verify(first).play("shrine_refill", true);
      verify(first).play("akara_greeting_1", false);
      verify(first, never()).play("akara_greeting_inactive_1", false);
      verifyNoInteractions(second, global);
      b.presentInteraction();
      verify(second).play("shrine_refill", true);
      verify(second).play("akara_greeting_1", false);
      verify(second).play("akara_greeting_inactive_1", false);
      verifyNoInteractions(global);
    } finally {
      Riiablo.audio = previous;
    }
  }

  @Test void questMenuUsesTheOwningWorldAndPlayerAfterGlobalScreenChanges() throws Exception {
    ClientNpc npc = npc(null, 41);
    World owner = mock(World.class), other = mock(World.class);
    QuestDialogController quests = mock(QuestDialogController.class);
    MenuManager menus = mock(MenuManager.class);
    set(Npc.class, npc, "world", owner);
    set(ClientNpc.class, npc, "menuManager", menus);
    when(owner.getSystem(QuestDialogController.class)).thenReturn(quests);
    World previousWorld = Riiablo.engine;
    GameScreen previousScreen = Riiablo.game;
    try {
      Riiablo.engine = other;
      Riiablo.game = mock(GameScreen.class);
      Riiablo.game.player = 99;
      Method method = ClientNpc.class.getDeclaredMethod("questClick");
      method.setAccessible(true);
      ((ClickListener) method.invoke(npc)).clicked(null, 0, 0);
      verify(menus).setMenu(null, Engine.INVALID_ENTITY);
      verify(quests).openQuestDialog(41, 11);
      verifyNoInteractions(other, Riiablo.game);
    } finally {
      Riiablo.engine = previousWorld;
      Riiablo.game = previousScreen;
    }
  }

  @Test void missingAudioDoesNotInterruptHeadlessNpcPresentation() throws Exception {
    assertDoesNotThrow(() -> npc(null, 1).presentInteraction());
  }

  private static ClientNpc npc(Audio audio, int player) throws Exception {
    ClientNpc npc = new ClientNpc(11);
    GameScreen screen = mock(GameScreen.class);
    npc.clientContext = new ClientGameplayContext(null, () -> player, () -> screen, null,
        null, audio, null, null, null, null, null, null, null, null);
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "Akara1";
    row.hcIdx = MonsterType.AKARA;
    set(Npc.class, npc, "monstats", row);
    return npc;
  }

  private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
