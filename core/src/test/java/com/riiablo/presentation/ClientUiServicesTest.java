package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.screen.GameScreen;
import com.riiablo.screen.panel.HireMercenaryPanel;
import com.riiablo.screen.panel.HirelingPanel;
import com.riiablo.screen.panel.InventoryPanel;
import com.riiablo.screen.panel.MercenaryHud;
import com.riiablo.screen.panel.TradePanel;
import com.riiablo.screen.panel.VendorPanel;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientUiServicesTest {
  @Test void missingScreenAndMissingPanelsAreSafeForHeadlessConsumers() {
    AtomicReference<GameScreen> screen = new AtomicReference<>();
    ClientGameplayContext context = context(screen);
    assertFalse(context.isGameActive());
    assertFalse(context.inventoryVisible());
    assertFalse(context.movementBlocked());
    assertFalse(context.mercenaryPortraitContains(5, 7));
    assertFalse(context.useMercenaryCursorPotion());
    context.showHirelingPanel();
    context.cancelVendorTradeMode();
    context.closeTradePanelsForMovement();
    context.presentNpcServiceResult(null);
    context.presentTradeState(null);
    screen.set(mock(GameScreen.class));
    assertTrue(context.isGameActive());
    assertFalse(context.inventoryVisible());
    assertFalse(context.movementBlocked());
    assertFalse(context.mercenaryPortraitContains(5, 7));
    assertFalse(context.useMercenaryCursorPotion());
    context.showHirelingPanel();
    context.presentNpcServiceResult(null);
    context.presentTradeState(null);
  }

  @Test void panelQueriesReadTheOwningScreenAndTrackItsVisibility() {
    GameScreen first = screen();
    GameScreen second = screen();
    AtomicReference<GameScreen> active = new AtomicReference<>(first);
    ClientUiServices context = context(active);
    when(first.inventoryPanel.isVisible()).thenReturn(true);
    when(first.hireMercenaryPanel.isVisible()).thenReturn(true);
    when(first.mercenaryHud.isVisible()).thenReturn(true);
    when(first.mercenaryHud.containsStagePoint(5, 7)).thenReturn(true);
    assertTrue(context.inventoryVisible());
    assertTrue(context.movementBlocked());
    assertTrue(context.mercenaryPortraitContains(5, 7));
    assertFalse(context.mercenaryPortraitContains(7, 5));
    when(first.mercenaryHud.isVisible()).thenReturn(false);
    assertFalse(context.mercenaryPortraitContains(5, 7));
    active.set(second);
    assertFalse(context.inventoryVisible());
    assertFalse(context.movementBlocked());
    assertFalse(context.mercenaryPortraitContains(5, 7));
  }

  @Test void intentsAndNetworkResultsNeverLeakIntoAnotherSession() {
    GameScreen first = screen();
    GameScreen second = screen();
    ClientGameplayContext a = context(new AtomicReference<>(first));
    ClientGameplayContext b = context(new AtomicReference<>(second));
    NpcServiceResult result = new NpcServiceResult();
    ClientNetworkReceiver receiver = mock(ClientNetworkReceiver.class);
    when(first.mercenaryHud.useCursorPotion()).thenReturn(true);
    assertTrue(a.useMercenaryCursorPotion());
    a.showHirelingPanel();
    a.cancelVendorTradeMode();
    a.closeTradePanelsForMovement();
    a.presentNpcServiceResult(result);
    a.presentTradeState(receiver);
    verify(first).setLeftPanel(first.hirelingPanel);
    verify(first).cancelVendorTradeMode();
    verify(first).closeTradePanelsForMovement();
    verify(first.hireMercenaryPanel).applyNetworkResult(result);
    verify(first.vendorPanel).applyNetworkResult(result);
    verify(first.tradePanel).applyNetworkState(receiver);
    verifyNoInteractions(second, second.mercenaryHud, second.hireMercenaryPanel,
        second.vendorPanel, second.tradePanel, second.hirelingPanel);
    assertFalse(b.useMercenaryCursorPotion());
    verify(second.mercenaryHud).useCursorPotion();
  }

  private static ClientGameplayContext context(AtomicReference<GameScreen> screen) {
    return new ClientGameplayContext(null, () -> 1, screen::get, null,
        null, null, null, null, null, null, null, null, null, null);
  }

  private static GameScreen screen() {
    GameScreen screen = mock(GameScreen.class);
    screen.inventoryPanel = mock(InventoryPanel.class);
    screen.hireMercenaryPanel = mock(HireMercenaryPanel.class);
    screen.mercenaryHud = mock(MercenaryHud.class);
    screen.hirelingPanel = mock(HirelingPanel.class);
    screen.vendorPanel = mock(VendorPanel.class);
    screen.tradePanel = mock(TradePanel.class);
    return screen;
  }
}
