"""Validate an actual original Fire Bolt trace before using it as a port oracle.

This checks recorded execution, not screenshots or a full seven-class parity claim.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def require(value, message):
    if not value:
        raise ValueError(message)


def rows(path):
    with path.open(encoding="utf8", newline="") as stream:
        return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(stream)]


def check(folder):
    summary = json.loads((folder / "summary.json").read_text(encoding="utf8"))
    identity = json.loads((folder / "identity.json").read_text(encoding="utf8"))
    require(summary.get("status") == "passed" and summary.get("cleanup_confirmed") is True
            and summary.get("detached") is True, "Original run or debugger cleanup failed")
    require((identity.get("name"), identity.get("class"), identity.get("skill"), identity.get("level"),
             identity.get("map_seed"), identity.get("target_class")) == ("CXSorc", 1, 36, 1, 127897359, 0),
            "Wrong original fixture identity")
    frames, hits = rows(folder / "frames.csv"), rows(folder / "damage.csv")
    require(len(frames) == 81 and len(hits) == 1, "Need 81 frame samples and one damage event")
    start = summary["first_frame"]
    require(all(row["step"] == i and row["frame"] == start + i for i, row in enumerate(frames))
            and summary["last_frame"] == start + 80, "Authoritative frame sequence is not contiguous")
    require(frames[0]["player_mode"] == 10, "No cast-start mode")
    require(all((row["target_x"], row["target_y"]) == (identity["target_x"], identity["target_y"])
                for row in frames), "Fixed target moved")
    hit = hits[0]
    require(hit["attacker"] == identity["player"] and hit["victim"] == identity["target"], "Wrong damage participants")
    require(hit["result_flags"] & 1 and hit["physical_fixed"] == 0
            and 768 <= hit["fire_fixed"] < 1536 and hit["total_fixed"] == hit["fire_fixed"], "Wrong fire packet")
    require(frames[0]["target_hp_fixed"] == summary["target_hp_before_fixed"] == 256000
            and frames[-1]["target_hp_fixed"] == summary["target_hp_after_fixed"]
            and 256000 - frames[-1]["target_hp_fixed"] == hit["fire_fixed"] == summary["damage_fire_fixed"], "HP delta disagrees with damage")
    drops = [row for before, row in zip(frames, frames[1:]) if row["target_hp_fixed"] < before["target_hp_fixed"]]
    require(len(drops) == 1 and drops[0]["frame"] == hit["frame"], "HP drop does not match damage frame")
    missiles = [row for row in frames if row["owned_firebolts"]]
    require(missiles and all(row["owned_firebolts"] == 1 for row in missiles)
            and len({row["first_missile_id"] for row in missiles}) == 1, "Missing/extra Fire Bolt")
    require(all(row["frame"] < hit["frame"] for row in missiles), "Missile lifetime ordering differs")
    require(any(row["frame"] > hit["frame"] and row["player_mode"] == 1 for row in frames), "No return to neutral")
    require(min(row["mana_fixed"] for row in frames) == summary["lowest_mana_fixed"] < frames[0]["mana_fixed"], "No mana debit observed")
    seed_rows = {(row["missile_seed_low"], row["missile_seed_high"], row["fire_min_fixed"], row["fire_max_fixed"]) for row in missiles}
    require(len(seed_rows) == 1, "Seed or endpoints changed before impact")
    low, high, minimum, maximum = next(iter(seed_rows))
    require((minimum, maximum) == (768, 1536), "Wrong original damage endpoints")
    advanced = high + low * 0x6AC690C5
    after_low, after_high = advanced & 0xFFFFFFFF, advanced >> 32
    require((after_low, after_high) == (hit["missile_seed_after_low"], hit["missile_seed_after_high"]), "RNG draw count/state differs")
    require(minimum + after_low % (maximum - minimum) == hit["fire_fixed"], "Original RNG does not explain damage")
    return dict(status="passed", scope="original level-one Fire Bolt server lifecycle and one seed-aligned impact",
        samples=81, spawn_step=missiles[0]["step"], hit_step=hit["frame"]-start,
        neutral_step=next(row["step"] for row in frames if row["frame"] > hit["frame"] and row["player_mode"] == 1),
        seed_before=[low,high], seed_after=[after_low,after_high], fire_fixed=hit["fire_fixed"],
        hp_after_fixed=frames[-1]["target_hp_fixed"],
        not_tested=["port animation timing", "visual parity", "other skills", "full-world RNG equivalence"],
        sources={name: hashlib.sha256((folder / name).read_bytes()).hexdigest()
                 for name in ("summary.json", "identity.json", "frames.csv", "damage.csv")})


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("run", type=Path)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    try:
        result = check(a.run)
    except (ValueError, KeyError, OSError) as error:
        result = dict(status="failed", error=str(error))
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf8")
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if result["status"] == "passed" else 1)
