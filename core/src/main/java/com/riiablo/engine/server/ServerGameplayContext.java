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
  private final com.riiablo.mpq.MPQFileHandleResolver mpqs;
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
    this(files, gameSeed, character, animations, null, characterStore);
  }

  public ServerGameplayContext(Files files, int gameSeed, CharData character,
      com.riiablo.codec.D2 animations,
      com.riiablo.mpq.MPQFileHandleResolver mpqs,
      java.util.function.Consumer<CharData> characterStore) {
    this.files = Objects.requireNonNull(files, "files");
    this.gameSeed = gameSeed;
    this.character = character;
    this.animations = animations;
    this.mpqs = mpqs;
    this.characterStore = Objects.requireNonNull(characterStore, "characterStore");
  }

  public Files files() {
    return files;
  }

  /** One world's table, game mode and state-duration ownership for combat. */
  public com.riiablo.engine.server.combat.NativeCombatContext combatContext(int difficulty) {
    return com.riiablo.engine.server.combat.NativeCombatContext.forGameplay(difficulty,
        files.DifficultyLevels != null ? files.DifficultyLevels.get(difficulty) : null,
        character == null || character.isExpansion());
  }

  /** Compatibility only: production server hosts register their own context. */
  public static com.riiablo.engine.server.combat.NativeCombatContext legacyCombatContext(int difficulty) {
    Files files = legacyFiles();
    return com.riiablo.engine.server.combat.NativeCombatContext.forGameplay(difficulty,
        files != null && files.DifficultyLevels != null
            ? files.DifficultyLevels.get(difficulty) : null,
        Riiablo.charData == null || Riiablo.charData.isExpansion());
  }

  /** Shared legacy table binding; read once per factory invocation. */
  private static Files legacyFiles() { return Riiablo.files; }

  public int gameSeed() {
    return gameSeed;
  }

  public CharData character() {
    return character;
  }

  public com.riiablo.codec.D2 animations() { return animations; }

  public com.riiablo.mpq.MPQFileHandleResolver mpqs() { return mpqs; }

  public void persistCharacter(CharData data) { characterStore.accept(data); }

  /** Persistence adapter for legacy hosts that have no registered server world. */
  public static void persistLegacyCharacter(CharData data) {
    if (Riiablo.saves != null) com.riiablo.save.D2SWriter.INSTANCE.save(data);
  }

  /** Compatibility bridge for old tests and tools that have no server host. */
  public static ServerGameplayContext fromGlobals() {
    return new ServerGameplayContext(legacyFiles(), Riiablo.gameSeed, Riiablo.charData, Riiablo.anim,
        Riiablo.mpqs,
        ServerGameplayContext::persistLegacyCharacter);
  }
}
