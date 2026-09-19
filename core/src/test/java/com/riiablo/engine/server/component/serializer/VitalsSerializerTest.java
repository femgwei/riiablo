package com.riiablo.engine.server.component.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.net.packet.d2gs.VitalsP;
import org.junit.jupiter.api.Test;

class VitalsSerializerTest extends RiiabloTest {
  @Test
  void clampsCurrentResourcesToSnapshotMaximums() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int offset = VitalsP.createVitalsP(builder,
        69f, 62f,
        30f, 21f,
        false,
        100f, 89f);
    builder.finish(offset);

    Attributes attrs = Attributes.obtainLarge();
    AttributesWrapper wrapper = new AttributesWrapper();
    wrapper.attrs = attrs;
    VitalsSerializer.apply(wrapper, VitalsP.getRootAsVitalsP(builder.dataBuffer()));

    assertEquals(62f, attrs.aggregate().get(Stat.hitpoints).asFixed());
    assertEquals(62f, attrs.aggregate().get(Stat.maxhp).asFixed());
    assertEquals(21f, attrs.aggregate().get(Stat.mana).asFixed());
    assertEquals(89f, attrs.aggregate().get(Stat.stamina).asFixed());
  }

  @Test
  void neverSerializesCurrentLifeAboveMaximumLife() {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().put(Stat.hitpoints, 69f);
    attrs.base().put(Stat.maxhp, 62f);
    attrs.base().put(Stat.mana, 21f);
    attrs.base().put(Stat.maxmana, 21f);
    attrs.base().put(Stat.stamina, 89f);
    attrs.base().put(Stat.maxstamina, 89f);
    attrs.reset();
    AttributesWrapper wrapper = new AttributesWrapper();
    wrapper.attrs = attrs;

    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int offset = new VitalsSerializer().putData(builder, wrapper);
    builder.finish(offset);
    VitalsP snapshot = VitalsP.getRootAsVitalsP(builder.dataBuffer());

    assertEquals(62f, snapshot.hitpoints());
    assertEquals(62f, snapshot.maxHitpoints());
  }
}
