package com.riiablo.widget;

import com.artemis.annotations.EntityId;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.riiablo.Riiablo;
import com.riiablo.engine.client.MenuManager;
import com.riiablo.graphics.BorderedPaletteIndexedDrawable;

public class NpcMenu extends Table {

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
    setBackground(new BorderedPaletteIndexedDrawable());
    pad(PADDING);
    add(new Label(header, Riiablo.fonts.font16) {{
      setColor(Riiablo.colors.gold);
    }}).space(SPACING).row();
  }

  public NpcMenu(MenuManager menuManager, int id) {
    this.menuManager = menuManager;
    setBackground(new BorderedPaletteIndexedDrawable());
    pad(PADDING);
    add(new Label(id, Riiablo.fonts.font16, Riiablo.colors.gold)).space(SPACING).row();
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
    button.addListener(clickListener);
    add(button).space(SPACING).row();
    return button;
  }

  /** Removes a dynamic option and repacks the menu without changing its owner. */
  public void removeItem(LabelButton button) {
    if (button != null && button.getParent() == this) {
      button.remove();
      pack();
    }
  }

  public NpcMenu addItem(int id, final NpcMenu menu) {
    menu.parent = this;
    menu.owner = owner;
    addItem(id, new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        menuManager.setMenu(menu, owner);
      }
    });
    return this;
  }

  public NpcMenu addCancel(CancellationListener cancellationListener) {
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
    pack();
    return this;
  }

  public void cancel() {
    if (cancellationListener != null) cancellationListener.onCancelled();
  }

  public interface CancellationListener {
    void onCancelled();
  }
}
