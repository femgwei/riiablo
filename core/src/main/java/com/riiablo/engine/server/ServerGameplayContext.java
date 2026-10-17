package com.riiablo.engine.server;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import java.util.Objects;

/**
 * Explicit data bindings for one authoritative server world.
 *
 * <p>Server systems should read game data and the world seed from this object
 * instead of consulting process-wide client state. The global factory remains
 * only as a compatibility bridge for legacy fixtures that do not register a
 * server world context.</p>
 */
public final class ServerGameplayContext {
  private final Files files;
  private final int gameSeed;
  private final CharData character;
  private final com.riiablo.codec.D2 animations;
  private final java.util.function.Consumer<CharData> characterStore;

  public ServerGameplayContext(Files files, int gameSeed) {
    this(files, gameSeed, null);
  }

  public ServerGameplayContext(Files files, int gameSeed, CharData character) {
    this(files, gameSeed, character, null);
  }

  public ServerGameplayContext(Files files, int gameSeed, CharData character,
      com.riiablo.codec.D2 animations) {
    this(files, gameSeed, character, animations, data -> {});
  }

  public ServerGameplayContext(Files files, int gameSeed, CharData character,
      com.riiablo.codec.D2 animations, java.util.function.Consumer<CharData> characterStore) {
    this.files = Objects.requireNonNull(files, "files");
    this.gameSeed = gameSeed;
    this.character = character;
    this.animations = animations;
    this.characterStore = Objects.requireNonNull(characterStore, "characterStore");
  }

  public Files files() {
    return files;
  }

  public int gameSeed() {
    return gameSeed;
  }

  public CharData character() {
    return character;
  }

  public com.riiablo.codec.D2 animations() { return animations; }

  public void persistCharacter(CharData data) { characterStore.accept(data); }

  /** Compatibility bridge for old tests and tools that have no server host. */
  public static ServerGameplayContext fromGlobals() {
    return new ServerGameplayContext(Riiablo.files, Riiablo.gameSeed, Riiablo.charData, Riiablo.anim,
        data -> { if (Riiablo.saves != null) com.riiablo.save.D2SWriter.INSTANCE.save(data); });
  }
}
