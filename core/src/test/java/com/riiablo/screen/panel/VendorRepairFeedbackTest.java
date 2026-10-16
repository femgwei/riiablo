package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.audio.Audio;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.Quality;
import com.riiablo.net.packet.d2gs.NpcServiceOperation;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.save.CharData;
import com.riiablo.widget.Button;

class VendorRepairFeedbackTest extends RiiabloTest {
  private Audio previousAudio;
  private CharData previousCharacter;
  private com.riiablo.screen.GameScreen previousGame;
  private VendorPanel panel;

  @BeforeEach void prepare() throws Exception {
    previousAudio = Riiablo.audio;
    previousCharacter = Riiablo.charData;
    previousGame = Riiablo.game; Riiablo.game = null;
    Riiablo.audio = mock(Audio.class);
    Riiablo.charData = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "RepairFeedback", Riiablo.AMAZON);
    Riiablo.charData.getStats().base().put(Stat.gold, 1000);
    Riiablo.charData.getStats().aggregate().put(Stat.gold, 1000);
    panel = mock(VendorPanel.class, CALLS_REAL_METHODS);
    set("character", Riiablo.charData); set("itemData", Riiablo.charData.getItems());
    set("assets", Riiablo.assets); set("audio", Riiablo.audio); set("cursor", Riiablo.cursor);
    doReturn(true).when(panel).isVisible();
    set("repairing", true);
    set("btnRepair", mock(Button.class));
  }

  @AfterEach void restore() {
    Riiablo.audio = previousAudio;
    Riiablo.charData = previousCharacter;
    Riiablo.game = previousGame;
  }

  @Test void fullDurabilitySingleRepairStillSoundsLocally() {
    addItem(10);
    assertFalse(panel.repairItem(0));
    verify(Riiablo.audio).play("cursor_repair_item", true);
    assertEquals(1000, Riiablo.charData.getStats().get(Stat.gold).asInt());
  }

  @Test void fullDurabilitySingleRepairStillSoundsAfterNetworkResponse() throws Exception {
    addItem(10);
    reply(NpcServiceOperation.REPAIR_ITEM, false, "NOTHING_TO_REPAIR");
    verify(Riiablo.audio).play("cursor_repair_item", true);
  }

  @Test void repairAllNetworkResponseRestoresEquipmentAndSoundsOnce() throws Exception {
    Item item = addItem(3);
    Riiablo.charData.getItems().equipItem(BodyLoc.HEAD, item);
    reply(NpcServiceOperation.REPAIR_ALL, true, null);
    assertEquals(10, item.attrs.base().get(Stat.durability).asInt());
    verify(Riiablo.audio).play("cursor_repair_item", true);
  }

  @Test void successfulRepairAllStillSoundsIfSnapshotAlreadyRestoredDurability() throws Exception {
    addItem(10);
    reply(NpcServiceOperation.REPAIR_ALL, true, null);
    verify(Riiablo.audio).play("cursor_repair_item", true);
  }

  @Test void repairAllWithNothingToRepairStillSounds() throws Exception {
    addItem(10);
    reply(NpcServiceOperation.REPAIR_ALL, false, "NOTHING_TO_REPAIR");
    verify(Riiablo.audio).play("cursor_repair_item", true);
  }

  @Test void localRepairAllWithNothingToRepairStillSoundsWithoutCharging() throws Exception {
    addItem(10);
    repairAll();
    verify(Riiablo.audio).play("cursor_repair_item", true);
    assertEquals(1000, Riiablo.charData.getStats().get(Stat.gold).asInt());
    assertFalse(panel.isRepairing());
  }

  @Test void localRepairAllRepairsEquipmentWithoutSelectingAnItem() throws Exception {
    Item item = addItem(3);
    Riiablo.charData.getItems().equipItem(BodyLoc.HEAD, item);
    repairAll();
    assertEquals(10, item.attrs.base().get(Stat.durability).asInt());
    assertFalse(panel.isRepairing());
    verify(Riiablo.audio).play("cursor_repair_item", true);
  }

  @Test void rejectedRepairAllStaysSilent() throws Exception {
    addItem(3);
    reply(NpcServiceOperation.REPAIR_ALL, false, "INSUFFICIENT_GOLD");
    verifyNoInteractions(Riiablo.audio);
  }

  @Test void rejectedSingleRepairStaysSilent() throws Exception {
    addItem(3);
    reply(NpcServiceOperation.REPAIR_ITEM, false, "INSUFFICIENT_GOLD");
    verifyNoInteractions(Riiablo.audio);
  }

  private Item addItem(int durability) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.armor.get("cap"));
    item.id = 12;
    item.quality = Quality.NORMAL;
    item.attrs.base().put(Stat.maxdurability, 10);
    item.attrs.base().put(Stat.durability, durability);
    assertTrue(Riiablo.charData.getItems().addToInventory(item));
    return item;
  }

  private void repairAll() throws Exception {
    java.lang.reflect.Method method = VendorPanel.class.getDeclaredMethod("repairAll");
    method.setAccessible(true);
    method.invoke(panel);
  }

  private void reply(byte operation, boolean success, String reason) throws Exception {
    set("pendingRequestId", 42L);
    set("pendingOperation", operation);
    set("pendingItemIndex", 0);
    NpcServiceResult result = mock(NpcServiceResult.class);
    when(result.requestId()).thenReturn(42L);
    when(result.success()).thenReturn(success);
    when(result.reason()).thenReturn(reason);
    when(result.gold()).thenReturn(1000L);
    panel.applyNetworkResult(result);
  }

  private void set(String name, Object value) throws Exception {
    Field field = VendorPanel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(panel, value);
  }
}
