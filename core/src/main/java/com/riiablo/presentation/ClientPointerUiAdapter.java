package com.riiablo.presentation;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.ClientPointerUiPort;
import java.util.Objects;
import java.util.function.Supplier;

/** Bridges Scene2D/UI state into immutable facts consumed by client input systems. */
public final class ClientPointerUiAdapter implements ClientPointerUiPort {
  private final Stage stage;
  private final Stage scaledStage;
  private final ClientGameplayContext context;
  private final Supplier<MenuManager> menus;
  private final Supplier<DialogManager> dialogs;
  private final Vector2 point = new Vector2();

  public ClientPointerUiAdapter(Stage stage, Stage scaledStage, ClientGameplayContext context,
      Supplier<MenuManager> menus, Supplier<DialogManager> dialogs) {
    this.stage = Objects.requireNonNull(stage, "stage");
    this.scaledStage = Objects.requireNonNull(scaledStage, "scaledStage");
    this.context = Objects.requireNonNull(context, "context");
    this.menus = Objects.requireNonNull(menus, "menus");
    this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
  }

  @Override public boolean blocksWorldInput() {
    return context.cursor() != null && context.cursor().blocksWorldInput();
  }

  @Override public boolean shiftDown() { return UIUtils.shift(); }

  @Override public String hitSource(float screenX, float screenY) {
    point.set(screenX, screenY);
    stage.screenToStageCoordinates(point);
    Actor hit = stage.hit(point.x, point.y, true);
    if (hit != null) return "stage:" + hit.getClass().getSimpleName();
    point.set(screenX, screenY);
    scaledStage.screenToStageCoordinates(point);
    hit = scaledStage.hit(point.x, point.y, true);
    return hit == null ? null : "scaledStage:" + hit.getClass().getSimpleName();
  }

  @Override public boolean mercenaryPortraitContains(float screenX, float screenY) {
    point.set(screenX, screenY);
    stage.screenToStageCoordinates(point);
    return context.mercenaryPortraitContains(point.x, point.y);
  }

  @Override public boolean useMercenaryCursorPotion() { return context.useMercenaryCursorPotion(); }

  @Override public boolean cursorHasItem() {
    return context.cursor() != null && context.cursor().getItem() != null;
  }

  @Override public int cursorItemId() {
    return cursorHasItem() ? context.cursor().getItem().id : -1;
  }

  @Override public void cancelVendorTradeMode() { context.cancelVendorTradeMode(); }
  @Override public void showHirelingPanel() { context.showHirelingPanel(); }
  @Override public void closeTradePanelsForMovement() { context.closeTradePanelsForMovement(); }

  @Override public boolean dismissDialogOrMenu() {
    DialogManager dialog = dialogs.get();
    if (dialog != null && dialog.getDialog() != null) {
      dialog.setDialog(null);
      return true;
    }
    MenuManager menu = menus.get();
    if (menu != null && menu.getMenu() != null) menu.setMenu(null, Engine.INVALID_ENTITY);
    return false;
  }
}
