package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.util.EventUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Three real front-end cycles; no network actions, saves, synthetic assets or visual acceptance. */
public final class OffscreenMenuLifecycleScreen extends ScreenAdapter {
  private static final String[] PATHS = {
      "data/global/ui/FrontEnd/TitleScreen.dc6",
      "data/global/ui/FrontEnd/D2logoFireLeft.DC6",
      "data/global/ui/FrontEnd/D2logoFireRight.DC6",
      "data/global/ui/FrontEnd/D2logoBlackLeft.DC6",
      "data/global/ui/FrontEnd/D2logoBlackRight.DC6",
      "data/global/ui/FrontEnd/3WideButtonBlank.dc6",
      "data/global/ui/FrontEnd/MediumButtonBlank.dc6",
      "data/global/sfx/cursor/button.wav",
      "data/global/sfx/cursor/select.wav"
  };
  private final String output;
  private boolean started;

  public OffscreenMenuLifecycleScreen(String output) { this.output = output; }

  @Override public void show() {
    if (started) return;
    started = true;
    Gdx.app.postRunnable(this::verify);
  }

  private void verify() {
    AssetManager assets = Riiablo.assets;
    int inputBaseline = Riiablo.input.getProcessors().size;
    int controllerBaseline = controllerListeners().size();
    Map<String, Integer> baseline = references(assets);
    for (int cycle = 1; cycle <= 3; cycle++) {
      Set<Integer> handles = new HashSet<>();
      SplashScreen splash = new SplashScreen();
      Stage splashStage = stage(splash);
      Riiablo.client.setScreen(splash);
      assets.finishLoading();
      require(Riiablo.input.getProcessors().size == inputBaseline + 2, "splash input");
      require(controllerListeners().size() == controllerBaseline + 1
          && hasController(splash.controllerListener), "splash controller");
      render(splash);
      sample(assets, handles);
      require(splash.inputProcessor.keyDown(Input.Keys.ENTER), "production splash handoff");
      require(Riiablo.client.getScreen() instanceof MenuScreen, "splash installed menu");
      MenuScreen menu = (MenuScreen) Riiablo.client.getScreen();
      Stage menuStage = stage(menu);
      require(splashStage.getActors().size == 0, "splash Stage retired by Client");
      require(!splash.inputProcessor.keyDown(Input.Keys.ENTER), "stale splash input cannot navigate");
      assets.finishLoading();
      require(Riiablo.input.getProcessors().size == inputBaseline + 1, "menu input");
      require(controllerListeners().size() == controllerBaseline + 1
          && hasController(menu.controllerListener) && !hasController(splash.controllerListener), "menu controller");
      require(refs(assets, PATHS[7]) == baseline.get(PATHS[7]) + 5, "menu owns one preload and four button sounds");
      Map<String, Integer> menuReferences = references(assets);
      render(menu);
      sample(assets, handles);
      // Use the real menu button listener, not direct screen construction/navigation.
      require(EventUtils.click(button(menu, "btnMultiplayer")), "multiplayer click");
      require(Riiablo.client.getScreen() instanceof MultiplayerScreen, "multiplayer installed");
      MultiplayerScreen multiplayer = (MultiplayerScreen) Riiablo.client.getScreen();
      Stage multiplayerStage = stage(multiplayer);
      assets.finishLoading();
      require(Riiablo.input.getProcessors().size == inputBaseline + 1, "only visible multiplayer input");
      require(controllerListeners().size() == controllerBaseline
          && !hasController(menu.controllerListener), "hidden menu controller detached");
      require(refs(assets, PATHS[7]) == baseline.get(PATHS[7]) + 9, "shared button sounds while stacked");
      require(refs(assets, PATHS[3]) == baseline.get(PATHS[3]) + 2, "two owners of borrowed logo frames");
      render(multiplayer);
      sample(assets, handles);
      require(EventUtils.click(button(multiplayer, "btnCancel")), "cancel click");
      require(Riiablo.client.getScreen() == menu, "same retained menu restored");
      require(multiplayerStage.getActors().size == 0, "popped Stage retired by Client");
      require(menuReferences.equals(references(assets)), "pop restores menu asset references exactly");
      require(Riiablo.input.getProcessors().size == inputBaseline + 1, "restored menu input");
      require(controllerListeners().size() == controllerBaseline + 1
          && hasController(menu.controllerListener), "restored menu controller");
      render(menu);
      for (int handle : handles) require(Gdx.gl.glIsTexture(handle), "shared menu textures remain live after pop");
      Riiablo.client.clearAndSet(this);
      require(menuStage.getActors().size == 0, "cleared menu Stage retired by Client");
      splash.dispose(); menu.dispose(); multiplayer.dispose(); // must not release another owner's references
      require(baseline.equals(references(assets)), "all nine menu assets return to baseline");
      require(Riiablo.input.getProcessors().size == inputBaseline, "input returns to baseline");
      require(controllerListeners().size() == controllerBaseline
          && !hasController(menu.controllerListener), "controllers return to baseline");
      for (int handle : handles) require(!Gdx.gl.glIsTexture(handle), "all sampled menu textures deleted");
      require(Gdx.gl.glGetError() == 0, "no GL errors");
      String report = "cycle=" + cycle + "\nassetBaselinesBalanced=" + baseline.size()
          + "\ntexturesDeleted=" + handles.size() + "\nstagesRetiredByClient=3"
          + "\nbuttonReferences=0/5/9/5/0\ninputProcessors=" + inputBaseline
          + "\ncontrollerListeners=" + controllerBaseline
          + "\nborrowedLogosSurvivePop=true\nresult=PASS\n";
      Gdx.files.absolute(output).child("menu-cycle-" + cycle + "-lifecycle.txt").writeString(report, false, "UTF-8");
      Gdx.app.log("OffscreenMenuLifecycle", "[MENU_LIFECYCLE] " + report.replace('\n', ' '));
    }
    // Exercise real backend order too: platform listeners retire before the active Client screen.
    MenuScreen terminalMenu = new MenuScreen();
    Riiablo.client.setScreen(terminalMenu);
    assets.finishLoading();
    render(terminalMenu);
    require(hasController(terminalMenu.controllerListener), "terminal menu owns controller before exit");
    Gdx.app.exit();
  }

