"""Export all CltDo6 creation alternatives and recorded pre-selection RNG inputs."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from export_poison_javelin import export

NAMES = {104: "firesmall", 105: "firemedium", 457: "firestorm"}


def build(run, output):
    data = export(run, "firestorm")
    worker = json.loads((run / "worker/summary.json").read_text(encoding="utf8"))
    raw = run / "worker/firestorm/client-callbacks.jsonl"
    events = [json.loads(line) for line in raw.read_text(encoding="utf8").splitlines()]
    pending, choices = None, []
    for event in events:
        if event["kind"] == "path_flag":
            assert pending is None
            assert event["result"] == event["path_flags"] & 8
            if event["result"]:
                pending = event
        else:
            assert event["kind"] == "create_return" and pending is not None
            assert event["parent_unit_id"] == pending["unit_id"]
            assert event["game_frame"] == pending["game_frame"]
            assert event["readable"] and event["child_type"] == 3 and event["child_class"] in NAMES
            choices.append(dict(parent_unit_id=pending["unit_id"], game_frame=pending["game_frame"],
                seed_low=pending["seed_low"], seed_high=pending["seed_high"],
                missile=NAMES[event["child_class"]], child_unit_id=event["child_unit_id"]))
            pending = None
    assert pending is None and choices
    sampled = {u["unit_id"] for u in data["units"] if u["side"] == "client" and u["missile"] in NAMES.values()}
    assert sampled == {c["child_unit_id"] for c in choices}
    counts = dict(Counter(c["missile"] for c in choices))
    assert set(counts) == set(NAMES.values())
    data.update(fixture=worker["fixture"], callback_choices=choices, callback_variant_counts=counts,
                callback_trace_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),
                path_flag_calls=sum(e["kind"] == "path_flag" for e in events))
    data["limitations"][0] = "Both Firestorm and Molten Boulder learned at one hard point; native synergies active"
    data["limitations"].append("This run traces callbacks without renderer probes; samples array is intentionally empty")
    data["limitations"].append("Pre-selection RNG is an explicit input; global client RNG allocation is not reproduced")
    output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(run_id=data["run_id"], path_flag_calls=data["path_flag_calls"], choices=counts), indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.run, args.output)
