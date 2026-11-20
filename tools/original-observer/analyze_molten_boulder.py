"""Compare retained original missile evidence with the real production replay.

This reports differences, without making a parity test pass by changing its oracle.
The optional expiry run is an independent native SrvHit47 entry/return probe.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def analyze(fixture, replay, expiry=None):
    original, replay_data = read(fixture), read(replay)
    production = replay_data["production"]
    units = original["units"]
    server = [u for u in units if u["side"] == "server"]
    client = [u for u in units if u["side"] == "client"]
    roll = next(u for u in server if u["missile"] == "moltenboulder")
    states = roll["states"]
    assert len(states) == states[0]["total_frames"] == 100
    assert states[-1]["current_frame"] == 1
    step = states[1]["x_fixed"] - states[0]["x_fixed"]
    assert all(b["x_fixed"] - a["x_fixed"] == step for a, b in zip(states, states[1:]))
    flames = [u for u in client if u["missile"] in {"firewall", "firesmall", "firemedium"}]
    burst = [u for u in client if u["missile"] == "moltenboulderfirepath"]
    rocks = [u for u in client if u["missile"] == "moltenboulder-flyingrocks"]
    replay_frames = {s["native_age"]: s["frame"] for s in production["rolling_states"]}
    paired = [s for s in original["samples"] if s["missile"] == "moltenboulder"
              and s["native_age"] in replay_frames]
    mismatches = [s["index"] for s in paired if s["frame"] != replay_frames[s["native_age"]]]
    native_speed = step * 25 / 65536
    port_speed = production["rolling_birth"]["velocity"][0]
    visual_births = [b for b in production["births"] if b["presentation_only"]]
    impact_tick = next(b["tick"] for b in visual_births if b["missile"] == "moltenboulderexplode")
    trail_births = [b for b in visual_births if b["missile"] in {
                        "moltenboulderfirepath", "firewall", "firesmall", "firemedium"}
                    and b["tick"] < impact_tick]
    # The final rolling crossing can share the destruction callback's tick.
    trail_births += [b for b in visual_births if b["missile"] in {
        "firewall", "firesmall", "firemedium"} and b["tick"] == impact_tick]
    endpoint_fire = [b for b in visual_births if b["missile"] == "moltenboulderfirepath"
                     and b["tick"] == impact_tick]
    result = dict(schema=1, run_id=original["run_id"], background_verified=original["background_verified"],
        scope="one direction, level one, no synergies; runtime units/DCC submissions and headless production replay; no GPU/target damage parity",
        production_skill_fixes_applied=replay_data.get("production_skill_fixes_applied", False),
        fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),
        replay_sha256=hashlib.sha256(replay.read_bytes()).hexdigest(),
        original=dict(client_counts=dict(Counter(u["missile"] for u in client)),
            rolling_step_fixed=step, rolling_speed_subtiles_per_second=native_speed,
            rolling_lifetime=100, emerge_lifetime=5,
            trail_count=len(flames), trail_lifetimes=sorted({u["states"][0]["total_frames"] for u in flames}),
            trail_variants=dict(Counter(u["missile"] for u in flames)),
            endpoint_fire_count=len(burst), endpoint_fire_lifetime=37,
            fragment_count_observed=len(rocks),
            fragment_lifetimes=sorted(u["states"][0]["total_frames"] for u in rocks),
            server_fire_lifetimes=dict(Counter(str(u["states"][0]["total_frames"]) for u in server
                if u["missile"] == "moltenboulderfirepath"))),
        production=dict(rolling_speed_subtiles_per_second=port_speed,
            speed_shortfall_percent=(1 - port_speed/native_speed)*100,
            visual_trail_count=len(trail_births), visual_trail_duration=trail_births[0]["duration_seconds"],
            visual_trail_type=trail_births[0]["missile"],
            visual_trail_variants=dict(Counter(b["missile"] for b in trail_births)),
            server_fire_lifetimes=dict(Counter(str(b["lifetime"]) for b in production["births"]
                if not b["presentation_only"] and b["missile"] == "moltenboulderfirepath")),
            endpoint_fire_count=len(endpoint_fire), endpoint_fire_duration=endpoint_fire[0]["duration_seconds"],
            fragment_count=production["visual_counts"].get("moltenboulder-flyingrocks", 0),
            fragment_lifetimes=sorted(b["lifetime"] for b in visual_births
                if b["missile"] == "moltenboulder-flyingrocks"),
            paired_rolling_sprite_samples=len(paired), rolling_frame_mismatches=len(mismatches),
            rolling_frame_mismatch_sample_indices=mismatches),
        limitations=original["limitations"])
    reference, port = result["original"], result["production"]
    result["checks"] = dict(
        rolling_speed=abs(native_speed-port_speed) < 1e-6,
        rolling_sprite_frames=not mismatches and len(paired) == 80,
        trail_count=reference["trail_count"] == port["visual_trail_count"],
        trail_variants=reference["trail_variants"] == port["visual_trail_variants"],
        trail_lifetime=all(b["lifetime"] in reference["trail_lifetimes"] for b in trail_births),
        endpoint_fire_count=reference["endpoint_fire_count"] == port["endpoint_fire_count"],
        endpoint_fire_lifetime=all(b["lifetime"] == reference["endpoint_fire_lifetime"] for b in endpoint_fire),
        server_fire_lifetimes=reference["server_fire_lifetimes"] == port["server_fire_lifetimes"],
        fragment_count=reference["fragment_count_observed"] == port["fragment_count"],
        fragment_lifetimes=reference["fragment_lifetimes"] == port["fragment_lifetimes"])
    result["findings"] = [name + (": matched retained cast" if passed else ": differs from retained cast")
        for name, passed in result["checks"].items()]
    if expiry:
        outer = read(expiry / "orchestrator.json")
        summary = read(expiry / "worker/capture/summary.json")
        calls = [json.loads(l) for l in (expiry / "worker/capture/server-hit-calls.jsonl").read_text().splitlines()]
        assert outer["status"] == summary["status"] == "passed"
        assert not outer["switched_desktop"] and outer["input_desktop_before"] == outer["input_desktop_after"]
        assert outer["screen_before"] == outer["screen_after"]
        assert summary["cleanup_confirmed"] and summary["detached"]
        assert summary["server_null_hit_calls"] > 0
        result["expiry_probe"] = dict(run_id=expiry.parent.name, calls=calls, summary=summary,
            source=str(expiry.resolve()))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path)
    parser.add_argument("replay", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--expiry", type=Path)
    args = parser.parse_args()
    result = analyze(args.fixture, args.replay, args.expiry)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(run_id=result["run_id"], original=result["original"], production=result["production"]), indent=2))
