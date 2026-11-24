"""Scoped, data-driven production comparison; differing observed counts are not automatic bugs."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def native_units(original, side, name):
    return [u for u in original["units"] if u["side"] == side and u["missile"] == name]


def life_hist(units):
    return dict(Counter(u["states"][0]["total_frames"] for u in units))


def compare(directory):
    summary = read(directory / "runtime-batch-summary.json")
    reports = []
    for name in ("firestorm", "molten-boulder"):
        original_path = directory / ("druid-" + name + "-replay-input-1.10f.json")
        replay_path = directory / ("druid-" + name + "-production-1.10f.json")
        original, replay = read(original_path), read(replay_path)
        port = replay["production"]
        assert original["run_id"] == replay["source_run_id"] == summary["run_id"]
        assert port["hard_points"] == summary["fixture"]["hard_points"]
        assert replay["fixture_sha256"] == summary["fixture"]["sha256"]
        checks, details = {}, {}
        if name == "firestorm":
            heads = native_units(original, "server", "firestormmaker")
            damage = native_units(original, "server", "firestorm")
            visuals = native_units(original, "client", "firestorm")
            checks["maker_count"] = len(heads) == len(port["head_births"])
            checks["maker_lifetimes"] = sorted(u["states"][0]["total_frames"] for u in heads) == sorted(h["lifetime"] for h in port["head_births"])
            checks["initial_velocity"] = all(abs(u["states"][0]["velocity_x"] * 25 / 65536 - h["velocity"][0]) < 1e-5
                and abs(u["states"][0]["velocity_y"] * 25 / 65536 - h["velocity"][1]) < 1e-5 for u, h in zip(heads, port["head_births"]))
            checks["damage_fire_count"] = len(damage) == port["damage_ground_fire_births"]
            checks["damage_fire_lifetimes"] = life_hist(damage) == dict(Counter(b["lifetime"] for b in port["ground_fire_births"] if not b["presentation_only"]))
            checks["visual_fire_lifetimes"] = set(life_hist(visuals)) == {b["lifetime"] for b in port["ground_fire_births"] if b["presentation_only"]}
            checks["maker_frames"] = port["maker_sprite_samples"] > 0 and port["maker_frame_mismatches"] == 0
            checks["ground_fire_frames"] = port["ground_fire_sprite_samples"] > 0 and port["ground_fire_frame_mismatches"] == 0
            checks["all_declared_visual_variants"] = set(port.get("visual_ground_fire_variants", {})) == {"firestorm", "firesmall", "firemedium"}
            variants_path = directory.parent.parent.parent / "core/src/test/resources/original/firestorm-variants-1.10f.json"
            variants = read(variants_path) if variants_path.exists() else {}
            details = dict(original_visual_count=len(visuals), production_visual_count=port["visual_ground_fire_births"],
                maker_samples=port["maker_sprite_samples"], ground_fire_samples=port["ground_fire_sprite_samples"],
                native_filter_in_first_batch=[457, 458], count_comparable=False,
                production_variants=port.get("visual_ground_fire_variants", {}),
                supplemental_callback_run=variants.get("run_id"),
                supplemental_callback_variants=variants.get("callback_variant_counts"),
                supplemental_callback_sha256=sha(variants_path) if variants else None,
                count_difference_attribution="First batch omitted client classes 104/105; comparing its class-457 count with the entire production stream was invalid. Supplementary trace proves three alternatives. Whole-stream cadence and global RNG alignment remain unverified.")
        else:
            roll = native_units(original, "server", "moltenboulder")[0]
            first = roll["states"][0]
            frames = {s["native_age"]: s for s in port["rolling_states"]}
            samples = [s for s in original["samples"] if s["missile"] == "moltenboulder"]
            missed = [s["index"] for s in samples if s["native_age"] not in frames
                      or frames[s["native_age"]]["frame"] != s["frame"]]
            motion_missed = [s for s in roll["states"] if s["total_frames"] - s["current_frame"] not in frames
                or any(abs(frames[s["total_frames"] - s["current_frame"]]["position"][i] - s[k] / 65536 + .5) > 1e-5
                       for i, k in enumerate(("x_fixed", "y_fixed")))]
            checks["rolling_motion"] = not motion_missed
            checks["rolling_velocity"] = all(abs(port["rolling_birth"]["velocity"][i] - first[k] * 25 / 65536) < 1e-6 for i, k in enumerate(("velocity_x", "velocity_y")))
            checks["rolling_lifetime"] = port["rolling_birth"]["lifetime"] == first["total_frames"]
            checks["emerge_lifetime"] = port["emerge_birth"]["lifetime"] == native_units(original, "server", "moltenboulderemerge")[0]["states"][0]["total_frames"]
            checks["rolling_frames"] = bool(samples) and not missed
            native_counts = dict(Counter(u["missile"] for u in original["units"] if u["side"] == "client" and u["missile"] not in ("moltenboulder", "moltenboulderemerge")))
            checks["visual_child_counts"] = native_counts == port["visual_counts"]
            server_fire = native_units(original, "server", "moltenboulderfirepath")
            checks["server_fire_lifetimes_and_count"] = life_hist(server_fire) == dict(Counter(b["lifetime"] for b in port["births"] if not b["presentation_only"] and b["missile"] == "moltenboulderfirepath"))
            for row_name in native_counts:
                checks["lifetime_" + row_name] = life_hist(native_units(original, "client", row_name)) == dict(Counter(
                    b["lifetime"] for b in port["births"] if b["presentation_only"] and b["missile"] == row_name))
            rocks = native_units(original, "client", "moltenboulder-flyingrocks")
            fragments = [b for b in port["births"] if b["presentation_only"] and b["missile"] == "moltenboulder-flyingrocks"]
            endpoints_match = len(rocks) == len(fragments)
            for unit, birth in zip(rocks, fragments):
                state = unit["states"][0]
                dx, dy = state["velocity_x"] / 65536 * state["total_frames"], state["velocity_y"] / 65536 * state["total_frames"]
                # EngineUtils worldToScreenCoords uses an upward-positive world Y.
                endpoints_match &= abs(birth["end_offset_pixels"][0] - 16 * (dx - dy)) < .002
                endpoints_match &= abs(birth["end_offset_pixels"][1] + 8 * (dx + dy)) < .002
            checks["fragment_displacements"] = endpoints_match
            details = dict(rolling_samples=len(samples), frame_mismatch_indices=missed, motion_mismatches=len(motion_missed),
                           native_visual_counts=native_counts, production_visual_counts=port["visual_counts"],
                           conditioned_on="Recorded rolling client pre-crossing seed; does not establish allocator RNG or all flame phases")
        status = "compared_scoped_match" if all(checks.values()) else "compared_with_differences"
        result = dict(schema=1, source_run_id=summary["run_id"], skill_id=original["skill_id"],
                      comparison_status=status, fixture=summary["fixture"], checks=checks, details=details,
                      original_sha256=sha(original_path), replay_sha256=sha(replay_path),
                      limitations=["Scoped single-cast comparison; not all required cases or all directions",
                          "Production replay starts at SkillDo; no caster input/sequence, map geometry, damage or GPU comparison",
                          "Observed count differences require attribution before changing production emission rules",
                          "First Firestorm batch omitted small/medium client rows; whole-stream count is not an acceptance check",
                          "No full RNG alignment claim; recorded seed is supplied for the Molten Boulder presentation diagnostic"])
        path = directory / ("druid-" + name + "-comparison-1.10f.json")
        path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
        reports.append(dict(source_run_id=summary["run_id"], skill_id=original["skill_id"], comparison_status=status, file=path.name, sha256=sha(path)))
        print(name, status, "failed checks:", [k for k, v in checks.items() if not v])
    summary_path = directory / "batch-comparison-summary.json"
    previous = read(summary_path) if summary_path.exists() else dict(schema=1, captures=[])
    previous["captures"] = [c for c in previous["captures"] if c["skill_id"] not in (225, 229)] + reports
    summary_path.write_text(json.dumps(previous, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    compare(parser.parse_args().directory)
