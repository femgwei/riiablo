package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.save.ItemController;

import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.system.core.PassiveSystem;

public class ItemInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "ItemInteractor";

  protected ItemManager items;

  @Wire(name = "itemController", failOnNull = false)
  protected ItemController itemController;
  protected ComponentMapper<Item> mItem;
  protected EventSystem event;

  @Override
  public void interact(int src, int entity) {
    Item item = mItem.get(entity);
    if (itemController != null) {
      itemController.groundToCursor(entity);
      if (item != null && item.item != null && item.item.code != null) {
        event.dispatch(QuestItemPickedUpEvent.obtain(src, entity, item.item.code));
      }
    }
  }
}
