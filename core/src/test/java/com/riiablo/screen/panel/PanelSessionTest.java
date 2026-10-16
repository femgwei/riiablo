package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.riiablo.Riiablo;
import com.riiablo.screen.GameScreen;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PanelSessionTest {
  @ParameterizedTest
  @ValueSource(strings = {"assets", "charData", "fonts", "colors", "textures", "string", "bundle", "game", "engine"})
  void replacingAnyBorrowedHostDisablesSessionWithoutTouchingNewHost(String alias) throws Exception {
    PanelSession session = new PanelSession(Riiablo.game);
    session.finish(new Group());
    assertTrue(session.isCurrent());
    Field field = Riiablo.class.getField(alias);
    Object previous = field.get(null), replacement = mock(field.getType());
    try {
      field.set(null, replacement);
      assertFalse(session.isCurrent());
      session.dispose(); session.dispose();
      verifyNoInteractions(replacement);
    } finally { field.set(null, previous); }
  }

  @Test void assemblyIsAllowedBeforePublicationAndWorldCanOnlyBeBoundOnce() {
    GameScreen previous = Riiablo.game;
    World previousWorld = Riiablo.engine;
    GameScreen owner = mock(GameScreen.class);
    World world = mock(World.class), different = mock(World.class);
    PanelSession session = new PanelSession(owner);
    try {
      assertTrue(session.isCurrent());
      session.finish(new Group());
      assertFalse(session.isCurrent());
      session.bindWorld(world); session.bindWorld(world);
      assertThrows(IllegalStateException.class, () -> session.bindWorld(different));
      assertThrows(NullPointerException.class, () -> session.bindWorld(null));
      Riiablo.game = owner; Riiablo.engine = world;
      assertTrue(session.isCurrent());
      session.dispose();
      assertFalse(session.isCurrent()); assertNull(session.owner()); assertNull(session.world());
      session.bindWorld(different); // Disposed owners cannot be revived.
      assertNull(session.world());
    } finally { Riiablo.game = previous; Riiablo.engine = previousWorld; session.dispose(); }
  }

  @Test void detachedAndPartiallyMountedTreesAreStillRetired() {
    PanelSession session = new PanelSession(Riiablo.game);
    Group root = new Group(), nested = new Group(); Actor child = new Actor();
    root.addActor(nested); nested.addActor(child);
    child.addListener(event -> false);
    child.addCaptureListener(event -> false);
    child.addAction(new com.badlogic.gdx.scenes.scene2d.Action() { public boolean act(float dt) { return false; } });
    session.finish(root); nested.remove();
    Group partiallyMounted = session.own(new Group());
    Actor partial = new Actor(); partiallyMounted.addActor(partial);
    session.dispose(root); session.dispose();
    assertNull(child.getParent()); assertTrue(child.getListeners().isEmpty());
    assertTrue(child.getCaptureListeners().isEmpty()); assertTrue(child.getActions().isEmpty());
    assertNull(partial.getParent()); assertTrue(root.getChildren().isEmpty());
  }
}
