package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.widget.CharacterCreateButton;
import com.riiablo.widget.TextField;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Three creation-page cycles on one real Client; no confirmation, save writes or networking. */
final class OffscreenCreateCharacterLifecycle {
  private static final String[] CHARACTERS = {
      "amazon", "assassin", "necromancer", "barbarian", "paladin", "sorceress", "druid"
  };

  static void verify(String output, Screen returnScreen) {
    AssetManager assets = Riiablo.assets;
    assets.finishLoading();
    Map<String, Integer> baseline = references(assets);
    int inputBaseline = Riiablo.input.getProcessors().size;
    Array<?> bounds = field(Riiablo.client, "screenBoundsListeners");
    int boundsBaseline = bounds.size;
    for (int cycle = 1; cycle <= 3; cycle++) {
      CreateCharacterScreen screen = new CreateCharacterScreen();
      Stage stage = field(screen, "stage");
      Riiablo.client.setScreen(screen);
      render(screen, assets);
      require(bounds.size == boundsBaseline + 1, "visible bounds subscription");
      require(Riiablo.input.getProcessors().size == inputBaseline + 1, "visible creation input");
      for (String name : CHARACTERS) {
        CharacterCreateButton button = field(screen, name);
        ClickListener hover = field(button, "clickListener");
        hover.enter(null, 0, 0, -1, null);
        render(screen, assets);
        hover.exit(null, 0, 0, -1, null);
        click(button);
        require(button.getState() == CharacterCreateButton.State.FW, "production select starts forward animation");
        complete(button);
        require(button.getState() == CharacterCreateButton.State.NU3, "forward completes to selected idle");
        render(screen, assets);
        click(button);
        require(button.getState() == CharacterCreateButton.State.BW, "production deselect starts backward animation");
        complete(button);
        require(button.getState() == CharacterCreateButton.State.NU1, "backward completes to initial idle");
        render(screen, assets);
      }
      CharacterCreateButton amazon = field(screen, "amazon");
      click(amazon); complete(amazon);
      TextField name = field(screen, "tfCharName");
      name.setText("Lifecycle");
      stage.setKeyboardFocus(name);
      render(screen, assets);
      int actorCount = stage.getActors().size;
      Map<String, Integer> live = references(assets);
      Set<Integer> textures = new HashSet<>();
      Set<Pixmap> pixels = new HashSet<>();
      int dc6Count = sample(assets, baseline, textures, pixels);
      require(dc6Count > 7 && !textures.isEmpty() && !pixels.isEmpty(), "real creation assets sampled");
      Riiablo.client.pushScreen(new ScreenAdapter());
      require(live.equals(references(assets)), "hide retains every reference");
      require(bounds.size == boundsBaseline, "hidden bounds detached");
      require(Riiablo.input.getProcessors().size == inputBaseline, "hidden input detached");
      require(stage.getKeyboardFocus() == null, "hidden text input unfocused");
      screen.render(1); // hidden render must not use retired/borrowed drawing state
      Riiablo.client.popScreen();
      require(Riiablo.client.getScreen() == screen, "same creation page restored");
      require(field(screen, "selected") == amazon && "Lifecycle".equals(name.getText()), "selection and name retained");
      require(stage.getActors().size == actorCount, "show does not duplicate actors");
      require(live.equals(references(assets)), "show does not acquire references");
      require(bounds.size == boundsBaseline + 1, "bounds restored exactly once");
      for (int texture : textures) require(Gdx.gl.glIsTexture(texture), "textures survive hide/pop");
      render(screen, assets);
      Riiablo.client.clearAndSet(returnScreen);
      require(stage.getActors().size == 0, "creation Stage retired by Client");
      screen.dispose(); screen.dispose();
      require(baseline.equals(references(assets)), "all creation references return to baseline");
      require(bounds.size == boundsBaseline, "final bounds balanced");
      require(Riiablo.input.getProcessors().size == inputBaseline, "final input balanced");
      for (int texture : textures) require(!Gdx.gl.glIsTexture(texture), "creation textures deleted");
      for (Pixmap pixel : pixels) require(pixel.isDisposed(), "decoded and upload pixels freed");
      require(Gdx.gl.glGetError() == 0, "no GL errors");
      String report = "cycle=" + cycle + "\ncharacters=7\nanimationTransitions=14\ndc6Assets=" + dc6Count
          + "\ntexturesDeleted=" + textures.size() + "\npixelsFreed=" + pixels.size()
          + "\nassetReferencesBalanced=true\ninputBalanced=true\nboundsBalanced=true"
          + "\nstateRetained=true\nstageRetiredByClient=true\nresult=PASS\n";
      Gdx.files.absolute(output).child("creation-cycle-" + cycle + ".txt").writeString(report, false, "UTF-8");
      Gdx.app.log("CreationLifecycle", "[CREATION_LIFECYCLE] " + report.replace('\n', ' '));
    }
  }

  private static void click(CharacterCreateButton button) {
    InputEvent event = new InputEvent();
    event.setListenerActor(button);
    // Both production listeners run in registration order: widget state, then screen selection.
    for (EventListener listener : button.getListeners()) {
      if (listener instanceof ClickListener) ((ClickListener) listener).clicked(event, 0, 0);
    }
  }

  private static void complete(CharacterCreateButton button) {
    Animation animation = field(button, "active");
    animation.act(100f); // deterministic completion through the production Animation callback
  }

  private static void render(Screen screen, AssetManager assets) {
    assets.finishLoading();
    for (int frame = 0; frame < 3; frame++) screen.render(1f / 60f);
  }

  private static Map<String, Integer> references(AssetManager assets) {
    Map<String, Integer> refs = new HashMap<>();
    for (String path : assets.getAssetNames()) refs.put(path, assets.getReferenceCount(path));
    return refs;
  }

  private static int sample(AssetManager assets, Map<String, Integer> baseline,
      Set<Integer> textures, Set<Pixmap> pixels) {
    int count = 0;
    for (String path : assets.getAssetNames()) {
      if (baseline.containsKey(path) || assets.getAssetType(path) != DC6.class) continue;
      count++;
      DC6 dc6 = assets.get(path, DC6.class);
      for (int direction = 0; direction < dc6.getNumDirections(); direction++) {
        for (int frame = 0; frame < dc6.getNumFramesPerDir(); frame++) pixels.add(dc6.getPixmap(direction, frame));
        if (!dc6.isLoaded(direction)) continue;
        for (int page = 0; page < dc6.getNumPages(direction); page++) {
          Texture texture = dc6.getTexture(direction, page).getTexture();
          require(Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "live DC6 texture");
          textures.add(texture.getTextureObjectHandle());
        }
      }
      Pixmap[][] uploads = field(dc6, "pixmaps");
      if (uploads != null) for (Pixmap[] direction : uploads) {
        if (direction != null) for (Pixmap pixel : direction) pixels.add(pixel);
      }
    }
    for (Pixmap pixel : pixels) require(pixel != null && !pixel.isDisposed(), "live DC6 pixels");
    return count;
  }

  @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
    try {
      for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
        try {
          java.lang.reflect.Field field = type.getDeclaredField(name);
          field.setAccessible(true);
          return (T) field.get(target);
        } catch (NoSuchFieldException missing) { /* continue to the owning base class */ }
      }
      throw new NoSuchFieldException(name);
    } catch (ReflectiveOperationException failure) { throw new IllegalStateException("creation lifecycle fixture", failure); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Creation lifecycle: " + message);
  }
}
