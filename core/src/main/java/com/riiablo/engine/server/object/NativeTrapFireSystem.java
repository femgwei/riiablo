package com.riiablo.engine.server.object;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeTrapFire;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import net.mostlyoriginal.api.event.common.EventSystem;

/** Ticks native fire trap objects and removes them after their animation lifetime. */
@Wire(failOnNull = false)
public class NativeTrapFireSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(NativeTrapFireSystem.class);

  protected ComponentMapper<NativeTrapFire> mFire;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected EventSystem events;

  private EntitySubscription players;

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, Position.class, AttributesWrapper.class, MapWrapper.class));
  }

  @Override
  protected void processSystem() {
    IntBag fires = world.getAspectSubscriptionManager()
        .get(Aspect.all(NativeTrapFire.class, Position.class, MapWrapper.class))
        .getEntities();
    int[] ids = fires.getData();
    float delta = Math.min(0.25f, Math.max(0f, world.getDelta()));
    for (int i = 0, size = fires.size(); i < size; i++) {
      int fireId = ids[i];
      if (!world.getEntityManager().isActive(fireId)) continue;
      NativeTrapFire fire = mFire.get(fireId);
      fire.remaining -= delta;
      fire.untilDamageTick -= delta;
      if (fire.remaining <= 0f) {
        log.debug("[OBJECT_TRAP_FIRE] expired entity={}", fireId);
        world.delete(fireId);
        continue;
      }
      if (fire.untilDamageTick > 0f) continue;
      fire.untilDamageTick = (NativeTrapFire.MIN_TICK_FRAMES
          + fire.nextInt(NativeTrapFire.TICK_FRAME_RANGE)) / 25f;
      tickTargets(fireId, fire, players);
    }
  }

  private void tickTargets(int fireId, NativeTrapFire fire, EntitySubscription subscription) {
    if (subscription == null) return;
    Position sourcePosition = mPosition.get(fireId);
    MapWrapper sourceWrapper = mMapWrapper.get(fireId);
    if (sourcePosition == null || sourceWrapper == null || sourceWrapper.zone == null
        || sourceWrapper.zone.isTown()) return;

    IntBag targets = subscription.getEntities();
    int[] ids = targets.getData();
    for (int i = 0, size = targets.size(); i < size; i++) {
      int targetId = ids[i];
      Position targetPosition = mPosition.get(targetId);
      MapWrapper targetWrapper = mMapWrapper.get(targetId);
      if (targetPosition == null || targetWrapper == null
          || targetWrapper.zone != sourceWrapper.zone
          || sourcePosition.position.dst2(targetPosition.position) > fire.radius * fire.radius) continue;
      applyDamage(fireId, targetId, fire);
    }
  }

  private void applyDamage(int fireId, int targetId, NativeTrapFire fire) {
    AttributesWrapper wrapper = mAttributes.get(targetId);
    Attributes attrs = wrapper == null ? null : wrapper.attrs;
    if (attrs == null) return;
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    if (hp == null || hp.asFixed() <= 0f) return;

    if (fire.damagePercent <= 0) return;
    int hitpoints = Math.max(1, Math.round(hp.asFixed()));
    int level = Math.max(1, attrs.getValue(Stat.level, 1));
    int dexterity = Math.max(0, attrs.getValue(Stat.dexterity, 0));
    int defense = Math.max(0, attrs.getValue(Stat.armorclass, 0));
    int levelRoll = fire.nextInt(Math.max(1, level >> 2));
    int chanceToHit = Math.max(
        2 * ((((level + levelRoll) & 0xFF) - 5 * (dexterity >> 1) - level))
            - defense + 125,
        65);
    if (fire.nextInt(100) >= chanceToHit) return;
    int min = Math.max(hitpoints >> 5, 1);
    int max = Math.max(hitpoints >> 3, min + 1);
    int roll = min + fire.nextInt(max - min + 1);
    float damage = Math.max(1f, roll * fire.damagePercent / 100f);
    DamageEvent event = DamageEvent.obtain(fireId, targetId, damage);
    if (events != null) events.dispatch(event);
    float applied = Math.max(0f, event.damage);
    hp.sub(applied);
    if (hp.asFixed() <= 0f) {
      hp.set(0f);
      if (events != null) events.dispatch(DeathEvent.obtain(fireId, targetId));
    }
    log.debug("[OBJECT_TRAP_FIRE] damage fire={} target={} damage={} hp={}",
        fireId, targetId, applied, hp.asFixed());
  }
}
