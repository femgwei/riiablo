package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.item.ItemWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

/** Real grids/actors: removing a view never disposes or mutates the borrowed ItemWrapper. */
class DynamicItemGridLifecycleTest {
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<ItemGrid> grids = new Array<>();
  private Application application;
  private ItemData items;
  private ItemGrid.GridListener listener;
  private MockedConstruction<Texture> uploads;

  @BeforeEach void fixture() throws Exception {
    GdxNativesLoader.load(); application = Gdx.app; Gdx.app = mock(Application.class);
    for (Field field : Riiablo.class.getFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    Riiablo.colors = new Colors(); Riiablo.charData = mock(CharData.class); items = mock(ItemData.class);
    when(Riiablo.charData.getItems()).thenReturn(items); listener = mock(ItemGrid.GridListener.class);
    uploads = mockConstruction(Texture.class);
  }

  @AfterEach void restore() throws Exception {
    try { for (ItemGrid grid : grids) grid.dispose(); }
    finally { uploads.close(); Gdx.app = application; for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue()); }
  }

  @Test void syncRemovalRetiresSavedInputDrawPositionActionsAndTargetsWhileOwnerIsLive() {
    ItemGrid grid = grid(); Item item = item(1); ItemGrid.StoredItem old = grid.addItem(item);
    Array<ClickListener> saved = clicks(old); old.addCaptureListener(new InputListener()); old.addAction(new Action() { public boolean act(float dt) { fail("retired action"); return false; } });
    grid.swap = old; grid.hits.add(old); assertSame(item, ItemGrid.targetItem(old));
    grid.syncItems(new Array<Item>()); assertFalse(grid.isDisposed()); assertNull(grid.swap); assertEquals(0, grid.hits.size);
    retired(old, saved); verify(uploads.constructed().get(0), never()).dispose();
  }

  @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
  void allGroupRemovalEntrypointsRetireOwnedItemActors(int mode) {
    ItemGrid grid = grid(); ItemGrid.StoredItem old = grid.addItem(item(1)); Array<ClickListener> saved = clicks(old);
    switch (mode) {
      case 0: assertTrue(old.remove()); break;
      case 1: assertTrue(grid.removeActor(old, false)); break;
      case 2: assertSame(old, grid.removeActorAt(0, false)); break;
      case 3: grid.clearChildren(); break;
      case 4: grid.clearChildren(false); break;
      case 5: grid.clear(); break;
      case 6: grid.clear(false); break;
      default: fail();
    }
    assertEquals(0, grid.getChildren().size); retired(old, saved);
  }

  @Test void aFreshViewOfTheSameItemDoesNotReactivateTheOldView() {
    ItemGrid grid = grid(); Item item = item(1); ItemGrid.StoredItem old = grid.addItem(item); Array<ClickListener> saved = clicks(old);
    grid.syncItems(null); grid.syncItems(Array.with(item)); ItemGrid.StoredItem current = (ItemGrid.StoredItem) grid.getChild(0);
    assertNotSame(old, current); retired(old, saved); assertSame(item, ItemGrid.targetItem(current));
    when(items.indexOf(item)).thenReturn(7); when(listener.onPickup(7)).thenReturn(true);
    InputEvent event = new InputEvent(); current.clickListener.clicked(event, 0, 0);
    verify(listener).onPickup(7); assertTrue(event.isHandled()); assertNull(current.getParent());
  }

  @Test void retainedItemsKeepTheirActorAndUpdateCoordinatesWithoutRebuild() {
    ItemGrid grid = grid(); Item item = item(1); ItemGrid.StoredItem actor = grid.addItem(item);
    item.gridX = 2; item.gridY = 1; clearInvocations(item.wrapper);
    grid.syncItems(Array.with(item)); assertSame(actor, grid.getChild(0)); assertSame(item, ItemGrid.targetItem(actor));
    assertEquals(62f, actor.getX()); assertEquals(62f, actor.getY()); verify(item.wrapper).setPosition(62f, 62f);
  }

  @Test void synchronousPickupReconciliationCannotRemoveTheReplacementView() {
    ItemGrid grid = grid(); Item item = item(1), replacement = item(2); ItemGrid.StoredItem old = grid.addItem(item);
    when(items.indexOf(item)).thenReturn(7); when(listener.onPickup(7)).thenAnswer(call -> { grid.syncItems(Array.with(replacement)); return true; });
    InputEvent event = new InputEvent(); old.clickListener.clicked(event, 0, 0);
    assertTrue(event.isHandled()); assertEquals(1, grid.getChildren().size); assertSame(replacement, ItemGrid.targetItem(grid.getChild(0)));
    retired(old, clicks(old));
  }

  @Test void currentRightClickKeepsTheUseRouteEvenWhenItSynchronouslyRemovesTheView() {
    ItemGrid grid = grid(); Item item = item(1); ItemGrid.StoredItem old = grid.addItem(item); Array<ClickListener> saved = clicks(old);
    when(listener.onUse(item)).thenAnswer(call -> { grid.syncItems(null); return true; });
    InputEvent event = new InputEvent(); saved.get(1).clicked(event, 0, 0); verify(listener).onUse(item);
    assertTrue(event.isHandled()); assertEquals(0, grid.getChildren().size); retired(old, saved);
  }

  @Test void falseRemovalFromAnotherGridDoesNotRetireItsOwnersActor() {
    ItemGrid owner = grid(), other = grid(); Item item = item(1); ItemGrid.StoredItem actor = owner.addItem(item);
    assertFalse(other.removeActor(actor)); assertSame(item, ItemGrid.targetItem(actor)); assertSame(owner, actor.getParent());
  }

