package com.riiablo.widget;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.D2S;
import com.riiablo.entity.CharacterPreview;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;
import java.util.Objects;

public class CharacterSelectButton extends Table implements Disposable {
  public static final int WIDTH  = 272;
  public static final int HEIGHT = 93;

  static final AssetDescriptor<DC6> charselectboxDescriptor = new AssetDescriptor<>("data\\global\\ui\\CharSelect\\charselectbox.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  private final AssetManager assets;
  private final AssetReferenceScope references;
  private CharacterPreview character;
  private boolean disposed;

  D2S d2s;
  FileHandle file;
  EntityWrapper preview;
  Label title;
  Label name;
  Label levelClass;
  Label expansion;

  public CharacterSelectButton(D2S d2s, FileHandle file) {
    this(d2s, file, Riiablo.assets);
  }

  public CharacterSelectButton(D2S d2s, FileHandle file, AssetManager assets) {
    this.assets = Objects.requireNonNull(assets, "assets");
    references = new AssetReferenceScope(assets);
    try {
      references.load(charselectboxDescriptor);
      create(d2s, file);
    } catch (RuntimeException | Error failure) {
      try { dispose(); } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void create(D2S d2s, FileHandle file) {
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);

    preview = new EntityWrapper();

    title = new Label(Riiablo.fonts.font16);
    title.setColor(Riiablo.colors.gold);

    name = new Label(Riiablo.fonts.font16);
    name.setColor(Riiablo.colors.gold);

    levelClass = new Label(Riiablo.fonts.font16);

    expansion = new Label(Riiablo.fonts.font16);
    expansion.setColor(Riiablo.colors.green);

    Table previewColumn = new Table();
    previewColumn.add(preview).width(72).growY();

    Table textColumn = new Table();
    textColumn.add(title).left().row();
    textColumn.add(name).left().row();
    textColumn.add(levelClass).left().row();
    textColumn.add(expansion).left().row();

    align(Align.topLeft);
    pad(4);
    add(previewColumn).growY();
    add(textColumn).growX().top();

    //setDebug(true, true);
    set(d2s, file);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    CharacterPreview closing = character;
    character = null;
    if (preview != null) preview.set(null); // wrapper borrows; only this button owns the preview
    setBackground((Drawable) null);
    setTouchable(Touchable.disabled);
    try (Disposal cleanup = new Disposal()) {
      cleanup.dispose(closing);
      cleanup.dispose(references);
    }
  }

  public void set(D2S d2s, FileHandle file) {
    if (disposed) throw new IllegalStateException("character button is disposed");
    CharacterPreview next = new CharacterPreview(d2s, assets);
    try {
      updateLabels(d2s);
    } catch (RuntimeException | Error failure) {
      try { next.dispose(); } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    CharacterPreview previous = character;
    character = next;
    preview.set(next);
    this.d2s = d2s;
    this.file = file;
    if (previous != null) previous.dispose();
  }

  private void updateLabels(D2S d2s) {
    setName(d2s.name());
    title.setText(d2s.getProgressionString()); // TODO: i18n? This may be hard-coded in-game
    name.setText(d2s.name());
    name.setColor(d2s.isHardcore() ? Riiablo.colors.red : Riiablo.colors.gold);
    levelClass.setText(Riiablo.string.format(5017, d2s.level()) + " " + Riiablo.string.lookup(CharacterClass.get(d2s.charClass()).name));
    expansion.setText(d2s.isExpansion() ? 11077 : -1);
  }

  public D2S getD2S() {
    return d2s;
  }

  public FileHandle getFile() {
    return file;
  }

  public void select() {
    if (disposed) throw new IllegalStateException("character button is disposed");
    assets.finishLoadingAsset(charselectboxDescriptor);
    TextureRegion charselectbox = assets.get(charselectboxDescriptor).getTexture();
    setBackground(new TextureRegionDrawable(charselectbox));
  }

  public void deselect() {
    setBackground((Drawable) null);
  }
}
