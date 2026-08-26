package com.riiablo.engine.server.object;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Shrines;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.event.ShrineInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;

/** Native D2Game shrine effects/cooldowns and well charge regeneration. */
@All({NativeObjectState.class, com.riiablo.engine.server.component.Object.class})
public class NativeShrineSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(NativeShrineSystem.class);
  private static final float NATIVE_FRAMES_PER_SECOND = Animation.FRAMES_PER_SECOND;
  private static final int FRAMES_PER_RESET_MINUTE = 1200;

  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Interactable> mInteractable;

  protected EventSystem event;
  protected CofManager cofs;
  protected ObjectInteractor objectInteractor;

  @Wire(name = "map")
  protected Map map;

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent interaction) {
    if (interaction == null) return;
    if (interaction.lifecycle == Lifecycle.SHRINE && interaction.firstActivation()) {
      activateShrine(interaction);
    } else if (interaction.lifecycle == Lifecycle.WELL) {
      useWell(interaction);
    }
  }

  private void activateShrine(ObjectInteractionEvent interaction) {
    NativeObjectState state = mNativeObjectState.get(interaction.entityId);
    com.riiablo.engine.server.component.Object object = mObject.get(interaction.entityId);
    if (state == null || object == null || object.base == null
        || Riiablo.files == null || Riiablo.files.Shrines == null) {
      log.warn("[SHRINE] activation data unavailable: entity={}, state={}, object={}, table={}",
          interaction.entityId, state != null, object != null,
          Riiablo.files != null && Riiablo.files.Shrines != null);
      return;
    }

    int shrineId = state.shrineId;
    if (shrineId < 0) {
      shrineId = resolveShrineId(state, object.base, interaction.entityId);
      state.persistShrineId(shrineId);
    }
    Shrines.Entry shrine = Riiablo.files.Shrines.get(shrineId);
    if (shrine == null) {
      log.warn("[SHRINE] unresolved row: entity={}, object={}, shrineId={}",
          interaction.entityId, interaction.objectClassId, shrineId);
      return;
    }

    NativeShrineEffectResolver.Effect effect = NativeShrineEffectResolver.resolve(shrine);
    Attributes attrs = attributes(interaction.playerId);
    boolean appliedLocally = applyBasicEffect(
        attrs, shrine.Code, shrine.Arg0, shrine.Arg1);
    int resetFrames = resetFrames(shrine.ResetTimeInMinutes);
    state.persistShrineCooldownFrames(resetFrames);
    event.dispatch(ShrineInteractionEvent.obtain(
        interaction.playerId, interaction.entityId, shrineId, effect.code,
        effect.effectClass, effect.kind, effect.arg0, effect.arg1,
        effect.durationFrames, resetFrames, appliedLocally));

    log.info("[SHRINE] activated: entity={}, player={}, shrineId={}, code={}, "
            + "kind={}, effectClass={}, localEffect={}, resetFrames={}",
        interaction.entityId, interaction.playerId, shrineId, shrine.Code,
        effect.kind, effect.effectClass, appliedLocally, resetFrames);
  }

  private int resolveShrineId(NativeObjectState state, Objects.Entry object, int entityId) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    int levelId = wrapper == null || wrapper.zone == null ? 0 : wrapper.zone.level.Id;
    Position position = mPosition.get(entityId);
    int x = state.source != null ? state.source.x
        : position == null ? 0 : (int) position.position.x;
    int y = state.source != null ? state.source.y
        : position == null ? 0 : (int) position.position.y;
    int seed = map == null ? 0 : map.seed();
    return NativeShrineResolver.resolve(Riiablo.files.Shrines, object,
        state.originalClassId, levelId, seed, x, y);
  }

  private void useWell(ObjectInteractionEvent interaction) {
    NativeObjectState state = mNativeObjectState.get(interaction.entityId);
    com.riiablo.engine.server.component.Object object = mObject.get(interaction.entityId);
    if (state == null || object == null || object.base == null) return;

    Objects.Entry base = object.base;
    int maxCharges = wellMaxCharges(base);
    if (state.wellCharges < 0) state.persistWellCharges(maxCharges);
    if (state.wellCharges <= 0) return;

    Attributes attrs = attributes(interaction.playerId);
    if (!applyWellEffect(attrs, base)) return;

    state.persistWellCharges(state.wellCharges - 1);
    state.persistWellRegenFrames(wellRegenDelay(base));
    updateWellMode(interaction.entityId, state, base);
    log.info("[WELL] used: entity={}, player={}, charges={}/{}, regenFrames={}",
        interaction.entityId, interaction.playerId, state.wellCharges,
        maxCharges, state.wellRegenFrames);
  }

  @Override
  protected void process(int entityId) {
    NativeObjectState state = mNativeObjectState.get(entityId);
    com.riiablo.engine.server.component.Object object = mObject.get(entityId);
    if (state == null || object == null || object.base == null) return;
    Lifecycle lifecycle = NativeObjectOperateTable.resolve(object.base, state.kind);
    float elapsedFrames = Math.max(0f, world.delta) * NATIVE_FRAMES_PER_SECOND;

    if (lifecycle == Lifecycle.SHRINE) {
      processShrineCooldown(entityId, state, object.base, elapsedFrames);
    } else if (lifecycle == Lifecycle.WELL) {
      processWellRegeneration(entityId, state, object.base, elapsedFrames);
    }
  }

  private void processShrineCooldown(int entityId, NativeObjectState state,
      Objects.Entry base, float elapsedFrames) {
    if (!state.activated) return;
    // Also repairs a room-recreated active shrine, which the factory initially
    // inserts as selectable before NativeObjectState is attached.
    if (mInteractable.has(entityId)) mInteractable.remove(entityId);
    if (state.shrineCooldownFrames <= 0f) return; // reset=0 is one-shot

    float remaining = state.shrineCooldownFrames - elapsedFrames;
    state.persistShrineCooldownFrames(remaining);
    if (remaining > 0f) return;

    state.persistActivated(false);
    state.persistMode(Engine.Object.MODE_NU);
    if (mCofReference.has(entityId)) cofs.setMode(entityId, Engine.Object.MODE_NU);
    mInteractable.create(entityId).set(interactionRange(base), objectInteractor);
    log.info("[SHRINE] reactivated: entity={}, shrineId={}", entityId, state.shrineId);
  }

  private void processWellRegeneration(int entityId, NativeObjectState state,
      Objects.Entry base, float elapsedFrames) {
    int maxCharges = wellMaxCharges(base);
    if (state.wellCharges < 0) state.persistWellCharges(maxCharges);
    if (state.wellCharges >= maxCharges || state.wellRegenFrames <= 0f) return;

    float remaining = state.wellRegenFrames - elapsedFrames;
    state.persistWellRegenFrames(remaining);
    if (remaining > 0f) return;

    state.persistWellCharges(Math.min(maxCharges, state.wellCharges + 1));
    state.persistWellRegenFrames(
        state.wellCharges < maxCharges ? wellRegenDelay(base) : 0f);
    updateWellMode(entityId, state, base);
  }

  private void updateWellMode(int entityId, NativeObjectState state, Objects.Entry base) {
    byte mode = (byte) wellMode(state.currentMode, state.wellCharges, parm(base, 2));
    if (state.currentMode == mode) return;
    state.persistMode(mode);
    if (mCofReference.has(entityId)) cofs.setMode(entityId, mode);
  }

  private Attributes attributes(int entityId) {
    AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
    return wrapper == null ? null : wrapper.attrs;
  }

  static boolean applyBasicEffect(Attributes attrs, int code, int arg0, int arg1) {
    if (attrs == null) return false;
    float hp = value(attrs, Stat.hitpoints);
    float maxHp = value(attrs, Stat.maxhp);
    float mana = value(attrs, Stat.mana);
    float maxMana = value(attrs, Stat.maxmana);
    switch (code) {
      case 1:
        setCurrent(attrs, Stat.hitpoints, maxHp);
        setCurrent(attrs, Stat.mana, maxMana);
        return true;
      case 2:
        setCurrent(attrs, Stat.hitpoints, maxHp);
        return true;
      case 3:
        setCurrent(attrs, Stat.mana, maxMana);
        return true;
      case 4: {
        float loss = hp * Math.max(0, arg0) / 100f;
        setCurrent(attrs, Stat.hitpoints, Math.max(0f, hp - loss));
        setCurrent(attrs, Stat.mana,
            Math.min(maxMana, mana + loss * Math.max(0, arg1) / 100f));
        return true;
      }
      case 5: {
        float loss = mana * Math.max(0, arg0) / 100f;
        setCurrent(attrs, Stat.mana, Math.max(0f, mana - loss));
        setCurrent(attrs, Stat.hitpoints,
            Math.min(maxHp, hp + loss * Math.max(0, arg1) / 100f));
        return true;
      }
      default:
        return false;
    }
  }

  static boolean applyWellEffect(Attributes attrs, Objects.Entry base) {
    if (attrs == null || base == null) return false;
    float fraction = Math.max(0, parm(base, 1)) / 256f;
    int mask = parm(base, 3);
    boolean used = false;
    if ((mask & 2) != 0) {
      used |= restoreFraction(attrs, Stat.hitpoints, Stat.maxhp, fraction);
    }
    if ((mask & 1) != 0) {
      used |= restoreFraction(attrs, Stat.mana, Stat.maxmana, fraction);
    }
    used |= restoreFraction(attrs, Stat.stamina, Stat.maxstamina, fraction);
    // Native wells also cleanse poison/freeze/curse states and heal pets.
    // Those effects remain behind ShrineInteractionEvent/combat ownership.
    return used;
  }

  private static boolean restoreFraction(Attributes attrs, short currentStat,
      short maxStat, float fraction) {
    float current = value(attrs, currentStat);
    float max = value(attrs, maxStat);
    if (current >= max) return false;
    setCurrent(attrs, currentStat, Math.min(max, current + max * fraction));
    return true;
  }

  private static float value(Attributes attrs, short stat) {
    return attrs.aggregate().getValue(stat, 0f);
  }

  private static void setCurrent(Attributes attrs, short stat, float value) {
    // Aggregate drives live gameplay while base is what D2SWriter persists.
    attrs.base().put(stat, value);
    attrs.aggregate().put(stat, value);
  }

  public static int resetFrames(int resetMinutes) {
    return Math.max(0, resetMinutes) * FRAMES_PER_RESET_MINUTE;
  }

  static int wellMaxCharges(Objects.Entry base) {
    return 2 * Math.max(0, parm(base, 2));
  }

  static int wellRegenDelay(Objects.Entry base) {
    return Math.max(0, parm(base, 0)) + 1;
  }

  static int wellMode(int currentMode, int charges, int chargesPerMode) {
    if (chargesPerMode <= 0) return Engine.Object.MODE_ON;
    int max = 2 * chargesPerMode;
    int safe = Math.max(0, Math.min(charges, max));
    if (safe == 0) return Engine.Object.MODE_ON;
    if (safe == chargesPerMode) return Engine.Object.MODE_OP;
    if (safe == max) return Engine.Object.MODE_NU;
    // D2Game changes the well animation only when a charge threshold is
    // crossed; intermediate uses/regeneration events retain the prior mode.
    return currentMode;
  }

  private static float interactionRange(Objects.Entry base) {
    return base.OperateRange > 0 ? base.OperateRange : 3f;
  }

  private static int parm(Objects.Entry base, int index) {
    return base == null || base.Parm == null || index < 0 || index >= base.Parm.length
        ? 0 : base.Parm[index];
  }
}
