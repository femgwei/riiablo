package com.riiablo.map;

import com.d2moo.common.drlg.D2ObjectIds;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDoorIdsTest extends RiiabloTest {
  @Test void nativeDoorClassesMatchObjectsIdsHeaderAndActualOperateHandlers() {
    int[] actual = {D2ObjectIds.OBJECT_ANDARIELS_DOOR,
        D2ObjectIds.OBJECT_IRONGRATEDOORLEFT, D2ObjectIds.OBJECT_IRONGRATEDOORRIGHT,
        D2ObjectIds.OBJECT_WOODENGRATEDOORLEFT, D2ObjectIds.OBJECT_WOODENGRATEDOORRIGHT,
        D2ObjectIds.OBJECT_WOODENDOORLEFT, D2ObjectIds.OBJECT_WOODENDOORRIGHT,
        D2ObjectIds.OBJECT_TOMBDOORLEFT, D2ObjectIds.OBJECT_TOMBDOORRIGHT,
        D2ObjectIds.OBJECT_SLIMEDOOR1, D2ObjectIds.OBJECT_SLIMEDOOR2};
    assertArrayEquals(new int[] {47, 290, 291, 292, 293, 294, 295, 91, 92, 229, 230}, actual);
    for (int id : actual) {
      assertTrue(NativeObjectOperateTable.isNetworkDoor(Riiablo.files.objects.get(id)),
          "native door must use an authoritative door handler id=" + id);
    }
    assertFalse(NativeObjectOperateTable.isNetworkDoor(Riiablo.files.objects.get(74)),
        "trap door is not an ordinary tomb door");
  }
}
