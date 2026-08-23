package com.riiablo.save;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListReader;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class D2SWriter96StatTest extends RiiabloTest {
  @Test
  void preservesCsvFixedPointValuesWhenACharacterIsResaved() {
    D2S.StatData source = new D2S.StatData();
    source.attrs = Attributes.obtainLarge();
    source.attrs.base().putEncoded(Stat.hitpoints, 50 << 8);
    source.attrs.base().put(Stat.level, 1);

    ByteOutput out = ByteOutput.wrap(Unpooled.buffer());
    new D2SWriter96().writeStatData(source, out);

    D2S.StatData decoded = D2SReader96.readStatData(
        ByteInput.wrap(out.buffer()), new StatListReader());
    assertEquals(50, decoded.attrs.base().get(Stat.hitpoints).asInt());
    assertEquals(50 << 8, decoded.attrs.base().get(Stat.hitpoints).encodedValues());
    assertEquals(1, decoded.attrs.base().get(Stat.level).asInt());
  }
}
