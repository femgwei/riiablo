#!/usr/bin/env python3
"""Compare a Riiablo Bash report with the validated original baseline.

The Riiablo report is intentionally a small JSON boundary produced by a
runtime harness. Random damage is compared as a distribution, while skill
ABI, mana, timing, and knockback are treated as deterministic contracts.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List


def load(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def check(name: str, actual: Any, expected: Any, checks: List[Dict[str, Any]]) -> None:
    checks.append({"name": name, "expected": expected, "actual": actual,
                   "passed": actual == expected})


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--riiablo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    baseline, contract, riiablo = load(args.baseline), load(args.contract), load(args.riiablo)
    checks: List[Dict[str, Any]] = []
    skill = riiablo.get("skill", {})
    expected_skill = contract["skill"]
    for field in ("id", "srvstfunc", "srvdofunc", "mode", "src_dam", "mana"):
        check("skill." + field, skill.get(field), expected_skill.get(field), checks)
    runtime = riiablo.get("runtime", {})
    original = contract["validated_observer_scope"]
    check("runtime.mana_drop_fixed", runtime.get("mana_drop_fixed"), original["mana_drop_fixed"], checks)
    check("runtime.attack_frames", runtime.get("attack_frames"), original["attack_frames"], checks)
    check("runtime.knockback_observed_on_hit", runtime.get("knockback_observed_on_hit"),
          original["knockback_observed_on_hit"], checks)
    damage = runtime.get("damage_total_fixed", {})
    expected_damage = baseline.get("damage_total_fixed", {})
    for field in ("min", "max"):
        value = damage.get(field)
        check("runtime.damage_total_fixed." + field, value,
              {"baseline": expected_damage.get(field), "within_baseline":
               expected_damage.get("min") is not None and expected_damage.get("max") is not None
               and expected_damage["min"] <= value <= expected_damage["max"]
               if isinstance(value, int) else False}, checks)
        checks[-1]["passed"] = bool(checks[-1]["expected"]["within_baseline"])
    result = {"status": "passed" if all(c["passed"] for c in checks) else "failed",
              "checks": checks, "baseline": str(args.baseline), "riiablo": str(args.riiablo)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "failed_checks":
                      [c["name"] for c in checks if not c["passed"]]}, ensure_ascii=False))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
