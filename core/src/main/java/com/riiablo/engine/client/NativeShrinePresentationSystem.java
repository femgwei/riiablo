package com.riiablo.engine.client;

import com.riiablo.presentation.OverlayManager;

import com.riiablo.presentation.ClientGameplayContext;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Shrines;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.client.component.Label;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.map.Map;

/**
 * Presents the native shrine glyph while a shrine is still usable.
 *
 * <p>The stock client keeps this symbol on the shrine, then removes it when
 * the operation sequence starts.  Timed shrine states use the same DCC on the
 * player through {@link StateOverlaySystem}; the two presentations therefore
 * cannot accidentally be shown at the same time.</p>
 */
@All({NativeObjectState.class, Object.class})
public final class NativeShrinePresentationSystem extends IteratingSystem {
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;
  protected CofManager cofs;
  @com.artemis.annotations.Wire(name = "map")
  protected Map map;
  protected OverlayManager overlays;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();

  @Override
  protected void process(int entityId) {
    NativeObjectState state = mNativeObjectState.get(entityId);
    Object object = mObject.get(entityId);
    if (state == null || object == null || object.base == null
        || NativeObjectOperateTable.resolve(object.base, state.kind)
            != NativeObjectOperateTable.Lifecycle.SHRINE) return;

    if (state.shrineId < 0 && clientContext.files() != null && clientContext.files().Shrines != null) {
      MapWrapper wrapper = mMapWrapper.get(entityId);
      Position position = mPosition.get(entityId);
      int levelId = wrapper == null || wrapper.zone == null || wrapper.zone.level == null
          ? 0 : wrapper.zone.level.Id;
      int x = position == null ? 0 : (int) position.position.x;
      int y = position == null ? 0 : (int) position.position.y;
      int seed = map == null ? 0 : map.seed();
      state.persistShrineId(com.riiablo.engine.server.object.NativeShrineResolver.resolve(
          clientContext.files().Shrines, object.base, state.originalClassId,
          levelId, seed, x, y));
    }
    updateLabel(entityId, state.shrineId);

    boolean activated = state.activated
        || (object.stateFlags & Object.STATE_ACTIVATED) != 0;
    if (activated) {
      overlays.clearShrineIcon(entityId);
      // A stale OP snapshot must not leave the one-shot activation COF
      // looping forever.  Let an in-flight sequence finish, then converge
      // the visual mode to the authoritative persistent mode.
      int targetMode = object.mode >= com.riiablo.engine.Engine.Object.MODE_NU
          && object.mode <= com.riiablo.engine.Engine.Object.MODE_S5
          ? object.mode : state.currentMode;
      if (!mSequence.has(entityId) && mCofReference.has(entityId)
          && targetMode >= com.riiablo.engine.Engine.Object.MODE_NU
          && targetMode <= com.riiablo.engine.Engine.Object.MODE_S5
          && mCofReference.get(entityId).mode != targetMode) {
        cofs.setMode(entityId, (byte) targetMode);
      }
      return;
    }

    String overlayId = overlayFor(state.shrineId);
    if (overlayId == null) {
      overlays.clearShrineIcon(entityId);
    } else {
      overlays.setShrineIcon(entityId, overlayId);
    }
  }

  String overlayFor(int shrineId) {
    if (clientContext.files() == null || clientContext.files().Shrines == null
        || shrineId < 0 || shrineId >= clientContext.files().Shrines.size()) return null;
    Shrines.Entry shrine = clientContext.files().Shrines.get(shrineId);
    return shrine == null ? null : ClientEntityFactory.shrineOverlay(clientContext, shrineId);
  }

  private void updateLabel(int entityId, int shrineId) {
    if (!mLabel.has(entityId) || mLabel.get(entityId).actor == null
        || clientContext.files() == null || clientContext.files().Shrines == null
        || shrineId < 0 || shrineId >= clientContext.files().Shrines.size()) return;
    Shrines.Entry shrine = clientContext.files().Shrines.get(shrineId);
    if (shrine == null) return;
    String name = ClientEntityFactory.shrineDisplayName(clientContext, shrine);
    if (name == null || name.isEmpty()) return;
    clientContext.updateWorldLabel(mLabel.get(entityId).actor, name);
  }
}

