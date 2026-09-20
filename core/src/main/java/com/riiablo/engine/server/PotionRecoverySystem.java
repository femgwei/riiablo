package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitLifecycle;

/** Advances native health- and mana-potion states once per 25 Hz game frame. */
@All({Player.class, AttributesWrapper.class})
public class PotionRecoverySystem extends IteratingSystem {
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<UnitLifecycle> mUnitLifecycle;

  @Override
  protected void process(int entityId) {
    Player player = mPlayer.get(entityId);
    AttributesWrapper attributes = mAttributes.get(entityId);
    if (player == null || player.data == null || attributes == null || attributes.attrs == null) {
      return;
    }
    if (mUnitLifecycle != null && mUnitLifecycle.has(entityId)
        && mUnitLifecycle.get(entityId).isDead()) return;
    StatRef life = attributes.attrs.get(Stat.hitpoints, StatRef.obtain());
    if (life != null && life.asFixed() <= 0f) return;
    player.data.tickPotionRecovery();
  }
}
