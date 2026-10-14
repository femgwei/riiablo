package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeTableIdentityTest {
  private static <T extends Excel> T load(Class<T> table, String contents) {
    Application previous = Gdx.app;
    Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
        new Class<?>[] {Application.class}, (proxy, method, args) -> null);
    try {
      return Excel.load(table, new FileHandle("identity-fixture.txt") {
        @Override public InputStream read() {
          return new ByteArrayInputStream(contents.getBytes(StandardCharsets.US_ASCII));
        }
      });
    } finally {
      Gdx.app = previous;
    }
  }

  @Test
  void levelTypesUseSourceOrdinalRatherThanAuthoredId() {
    LvlTypes table = load(LvlTypes.class,
        "Name\tId\tFile 1\n"
        + "none\t0\t\n"
        + "decoy\t2\twrong-row.dt1\n"
        + "outdoor\t9\toutdoor.dt1\n");
    assertEquals(9, table.get(2).Id);
    assertEquals("outdoor.dt1", table.get(2).File[0]);
    assertEquals("wrong-row.dt1", table.get(1).File[0]);
    assertNull(table.get(9));
  }

  @Test
  void soundNamesResolveTheSameIndexAsNumericReferences() {
    Sounds sounds = load(Sounds.class,
        "Sound\tIndex\tFileName\tGroup Size\n"
        + "first\t10\tfirst.wav\t2\nsecond\t11\tsecond.wav\t0\n");
    assertEquals(10, sounds.index("first"));
    assertEquals("first.wav", sounds.get("first").FileName);
    assertEquals("second.wav", sounds.get(11).FileName);
    assertNull(sounds.get(0));
  }

  @Test
  void skillsUseCompiledRowsAndDescriptionsUseNames() {
    Skills skills = load(Skills.class,
        "Id\tskill\tskilldesc\n99\tAttack\tattack-description\n"
        + "77\tDivergent\tdivergent-description\n");
    SkillDesc descriptions = load(SkillDesc.class,
        "skilldesc\tIconCel\nunused\t3\nattack-description\t4\n"
        + "divergent-description\t17\n");
    assertEquals("Divergent", skills.get(1).skill);
    assertEquals(1, skills.get(1).Id);
    assertEquals(1, skills.index("Divergent"));
    assertEquals(17, descriptions.get(skills.get(1).skilldesc).IconCel);
    assertNull(skills.get(77));
    assertNull(descriptions.get("missing"));
  }

  @Test
  void objectsKeepDuplicateAuthoredIdsAsDistinctSourceRows() {
    Objects objects = load(Objects.class,
        "Id\tName\n408\tfirst\n408\tsecond\n");
    assertEquals("first", objects.get(0).Name);
    assertEquals("second", objects.get(1).Name);
    assertEquals(1, objects.get(1).Id);
    assertEquals(2, objects.size());
    assertNull(objects.get(408));
  }

  @Test
  void monsterPresetsUseIndependentPerActOrdinals() {
    // Locks riiablo's existing interleaved-act extension. Native 1.10f instead
    // partitions contiguous act sections; this is not a native interleaving claim.
    MonPreset table = load(MonPreset.class,
        "Act\tPlace\n1\tact-one-first\n2\tact-two-first\n"
        + "1\tact-one-second\n2\tact-two-second\n");
    assertEquals("act-one-second", table.getPlace(1, 1));
    assertEquals("act-two-first", table.getPlace(2, 0));
    assertEquals("act-two-second", table.getPlace(2, 1));
    assertEquals("act-one-second", table.get(2).Place);
  }
}
