"""Compare the retained background Inferno observation with production replay.

The production diagnostic is a single stream. It does not establish parity of
held input, caster sequences, client allocation seeds, damage or GPU pixels.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def analyze(fixture, replay, after_fix=False):
    original = json.loads(fixture.read_text(encoding="utf8"))
    production = json.loads(replay.read_text(encoding="utf8"))["production"]
    units = original["units"]
    server = [u for u in units if u["side"] == "server"]
    client = [u for u in units if u["side"] == "client"]
    states = {s["native_age"]: s for s in production["states"]}
    paired = [s for s in original["samples"] if s["native_age"] in states]
    mismatch = [dict(index=s["index"], age=s["native_age"], original=s["frame"],
                     production=states[s["native_age"]]["frame"])
                for s in paired if s["frame"] != states[s["native_age"]]["frame"]]
    first = server[0]["states"][0]
    native_velocity = [first["velocity_x"] * 25 / 65536, first["velocity_y"] * 25 / 65536]
    speed = sum(v*v for v in native_velocity)**.5
    port_speed = sum(v*v for v in production["velocity"])**.5
    client_births = sorted(s["game_frame"] - s["total_frames"] + s["current_frame"]
                          for u in client for s in u["states"][:1])
    server_births = sorted(s["game_frame"] - s["total_frames"] + s["current_frame"]
                          for u in server for s in u["states"][:1])
    release = original["caster_states"][0]["game_frame"] + original["identity"]["hold_ticks"]
    comparisons = []
    for age, state in sorted(states.items()):
        reference = [(first["x_fixed"] + age * first["velocity_x"]) / 65536 - .5,
                     (first["y_fixed"] + age * first["velocity_y"]) / 65536 - .5]
        comparisons.append(dict(age=age, native_normalized_position=reference,
            production_position=state["position"], native_frame=age*384 >> 8,
            production_frame=state["frame"]))
    return dict(schema=1, run_id=original["run_id"], background_verified=original["background_verified"],
        scope="One level-one positive-X original cast and one production flame lifecycle; full held-input cadence is not compared",
        production_fixes_applied=after_fix,
        fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),
        replay_sha256=hashlib.sha256(replay.read_bytes()).hexdigest(),
        original=dict(server_counts=dict(Counter(u["missile"] for u in server)),
            client_counts=dict(Counter(u["missile"] for u in client)),
            lifetime_frames=first["total_frames"], velocity_subtiles_per_second=native_velocity,
            client_frame_delta=client[0]["states"][0]["unit_4c"],
            server_birth_frames=server_births, client_birth_frames=client_births,
            release_game_frame=release, last_client_birth_after_release=client_births[-1]-release,
            last_client_observed_frame=max(s["game_frame"] for u in client for s in u["states"])),
        production=dict(lifetime_frames=production["lifetime"],
            velocity_subtiles_per_second=production["velocity"],
            speed_shortfall_percent=(1-port_speed/speed)*100,
            frame_delta=production["states"][0]["frame_delta"],
            native_precision=production["native_precision"],
            paired_dcc_samples=len(paired), frame_mismatches=len(mismatch),
            mismatch_details=mismatch, positions_and_frames=comparisons),
        findings=["Recorded original speed is 14.0625 world units/second; measured production speed is reported above",
            "Recorded original frame delta is 384; all paired native-age frame mismatches are reported above",
            "Original client skill callback owns an independent A/B stream; missile pCltDo is 1, not 24",
            "Observed original client/server birth counts differ; production held-cast cadence still needs separate replay"],
        limitations=production["limitations"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path)
    parser.add_argument("replay", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--after-fix", action="store_true")
    args = parser.parse_args()
    result = analyze(args.fixture, args.replay, args.after_fix)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(original=result["original"],
        speed_shortfall_percent=result["production"]["speed_shortfall_percent"],
        paired_dcc_samples=result["production"]["paired_dcc_samples"],
        frame_mismatches=result["production"]["frame_mismatches"]), indent=2))
