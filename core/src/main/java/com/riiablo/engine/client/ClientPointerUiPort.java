package com.riiablo.engine.client;

/** Presentation-owned UI/input facts consumed by the fixed-step pointer system. */
public interface ClientPointerUiPort {
  boolean blocksWorldInput();
  boolean shiftDown();
  String hitSource(float screenX, float screenY);
  boolean mercenaryPortraitContains(float screenX, float screenY);
  boolean useMercenaryCursorPotion();
  boolean cursorHasItem();
  int cursorItemId();
  void cancelVendorTradeMode();
  void showHirelingPanel();
  void closeTradePanelsForMovement();
  /** Returns true when a dialog consumed the click; menus are dismissed and allow continuation. */
  boolean dismissDialogOrMenu();
}
