package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.codec.DCC;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import com.riiablo.save.D2SWriter96;
import com.riiablo.widget.CharacterSelectButton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Real seven-class previews and selection-page retirement; no network or playable-save acceptance. */
final class OffscreenCharacterLifecycle {
  static void verify(String output, Screen returnScreen) {
    FileHandle originalSaves = Riiablo.saves;
    // Never enumerate, overwrite or delete the user's saves; leave generated fixtures for inspection.
    FileHandle fixtures = Gdx.files.absolute(output).child("character-saves-" + System.nanoTime());
    fixtures.mkdirs();
    try {
      for (CharacterClass type : CharacterClass.values()) {
        CharData data = CharData.obtain(Riiablo.NORMAL, false, "Preview" + type.id, (byte) type.id);
        CreateCharacterScreen.initializeNewCharacter(data, type);
        require(D2SWriter.INSTANCE.writeD2S(D2SWriter96.createD2S(data),
            fixtures.child(data.name + ".d2s")), "generated character fixture");
      }
      Riiablo.saves = fixtures;
      AssetManager assets = Riiablo.assets;
      assets.finishLoading();
      Map<String, Integer> baseline = references(assets);
      int inputBaseline = Riiablo.input.getProcessors().size;
      int requestedCycle = Integer.getInteger("riiablo.offscreen-character-cycle", 1);
      int requestedVariant = Integer.getInteger("riiablo.offscreen-character-variant", 1);
      require(requestedCycle >= 1 && requestedCycle <= 3 && requestedVariant >= 1 && requestedVariant <= 3,
          "cycle/variant in 1..3");
      for (int cycle = 1; cycle <= 3; cycle++) {
        for (int variant = 1; variant <= 3; variant++) {
          // Each heavy seven-class group has its own JVM and unchanged 120-second watchdog.
          if (cycle != requestedCycle || variant != requestedVariant) continue;
          Screen screen = variant == 1 ? new SelectCharacterScreen()
              : variant == 2 ? new SelectCharacterScreen2(null) : new SelectCharacterScreen3(null);
          Riiablo.client.setScreen(screen);
          render(screen, assets);
          Stage stage = field(screen, "stage");
          Array<CharacterSelectButton> characters = field(screen, "characters");
          require(characters.size == CharacterClass.values().length, "all seven character classes");
          int characterCount = characters.size;
          Map<String, Integer> live = references(assets);
          Set<Integer> retiredHandles = new HashSet<>();
          Set<Pixmap> retiredPixels = new HashSet<>();
          sample(assets, retiredHandles, retiredPixels);
          require(!retiredHandles.isEmpty(), "actual DCC textures sampled");
          Riiablo.client.pushScreen(new ScreenAdapter());
          require(Riiablo.input.getProcessors().size == inputBaseline, "hidden selection input detached");
          require(live.equals(references(assets)), "hide retains assets");
          Riiablo.client.popScreen();
          require(Riiablo.client.getScreen() == screen, "same selection restored");
          require(live.equals(references(assets)), "show does not acquire more references");
          for (int handle : retiredHandles) require(Gdx.gl.glIsTexture(handle), "preview survives hide/pop");

          CharacterSelectButton previous = characters.first();
          refresh(screen); // production list-refresh path, without deleting a save
          require(field(previous, "character") == null, "discarded preview detached");
          for (int handle : retiredHandles) require(!Gdx.gl.glIsTexture(handle), "refresh deletes old DCC textures");
          for (Pixmap pixel : retiredPixels) require(pixel.isDisposed(), "refresh frees old DCC pixels");
          render(screen, assets);
          require(live.equals(references(assets)), "recreated list has the same references");

          Set<Integer> handles = new HashSet<>();
          Set<Pixmap> pixels = new HashSet<>();
          sample(assets, handles, pixels);
          int previewAssets = 0;
          for (String name : assets.getAssetNames()) if (assets.getAssetType(name) == DCC.class) previewAssets++;
          Riiablo.client.clearAndSet(returnScreen);
          require(stage.getActors().size == 0, "selection Stage retired by Client");
          screen.dispose(); screen.dispose();
          require(baseline.equals(references(assets)), "all selection references return to baseline");
          require(Riiablo.input.getProcessors().size == inputBaseline, "selection input balanced");
          for (int handle : handles) require(!Gdx.gl.glIsTexture(handle), "final DCC textures deleted");
          for (Pixmap pixel : pixels) require(pixel.isDisposed(), "final DCC pixels freed");
          require(Gdx.gl.glGetError() == 0, "no GL errors");
          String report = "cycle=" + cycle + "\nvariant=" + variant + "\ncharacters=" + characterCount
              + "\npreviewAssets=" + previewAssets + "\npreviewTexturesDeleted=" + handles.size()
              + "\nrefreshTexturesDeleted=" + retiredHandles.size()
              + "\nassetReferencesBalanced=true\ninputBalanced=true\nstageRetiredByClient=true\nresult=PASS\n";
          Gdx.files.absolute(output).child("character-cycle-" + cycle + "-variant-" + variant + ".txt")
              .writeString(report, false, "UTF-8");
          Gdx.app.log("CharacterLifecycle", "[CHARACTER_LIFECYCLE] " + report.replace('\n', ' '));
        }
      }
    } finally {
      Riiablo.saves = originalSaves;
    }
  }

  private static void render(Screen screen, AssetManager assets) {
    for (int frame = 0; frame < 3; frame++) {
      assets.finishLoading();
      screen.render(1f / 60f);
    }
    assets.finishLoading();
  }

  private static Map<String, Integer> references(AssetManager assets) {
    Map<String, Integer> references = new HashMap<>();
    for (String name : assets.getAssetNames()) references.put(name, assets.getReferenceCount(name));
    return references;
  }

  private static void sample(AssetManager assets, Set<Integer> handles, Set<Pixmap> pixels) {
    for (String name : assets.getAssetNames()) {
      if (assets.getAssetType(name) != DCC.class) continue;
      DCC dcc = assets.get(name, DCC.class);
      for (int direction = 0; direction < dcc.getNumDirections(); direction++) {
        if (!dcc.isLoaded(direction)) continue;
        for (int frame = 0; frame < dcc.getNumFramesPerDir(); frame++) {
          TextureRegion region = dcc.getTexture(direction, frame);
          if (region == null) continue;
          Texture texture = region.getTexture();
          require(Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "live DCC texture");
          handles.add(texture.getTextureObjectHandle());
          Pixmap pixel = dcc.getPixmap(direction, frame);
          require(pixel != null && !pixel.isDisposed(), "live DCC pixels");
          pixels.add(pixel);
        }
      }
    }
  }

  private static void refresh(Screen screen) {
    try {
      java.lang.reflect.Method method = screen.getClass().getDeclaredMethod("refreshCharacters");
      method.setAccessible(true);
      method.invoke(screen);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("character refresh fixture", failure);
    }
  }

  @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) {
    try {
      java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return (T) field.get(object);
    } catch (ReflectiveOperationException failure) { throw new IllegalStateException("character fixture", failure); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Character lifecycle: " + message);
  }
}
