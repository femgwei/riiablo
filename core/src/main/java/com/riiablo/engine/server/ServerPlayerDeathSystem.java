package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DeathEvent;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Installs the authoritative, presentation-neutral player death state on D2GS. */
public class ServerPlayerDeathSystem extends PassiveSystem {
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;

  protected CofManager cofs;

  @Subscribe
  public void onDeath(DeathEvent event) {
    int playerId = event.victim;
    if (playerId < 0 || !mPlayer.has(playerId)) return;

    if (mAttributesWrapper.has(playerId)) {
      StatRef life = mAttributesWrapper.get(playerId).attrs.get(Stat.hitpoints, StatRef.obtain());
      if (life != null) life.set(0f);
    }
    if (mVelocity.has(playerId)) mVelocity.remove(playerId);
    if (mTarget.has(playerId)) mTarget.remove(playerId);
    if (mPathfind.has(playerId)) mPathfind.remove(playerId);
    if (mCasting.has(playerId)) mCasting.remove(playerId);
    if (mSequence.has(playerId)) mSequence.remove(playerId);

    // Native player DT/DD records only exist for HTH. This state is included
    // in the owner's authoritative CofReferenceP snapshots.
    cofs.setWClass(playerId, Engine.WEAPON_HTH);
    mSequence.create(playerId).sequence(Engine.Player.MODE_DT, Engine.Player.MODE_DD);
    Gdx.app.log("ServerPlayerDeathSystem", String.format(
        "[PLAYER_DEATH_SYNC] phase=server player=%d killer=%d hp=0 mode=DT->DD",
        playerId, event.killer));
  }
}
