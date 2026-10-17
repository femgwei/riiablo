package com.riiablo.map;

import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.util.Objects;

/** Borrowed data/asset services for one map; the host retains disposal ownership. */
public final class MapResources {
  private final Files files;
  private final AssetManager assets;
  private final MPQFileHandleResolver mpqs;
  private final com.riiablo.COFs cofs;
  private final com.riiablo.GdxCvarManager cvars;

  public MapResources(Files files, AssetManager assets, MPQFileHandleResolver mpqs) {
    this(files, assets, mpqs, null, null);
  }

  public MapResources(Files files, AssetManager assets, MPQFileHandleResolver mpqs,
      com.riiablo.COFs cofs, com.riiablo.GdxCvarManager cvars) {
    this.files = Objects.requireNonNull(files, "files");
    this.assets = assets;
    this.mpqs = mpqs;
    this.cofs = cofs;
    this.cvars = cvars;
  }

  public Files files() { return files; }
  public AssetManager assets() { return assets; }
  public MPQFileHandleResolver mpqs() { return mpqs; }
  public com.riiablo.COFs cofs() { return cofs; }
  public com.riiablo.GdxCvarManager cvars() { return cvars; }

  public static MapResources fromGlobals() {
    return new MapResources(Riiablo.files, Riiablo.assets, Riiablo.mpqs, Riiablo.cofs, Riiablo.cvars);
  }
}