  private static void render(com.badlogic.gdx.Screen screen) {
    for (int frame = 0; frame < 3; frame++) screen.render(1f / 60f);
  }

  private static Map<String, Integer> references(AssetManager assets) {
    Map<String, Integer> references = new HashMap<>();
    for (String path : PATHS) references.put(path, refs(assets, path));
    return references;
  }

  private static int refs(AssetManager assets, String path) {
    return assets.isLoaded(path) ? assets.getReferenceCount(path) : 0;
  }

  private static void sample(AssetManager assets, Set<Integer> handles) {
    for (String path : PATHS) {
      if (!path.endsWith("dc6") && !path.endsWith("DC6")) continue;
      if (!assets.isLoaded(path)) continue;
      DC6 image = assets.get(path, DC6.class);
      for (int direction = 0; direction < image.getNumDirections(); direction++) {
        for (int page = 0; page < image.getNumPages(direction); page++) {
          Texture texture = image.getTexture(direction, page).getTexture();
          require(Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "live menu texture");
          handles.add(texture.getTextureObjectHandle());
        }
      }
    }
  }

  private static Stage stage(Object screen) { return field(screen, "stage", Stage.class); }

  private static List<com.badlogic.gdx.controllers.ControllerListener> controllerListeners() {
    List<com.badlogic.gdx.controllers.ControllerListener> listeners = new ArrayList<>();
    for (com.badlogic.gdx.controllers.ControllerListener listener : Controllers.getListeners()) {
      // Desktop 2.2.1 exposes one composite wrapper, not its registered listeners.
      // Inspect the actual registrations rather than weakening the count assertion.
      if (listener.getClass().getName().equals(
          "com.badlogic.gdx.controllers.desktop.support.CompositeControllerListener")) {
        for (Object child : field(listener, "listeners", Collection.class)) {
          listeners.add((com.badlogic.gdx.controllers.ControllerListener) child);
        }
      } else {
        listeners.add(listener);
      }
    }
    return listeners;
  }

  private static boolean hasController(com.badlogic.gdx.controllers.ControllerListener target) {
    for (com.badlogic.gdx.controllers.ControllerListener listener : controllerListeners()) {
      if (listener == target) return true;
    }
    return false;
  }
  private static Button button(Object screen, String name) { return field(screen, name, Button.class); }
  private static <T> T field(Object owner, String name, Class<T> type) {
    try {
      java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException failure) { throw new IllegalStateException("menu fixture " + name, failure); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Menu lifecycle: " + message);
  }
}
