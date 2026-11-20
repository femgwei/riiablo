"""Summarize captured original states against the unchanged production diagnostic."""
from collections import Counter
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def analyze():
    fixture = ROOT / "core/src/test/resources/original/plague-javelin-1.10f.json"
    diagnostic = ROOT / "tools/original-observer/plague-javelin-comparison-1.10f.json"
    original = json.loads(fixture.read_text(encoding="utf8"))
    current = json.loads(diagnostic.read_text(encoding="utf8"))
    assert original["identity"]["skill"] == 25
    assert original["background_verified"] and original["owned_game_closed"]
    run = Path(original["source_capture"]).parents[1]
    outer = json.loads((run / "orchestrator.json").read_text(encoding="utf8"))
    assert outer["status"] == "passed" and not outer["switched_desktop"]
    units = original["units"]
    server = [u for u in units if u["side"] == "server"]
    clouds = [u for u in server if u["missile"] == "plaguejavcloud"]
    client_clouds = [u for u in units if u["side"] == "client" and u["missile"] == "plaguejavcloud"]
    head = next(u for u in server if u["missile"] == "plaguejavelin")["states"][0]
    first = clouds[0]["states"][0]
    velocities = Counter((u["states"][0]["velocity_x"], u["states"][0]["velocity_y"]) for u in clouds)
    actual = current["server"]["burst_at_tick15"]
    actual_velocities = Counter(tuple(u["fp16_velocity_per_tick"]) for u in actual)
    center = [first["x_fixed"] / 65536 - .5, first["y_fixed"] / 65536 - .5]
    native_speed = head["velocity_x"] / 65536
    riiablo_speed = current["server"]["head_world_velocity_per_second"][0] / 25
    sources = [fixture, diagnostic,
        ROOT / "tools/original-observer/plague-javelin-assets-1.10f.json",
        ROOT / "core/src/test/java/com/riiablo/presentation/PlagueJavelinOriginalAnalysis.java",
        ROOT / "tools/original-observer/analyze_plague_javelin.py"]
    return dict(
        status="analysis_complete_differences_found", skill=25, version="1.10f",
        run_id=original["run_id"], level=1, direction_index=original["identity"]["direction_index"],
        background=dict(verified=True, switched_desktop=False, owned_game_closed=True,
            input_desktop_before=outer["input_desktop_before"], input_desktop_after=outer["input_desktop_after"],
            screen_before=outer["screen_before"], screen_after=outer["screen_after"],
            foreground_before=outer["foreground_before"], foreground_after=outer["foreground_after"],
            isolation_checks=outer["private_desktop_not_input_checks"]),
        native=dict(server_burst_clouds=len(clouds),
            client_stationary_trail_clouds=sum(u["states"][0]["total_frames"] == 60 for u in client_clouds),
            client_burst_clouds=sum(u["states"][0]["total_frames"] == 87 for u in client_clouds),
            observed_client_smoke=sum(u["side"] == "client" and u["missile"] == "poisonpuff" for u in units),
            head_lifetime=head["total_frames"], paired_draws=original["paired_draws"],
            verified_dcc_selections=original["selections_observed"]),
        confirmed_differences=dict(
            head_speed=dict(original_subtiles_per_tick=native_speed, riiablo_subtiles_per_tick=riiablo_speed,
                slower_percent=(1-riiablo_speed/native_speed)*100),
            burst_center=dict(original=center, riiablo=actual[0]["position"],
                delta_x=actual[0]["position"][0]-center[0]),
            trail_motion=dict(original="stationary", riiablo="inherits head velocity"),
            trail_sprite=dict(original="actual DCC selections observed", riiablo="all presentation-only plaguejavcloud DCCs suppressed"),
            cloud_animation=dict(original="wrap at age 19 to frame 10; exit loop near expiry",
                riiablo="generic full-pass subloop plus float animation stepping"),
            burst_direction=dict(original="direction follows native path", riiablo="Angle remains X after changing Velocity")),
        matched_components=dict(server_burst_count=len(actual) == len(clouds),
            server_burst_velocity_multiset=actual_velocities == velocities,
            server_burst_lifetime=all(u["lifetime"] == 87 for u in actual)),
        diagnostic_sprite_counts=current["sprite_selection"],
        limitations=["One level-one no-synergy cardinal cast, natural range expiry; no controlled monster hit",
            "Sprite comparison drives existing warm animation from birth; suppressed trail is forcibly attached for diagnosis",
            "38 smoke direction mismatches are unresolved quantization/path-direction reconstruction evidence, not attributed to RNG",
            "Ring velocity and lifetime agreement does not prove full native collision or damage parity",
            "No GPU pixel comparison; compatibility SCREEN blending was not changed",
            "No production gameplay/presentation fixes applied in this analysis"],
        evidence=[dict(path=p.relative_to(ROOT).as_posix(), sha256=digest(p)) for p in sources])


if __name__ == "__main__":
    report = analyze()
    output = ROOT / "tools/original-observer/plague-javelin-analysis-2026-11-20.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: report[k] for k in ("status", "run_id", "matched_components")}))