  @Test void reparentingCannotTransferTheOwnedActorsInputOrWrapperMutations() {
    ItemGrid grid = grid(); ItemGrid.StoredItem old = grid.addItem(item(1)); Array<ClickListener> saved = clicks(old);
    Group foreign = new Group(); foreign.addActor(old); assertSame(foreign, old.getParent());
    inactive(old, saved); assertNull(ItemGrid.targetItem(old)); foreign.clearChildren();
  }

  @Test void nonItemActorsPreserveNormalGroupRemovalSemantics() {
    ItemGrid grid = grid(); Actor borrowed = new Actor(); InputListener input = new InputListener(); borrowed.addListener(input);
    grid.addActor(borrowed); grid.removeActor(borrowed); assertTrue(borrowed.getListeners().contains(input, true));
  }

  @Test void cleanupFailureStillDetachesTheFailedActorAndEveryOtherChild() {
    ItemGrid grid = grid(); ItemGrid.StoredItem failed = spy(grid.new StoredItem(item(1))); grid.addActor(failed);
    ItemGrid.StoredItem survivor = grid.addItem(item(2)); Array<ClickListener> saved = clicks(failed);
    Error failure = new AssertionError("listener retirement"); doThrow(failure).when(failed).clearListeners();
    assertSame(failure, assertThrows(AssertionError.class, grid::clearChildren)); assertEquals(0, grid.getChildren().size);
    assertNull(failed.getParent()); inactive(failed, saved); retired(survivor, clicks(survivor));
  }

  @Test void failedSyncBalancesItsSnapshotAndAllowsSubsequentReconciliation() {
    ItemGrid grid = grid(); ItemGrid.StoredItem failed = spy(grid.new StoredItem(item(1))); grid.addActor(failed);
    doThrow(new AssertionError("clear")).when(failed).clearListeners(); assertThrows(AssertionError.class, () -> grid.syncItems(null));
    Item next = item(2); grid.syncItems(Array.with(next)); assertEquals(1, grid.getChildren().size); assertSame(next, ItemGrid.targetItem(grid.getChild(0)));
    grid.syncItems(null); assertEquals(0, grid.getChildren().size);
  }

  @Test void addFailureRetiresAndDetachesAViewAlreadyAttachedBeforeFailure() {
    final ItemGrid.StoredItem[] returned = new ItemGrid.StoredItem[1]; Error failure = new AssertionError("attach");
    ItemGrid grid = new ItemGrid(4, 4, 31, 31, listener) {
      @Override public void addActor(Actor actor) { super.addActor(actor); returned[0] = (StoredItem) actor; throw failure; }
    }; grids.add(grid); Item item = item(1);
    assertSame(failure, assertThrows(AssertionError.class, () -> grid.populate(Array.with(item))));
    assertEquals(0, grid.getChildren().size); retired(returned[0], clicks(returned[0]));
  }

  @Test void vendorDrainRetiresItsOldPageAndPreservesPlacementAndBorrowedItems() {
    Inventory.Entry shape = new Inventory.Entry(); shape.gridX = shape.gridY = 4; shape.gridBoxWidth = shape.gridBoxHeight = 31;
    VendorGrid vendor = new VendorGrid(shape, listener); grids.add(vendor); Item first = item(1), second = item(2);
    vendor.drain(Array.with(first)); ItemGrid.StoredItem old = (ItemGrid.StoredItem) vendor.getChild(0); Array<ClickListener> saved = clicks(old);
    assertEquals(1, vendor.drain(Array.with(second))); retired(old, saved); assertSame(second, ItemGrid.targetItem(vendor.getChild(0)));
    assertEquals(0, second.gridX); assertEquals(0, second.gridY); verify(first, never()).unload(); verify(first.wrapper, never()).dispose();
  }

  @Test void finalGridExitRetiresEveryViewAndReturnsOnlyItsFillOnce() {
    ItemGrid grid = grid(); ItemGrid.StoredItem actor = grid.addItem(item(1)); Array<ClickListener> saved = clicks(actor);
    grid.dispose(); grid.dispose(); retired(actor, saved); verify(uploads.constructed().get(0)).dispose();
    grid.syncItems(Array.with(item(2))); assertEquals(0, grid.getChildren().size);
  }

  private ItemGrid grid() { ItemGrid grid = new ItemGrid(4, 4, 31, 31, listener); grids.add(grid); return grid; }
  private Item item(int id) { Item item = mock(Item.class); item.base = new ItemEntry(); item.base.invwidth = item.base.invheight = 1; item.wrapper = mock(ItemWrapper.class); when(item.getBaseIndex()).thenReturn(id); return item; }
  private Array<ClickListener> clicks(Actor actor) { Array<ClickListener> result = new Array<>(); for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result.add((ClickListener) listener); return result; }
  private void retired(ItemGrid.StoredItem actor, Array<ClickListener> callbacks) {
    assertNull(actor.getParent()); assertEquals(0, actor.getListeners().size); assertEquals(0, actor.getCaptureListeners().size); assertEquals(0, actor.getActions().size);
    inactive(actor, callbacks);
  }
  private void inactive(ItemGrid.StoredItem actor, Array<ClickListener> callbacks) {
    clearInvocations(actor.item, actor.item.wrapper, items, listener);
    for (ClickListener callback : callbacks) callback.clicked(null, 0, 0);
    Batch batch = mock(Batch.class); actor.draw(batch, 1); actor.act(1); actor.setPosition(99, 88); assertNull(actor.hit(0, 0, true));
    assertNull(ItemGrid.targetItem(actor)); verifyNoInteractions(batch, actor.item, actor.item.wrapper, items, listener);
  }
}
