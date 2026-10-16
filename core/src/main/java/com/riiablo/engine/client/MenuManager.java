package com.riiablo.engine.client;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Label;
import com.riiablo.engine.server.component.MenuWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.widget.NpcMenu;
import com.riiablo.util.Disposal;

import net.mostlyoriginal.api.event.common.Subscribe;

@All(MenuWrapper.class)
public class MenuManager extends BaseEntitySystem {
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MenuWrapper> mMenuWrapper;

  @Wire(name = "iso", failOnNull = false)
  protected IsometricCamera iso;

  @Wire(name = "scaledStage", failOnNull = false)
  protected Stage scaledStage;

  private NpcMenu menu;
  private final IntMap<NpcMenu> owned = new IntMap<>();
  private final IntArray retired = new IntArray();
  private boolean disposed;

  private final Vector2 tmpVec2 = new Vector2();

  @Subscribe
  public void onNpcInteraction(NpcInteractionEvent event) {
    if (disposed) return;
    MenuWrapper menuWrapper = mMenuWrapper.get(event.npcId);
    if (menuWrapper == null) return;
    setMenu(menuWrapper.menu, event.npcId);
  }

  @Override
  protected void processSystem() {
    if (disposed) return;
    retired.clear();
    for (IntMap.Entry<NpcMenu> entry : owned) {
      if (!isCurrent(entry.key, entry.value)) retired.add(entry.key);
    }
    if (retired.isEmpty()) return;
    try (Disposal cleanup = new Disposal()) {
      for (int i = 0; i < retired.size; i++) {
        final int entityId = retired.get(i);
        cleanup.run(() -> retire(entityId));
      }
    }
  }

  /** The entity owns the whole tree, including submenus that never enter a Stage. */
  public NpcMenu createMenu(int owner, String header) {
    if (disposed) throw new IllegalStateException("menu manager is disposed");
    retire(owner);
    NpcMenu root = new NpcMenu(this, owner, header);
    owned.put(owner, root);
    try {
      mMenuWrapper.create(owner).menu = root;
    } catch (RuntimeException | Error failure) {
      try { retire(owner); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
    return root;
  }

  private boolean isCurrent(int owner, NpcMenu root) {
    return subscription.getActiveEntityIds().get(owner) && mMenuWrapper.has(owner)
        && mMenuWrapper.get(owner).menu == root;
  }

  @Override protected void removed(int owner) {
    NpcMenu root = owned.get(owner);
    if (root != null && !isCurrent(owner, root)) retire(owner);
  }

  private void retire(int owner) {
    NpcMenu root = owned.remove(owner);
    if (root == null) return;
    if (menu != null && menu.getRootMenu() == root) menu = null;
    if (mMenuWrapper.has(owner) && mMenuWrapper.get(owner).menu == root) {
      mMenuWrapper.get(owner).menu = null;
    }
    root.dispose();
  }

  @Override protected void dispose() {
    if (disposed) return;
    disposed = true;
    NpcMenu closing = menu;
    menu = null;
    int[] owners = owned.keys().toArray().toArray();
    try (Disposal cleanup = new Disposal()) {
      if (closing != null) cleanup.run(closing::remove);
      for (int owner : owners) cleanup.run(() -> retire(owner));
    }
  }

  public NpcMenu getMenu() {
    return menu;
  }

  // TODO: notify menu open/close to set AI for owner to not move
  public void setMenu(NpcMenu menu, int owner) {
    if (disposed) return;
    if (menu != null && menu.isDisposed()) throw new IllegalStateException("NPC menu is disposed");
    if (this.menu == menu) return;
    if (this.menu != null) {
      // FIXME: Validate that cancel is only called if upnav, downnav -- looks good at a glance
      if (menu == null || menu.getParent() != this.menu) {
        for (NpcMenu parent = this.menu; parent != null && parent != menu; parent = parent.getParent()) {
          parent.cancel();
        }
      }
      this.menu.remove();
    }

    this.menu = menu;
    if (menu != null && owner != Engine.INVALID_ENTITY) {
      scaledStage.addActor(menu);

      Label label = mLabel.get(owner);
      Vector2 position = mPosition.get(owner).position;
      iso.toScreen(tmpVec2.set(position));
      tmpVec2.add(label.offset);
      iso.project(tmpVec2);
      tmpVec2.y = iso.viewportHeight - tmpVec2.y; // stage coords expect y-down coords
      scaledStage.screenToStageCoordinates(tmpVec2);
      menu.setPosition(tmpVec2.x, tmpVec2.y, Align.center | Align.bottom);
    }
  }
}
