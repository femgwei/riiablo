package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Minimal opt-in GL smoke test for the Automap integration path.
 *
 * This deliberately does not load a game or a character.  It verifies that a
 * small real LWJGL window can create a GL context, render a few frames, and
 * terminate without hanging.  Full town/exit movement is layered on top of
 * this test once a deterministic client fixture is available.
 */
@Tag("visual")
class AutomapWindowIntegrationTest {
  @Test
  void opensSmallWindowRendersAndExits() throws Exception {
    CountDownLatch disposed = new CountDownLatch(1);
    AtomicInteger frames = new AtomicInteger();
    AtomicInteger width = new AtomicInteger();
    AtomicInteger height = new AtomicInteger();

    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("riiablo automap smoke test");
    config.setWindowedMode(320, 240);
    config.setResizable(false);
    config.setForegroundFPS(60);
    config.setIdleFPS(60);
    config.useVsync(false);
    config.disableAudio(true);

    Lwjgl3Application application = new Lwjgl3Application(new ApplicationAdapter() {
      @Override
      public void create() {
        width.set(Gdx.graphics.getWidth());
        height.set(Gdx.graphics.getHeight());
      }

      @Override
      public void render() {
        if (frames.incrementAndGet() >= 3) Gdx.app.exit();
      }

      @Override
      public void dispose() {
        disposed.countDown();
      }
    }, config);

    try {
      assertTrue(disposed.await(10, TimeUnit.SECONDS),
          "LWJGL smoke window did not terminate within 10 seconds");
      assertTrue(frames.get() >= 3, "Expected at least three rendered frames");
      assertTrue(width.get() > 0 && height.get() > 0,
          "GL context reported an invalid drawable size");
      assertEquals(320, width.get(), "Unexpected smoke-test width");
      assertEquals(240, height.get(), "Unexpected smoke-test height");
    } finally {
      application.exit();
    }
  }
}
