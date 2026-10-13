package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual 1.10f DS1/table regression for the fortress stair braziers. */
class Act4FortressResourceTest extends RiiabloTest {
  @Test void fortressBraziersUseNativeObjectClasses() {
    DS1 ds1 = DS1.loadFromFile(Riiablo.mpqs.resolve(
        "data/global/tiles/act4/fort/fortress.ds1"));
    assertEquals(4, ds1.getAct());
    int braziers = 0;
    for (DS1.Object object : ds1.objects) {
      if (object.type != DS1.Object.STATIC_TYPE) continue;
      int classId = Riiablo.files.obj.getObjectId(ds1.getAct(), object.id);
      com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(classId);
      if (object.id != 64 && object.id != 65) continue;
      braziers++;
      assertEquals(object.id == 64 ? "98" : "99", base.Token);
      assertEquals(classId, base.Id);
      assertTrue(base.Description.startsWith("fortress brazier"));
    }
    assertEquals(408, Riiablo.files.obj.getObjectId(4, 64));
    assertEquals(409, Riiablo.files.obj.getObjectId(4, 65));
    assertTrue(braziers > 0);
  }
}
