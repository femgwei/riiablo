package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class SkillsTest {
  @Test
  public void indexesNumericSkillsByTextNameForMonStatsReferences() {
    Skills skills = new Skills();
    Skills.Entry entry = new Skills.Entry();
    entry.Id = 321;
    entry.skill = "native monster skill";
    skills.put(entry.Id, entry);

    skills.init();

    assertEquals(entry.Id, skills.index(entry.skill));
    assertSame(entry, skills.get(entry.skill));
  }
}
