package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;

/** Samples presentation transforms without writing simulation/prediction components. */
public final class AuthoritativeInterpolationSystem extends BaseSystem {
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Angle> mAngle;

  private final IntMap<Entry> entries = new IntMap<>();
  private final IntMap<LocalCorrection> localCorrections = new IntMap<>();
  private final IntArray expiredLocalCorrections = new IntArray();
  private boolean rendering;

  @Override
  protected void processSystem() {
    // Snapshot recording happens in ClientNetworkReceiver. Simulation uses
    // authoritative Position/Angle directly and needs no interpolation work.
  }

  public boolean record(int entityId, long tick, long serverTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY) {
    return record(entityId, tick, serverTimeMillis, positionPresent, x, y,
        anglePresent, angleX, angleY, false);
  }

  public boolean record(int entityId, long tick, long serverTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY,
                        boolean forceSnap) {
    Entry entry = entries.get(entityId);
    if (entry == null) {
      entry = new Entry();
      entries.put(entityId, entry);
    }
    return entry.buffer.record(tick, serverTimeMillis,
        positionPresent, x, y, anglePresent, angleX, angleY, forceSnap);
  }

  public void remove(int entityId) {
    entries.remove(entityId);
    localCorrections.remove(entityId);
  }

  /** Keeps simulation corrected immediately while fading the visual offset. */
  public void correctLocal(int entityId, float renderOffsetX, float renderOffsetY,
      boolean hardCorrection) {
    if (hardCorrection || renderOffsetX * renderOffsetX + renderOffsetY * renderOffsetY
        <= ClientPredictionBuffer.SMALL_ERROR * ClientPredictionBuffer.SMALL_ERROR) {
      localCorrections.remove(entityId);
      return;
    }
    LocalCorrection correction = localCorrections.get(entityId);
    if (correction == null) {
      correction = new LocalCorrection();
      localCorrections.put(entityId, correction);
    }
    correction.offset.set(renderOffsetX, renderOffsetY);
    correction.remaining = LocalCorrection.DURATION;
  }

  public void beginRender(float renderDelta) {
    if (rendering) throw new IllegalStateException("render interpolation already active");
    rendering = true;
    expiredLocalCorrections.clear();
    for (IntMap.Entry<Entry> mapEntry : entries) {
      Entry entry = mapEntry.value;
      entry.buffer.advance(renderDelta);
      entry.buffer.samplePosition(entry.renderPosition);
      entry.buffer.sampleAngle(entry.renderAngle);
    }
    for (IntMap.Entry<LocalCorrection> mapEntry : localCorrections) {
      int entityId = mapEntry.key;
      LocalCorrection correction = mapEntry.value;
      if (!mPosition.has(entityId)) {
        expiredLocalCorrections.add(entityId);
        continue;
      }
      Position position = mPosition.get(entityId);
      float alpha = Math.max(0f, correction.remaining / LocalCorrection.DURATION);
      correction.renderPosition.set(position.position).mulAdd(correction.offset, alpha);
      correction.remaining -= Float.isFinite(renderDelta) ? Math.max(0f, renderDelta) : 0f;
      if (correction.remaining <= 0f) expiredLocalCorrections.add(entityId);
    }
  }

  public void endRender() {
    if (!rendering) return;
    for (int i = 0; i < expiredLocalCorrections.size; i++) {
      localCorrections.remove(expiredLocalCorrections.get(i));
    }
    expiredLocalCorrections.clear();
    rendering = false;
  }

  /** Always copies into caller-owned storage; no simulation vector escapes. */
  public Vector2 position(int entityId, Vector2 out) {
    LocalCorrection correction = rendering ? localCorrections.get(entityId) : null;
    if (correction != null && mPosition.has(entityId)) return out.set(correction.renderPosition);
    Entry entry = rendering ? entries.get(entityId) : null;
    if (entry != null && entry.buffer.hasPosition() && mPosition.has(entityId)) {
      return out.set(entry.renderPosition);
    }
    return out.set(mPosition.get(entityId).position);
  }

  public Vector2 angle(int entityId, Vector2 out) {
    Entry entry = rendering ? entries.get(entityId) : null;
    if (entry != null && entry.buffer.hasAngle() && mAngle.has(entityId)) {
      return out.set(entry.renderAngle);
    }
    return out.set(mAngle.get(entityId).angle);
  }

  public boolean hasRenderAngle(int entityId) {
    Entry entry = rendering ? entries.get(entityId) : null;
    return entry != null && entry.buffer.hasAngle() && mAngle.has(entityId);
  }

  public int trackedEntities() {
    return entries.size;
  }

  /** Drops interpolation history when a new authoritative baseline starts. */
  public void clear() {
    entries.clear();
    localCorrections.clear();
    expiredLocalCorrections.clear();
  }

  private static final class Entry {
    final AuthoritativeTransformInterpolator buffer =
        new AuthoritativeTransformInterpolator();
    final Vector2 renderPosition = new Vector2();
    final Vector2 renderAngle = new Vector2();
  }

  private static final class LocalCorrection {
    static final float DURATION = 0.12f;
    final Vector2 offset = new Vector2();
    final Vector2 renderPosition = new Vector2();
    float remaining;
  }
}
