"""Summarize asynchronous observations without claiming AI decision parity."""
import argparse
import json
from collections import defaultdict
from pathlib import Path


def summarize(rows):
    if not rows:
        raise ValueError("No server samples")
    histories = defaultdict(list)
    for row in rows:
        for unit in row["units"]:
            histories[(unit["type"], unit["unit_id"], unit["class_id"])].append(
                (row, unit))
    units = []
    for (kind, guid, class_id), history in sorted(histories.items()):
        transitions = []
        previous = None
        for row, unit in history:
            state = (unit["mode"], unit.get("x"), unit.get("y"))
            if previous is not None and state != previous:
                transitions.append({"frame_before": row["frame_before"],
                                    "frame_after": row["frame_after"],
                                    "from": previous, "to": state})
            previous = state
        units.append({"type": kind, "unit_id": guid, "class_id": class_id,
                      "observations": len(history),
                      "observed_modes": sorted({u["mode"] for _, u in history}),
                      "rng_states": len({(u["seed_low"], u["seed_high"])
                                         for _, u in history}),
                      "observed_transitions": transitions})
        ai = [u for _, u in history if u.get("ai_control_valid")]
        if ai:
            units[-1]["ai_observations"] = {
                "valid_samples": len(ai),
                "owner_identities_ex": sorted({(u["owner_type_ex"], u["owner_guid_ex"])
                                                for u in ai}),
                "command_params_observed": sorted({tuple(u["command_params"])
                                                   for u in ai if "command_params" in u}),
                "command_codes_observed": sorted({u["command_params"][0]
                                                   for u in ai if "command_params" in u}),
                "command_parameter_scope": "raw words; only parameter 0 is decoded as command code",
                "command_read_errors": sum(bool(u.get("command_read_error")) for u in ai),
                "null_command_samples": sum(u["current_command_pointer"] == 0 for u in ai),
                "scope": "asynchronous observations; short-lived commands may be missed"}
    return {"scope": "sampled state changes; not every tick or AI decision",
            "ai_parity": "untested", "samples": len(rows),
            "elapsed_ms": rows[-1]["wall_ms"] - rows[0]["wall_ms"],
            "frame_delta": rows[-1]["frame_after"] - rows[0]["frame_before"],
            "cross_frame_samples": sum(r["frame_before"] != r["frame_after"]
                                       for r in rows),
            "atomic": False, "units": units}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("samples", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.samples.read_text(
        encoding="utf-8").splitlines() if line.strip()]
    result = summarize(rows)
    result["source"] = str(args.samples.resolve())
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2),
                           encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "units"}))


if __name__ == "__main__":
    main()
