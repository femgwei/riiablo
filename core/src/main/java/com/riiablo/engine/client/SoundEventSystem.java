package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.audio.Audio;
import com.riiablo.audio.MonsterAudio;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.presentation.ClientSpatialAudioServices;
import java.util.Objects;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Client-side presentation for authoritative one-shot sound intents.
 *
 * <p>Only the sound key and source entity cross the simulation boundary. The
 * local audio session owns playback, while this system applies the listener's
 * current zone and native monster distance falloff.</p>
 */
public final class SoundEventSystem extends PassiveSystem {
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  private final ClientSpatialAudioServices session;

  public SoundEventSystem(ClientSpatialAudioServices session) {
    this.session = Objects.requireNonNull(session, "session");
  }

  @Subscribe
  public void onSound(SoundEvent event) {
    if (event == null || event.sound == null || event.sound.isEmpty()) return;
    Audio audio = session.audio();
    if (audio == null) return;

    int listenerId = session.listener();
    if (listenerId == Engine.INVALID_ENTITY || !mPosition.has(listenerId)) return;

    boolean hasEntityPosition = mPosition.has(event.entityId);
    if (!hasEntityPosition && !event.positionPresent) return;
    float sourceX = hasEntityPosition ? mPosition.get(event.entityId).position.x : event.x;
    float sourceY = hasEntityPosition ? mPosition.get(event.entityId).position.y : event.y;
    boolean sameZone = hasEntityPosition ? sameZone(event.entityId, listenerId) : true;
    float gain = MonsterAudio.spatialGain(
        mPosition.get(listenerId).position.dst2(sourceX, sourceY), sameZone);
    if (gain <= 0f) return;

    Audio.Instance instance = audio.play(event.sound, event.global);
    if (instance != null) instance.setVolume(gain);
  }

  private boolean sameZone(int source, int listener) {
    if (!mMapWrapper.has(source) || !mMapWrapper.has(listener)) return true;
    MapWrapper sourceMap = mMapWrapper.get(source);
    MapWrapper listenerMap = mMapWrapper.get(listener);
    if (sourceMap == null || listenerMap == null
        || sourceMap.zone == null || listenerMap.zone == null) return true;
    return sourceMap.zone == listenerMap.zone;
  }
}

