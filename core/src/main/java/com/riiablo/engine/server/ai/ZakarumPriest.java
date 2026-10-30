package com.riiablo.engine.server.ai;

import com.artemis.utils.IntBag;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** D2MOO AiThink.cpp AITHINK_Fn049_ZakarumPriest (0x6FCD9A10). */
public class ZakarumPriest extends NativeDecisionAI {
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, frame);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, teleportAfter);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, blizzardAfter);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, lightningAfter);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    float savedFrame = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedTeleportAfter = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedBlizzardAfter = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedLightningAfter = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    if (apply) {
      frame = savedFrame;
      teleportAfter = savedTeleportAfter;
      blizzardAfter = savedBlizzardAfter;
      lightningAfter = savedLightningAfter;
    }
  }
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_BLIZZARD_CHANCE = 1;
  static final int PARAM_LIGHTNING_CHANCE = 2;
  static final int PARAM_CAST_CHANCE = 3;
  static final int PARAM_SPELL_TIMER = 4;
  static final int PARAM_HEAL_RANGE = 5;

  private float frame;
  private float teleportAfter;
  private float blizzardAfter;
  private float lightningAfter;
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
  private final Vector2 landing = new Vector2();

  public ZakarumPriest(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    frame += Math.max(0f, delta) / Animation.FRAME_DURATION;
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    // The shared runtime has no native MONSTER_GetAiState 3/19 projection yet;
    // this branch currently handles bCombat, not that additional native gate.
    if (combat) {
      if (hasSkill(2) && lifePercentage(entityId) < 33 && frame > teleportAfter) {
        teleportAfter = frame + 4f * Math.max(0, params[PARAM_SPELL_TIMER]);
        Vector2 source = mPosition.get(entityId).position;
        Vector2 target = mPosition.get(targetId).position;
        int targetX = Math.round(target.x);
        int targetY = Math.round(target.y);
        landing.set(targetX + 4 * (targetX - Math.round(source.x)),
            targetY + 4 * (targetY - Math.round(source.y)));
        if (canTeleportTo(landing)) {
          castAt(2, Engine.INVALID_ENTITY, landing);
          return;
        }
      }
      if (rollAiChance(params[PARAM_ATTACK_CHANCE])) {
        attack(targetId);
        return;
      }
    }

    int ally = findHealTarget();
    if (hasSkill(0) && ally != Engine.INVALID_ENTITY && rollAiChance(25)) {
      cast(0, ally);
      return;
    }

    if (hasMissileBarrier(targetId)) {
      // Behind a missile barrier only Blizzard is eligible; do not roll the
      // ordinary cast/lightning probabilities or chase through the barrier.
      if (rollAiChance(params[PARAM_ATTACK_CHANCE]) && hasSkill(3)
          && frame > blizzardAfter && rollAiChance(params[PARAM_BLIZZARD_CHANCE])) {
        cast(3, targetId);
        blizzardAfter = frame + Math.max(0, params[PARAM_SPELL_TIMER]);
        return;
      }
    } else if (rollAiChance(params[PARAM_CAST_CHANCE])) {
      if (hasSkill(3) && frame > blizzardAfter
          && rollAiChance(params[PARAM_BLIZZARD_CHANCE])) {
        cast(3, targetId);
        blizzardAfter = frame + Math.max(0, params[PARAM_SPELL_TIMER]);
        return;
      }
      if (hasSkill(1) && frame > lightningAfter
          && rollAiChance(params[PARAM_LIGHTNING_CHANCE])
          && hasLineOfSight(targetId)) {
        cast(1, targetId);
        lightningAfter = frame + 20;
        return;
      }
    }

    if (rollAiPercentage() >= 30) idle(20);
    else circle(targetId, 4f); // shared approximation of sub_6FCD0E80
  }

  /** Native callback keeps the last eligible unit (it never updates its life sentinel). */
  int findHealTarget() {
    // STATLIST_AreUnitsAligned accepts only evil/evil or good/good, and the
    // callback additionally excludes good targets; neutral is not aligned.
    if (monster.converted || monster.monstats.Align != 0) return Engine.INVALID_ENTITY;
    long range = Math.max(0, params[PARAM_HEAL_RANGE]);
    long range2 = range * range;
    int selected = Engine.INVALID_ENTITY;
    IntBag units = getEnemyEntities().getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (id == entityId || !mMonster.has(id) || mCorpse.has(id)
          || mPlayer.has(id) || mMercenary.has(id) || mSummonedPet.has(id)) continue;
      Monster candidate = mMonster.get(id);
      if (candidate.monstats == null || candidate.converted
          || candidate.monstats.Align != 0) continue;
      String base = candidate.monstats.BaseId;
      if (!"zealot1".equalsIgnoreCase(base) && !"cantor1".equalsIgnoreCase(base)) continue;
      if (!inHealRooms(id) || squaredDistance(id) > range2) continue;
      int life = lifePercentage(id);
      if (life < 0 || life > 60) continue;
      selected = id;
    }
    return selected;
  }

  private long squaredDistance(int id) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(id).position;
    long dx = (long) Math.round(source.x) - Math.round(target.x);
    long dy = (long) Math.round(source.y) - Math.round(target.y);
    return dx * dx + dy * dy;
  }

  private boolean inHealRooms(int id) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    MapWrapper target = mMapWrapper.has(id) ? mMapWrapper.get(id) : null;
    if (source != null && target != null && source.map != null && target.map != null
        && source.map != target.map) return false;
    if (source == null || source.zone == null) {
      return target == null || target.zone == null;
    }
    if (target == null || target.map != source.map || target.zone == null
        || target.zone.isTown()) return false;
    if (target.zone != source.zone) {
      return source.map != null && source.map.areZonesAdjacent(source.zone, target.zone);
    }
    Vector2 a = mPosition.get(entityId).position;
    Vector2 b = mPosition.get(id).position;
    return source.zone.areRoomsAdjacent(a.x, a.y, b.x, b.y);
  }

  private int lifePercentage(int id) {
    if (!mAttributesWrapper.has(id)) return 100;
    Attributes attrs = mAttributesWrapper.get(id).attrs;
    if (attrs == null) return 100;
    if (!attrs.contains(Stat.hitpoints) || !attrs.contains(Stat.maxhp)) return 100;
    // Attributes.get reuses one StatRef; capture values, not two aliases to it.
    float hp = attrs.getValue(Stat.hitpoints, 0f);
    float max = attrs.getValue(Stat.maxhp, 0f);
    if (max <= 0f) return 100;
    // Keep living sub-one-percent units eligible for healing, not corpses.
    if (hp <= 0f) return -1;
    return (int) (100f * hp / max);
  }

  private boolean hasMissileBarrier(int targetId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.map == null || source.zone == null) return false;
    ray.set(mPosition.get(entityId).position, mPosition.get(targetId).position);
    return source.map.castRay(source.zone, ray, DT1.Tile.FLAG_BLOCK_JUMP, 0, collision, false);
  }

  /** Lightning is a direct line spell; solid map walls stop the cast. */
  private boolean hasLineOfSight(int targetId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    // Unit-test/synthetic worlds may omit a map entirely; retain their
    // previous behavior and enforce LOS whenever authoritative terrain exists.
    if (source == null || source.map == null || source.zone == null
        || !mPosition.has(targetId)) return true;
    ray.set(mPosition.get(entityId).position, mPosition.get(targetId).position);
    return !source.map.castRay(source.zone, ray, 0xC01, 0, collision, false);
  }

  private boolean canTeleportTo(Vector2 destination) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.map == null || source.zone == null) return true;
    Map map = source.map;
    Map.Zone zone = map.getZone(destination);
    if (zone == null || zone.isTown() || zone != source.zone) return false;
    Vector2 position = mPosition.get(entityId).position;
    if (!zone.areRoomsAdjacent(position.x, position.y, destination.x, destination.y)) {
      return false;
    }
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    if (map.isBlockedForPath(zone, Map.round(destination.x), Map.round(destination.y),
        DT1.Tile.FLAG_BLOCK_WALK, size, false)) return false;
    // Artemis treats BaseSystem fields as required even with failOnNull=false.
    // Look up this optional service instead of making every AI test world own it.
    DynamicUnitCollisionSystem dynamicCollision =
        Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    if (dynamicCollision != null && !dynamicCollision.isFreeForLanding(entityId,
        Map.round(destination.x), Map.round(destination.y), size)) return false;
    ray.set(mPosition.get(entityId).position, destination);
    return !map.castRay(source.zone, ray, 0xC01, 0, collision, false);
  }
}
