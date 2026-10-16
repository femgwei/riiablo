package com.riiablo.entity;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.Direction;
import com.riiablo.engine.Engine.Player;
import com.riiablo.item.ItemCodes;
import com.riiablo.save.D2S;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.util.AssetReferenceScope;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Deprecated
public class CharacterPreview extends Entity implements Disposable {
  private static final String TAG = "CharacterPreview";
  private static final boolean DEBUG       = true;
  private static final boolean DEBUG_STATE = DEBUG && true;

  final D2S d2s;
  private final AssetManager assets;
  private final AssetReferenceScope references;
  private final Set<String> requested = new HashSet<>();
  private boolean disposed;

  public CharacterPreview(D2S d2s) {
    this(d2s, Riiablo.assets);
  }

  public CharacterPreview(D2S d2s, AssetManager assets) {
    super(Type.PLR, "char-preview", Player.getToken(d2s.charClass()), d2s.composites(), d2s.colors());
    this.d2s = d2s;
    this.assets = Objects.requireNonNull(assets, "assets");
    references = new AssetReferenceScope(assets);
    setMode(Player.MODE_TN);
    setWeapon(Entity.WEAPON_1HS);
    angle(Direction.direction8ToRadians(Direction.DOWN));
    updateWeaponClass();
  }

  @Override protected AssetManager assetManager() { return assets; }

  @Override protected void loadAsset(AssetDescriptor descriptor) {
    if (disposed) throw new IllegalStateException("preview is disposed");
    // A preview retains each visited layer until retirement, not one reference per redraw.
    if (requested.contains(descriptor.fileName)) return;
    references.load(descriptor);
    requested.add(descriptor.fileName);
  }

  @Override public void act(float delta) { if (!disposed) super.act(delta); }

  @Override public void draw(PaletteIndexedBatch batch) { if (!disposed) super.draw(batch); }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    animation = overlay = null;
    java.util.Arrays.fill(layer, null);
    dirty = load = Dirty.NONE;
    requested.clear();
    references.dispose();
  }

  private void updateWeaponClass() {
    final byte[] composites = d2s.composites();
    String RH = ItemCodes.getCode(composites[COF.Component.RH]);
    String LH = ItemCodes.getCode(composites[COF.Component.LH]);
    String SH = ItemCodes.getCode(composites[COF.Component.SH]);

    Weapons.Entry RHEntry = Riiablo.files.weapons.get(StringUtils.defaultString(RH).toLowerCase());
    com.riiablo.item.Type RHtype = RHEntry != null ? com.riiablo.item.Type.get(RHEntry.type) : null;
    Weapons.Entry LHEntry = Riiablo.files.weapons.get(StringUtils.defaultString(LH).toLowerCase());
    com.riiablo.item.Type LHtype = LHEntry != null ? com.riiablo.item.Type.get(LHEntry.type) : null;

    if (DEBUG_STATE) {
      Gdx.app.debug(TAG, "RH = " + RH + "; " + RHEntry + "; "+ RHtype);
      Gdx.app.debug(TAG, "LH = " + LH + "; " + LHEntry + "; "+ LHtype);
      Gdx.app.debug(TAG, "SH = " + SH);
    }

    if (LHEntry != null && RHEntry != null) {
      if (       LHEntry.wclass.equals("1hs") && RHEntry.wclass.equals("1hs")) {
        setWeapon(WEAPON_1SS); // Left Swing Right Swing
      } else if (LHEntry.wclass.equals("1hs") && RHEntry.wclass.equals("1ht")) {
        setWeapon(WEAPON_1ST); // Left Swing Right Thrust
      } else if (LHEntry.wclass.equals("1ht") && RHEntry.wclass.equals("1hs")) {
        setWeapon(WEAPON_1JS); // Left Jab Right Swing
      } else if (LHEntry.wclass.equals("1ht") && RHEntry.wclass.equals("1ht")) {
        setWeapon(WEAPON_1JT); // Left Jab Right Thrust
      } else if (LHtype.is(com.riiablo.item.Type.MISS) || RHtype.is(com.riiablo.item.Type.MISS)) {
        setWeapon((byte) Riiablo.files.WeaponClass.index(LHtype.is(com.riiablo.item.Type.MISS) ? LHEntry.wclass : RHEntry.wclass));
      } else if (LHtype.is(com.riiablo.item.Type.H2H)  || RHtype.is(com.riiablo.item.Type.H2H)) {
        setWeapon(WEAPON_HT2); // Two Hand-to-Hand
      } else {
        setWeapon(WEAPON_HTH);
        Gdx.app.error(TAG, String.format(
            "Unknown weapon combination: LH=%s RH=%s", LHEntry.wclass, RHEntry.wclass));
      }
    } else if (LHEntry != null || RHEntry != null) {
      RH = ObjectUtils.firstNonNull(RH, LH);
      LH = null;
      RHEntry = ObjectUtils.firstNonNull(RHEntry, LHEntry);
      LHEntry = null;
      RHtype = ObjectUtils.firstNonNull(RHtype, LHtype);
      LHtype = null;
      if (RHtype.is(com.riiablo.item.Type.BOW)) {
        LH = RH;
        RH = null;
        LHEntry = RHEntry;
        RHEntry = null;
        setWeapon((byte) Riiablo.files.WeaponClass.index(LHEntry.wclass));
      } else if (RHtype.is(com.riiablo.item.Type.WEAP)) { // make sure weap and not e.g. misl, might not be required
        setWeapon((byte) Riiablo.files.WeaponClass.index(RHEntry.wclass));
      } else {
        setWeapon(WEAPON_HTH);
      }
    } else {
      setWeapon(WEAPON_HTH);
    }

    setComponent(COF.Component.RH, RH != null ? composites[COF.Component.RH] : 0);
    setComponent(COF.Component.LH, LH != null ? composites[COF.Component.LH] : 0);
    setComponent(COF.Component.SH, SH != null ? composites[COF.Component.SH] : 0);
  }
}
