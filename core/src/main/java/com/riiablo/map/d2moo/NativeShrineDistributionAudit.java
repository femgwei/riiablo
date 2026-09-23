package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.drlg.DrlgExport;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Shrines;
import com.riiablo.engine.server.object.NativeShrineResolver;
import com.riiablo.map.NativePresetObjectResolver;

import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic, resource-backed audit of Act I outdoor shrine generation.
 *
 * <p>This deliberately uses the same D2MOO DRLG export that the runtime map
 * bridge consumes. It records both the raw preset class and the concrete
 * {@code Shrines.txt} row, so a missing type can be distinguished from a
 * resolver remap.</p>
 */
public final class NativeShrineDistributionAudit {
  private NativeShrineDistributionAudit() {}

  public static String run(int firstSeed, int seedCount, int difficulty) {
    if (seedCount <= 0) throw new IllegalArgumentException("seedCount must be positive");
    int burialGroundsId = findLevelId("Burial Grounds");
    if (burialGroundsId < 0) {
      throw new IllegalStateException("Burial Grounds is missing from Levels.txt");
    }

    Map<Integer, Integer> rawCounts = new TreeMap<>();
    Map<Integer, String> rawDetails = new TreeMap<>();
    Map<Integer, Integer> shrineModeCounts = new TreeMap<>();
    Map<Integer, Integer> shrineCounts = new TreeMap<>();
    Map<String, Integer> viewCounts = new TreeMap<>();
    Map<Integer, String> shrineDetails = new TreeMap<>();
    int generatedSeeds = 0;
    int failedSeeds = 0;
    int[] bloodMoorUnits = {0};
    int[] shrinePresets = {0};
    int[] wells = {0};
    int[] ordinaryObjects = {0};

    for (int i = 0; i < seedCount; i++) {
      int gameSeed = firstSeed + i;
      com.d2moo.common.drlg.D2DrlgStrc generated =
          Act1D2MOOLayoutBridge.getBloodMoorDrlg(gameSeed, difficulty, burialGroundsId);
      if (generated == null) {
        failedSeeds++;
        continue;
      }
      generatedSeeds++;
      try {
        final int seed = gameSeed;
        bloodMoorUnits[0] += DrlgExport.exportLevelPresetUnits(generated,
            D2LevelIds.LEVEL_BLOODMOOR,
            (levelId, unitType, index, mode, x, y, ds1Raw, spawned) -> {
              // D2MOO exports non-object presets too; only Objects.txt classes
              // participate in shrine/well creation.
              if (unitType != D2UnitTypes.UNIT_OBJECT || spawned) return;
              int objectId = ds1Raw ? Riiablo.files.obj.getObjectId(1, index) : index;
              rawCounts.merge(objectId, 1, Integer::sum);
              NativePresetObjectResolver.Resolution resolution =
                  NativePresetObjectResolver.resolve(1, levelId, objectId, seed, x, y);
              Objects.Entry base = Riiablo.files.objects.get(resolution.classId);
              if (base != null) {
                rawDetails.put(objectId, "name=" + base.Name
                    + ",operateFn=" + base.OperateFn
                    + ",shrineFunction=" + base.ShrineFunction
                    + ",parm0=" + parm(base, 0));
              }
              // Runtime lifecycle classification also treats ordinary
              // Objects.txt rows with OperateFn=2/22 as shrine/well. The
              // reserved 574..579 rows are only one of the native paths.
              if (resolution.kind == NativePresetObjectResolver.Kind.SHRINE
                  || (base != null && base.OperateFn == 2)) {
                shrinePresets[0]++;
                shrineModeCounts.merge(mode, 1, Integer::sum);
                int shrineId = NativeShrineResolver.resolve(Riiablo.files.Shrines, base,
                    objectId, levelId, seed, x, y);
                shrineCounts.merge(shrineId, 1, Integer::sum);
                Shrines.Entry shrine = Riiablo.files.Shrines.get(shrineId);
                String view = shrine == null || shrine.ViewName == null
                    ? "<missing-row-" + shrineId + ">" : shrine.ViewName;
                viewCounts.merge(view, 1, Integer::sum);
                if (shrine != null) {
                  shrineDetails.put(shrineId, "code=" + shrine.Code
                      + ",view=" + view + ",effectClass=" + shrine.EffectClass
                      + ",levelMin=" + shrine.LevelMin);
                }
              } else if ((base != null && base.OperateFn == 22)
                  || NativeShrineDistributionAudit.isWell(objectId)) {
                wells[0]++;
              } else {
                ordinaryObjects[0]++;
              }
            });
      } finally {
        Act1D2MOOLayoutBridge.releaseDrlg(generated);
      }
    }

    StringBuilder report = new StringBuilder(2048);
    report.append("mode=shrine-distribution-audit\n")
        .append("window=1x1\n")
        .append("level=Blood Moor (2)\n")
        .append("firstSeed=").append(firstSeed).append('\n')
        .append("seedCount=").append(seedCount).append('\n')
        .append("generatedSeeds=").append(generatedSeeds).append('\n')
        .append("failedSeeds=").append(failedSeeds).append('\n')
        .append("presetUnits=").append(bloodMoorUnits[0]).append('\n')
        .append("shrinePresets=").append(shrinePresets[0]).append('\n')
        .append("wells=").append(wells[0]).append('\n')
        .append("ordinaryObjects=").append(ordinaryObjects[0]).append('\n')
        .append("rawObjectCounts=").append(rawCounts).append('\n')
        .append("rawObjectDetails=").append(rawDetails).append('\n')
        .append("shrineModeCounts=").append(shrineModeCounts).append('\n')
        .append("shrineIdCounts=").append(shrineCounts).append('\n')
        .append("shrineDetails=").append(shrineDetails).append('\n')
        .append("shrineViewCounts=").append(viewCounts).append('\n')
        .append("result=").append(failedSeeds == 0 ? "PASS" : "PARTIAL").append('\n');
    return report.toString();
  }

  private static boolean isWell(int objectId) {
    return objectId == 122 || objectId == 183 || objectId == 185;
  }

  private static int parm(Objects.Entry object, int index) {
    return object == null || object.Parm == null || index >= object.Parm.length
        ? 0 : object.Parm[index];
  }

  private static int findLevelId(String name) {
    for (com.riiablo.codec.excel.Levels.Entry level : Riiablo.files.Levels) {
      if (level != null && name.equals(level.LevelName)) return level.Id;
    }
    return -1;
  }
}
