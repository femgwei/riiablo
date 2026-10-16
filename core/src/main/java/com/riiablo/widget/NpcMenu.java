package com.riiablo.widget;

import com.artemis.annotations.EntityId;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.engine.client.MenuManager;
import com.riiablo.graphics.BorderedPaletteIndexedDrawable;
import com.riiablo.util.Disposal;

public class NpcMenu extends Table implements Disposable {
  private final Array<NpcMenu> subMenus = new Array<>();
  private final Array<LabelButton> buttons = new Array<>();
  private final BorderedPaletteIndexedDrawable border;
  private boolean disposed;

  private static final float PADDING = 8;
  private static final float SPACING = 2;

  @EntityId
  int         owner;
  NpcMenu     parent;
  MenuManager menuManager;

  CancellationListener cancellationListener;

  public NpcMenu(MenuManager menuManager, int owner, String header) {
    parent = null;
    this.menuManager = menuManager;
    this.owner = owner;
    pad(PADDING);
    add(new Label(header, Riiablo.fonts.font16) {{
      setColor(Riiablo.colors.gold);
    }}).space(SPACING).row();
    setBackground(border = new BorderedPaletteIndexedDrawable());
  }

  public NpcMenu(MenuManager menuManager, int id) {
    this.menuManager = menuManager;
    pad(PADDING);
    add(new Label(id, Riiablo.fonts.font16, Riiablo.colors.gold)).space(SPACING).row();
    setBackground(border = new BorderedPaletteIndexedDrawable());
  }

  public boolean hasParent() {
    return parent != null;
  }

  public NpcMenu getParent() {
    return parent;
  }

  public NpcMenu addItem(int id, ClickListener clickListener) {
    addItemButton(new LabelButton(id, Riiablo.fonts.font16), clickListener);
    return this;
  }

  public NpcMenu addItem(String text, ClickListener clickListener) {
    addItemButton(new LabelButton(text, Riiablo.fonts.font16), clickListener);
    return this;
  }

  /** Adds a menu button and returns it so dynamic NPC options can be refreshed. */
  public LabelButton addItemButton(LabelButton button, ClickListener clickListener) {
    ensureOpen();
    buttons.add(button);
    button.addListener(clickListener);
    add(button).space(SPACING).row();
    return button;
  }

  /** Removes a dynamic option and repacks the menu without changing its owner. */
  public void removeItem(LabelButton button) {
    if (disposed) return;
    if (button != null && button.getParent() == this) {
      buttons.removeValue(button, true);
      button.clearListeners();
      button.remove();
      pack();
    }
  }

  public NpcMenu addItem(int id, final NpcMenu menu) {
    ownSubMenu(menu);
    addItem(id, new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        menuManager.setMenu(menu, owner);
      }
    });
    return this;
  }

  /** Register ownership before building options which may fail before addItem attaches the link. */
  public NpcMenu createSubMenu(int id) {
    ensureOpen();
    NpcMenu child = new NpcMenu(menuManager, id);
    ownSubMenu(child);
    return child;
  }

  private void ownSubMenu(NpcMenu child) {
    ensureOpen(); child.ensureOpen();
    for (NpcMenu ancestor = this; ancestor != null; ancestor = ancestor.parent) {
      if (ancestor == child) throw new IllegalArgumentException("cyclic NPC menu");
    }
    if (child.parent != null && child.parent != this) throw new IllegalArgumentException("NPC menu already owned");
    if (!subMenus.contains(child, true)) subMenus.add(child);
    child.parent = this;
    child.owner = owner;
  }

  public NpcMenu getRootMenu() {
    NpcMenu root = this;
    while (root.parent != null) root = root.parent;
    return root;
  }

  public boolean isDisposed() { return disposed; }

  private void ensureOpen() {
    if (disposed) throw new IllegalStateException("NPC menu is disposed");
  }

  public NpcMenu addCancel(CancellationListener cancellationListener) {
    ensureOpen();
    this.cancellationListener = cancellationListener;
    addItem(3400, new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        cancel();
        menuManager.setMenu(parent, owner);
      }
    });
    return this;
  }

  public NpcMenu build() {
    ensureOpen();
    pack();
    return this;
  }

  public void cancel() {
    if (!disposed && cancellationListener != null) cancellationListener.onCancelled();
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    cancellationListener = null; // Teardown is not the gameplay cancel action.
    NpcMenu[] children = subMenus.toArray(NpcMenu.class);
    subMenus.clear();
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearListeners);
      for (LabelButton button : buttons) cleanup.run(button::clearListeners);
      buttons.clear();
      cleanup.run(this::remove);
      if (parent != null) parent.subMenus.removeValue(this, true);
      parent = null;
      for (NpcMenu child : children) cleanup.dispose(child);
      cleanup.run(() -> setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null));
      cleanup.run(this::clearChildren);
      cleanup.dispose(border);
    }
  }

  public interface CancellationListener {
    void onCancelled();
  }
}
