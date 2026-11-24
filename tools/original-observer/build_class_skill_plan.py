"""Derive resumable class/family case manifests; never infer runtime coverage from tables."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
PRIOR = {225: "firestorm-1.10f.json", 229: "molten-boulder-1.10f.json",
         88: "bone-prison-states-1.10f.json", 84: "bone-spear-1.10f.json",
         41: "inferno-1.10f.json", 52: "enchant-1.10f.json", 46: "blaze-client-animation-1.10f.json",
         64: "frozen-orb-sprites-1.10f.json", 15: "poison-javelin-1.10f.json", 25: "plague-javelin-1.10f.json"}


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def build(directory):
    inventory_path = directory / "class-skill-inventory.json"
    inventory = json.loads(inventory_path.read_text(encoding="utf8"))
    captured = {}
    for filename in ("runtime-batch-summary.json", "runtime-surface-batch-summary.json", "runtime-wind-polling-summary.json", "runtime-arctic-polling-summary.json"):
        runtime_path = directory / filename
        runtime = json.loads(runtime_path.read_text(encoding="utf8")) if runtime_path.exists() else {}
        captured.update({case["skill_id"]: dict(case, source_run_id=runtime["run_id"]) for case in runtime.get("captures", [])})
    comparison_path = directory / "batch-comparison-summary.json"
    comparisons = json.loads(comparison_path.read_text(encoding="utf8")) if comparison_path.exists() else {}
    for case in comparisons.get("captures", []):
        if case["skill_id"] in captured and case["source_run_id"] == captured[case["skill_id"]]["source_run_id"]:
            evidence = directory / case["file"]
            if hashlib.sha256(evidence.read_bytes()).hexdigest() != case["sha256"]:
                raise ValueError("Comparison evidence hash mismatch")
            captured[case["skill_id"]].update(comparison_status=case["comparison_status"], comparison_evidence=case["file"])
    groups, all_ids = [], []
    for group in inventory["classes"]:
        skills = []
        for entry in group["skills"]:
            sid, row = entry["skill_id"], entry["row"]
            all_ids.append(sid)
            cases = list(entry["required_cases"])
            if row.get("aura") == "1":
                cases += ["aura_enter_leave_radius", "aura_refresh"]
            if row.get("prgstack") or any(row.get("prgcalc" + str(n)) for n in range(1, 4)):
                cases += ["charge_build_and_release"]
            if entry["family"] == "action_or_target":
                cases += ["target_and_equipment_preconditions"]
            old = REPO / "core/src/test/resources/original" / PRIOR.get(sid, "missing")
            status = captured.get(sid, {})
            skills.append(dict(skill_id=sid, skill=entry["skill"], proposed_family=entry["family"],
                required_cases=list(dict.fromkeys(cases)),
                runtime_status=status.get("runtime_status", "not_run_in_class_batch"),
                observation_mode=status.get("observation_mode", "stopped_states_and_dcc_calls" if status else None),
                comparison_status=status.get("comparison_status", "not_compared_in_class_batch"),
                batch_evidence=status.get("file"),
                comparison_evidence=status.get("comparison_evidence"),
                prior_individual_evidence=str(old.relative_to(REPO)).replace("\\", "/") if old.is_file() else None,
                execution_readiness="bounded_client_polling_available" if sid in (230, 240, 245) else "bounded_collector_available" if sid in (225, 229, 234, 244) else "needs_fixture_and_family_collector",
                resource_paths=entry["resources"],
                required_observations=["caster_mode_sequence", "client_callback_or_state_transition",
                    "created_units_or_attached_overlays", "resource_direction_frame_clock", "expiry_or_interrupt"],
                completion_rule="Each applicable case needs runtime evidence and a matched Riiablo replay; static headers are not a pass"))
        manifest = dict(schema=1, class_code=group["class"], priority=group["priority"], deferred=group["deferred"],
                        source_inventory_sha256=hashlib.sha256(inventory_path.read_bytes()).hexdigest(),
                        fixture_policy="Record learned skills/equipment/synergies; separate loadouts or sessions when prerequisites conflict",
                        family_classification="Table-based scheduling hint, requires per-skill review",
                        skills=skills)
        write(directory / (group["class"] + "-manifest.json"), manifest)
        groups.append(dict(class_code=group["class"], priority=group["priority"], deferred=group["deferred"],
                           total=len(skills), captured_in_class_batch=sum(s["runtime_status"] == "captured" for s in skills),
                           captured_client_polling=sum(s["runtime_status"] == "captured_client_polling" for s in skills),
                           compared_in_class_batch=sum(s["comparison_evidence"] is not None for s in skills), prior_individual_fixtures=sum(s["prior_individual_evidence"] is not None for s in skills),
                           family_counts=group["family_counts"]))
    assert len(all_ids) == len(set(all_ids)) == 210
    assert all(g["total"] == 30 for g in groups)
    report = dict(schema=1, class_order=[g["class_code"] for g in groups], classes=groups,
                  total_skills=210, priority_skills=150, deferred_skills=60,
                  static_resource_headers=len(inventory["resources"]),
                  resource_status_counts=dict(Counter(r["status"] for r in inventory["resources"].values())),
                  scope="Static inventory complete; runtime and scoped comparison coverage counted separately; no all-case or pixel parity claim")
    write(directory / "coverage.json", report)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.directory), indent=2))
