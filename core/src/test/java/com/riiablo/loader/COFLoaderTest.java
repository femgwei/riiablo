package com.riiablo.loader;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;

class COFLoaderTest {
  @Test void absentAsyncCofFailsInsteadOfReturningAnUnfinishableNullAsset() {
    COFLoader loader = new COFLoader(name -> null);
    GdxRuntimeException error = assertThrows(GdxRuntimeException.class,
        () -> loader.loadAsync(null, "boOPHTH.cof", null, null));
    assertTrue(error.getMessage().contains("boOPHTH.cof"));
  }

  @Test void absentSyncCofFailsInsteadOfReturningAnUnfinishableNullAsset() {
    COFLoader loader = new COFLoader(name -> null);
    assertThrows(GdxRuntimeException.class,
        () -> loader.loadSync(null, "boOPHTH.cof", null, null));
  }

  @Test void nonexistentFileHandleAlsoFailsExplicitly() {
    FileHandle missing = new FileHandle("missing.cof") {
      @Override public boolean exists() { return false; }
    };
    COFLoader loader = new COFLoader(name -> missing);
    assertThrows(GdxRuntimeException.class,
        () -> loader.loadAsync(null, "missing.cof", missing, null));
    assertThrows(GdxRuntimeException.class,
        () -> loader.loadSync(null, "missing.cof", missing, null));
  }
}
