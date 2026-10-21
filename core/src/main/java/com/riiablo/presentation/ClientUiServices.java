package com.riiablo.presentation;

/** UI queries and intents exposed to client gameplay systems without screen/panel types. */
public interface ClientUiServices {
  boolean isGameActive();
  boolean inventoryVisible();
  boolean movementBlocked();
  boolean mercenaryPortraitContains(float stageX, float stageY);
  boolean useMercenaryCursorPotion();
  void showHirelingPanel();
  void cancelVendorTradeMode();
  void closeTradePanelsForMovement();
}
