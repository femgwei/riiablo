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
    if (cached != null) {
      data = FontTBL.dataFromCache(cached);
      cached = null;
    } else {
      dc6 = assets.get(name.replace('\\', '/') + ".DC6", DC6.class); // workaround for libgdx path delimiter constraint
      FontTBL tbl = FontTBL.loadFromFile(resolve(name + ".TBL"));
      data = tbl.data(dc6);
      FontAtlasCache.write(cacheFile, data);
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
    FileHandle dc6File = resolve(name + ".DC6");
    cacheFile = FontAtlasCache.fileFor(file, dc6File);
    cached = FontAtlasCache.read(cacheFile);
    if (cached != null) return null;
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
