package com.riiablo.item;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.codec.StringTBLs;
import com.riiablo.codec.excel.Armor;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import com.riiablo.mpq.MPQFileHandleResolver;

public class ItemWriterTest {
  @BeforeAll
  public static void setup() {
    Gdx.app = new HeadlessApplication(new ApplicationAdapter() {});
    String d2Home = System.getenv("D2_HOME");
    if (d2Home == null || d2Home.isEmpty()) {
      d2Home = "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Diablo II";
    }
    Riiablo.home = Gdx.files.absolute(d2Home);
    Riiablo.mpqs = new MPQFileHandleResolver();
    Riiablo.string = new StringTBLs(Riiablo.mpqs);
    Riiablo.files = new Files();
  }

  @AfterAll
  public static void teardown() {
    Gdx.app.exit();
  }

  private void testItem(byte[] data) {
    ByteInput in = ByteInput.wrap(data);
    ItemReader reader = new ItemReader();
    Item spirit = reader.readItem(in);
    final String firstHexDump = ByteBufUtil.prettyHexDump(in.buffer(), 0, in.buffer().readerIndex());
    System.out.println(firstHexDump);

    ByteOutput out = ByteOutput.wrap(Unpooled.buffer(data.length, data.length));
    ItemWriter writer = new ItemWriter();
    writer.writeItem(spirit, out);
    System.out.println("Actual:");
    System.out.println(ByteBufUtil.prettyHexDump(out.buffer()));

    boolean equal = ByteBufUtil.equals(in.buffer(), 0, out.buffer(), 0, in.buffer().readerIndex());
    if (!equal) {
      System.out.println("Expected:");
      System.out.println(firstHexDump);
    }
    assertTrue(equal);
  }

  @Test
  public void Spirit() {
    testItem(Gdx.files.internal("test/Spirit.d2i").readBytes());
  }

  @Test
  public void Annihilus() {
    testItem(Gdx.files.internal("test/Annihilus.d2i").readBytes());
  }

  @Test
  public void Hunters_Bow_of_Blight() {
    testItem(Gdx.files.internal("test/Hunter's Bow of Blight.d2i").readBytes());
  }

  @Test
  public void Horadric_Malus() {
    testItem(Gdx.files.internal("test/Horadric Malus.d2i").readBytes());
  }

  @Test
  public void Wirts_Leg() {
    testItem(Gdx.files.internal("test/Wirt's Leg.d2i").readBytes());
  }

  @Test
  public void Grief() {
    testItem(Gdx.files.internal("test/Grief.d2i").readBytes());
  }

  @Test
  public void Horadric_Cube() {
    testItem(Gdx.files.internal("test/Horadric Cube.d2i").readBytes());
  }

  @Test
  public void Flawed_Ruby() {
    testItem(Gdx.files.internal("test/Flawed Ruby.d2i").readBytes());
  }

  @Test
  public void Thul_Rune() {
    testItem(Gdx.files.internal("test/Thul Rune.d2i").readBytes());
  }

  @Test
  public void Tome_of_Town_Portal() {
    testItem(Gdx.files.internal("test/Tome of Town Portal.d2i").readBytes());
  }

  @Test
  public void Tome_of_Identify() {
    testItem(Gdx.files.internal("test/Tome of Identify.d2i").readBytes());
  }

  @Test
  public void MissingWeaponDurabilityStatsStillSerialize() {
    // Quest weapons such as Khalim's Will can be created without the normal
    // durability StatRefs.  The writer must still emit the native zero-value
    // slots so the following item/stat lists remain bit-aligned.
    Item item = new ItemGenerator().generate("qf2");
    assertNotNull(item);
    assertNotNull(item.attrs);
    prepareStandardItem(item);
    item.attrs.base().clear();
    ByteOutput out = ByteOutput.wrap(Unpooled.buffer());
    assertDoesNotThrow(() -> new ItemWriter().writeItem(item, out));
    assertTrue(out.bytesWritten() > 0);
  }

  @Test
  public void MissingArmorClassStatStillSerializes() {
    // A generated/legacy armor can lose its base stat during item mutation.
    // The native stream still requires the inline armorclass field.
    Item item = new ItemGenerator().generate("cap");
    assertNotNull(item);
    assertNotNull(item.attrs);
    assertTrue(item.type.is(Type.ARMO));
    prepareStandardItem(item);
    Armor.Entry base = item.getBase();
    int expectedArmorClass = Math.min(base.minac, base.maxac)
        + Math.floorMod(item.id, Math.abs(base.maxac - base.minac) + 1);
    int expectedDurability = item.base.nodurability ? 0 : base.durability;
    item.attrs.base().clear();

    ByteOutput out = ByteOutput.wrap(Unpooled.buffer());
    assertDoesNotThrow(() -> new ItemWriter().writeItem(item, out));
    assertTrue(out.bytesWritten() > 0);

    Item restored = assertDoesNotThrow(() ->
        new ItemReader().readItem(ByteInput.wrap(out.buffer())));
    assertTrue(restored.type.is(Type.ARMO));
    assertEquals(expectedArmorClass,
        restored.attrs.base().get(com.riiablo.attributes.Stat.armorclass).asInt());
    assertEquals(expectedDurability,
        restored.attrs.base().get(com.riiablo.attributes.Stat.maxdurability).asInt());
    if (expectedDurability > 0) {
      assertEquals(expectedDurability,
          restored.attrs.base().get(com.riiablo.attributes.Stat.durability).asInt());
    }
  }

  private static void prepareStandardItem(Item item) {
    item.id = 1;
    item.version = Item.VERSION_110;
    item.ilvl = 1;
    item.quality = Quality.NORMAL;
    item.flags |= Item.ITEMFLAG_IDENTIFIED;
  }

  @Test
  public void Rugged_Small_Charm_of_Vita() {
    testItem(Gdx.files.internal("test/Rugged Small Charm of Vita.d2i").readBytes());
  }

  @Test
  public void Aldurs_Advance() {
    testItem(Gdx.files.internal("test/Aldur's Advance.d2i").readBytes());
  }

  @Test
  public void Blood_Eye() {
    testItem(Gdx.files.internal("test/Blood Eye.d2i").readBytes());
  }

  @Test
  public void Vampire_Gaze() {
    testItem(Gdx.files.internal("test/Vampire Gaze.d2i").readBytes());
  }

  @Test
  @Disabled("item is erroneously flagged socketed")
  public void Tome_of_Town_Portal_2() {
    // FIXME: should gracefully handle this as original game does
    testItem(Gdx.files.internal("test/Tome of Town Portal 2.d2i").readBytes());
  }
}
