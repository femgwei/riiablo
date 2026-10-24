package com.riiablo.presentation;

import java.util.Objects;

import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.client.ClientActionInputPort;
import com.riiablo.engine.client.ClientKeyboardInputPort;
import com.riiablo.item.Item;

/** Bridges character/input facts from the host context into narrow client ports. */
public final class ClientGameplayInputAdapter
    implements ClientActionInputPort, ClientKeyboardInputPort {
  private final ClientGameplayContext context;

  public ClientGameplayInputAdapter(ClientGameplayContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override public boolean movementBlocked() {
    return context.movementBlocked();
  }

  @Override public int action(int button) {
    return context.charData() == null ? -1 : context.charData().getAction(button);
  }

  @Override public Skills.Entry skill(int skillId) {
    return skillId < 0 || context.files() == null || context.files().skills == null
        ? null : context.files().skills.get(skillId);
  }

  @Override public Item equippedThrowableWeapon() {
    return context.charData() == null || context.charData().getItems() == null
        ? null : context.charData().getItems().getEquippedThrowableWeapon();
  }

  @Override public Item equippedRangedWeapon() {
    return context.charData() == null || context.charData().getItems() == null
        ? null : context.charData().getItems().getEquippedRangedWeapon();
  }
}
