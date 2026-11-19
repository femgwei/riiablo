package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible data export. Passing this test is NOT native parity. */
class BarbarianBatchExportTest extends RiiabloTest {
  private static String csv(Object value) {
    return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
  }

  @Test void exportAllThirtySkillsAndFortyLevels() throws Exception {
    Path out = Paths.get(System.getProperty("barbarian.export",
        "build/barbarian-batch")).toAbsolutePath();
    Files.createDirectories(out);
    // Preserve every TXT column, including columns not mapped by Skills.Entry.
    for (String table : new String[] {"Skills", "SkillDesc", "States", "Missiles",
        "ItemStatCost", "Overlay", "Weapons", "Armor", "MonStats", "MonStats2"}) {
      Files.write(out.resolve(table + ".txt"),
          Riiablo.mpqs.resolve("data\\global\\excel\\" + table + ".txt").readBytes());
    }
    Field[] fields = Skills.Entry.class.getDeclaredFields();
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    int count = 0;
    try (BufferedWriter catalog = Files.newBufferedWriter(out.resolve("catalog.csv"), StandardCharsets.UTF_8);
         BufferedWriter formulas = Files.newBufferedWriter(out.resolve("formula-values.csv"), StandardCharsets.UTF_8)) {
      catalog.write("skill,name,field,value\n");
      formulas.write("skill,level,field,expression,value,owner_class,profile\n");
      for (int id = 126; id <= 155; id++) {
        Skills.Entry skill = Riiablo.files.skills.get(id);
        assertNotNull(skill); assertEquals("bar", skill.charclass); count++;
        for (Field field : fields) {
          Object value = field.get(skill);
          if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++)
              catalog.write(id + "," + csv(skill.skill) + "," + csv(field.getName() + (i + 1))
                  + "," + csv(Array.get(value, i)) + "\n");
          } else catalog.write(id + "," + csv(skill.skill) + "," + csv(field.getName()) + "," + csv(value) + "\n");
        }
        Map<String, String> expressions = new LinkedHashMap<>();
        for (String name : new String[] {"prgcalc1", "prgcalc2", "prgcalc3", "auralencalc", "aurarangecalc",
            "aurastatcalc1", "aurastatcalc2", "aurastatcalc3", "aurastatcalc4", "aurastatcalc5", "aurastatcalc6",
            "passivecalc1", "passivecalc2", "passivecalc3", "passivecalc4", "passivecalc5", "petmax",
            "sumsk1calc", "sumsk2calc", "sumsk3calc", "sumsk4calc", "sumsk5calc", "cltcalc1", "cltcalc2", "cltcalc3",
            "perdelay", "calc1", "calc2", "calc3", "calc4", "skpoints", "delay", "ToHitCalc",
            "DmgSymPerCalc", "EDmgSymPerCalc", "ELenSymPerCalc"})
          expressions.put(name, Riiablo.files.NativeSkills.get(id).string(name));
        for (int level = 1; level <= 40; level++) {
          for (Map.Entry<String, String> expression : expressions.entrySet()) {
            if (expression.getValue() == null || expression.getValue().trim().isEmpty()) continue;
            int result = SkillFormula.evaluate(expression.getValue(), skill, level,
                name -> "Fire Bolt".equalsIgnoreCase(name) ? 1 : 0,
                name -> Riiablo.files.skills.get(name), 99, name -> 0);
            formulas.write(id + "," + level + "," + expression.getKey() + "," + csv(expression.getValue())
                + "," + result + ",1,zero-barbarian-hardpoints\n");
          }
        }
      }
    }
    assertEquals(30, count);
    Files.write(out.resolve("scope.txt"), ("30 skills, levels 1..40. Parsed original MPQ TXT + riiablo formula evaluator.\n"
        + "Context: level 99, zero Barbarian hardpoints, Fire Bolt hardpoint 1.\n"
        + "No native execution, cast/damage/AI/visual parity is implied. stat() expressions need explicit context matching.\n")
        .getBytes(StandardCharsets.UTF_8));
  }
}
