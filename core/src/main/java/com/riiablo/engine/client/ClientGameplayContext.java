package com.riiablo.engine.client;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.Colors;
import com.riiablo.Files;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.StringTBLs;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Explicit host bindings for one client gameplay world.
 *
 * <p>The world owns systems, while the screen/application owns these services.
 * The bindings are read live only where a value is intentionally dynamic (the
 * local entity and screen); all session resources are captured at construction
 * so a later screen cannot leak into an older world. The {@link #fromGlobals()}
 * factory exists for legacy tests that construct systems outside Artemis.
 */
public final class ClientGameplayContext {
  private final CharData charData;
  private final IntSupplier playerId;
  private final Supplier<GameScreen> game;
  private final IntConsumer actTransition;
  private final AssetManager assets;
  private final Audio audio;
  private final com.riiablo.Cursor cursor;
  private final Files files;
  private final PaletteIndexedBatch batch;
  private final Fonts fonts;
  private final Colors colors;
  private final StringTBLs strings;
  private final FileHandle saves;
  private final FileHandle home;

  public ClientGameplayContext(CharData charData, IntSupplier playerId,
      Supplier<GameScreen> game, IntConsumer actTransition, AssetManager assets,
      Audio audio, com.riiablo.Cursor cursor, Files files, PaletteIndexedBatch batch,
      Fonts fonts, Colors colors, StringTBLs strings, FileHandle saves, FileHandle home) {
    this.charData = charData;
    this.playerId = Objects.requireNonNull(playerId, "playerId");
    this.game = Objects.requireNonNull(game, "game");
    this.actTransition = actTransition;
    this.assets = assets;
    this.audio = audio;
    this.cursor = cursor;
    this.files = files;
    this.batch = batch;
    this.fonts = fonts;
    this.colors = colors;
    this.strings = strings;
    this.saves = saves;
    this.home = home;
  }

  public CharData charData() { return charData; }
  public int playerId() { return playerId.getAsInt(); }
  public GameScreen game() { return game.get(); }
  public AssetManager assets() { return assets; }
  public Audio audio() { return audio; }
  public com.riiablo.Cursor cursor() { return cursor; }
  public Files files() { return files; }
  public PaletteIndexedBatch batch() { return batch; }
  public Fonts fonts() { return fonts; }
  public Colors colors() { return colors; }
  public StringTBLs strings() { return strings; }
  public FileHandle saves() { return saves; }
  public FileHandle home() { return home; }

  /** Delivers an accepted server act transition to the owning screen. */
  public boolean transitionAct(int act) {
    if (actTransition == null) return false;
    actTransition.accept(act);
    return true;
  }

  /** Compatibility bridge for old tests and tools that have no host object. */
  public static ClientGameplayContext fromGlobals() {
    return new ClientGameplayContext(
        Riiablo.charData,
        () -> Riiablo.game == null ? -1 : Riiablo.game.player,
        () -> Riiablo.game,
        game -> { if (Riiablo.game != null) Riiablo.game.setAct(game); },
        Riiablo.assets, Riiablo.audio, Riiablo.cursor, Riiablo.files, Riiablo.batch,
        Riiablo.fonts, Riiablo.colors, Riiablo.string, Riiablo.saves, Riiablo.home);
  }
}
