package com.riiablo.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import com.riiablo.Riiablo;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.logger.MDC;

public enum D2SWriter {
  INSTANCE;

  private static final Logger log = LogManager.getLogger(D2SWriter.class);

  private static final int VERSION = D2S.VERSION_110;

  private final D2SWriter96 writer96 = new D2SWriter96();

  /**
   * Writes a D2S to byte array.
   */
  public byte[] writeD2S(D2S d2s) {
    log.trace("Writing d2s...");
    try {
      MDC.put("d2s.name", d2s.name);
      MDC.put("d2s.version", d2s.version);

      switch (d2s.version) {
        case D2S.VERSION_110:
          return writer96.writeD2S(d2s);
        case D2S.VERSION_100:
        case D2S.VERSION_107:
        case D2S.VERSION_108:
        case D2S.VERSION_109:
        default:
          log.error("Unsupported d2s version: " + D2S.getVersionString(d2s.version));
          return null;
      }
    } finally {
      MDC.remove("d2s.version");
      MDC.remove("d2s.name");
    }
  }

  /**
   * Writes a D2S to a file.
   */
  public boolean writeD2S(D2S d2s, FileHandle file) {
    byte[] data = writeD2S(d2s);
    if (data == null) {
      return false;
    }

    try {
      file.writeBytes(data, false);
      log.info("Saved character '{}' to {}", d2s.name, file.path());
      return true;
    } catch (Exception e) {
      log.error("Failed to write D2S file: " + file.path(), e);
      return false;
    }
  }

  /**
   * Saves a CharData to a D2S file.
   */
  public boolean save(CharData charData) {
    try {
      D2S d2s = D2SWriter96.createD2S(charData);
      String fileName = charData.name + "." + D2S.EXT;
      FileHandle file = Riiablo.saves.child(fileName);
      return writeD2S(d2s, file);
    } catch (IllegalArgumentException e) {
      log.error("Refusing to write an incompatible D2S character name: " + charData.name, e);
      return false;
    }
  }
}
