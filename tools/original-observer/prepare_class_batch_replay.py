"""Build replay inputs from frozen raw captures, retaining the actual batch loadout."""
import argparse
import hashlib
import json
from pathlib import Path
from export_poison_javelin import export as export_firestorm
from export_molten_boulder import export as export_molten


def prepare(directory, surface=False, wind=False):
    if surface and wind:
        raise ValueError("Select one batch")
    summary = json.loads((directory / ("runtime-wind-batch-summary.json" if wind else "runtime-surface-batch-summary.json" if surface else "runtime-batch-summary.json")).read_text(encoding="utf8"))
    run = Path(summary["source"])
    fixture = summary["fixture"]
    assert fixture["class_id"] == 5 and fixture["equipment"] == "empty"
    expected = {"240": 1, "245": 1} if wind else {"234": 1, "244": 1} if surface else {"225": 1, "229": 1}
    assert fixture["hard_points"] == expected and fixture["other_hard_points"] == 0
    cases = ((234, "fissure", export_firestorm), (244, "volcano", export_firestorm)) if surface else ((225, "firestorm", export_firestorm), (229, "molten-boulder", export_molten))
    if wind:
        cases = ((240, "twister", export_firestorm), (245, "tornado", export_firestorm))
    for skill, name, exporter in cases:
        capture = next(c for c in summary["captures"] if c["skill_id"] == skill)
        frozen = directory / capture["file"]
        assert hashlib.sha256(frozen.read_bytes()).hexdigest() == capture["sha256"]
        data = exporter(run, name)
        data["fixture"] = fixture
        data["skill_id"] = skill
        data["source_batch_sha256"] = capture["sha256"]
        data["limitations"][0] = "One level-one cast with recorded hard points " + str(expected) + "; native synergies active"
        data["limitations"].append("No caster attributes/attack damage comparison; input starts at SkillDo in the production replay")
        path = directory / ("druid-" + name + "-replay-input-1.10f.json")
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
        print(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--surface", action="store_true")
    parser.add_argument("--wind", action="store_true")
    args = parser.parse_args()
    prepare(args.directory, args.surface, args.wind)
