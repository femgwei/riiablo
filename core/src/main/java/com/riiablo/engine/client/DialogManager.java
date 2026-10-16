package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.riiablo.util.Disposal;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.server.component.Position;
import com.riiablo.widget.NpcDialogBox;
import com.riiablo.widget.NpcMenu;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public class DialogManager extends PassiveSystem {
  protected ComponentMapper<Position> mPosition;

  protected MenuManager menuManager;

  @Wire(name = "iso", failOnNull = false)
  protected IsometricCamera iso;

  @Wire(name = "scaledStage", failOnNull = false)
  protected Stage scaledStage;

  private NpcDialogBox dialog;
  private boolean disposed;

  private final Vector2 tmpVec2 = new Vector2();

  public NpcDialogBox getDialog() {
    return dialog;
  }

  public void setDialog(NpcDialogBox dialog) {
    if (disposed) {
      if (dialog != null) dialog.dispose();
      return;
    }
    if (this.dialog == dialog) return;
    NpcDialogBox closing = this.dialog;
    this.dialog = dialog;
    try (Disposal cleanup = new Disposal()) {
      if (closing != null) {
        cleanup.run(closing::remove);
        cleanup.dispose(closing);
      }
      NpcMenu menu = menuManager.getMenu();
      if (menu != null && !menu.isDisposed()) cleanup.run(() -> menu.setVisible(dialog == null));
      if (dialog != null) {
        cleanup.run(() -> attach(dialog));
      }
    }
  }

  private void attach(NpcDialogBox next) {
    try {
      tmpVec2.set(Gdx.graphics.getWidth() / 2, 0);
      scaledStage.screenToStageCoordinates(tmpVec2);
      next.setPosition(tmpVec2.x, tmpVec2.y, Align.top | Align.center);
      scaledStage.addActor(next);
    } catch (RuntimeException | Error failure) {
      dialog = null;
      try { next.dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      NpcMenu menu = menuManager.getMenu();
      if (menu != null && !menu.isDisposed()) {
        try { menu.setVisible(true); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      }
      throw failure;
    }
  }

  @Override protected void dispose() {
    if (disposed) return;
    disposed = true;
    NpcDialogBox closing = dialog;
    dialog = null;
    try (Disposal cleanup = new Disposal()) {
      if (closing != null) {
        cleanup.run(closing::remove);
        cleanup.dispose(closing);
      }
    }
  }
}
