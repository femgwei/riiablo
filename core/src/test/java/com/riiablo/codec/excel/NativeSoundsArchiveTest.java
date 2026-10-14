package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.mpq.MPQ;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Optional check against the user's unmodified 1.10f archives. */
class NativeSoundsArchiveTest {
  @Test
  void originalSoundsHeaderHasGroupSizeButNoModernRedirectOrWeight() {
    String home = System.getenv("D2_110F_HOME");
    assumeTrue(home != null && !home.isEmpty(), "D2_110F_HOME not configured");
    Application previous = Gdx.app;
    Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
        new Class<?>[] {Application.class}, (proxy, method, args) -> null);
    try {
      boolean found = false;
      for (String archive : new String[] {"patch_d2.mpq", "d2exp.mpq", "d2data.mpq"}) {
        FileHandle file = new FileHandle(home).child(archive);
        if (!file.exists()) continue;
        MPQ mpq = MPQ.loadFromFile(file);
        String path = "data\\global\\excel\\sounds.txt";
        if (!mpq.contains(path)) continue;
        String header = new String(mpq.readBytes(path), StandardCharsets.US_ASCII)
            .split("\\r?\\n", 2)[0];
        System.out.println(archive + " Sounds.txt header: " + header);
        assertTrue(header.contains("Group Size"));
        assertFalse(header.contains("Redirect"));
        assertFalse(header.contains("Group Weight"));
        found = true;
      }
      assertTrue(found, "No Sounds.txt found in original archives");
    } finally {
      Gdx.app = previous;
    }
  }
}
