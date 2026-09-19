package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.VitalsP;

/** Serializes the resolved server-authoritative life, mana, and stamina resources. */
public class VitalsSerializer implements FlatBuffersSerializer<AttributesWrapper, VitalsP> {
  public static final VitalsP table = new VitalsP();

  @Override
  public byte getDataType() {
    return ComponentP.VitalsP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, AttributesWrapper component) {
    Attributes attrs = component.attrs;
    float maxHitpoints = fixed(attrs, Stat.maxhp);
    float maxMana = fixed(attrs, Stat.maxmana);
    float maxStamina = fixed(attrs, Stat.maxstamina);
    float hitpoints = Math.min(fixed(attrs, Stat.hitpoints), maxHitpoints);
    return VitalsP.createVitalsP(
        builder,
        hitpoints,
        maxHitpoints,
        Math.min(fixed(attrs, Stat.mana), maxMana),
        maxMana,
        hitpoints <= 0f,
        Math.min(fixed(attrs, Stat.stamina), maxStamina),
        maxStamina);
  }

  @Override
  public VitalsP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public AttributesWrapper getData(
      EntitySync sync, int index, AttributesWrapper component) {
    VitalsP data = getTable(sync, index);
    apply(component, data);
    return component;
  }

  /** Applies resolved values only to the aggregate list to avoid double-counting equipment. */
  public static void apply(AttributesWrapper component, VitalsP data) {
    if (component == null || component.attrs == null) return;
    float maxHitpoints = sanitize(data.maxHitpoints());
    float maxMana = sanitize(data.maxMana());
    float maxStamina = sanitize(data.maxStamina());
    component.attrs.aggregate().put(Stat.maxhp, maxHitpoints);
    component.attrs.aggregate().put(Stat.hitpoints,
        Math.min(sanitize(data.hitpoints()), maxHitpoints));
    component.attrs.aggregate().put(Stat.maxmana, maxMana);
    component.attrs.aggregate().put(Stat.mana,
        Math.min(sanitize(data.mana()), maxMana));
    component.attrs.aggregate().put(Stat.maxstamina, maxStamina);
    component.attrs.aggregate().put(Stat.stamina,
        Math.min(sanitize(data.stamina()), maxStamina));
  }

  private static float fixed(Attributes attrs, short stat) {
    if (attrs == null) return 0f;
    StatRef value = attrs.get(stat, StatRef.obtain());
    return value == null ? 0f : sanitize(value.asFixed());
  }

  private static float sanitize(float value) {
    return Float.isFinite(value) ? Math.max(0f, value) : 0f;
  }
}
