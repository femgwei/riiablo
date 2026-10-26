package com.riiablo.presentation;

import com.riiablo.presentation.ClientGameplayContext;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.riiablo.codec.DC6;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.graphics.BlendMode;
import com.riiablo.loader.DC6Loader;
import com.riiablo.map.Map;
import com.riiablo.widget.DCWrapper;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

import net.mostlyoriginal.api.event.common.Subscribe;

public class ZoneEntryDisplayer extends BaseSystem {
  @Wire(name = "stage")
  protected Stage stage;

  private static final String[] ACT_NAME = {"act1", "act2", "act3", "act4", "expansion"};

  private boolean loaded;
  private AssetDescriptor<DC6> entryDescriptor;
  private DCWrapper entryImage;
  private AssetManager assets;
  private AssetReferenceScope references;
  private boolean disposed;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (disposed) return;
    if (event.entityId != clientContext.playerId()) return;
    if (event.zone == null) return; // zone 可能为 null，跳过显示
    displayEntry(event.zone);
  }

  @Override
  protected void initialize() {
    assets = clientContext.assets();
    entryImage = new DCWrapper();
    entryImage.setScaling(Scaling.none);
    entryImage.setAlign(Align.center);
    entryImage.setBlendMode(BlendMode.DARKEN);
    if (clientContext.colors() == null) return;
    entryImage.setColor(clientContext.colors().darkenRed);
    entryImage.setVisible(false);
    stage.addActor(entryImage);
  }

  @Override
  protected void processSystem() {
    if (disposed || loaded || entryDescriptor == null || !assets.isLoaded(entryDescriptor)) return;
    loaded = true;

    entryImage.setDrawable(assets.get(entryDescriptor));
    entryImage.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.8f, Align.center);
    entryImage.clearActions();
    entryImage.addAction(Actions.sequence(
        Actions.show(),
        Actions.alpha(1),
        Actions.delay(4, Actions.fadeOut(1, Interpolation.pow2In)),
        Actions.hide()));
  }

  private void displayEntry(Map.Zone zone) {
    clearEntry();
    if (assets == null) assets = java.util.Objects.requireNonNull(clientContext.assets(), "assets");

    // TODO: i18n? Not sure if these have translations.
    String entryFile = zone.level.Id == 8 ? "A1Q1" : zone.level.EntryFile;
    String language = clientContext.languageCode() == null ? "eng" : clientContext.languageCode();
    AssetDescriptor<DC6> descriptor = new AssetDescriptor<>("data\\local\\ui\\" + language + "\\"
        + ACT_NAME[zone.map.getAct()] + "\\" + entryFile + ".dc6",
        DC6.class, DC6Loader.DC6Parameters.COMBINE);
    AssetReferenceScope next = new AssetReferenceScope(assets);
    next.load(descriptor);
    references = next;
    entryDescriptor = descriptor;
  }

  private void clearEntry() {
    AssetReferenceScope closing = references;
    references = null;
    entryDescriptor = null;
    loaded = false;
    try (Disposal cleanup = new Disposal()) {
      if (entryImage != null) {
        cleanup.run(entryImage::clearActions);
        cleanup.run(() -> entryImage.setVisible(false));
        cleanup.run(() -> entryImage.setDrawable((TextureRegion) null));
      }
      cleanup.dispose(closing);
    }
  }

  @Override protected void dispose() {
    if (disposed) return;
    disposed = true;
    DCWrapper closing = entryImage;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::clearEntry);
      if (closing != null) cleanup.run(closing::remove);
    } finally {
      entryImage = null;
    }
  }
}

