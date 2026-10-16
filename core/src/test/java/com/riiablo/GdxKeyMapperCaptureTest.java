package com.riiablo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.riiablo.key.MappedKey;
import org.junit.jupiter.api.*;

class GdxKeyMapperCaptureTest {
  private Application previous;
  private GdxKeyMapper mapper;

  @BeforeEach void setup() {
    previous = Gdx.app;
    Gdx.app = mock(Application.class);
    when(Gdx.app.getPreferences(anyString())).thenReturn(mock(Preferences.class));
    mapper = new GdxKeyMapper();
  }

  @AfterEach void restore() { Gdx.app = previous; }

  @Test void onlyCurrentIdentityCanReleaseCapture() {
    Object first = new String("owner"), second = new String("owner");
    mapper.beginCapture(first); assertTrue(mapper.isCaptureOwner(first));
    mapper.beginCapture(second); assertFalse(mapper.isCaptureOwner(first));
    assertFalse(mapper.endCapture(first)); assertTrue(mapper.isCaptureOwner(second));
    assertTrue(mapper.endCapture(second)); assertFalse(mapper.endCapture(second));
    assertFalse(mapper.isCaptureOwner(null)); assertFalse(mapper.endCapture(null));
  }

  @Test void nullOwnerDoesNotReplaceLiveOwner() {
    Object owner = new Object(); mapper.beginCapture(owner);
    assertThrows(NullPointerException.class, () -> mapper.beginCapture(null));
    assertTrue(mapper.isCaptureOwner(owner));
  }

  @Test void legacySetterInvalidatesOldTokenWithoutAllowingItToReleaseNewCapture() {
    Object owner = new Object(); mapper.beginCapture(owner);
    mapper.setCaptureMode(true); assertFalse(mapper.endCapture(owner));
    assertFalse(mapper.isCaptureOwner(owner));
    mapper.setCaptureMode(false); mapper.beginCapture(owner);
    assertTrue(mapper.endCapture(owner));
  }

  @Test void productionInputResumesAfterCurrentOwnerReleasesButNotAfterOldOwnerReleases() {
    MappedKey key = new MappedKey("fixture", "fixture", Input.Keys.A);
    mapper.add(key);
    try {
      InputProcessor input = mapper.newInputProcessor();
      Object first = new Object(), second = new Object();
      mapper.beginCapture(first); mapper.beginCapture(second);
      input.keyDown(Input.Keys.A); assertFalse(key.isPressed());
      assertFalse(mapper.endCapture(first)); input.keyDown(Input.Keys.A); assertFalse(key.isPressed());
      assertTrue(mapper.endCapture(second)); input.keyDown(Input.Keys.A); assertTrue(key.isPressed());
      input.keyUp(Input.Keys.A); assertFalse(key.isPressed());
      mapper.setCaptureMode(true); input.keyDown(Input.Keys.A); assertFalse(key.isPressed());
      mapper.setCaptureMode(false); input.keyDown(Input.Keys.A); assertTrue(key.isPressed());
      input.keyUp(Input.Keys.A); assertFalse(key.isPressed());
    } finally { mapper.remove(key); }
  }
}
