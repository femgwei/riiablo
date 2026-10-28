package com.riiablo.presentation;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.Colors;
import com.riiablo.Files;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.StringTBLs;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.Palettes;
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
public final class ClientGameplayContext implements ClientPresentationServices, ClientMonsterAudioServices,
    ClientUiServices {
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
  private final String languageCode;
  private final MPQFileHandleResolver mpqs;
  private final Palettes palettes;

  public ClientGameplayContext(CharData charData, IntSupplier playerId,
      Supplier<GameScreen> game, IntConsumer actTransition, AssetManager assets,
      Audio audio, com.riiablo.Cursor cursor, Files files, PaletteIndexedBatch batch,
      Fonts fonts, Colors colors, StringTBLs strings, FileHandle saves, FileHandle home) {
    this(charData, playerId, game, actTransition, assets, audio, cursor, files, batch,
        fonts, colors, strings, saves, home, null);
  }

  public ClientGameplayContext(CharData charData, IntSupplier playerId,
      Supplier<GameScreen> game, IntConsumer actTransition, AssetManager assets,
      Audio audio, com.riiablo.Cursor cursor, Files files, PaletteIndexedBatch batch,
      Fonts fonts, Colors colors, StringTBLs strings, FileHandle saves, FileHandle home,
      String languageCode) {
    this(charData, playerId, game, actTransition, assets, audio, cursor, files, batch,
        fonts, colors, strings, saves, home, languageCode, null);
  }

  public ClientGameplayContext(CharData charData, IntSupplier playerId,
      Supplier<GameScreen> game, IntConsumer actTransition, AssetManager assets,
      Audio audio, com.riiablo.Cursor cursor, Files files, PaletteIndexedBatch batch,
      Fonts fonts, Colors colors, StringTBLs strings, FileHandle saves, FileHandle home,
      String languageCode, MPQFileHandleResolver mpqs) {
    this(charData, playerId, game, actTransition, assets, audio, cursor, files, batch,
        fonts, colors, strings, saves, home, languageCode, mpqs, null);
  }

  public ClientGameplayContext(CharData charData, IntSupplier playerId,
      Supplier<GameScreen> game, IntConsumer actTransition, AssetManager assets,
      Audio audio, com.riiablo.Cursor cursor, Files files, PaletteIndexedBatch batch,
      Fonts fonts, Colors colors, StringTBLs strings, FileHandle saves, FileHandle home,
      String languageCode, MPQFileHandleResolver mpqs, Palettes palettes) {
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
    this.languageCode = languageCode;
    this.mpqs = mpqs;
    this.palettes = palettes;
  }

  public CharData charData() { return charData; }
  public int playerId() { return playerId.getAsInt(); }
  @Override public Supplier<GameScreen> gameSupplier() { return game; }
  public GameScreen game() { return game.get(); }
  @Override public boolean isGameActive() { return game() != null; }
  @Override public boolean inventoryVisible() {
    GameScreen screen = game();
    return screen != null && screen.inventoryPanel != null && screen.inventoryPanel.isVisible();
  }
  @Override public boolean movementBlocked() {
    GameScreen screen = game();
    return screen != null && screen.hireMercenaryPanel != null
        && screen.hireMercenaryPanel.isVisible();
  }
  @Override public boolean mercenaryPortraitContains(float stageX, float stageY) {
    GameScreen screen = game();
    return screen != null && screen.mercenaryHud != null && screen.mercenaryHud.isVisible()
        && screen.mercenaryHud.containsStagePoint(stageX, stageY);
  }
  @Override public boolean useMercenaryCursorPotion() {
    GameScreen screen = game();
    return screen != null && screen.mercenaryHud != null && screen.mercenaryHud.useCursorPotion();
  }
  @Override public void showHirelingPanel() {
    GameScreen screen = game();
    if (screen != null && screen.hirelingPanel != null) screen.setLeftPanel(screen.hirelingPanel);
  }
  @Override public void cancelVendorTradeMode() {
    GameScreen screen = game();
    if (screen != null) screen.cancelVendorTradeMode();
  }
  @Override public void closeTradePanelsForMovement() {
    GameScreen screen = game();
    if (screen != null) screen.closeTradePanelsForMovement();
  }

  /** Legacy result routes for callers that do not install a ClientNetworkSession router. */
  public void presentNpcServiceResult(com.riiablo.net.packet.d2gs.NpcServiceResult result) {
    GameScreen screen = game();
    if (screen == null) return;
    if (screen.hireMercenaryPanel != null) screen.hireMercenaryPanel.applyNetworkResult(result);
    if (screen.vendorPanel != null) screen.vendorPanel.applyNetworkResult(result);
  }
  public void presentTradeState(com.riiablo.engine.client.ClientNetworkReceiver receiver) {
    GameScreen screen = game();
    if (screen != null && screen.tradePanel != null) screen.tradePanel.applyNetworkState(receiver);
  }

  public com.badlogic.gdx.scenes.scene2d.Actor createWorldLabel(String text) {
    com.riiablo.widget.Label label = new com.riiablo.widget.Label(fonts().font16);
    label.setAlignment(com.badlogic.gdx.utils.Align.center);
    label.getStyle().background = com.riiablo.widget.Label.MODAL;
    label.setText(text);
    return label;
  }
  public void updateWorldLabel(com.badlogic.gdx.scenes.scene2d.Actor actor, String text) {
    if (actor instanceof com.riiablo.widget.Label) ((com.riiablo.widget.Label) actor).setText(text);
  }
  public AssetManager assets() { return assets; }
  @Override public int listener() { return playerId(); }
  @Override public Audio audio() { return audio; }
  public com.riiablo.Cursor cursor() { return cursor; }
  public Files files() { return files; }
  @Override public PaletteIndexedBatch batch() { return batch; }
  @Override public void playEntitySound(int entityId, String sound, boolean loop) {
    com.riiablo.audio.MonsterAudio.play(entityId, sound, loop);
  }
  public Fonts fonts() { return fonts; }
  public Colors colors() { return colors; }
  public StringTBLs strings() { return strings; }
  public FileHandle saves() { return saves; }
  public FileHandle home() { return home; }
  public String languageCode() { return languageCode; }
  public MPQFileHandleResolver mpqs() { return mpqs; }
  public Palettes palettes() { return palettes; }
  public com.riiablo.COFs cofs() { return Riiablo.cofs; }
  public com.riiablo.GdxCvarManager cvars() { return Riiablo.cvars; }

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
        Riiablo.fonts, Riiablo.colors, Riiablo.string, Riiablo.saves, Riiablo.home,
        Riiablo.language == null ? null : Riiablo.language.resourceCode, Riiablo.mpqs,
        Riiablo.palettes);
  }
}







