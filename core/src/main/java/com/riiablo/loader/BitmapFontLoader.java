package com.riiablo.loader;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

import com.riiablo.codec.DC6;
import com.riiablo.codec.FontAtlasCache;
import com.riiablo.codec.FontTBL;
import com.riiablo.graphics.BlendMode;

public class BitmapFontLoader extends AsynchronousAssetLoader<FontTBL.BitmapFont, BitmapFontLoader.Params> {

  String name;
  DC6 dc6;
  FontTBL.BitmapFontData data;
  FontAtlasCache.CachedData cached;
  FileHandle cacheFile;

  public BitmapFontLoader(FileHandleResolver resolver) {
    super(resolver);
  }

  @Override
  public void loadAsync(AssetManager assets, String fileName, FileHandle file, Params params) {
    String fontName = file.pathWithoutExtension();
    FileHandle tblFile = file;
    FileHandle dc6File = resolve(fontName + ".DC6");
    FileHandle currentCacheFile = FontAtlasCache.fileFor(tblFile, dc6File);
    FontAtlasCache.CachedData currentCache = FontAtlasCache.read(currentCacheFile);
    if (currentCache != null) {
      cached = currentCache;
      data = FontTBL.dataFromCache(cached);
      cached = null;
    } else {
      dc6 = assets.get(fontName.replace('\\', '/') + ".DC6", DC6.class); // workaround for libgdx path delimiter constraint
      FontTBL tbl = FontTBL.loadFromFile(resolve(fontName + ".TBL"));
      data = tbl.data(dc6);
      FontAtlasCache.write(currentCacheFile, data);
    }
    data.blendMode = params != null ? params.blendMode : BlendMode.LUMINOSITY_TINT;
  }

  @Override
  public FontTBL.BitmapFont loadSync(AssetManager assets, String fileName, FileHandle file, Params params) {
    FontTBL.BitmapFont font = new FontTBL.BitmapFont(data);
    name = null;
    dc6 = null;
    data = null;
    cacheFile = null;
    return font;
  }

  @Override
  public Array<AssetDescriptor> getDependencies(String assets, FileHandle file, Params params) {
    name = file.pathWithoutExtension();
    // Cache probing is deliberately deferred to loadAsync. getDependencies is
    // called by AssetManager on the render thread; hashing and reading a full
    // CJK atlas here made screen transitions appear frozen.
    cached = null;
    return Array.<AssetDescriptor>with(new AssetDescriptor<>(name + ".DC6", DC6.class));
  }

  public static class Params extends AssetLoaderParameters<FontTBL.BitmapFont> {
    public int blendMode;

    public static Params of(int blendMode) {
      Params params = new Params();
      params.blendMode = blendMode;
      return params;
    }
  }
}
