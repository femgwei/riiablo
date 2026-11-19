"""Canonicalize and compare original/Riiablo skill animation traces.

The comparator deliberately separates server observations (mode, movement,
combat events) from client animation observations (COF frame/keyframe and
render hashes). A trace missing a requested capability is ``incomplete``;
it is never reported as a visual pass.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

SCHEMA = 1
# Player animation mode ids used by the 1.10f client.  Keeping the complete
# table avoids silently losing the post-sequence TN/TW and action modes.
MODE_NAMES = {
    0: "DT", 1: "NU", 2: "WL", 3: "RN", 4: "GH", 5: "TN", 6: "TW",
    7: "A1", 8: "A2", 9: "BL", 10: "SC", 11: "TH", 12: "KK",
    13: "S1", 14: "S2", 15: "S3", 16: "S4", 17: "DD", 18: "SQ",
}
COMPARE_FIELDS = ("mode_id", "mode", "token", "wclass", "animation_frame_raw",
                  "animation_frame_count", "animation_speed", "keyframes")
FIELD_CAPABILITIES = {
    "token": "token", "wclass": "wclass",
    "animation_frame_raw": "animation_frame",
    "animation_frame_count": "animation_frame_count",
    "animation_speed": "animation_speed", "keyframes": "keyframes",
}
FRAME_FLAG_KEYFRAMES = {1: "ATK", 2: "MIS", 3: "SND", 4: "SKL"}


def _json(path):
    return json.loads(path.read_text(encoding="utf8"))


def _rows(path):
    with path.open(encoding="utf8", newline="") as stream:
        return list(csv.DictReader(stream))


def _int(value):
    return None if value in (None, "", "null") else int(value)


def _trace_records(path):
    """Read JSON or header/frame JSONL emitted by a Riiablo recorder."""
    if path.suffix.lower() == ".json":
        data = _json(path)
        return data["metadata"], data["frames"]
    metadata, frames = None, []
    for line in path.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        if record.get("type") == "header":
            metadata = record.get("metadata", {})
        elif record.get("type") == "frame":
            frames.append(record)
    if metadata is None:
        raise ValueError("trace JSONL is missing a header record")
    return metadata, frames


def _trace_events(path):
    """Read ordered semantic animation events from a JSON/JSONL trace."""
    if path.suffix.lower() == ".json":
        data = _json(path)
        return data.get("events", [])
    events = []
    for line in path.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        if record.get("type") == "event":
            events.append(record)
    return events


def semantic_event_diff(left_path, right_path):
    """Compare the ordered client presentation calls for each sample tick.

    Events are intentionally separate from the frame fields. A visual hash can
    differ because of a palette or viewport even when the call sequence is
    correct; this report instead identifies the first missing, extra, or
    reordered semantic step.
    """
    left = _trace_events(left_path)
    right = _trace_events(right_path)
    if not left or not right:
        return {"status": "incomplete", "left_events": len(left),
                "right_events": len(right),
                "reason": "both traces need type=event records"}

    def by_tick(events):
        grouped = {}
        for event in events:
            grouped.setdefault(event.get("tick"), []).append(event)
        return grouped

    def signature(event):
        ignored = {"type", "tick", "game_frame", "entity_id"}
        return {key: value for key, value in event.items() if key not in ignored}

    left_by_tick, right_by_tick = by_tick(left), by_tick(right)
    first = None
    exact_ticks = 0
    compared_ticks = 0
    divergences = []
    for tick in sorted(set(left_by_tick) | set(right_by_tick), key=lambda x: (x is None, x)):
        a = [signature(event) for event in left_by_tick.get(tick, [])]
        b = [signature(event) for event in right_by_tick.get(tick, [])]
        compared_ticks += 1
        if a == b:
            exact_ticks += 1
            continue
        index = 0
        while index < min(len(a), len(b)) and a[index] == b[index]:
            index += 1
        divergence = {"tick": tick, "index": index,
                      "original": a[index] if index < len(a) else None,
                      "riiablo": b[index] if index < len(b) else None,
                      "original_event_count": len(a),
                      "riiablo_event_count": len(b)}
        divergences.append(divergence)
        if first is None:
            first = divergence
    return {"status": "passed" if not divergences else "failed",
            "left_events": len(left), "right_events": len(right),
            "compared_ticks": compared_ticks, "exact_ticks": exact_ticks,
            "first_divergence": first, "divergences": divergences[:100]}


def write_trace(path, metadata, frames):
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"schema": SCHEMA, "metadata": metadata, "frames": frames}
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def native_trace(sample):
    """Convert an original d2game collector directory to canonical trace JSON."""
    summary = _json(sample / "summary.json")
    identity = _json(sample / "identity.json")
    rows = _rows(sample / "frames.csv")
    damage = {int(row["frame"]) for row in _rows(sample / "damage.csv")}
    frames = []
    first = int(summary["first_frame"])
    for row in rows:
        absolute = int(row["frame"])
        frame = {
            "type": "frame",
            "tick": absolute - first,
            "game_frame": absolute,
            "mode_id": _int(row.get("player_mode")),
            "mode": MODE_NAMES.get(_int(row.get("player_mode"))),
            "x_fp16": _int(row.get("x_fp16")),
            "y_fp16": _int(row.get("y_fp16")),
            "combat_events": ["DAMAGE"] if absolute in damage else [],
            # The D2Game collector does not observe D2Client's COF cursor.
            "animation_frame_raw": None,
            "animation_frame_count": None,
            "animation_speed": None,
            "keyframes": None,
            "visual_sha256": None,
        }
        frames.append(frame)
    metadata = {
        "schema": SCHEMA,
        "source": "original-d2game-observer",
        "skill": identity.get("skill"),
        "level": identity.get("level"),
        "class": identity.get("class"),
        "character": identity.get("character"),
        "token": identity.get("token"),
        "wclass": identity.get("wclass"),
        "capabilities": ["mode", "position", "combat_events"],
        "missing_capabilities": ["animation_frame", "keyframes", "visual"],
        "identity": identity,
        "summary": {"status": summary.get("status"), "cleanup_confirmed": summary.get("cleanup_confirmed"),
                    "detached": summary.get("detached"), "fixtures_restored": summary.get("fixtures_restored")},
    }
    return metadata, frames


def client_trace(path):
    """Convert read-only live-observer samples of D2Client's player unit."""
    metadata = {"schema": SCHEMA, "source": "original-d2client-read-only-observer",
                "capabilities": ["mode", "animation_frame", "position"],
                "missing_capabilities": ["keyframes", "visual"],
                "candidate_scan": {"status": "not_present"}}
    frames = []
    first_class = None
    visual_samples = 0
    for record in json.loads("[" + ",".join(
            line for line in path.read_text(encoding="utf8").splitlines() if line.strip()) + "]"):
        if record.get("type") not in (None, "sample"):
            continue
        state = record.get("candidate_player") or record.get("player") or record
        if state.get("status") != "player":
            continue
        position = state.get("position") or {}
        if first_class is None:
            first_class = state.get("class_id")
        candidates = record.get("animation_candidates") or {}
        known = candidates.get("known_fields") or {}
        anim_pointer = next((item for item in candidates.get("pointer_records", [])
                             if item.get("offset") == 0x50), {})
        anim_record = anim_pointer.get("anim_data_record") or {}
        visual = record.get("visual") or {}
        visual_sha256 = record.get("visual_sha256") or visual.get("sha256")
        if visual.get("status") == "captured" and visual_sha256:
            visual_samples += 1
        frame = {"type": "frame", "tick": len(frames),
                       "sample_elapsed_ms": record.get("elapsed_ms"),
                       "game_frame": state.get("game_frame"),
                       "mode_id": state.get("mode"), "mode": MODE_NAMES.get(state.get("mode")),
                       "token": None, "wclass": None,
                       "animation_frame_raw": state.get("animation_frame_raw"),
                       "animation_frame_count": None, "animation_speed": None,
                       "keyframes": None, "combat_events": [], "visual_sha256": visual_sha256,
                       "x_fp16": position.get("x", 0) << 16 if "x" in position else None,
                       "y_fp16": position.get("y", 0) << 16 if "y" in position else None}
        if record.get("animation_candidates") is not None:
            frame["animation_candidates"] = record["animation_candidates"]
            frame["sequence_frame_raw"] = known.get("sequence_frame_raw")
            frame["animation_frame_count_candidate_raw"] = known.get("animation_frame_count_raw")
            frame["animation_speed_candidate_raw"] = known.get("animation_speed_raw")
            frame["animation_speed_16_candidate"] = known.get("animation_speed_16")
            frame["sequence_mode"] = known.get("sequence_mode")
            frame["action_frame_candidate"] = known.get("action_frame")
            frame["token_candidate"] = anim_pointer.get("ascii_token")
            frame["wclass_candidate"] = anim_pointer.get("weapon_class_candidate")
            frame["cof_frames_candidate"] = anim_record.get("frames")
            frame["cof_anim_speed_candidate"] = anim_record.get("anim_speed")
            frame["cof_frame_flags_candidate"] = anim_record.get("frame_flags")
            metadata["candidate_scan"] = {"status": "recorded",
                                           "scope": "hash-gated UnitAny animation fields and read-only pointer previews",
                                           "layout_source": candidates.get("layout_source")}
        frames.append(frame)
    if not frames:
        raise ValueError("client observer trace has no player samples")
    metadata["class"] = first_class
    if visual_samples == len(frames):
        metadata["capabilities"].append("visual")
        metadata["missing_capabilities"] = [item for item in metadata["missing_capabilities"]
                                              if item != "visual"]
        metadata["visual_capture"] = {"status": "complete", "samples": visual_samples}
    else:
        metadata["visual_capture"] = {"status": "partial", "samples": visual_samples,
                                        "total": len(frames)}
    return metadata, frames


def promote_client_candidates(metadata, frames, promote_keyframes=False):
    """Promote fully observed COF candidates into comparable trace fields.

    Promotion is explicit so a raw observer run never silently becomes a
    visual pass.  It only promotes token, weapon class, AnimData frame count,
    and AnimData speed.  Keyframes can additionally be promoted when the
    caller explicitly requests it.  SQ uses the observed nActionFrame result
    of the original MonSeq calculation; ordinary modes use pFrameFlags.
    """
    candidates = {
        "token": "token_candidate",
        "wclass": "wclass_candidate",
        "animation_frame_count": "cof_frames_candidate",
        "animation_speed": "cof_anim_speed_candidate",
    }
    promoted, coverage = [], {}
    for field, source in candidates.items():
        present = sum(frame.get(source) is not None for frame in frames)
        coverage[field] = {"present": present, "total": len(frames)}
        if present != len(frames):
            continue
        for frame in frames:
            frame[field] = frame[source]
        promoted.append(field)
    keyframe_status = "not_requested"
    if promote_keyframes:
        # Both sources use the same 1..4 event codes as COF keyframes:
        # sequence mode exposes the already-computed nActionFrame, while
        # ordinary modes expose AnimData pFrameFlags.
        eligible = all(frame.get("mode") not in (None, "") for frame in frames)
        complete = all((frame.get("mode") == "SQ" and
                        frame.get("action_frame_candidate") is not None) or
                       (frame.get("mode") != "SQ" and
                        isinstance(frame.get("cof_frame_flags_candidate"), list))
                       for frame in frames)
        if eligible and complete:
            for frame in frames:
                if frame.get("mode") == "SQ":
                    value = frame.get("action_frame_candidate") or 0
                else:
                    raw = frame.get("cof_frame_flags_candidate")
                    index = (frame.get("animation_frame_raw") or 0) >> 8
                    value = raw[index] if index < len(raw) else 0
                frame["keyframes"] = ([FRAME_FLAG_KEYFRAMES[value]]
                                       if value in FRAME_FLAG_KEYFRAMES else [])
            promoted.append("keyframes")
            keyframe_status = "promoted_from_sequence_events_and_frame_flags"
        else:
            keyframe_status = "skipped_ineligible_or_incomplete"
    capabilities = set(metadata.get("capabilities", []))
    capabilities.update(promoted)
    # Keep metadata deterministic for downstream reports and tests.
    promoted.sort()
    metadata["capabilities"] = sorted(capabilities)
    missing = set(metadata.get("missing_capabilities", []))
    missing.difference_update(promoted)
    metadata["missing_capabilities"] = sorted(missing)
    scan = metadata.setdefault("candidate_scan", {})
    scan["promotion"] = {"status": "explicit", "promoted": promoted,
                           "coverage": coverage,
                           "keyframes": keyframe_status}
    return metadata, frames


def validate(metadata, frames):
    if metadata.get("schema", SCHEMA) != SCHEMA:
        raise ValueError("unsupported animation trace schema")
    if not frames:
        raise ValueError("trace has no frame records")
    ticks = [frame.get("tick") for frame in frames]
    if ticks != list(range(len(frames))):
        raise ValueError("trace ticks are not contiguous from zero")
    for frame in frames:
        if frame.get("type") not in (None, "frame"):
            raise ValueError("unexpected non-frame record")
        if frame.get("mode_id") is None:
            raise ValueError("frame is missing mode_id")
    return True


def _mismatch(mismatches, tick, field, left, right):
    mismatches.append({"tick": tick, "field": field, "left": left, "right": right})


def sequence_diagnostics(left_frames, right_frames):
    """Summarize native SQ cursor evidence without making it a pass gate.

    The original read-only observer keeps the candidate ``sequence_frame_raw``
    under ``animation_candidates.known_fields``.  The production offscreen
    client emits the corresponding ``native_sequence_frame_raw`` diagnostic.
    These values are intentionally reported separately from the public client
    animation fields because they are version-pinned implementation evidence.
    """
    pairs = {}
    compared = exact = 0
    mapping = {}
    events = []
    for tick, (left, right) in enumerate(zip(left_frames, right_frames)):
        if left.get("mode") != "SQ" or right.get("mode") != "SQ":
            continue
        original = left.get("sequence_frame_raw")
        if original is None:
            known = (left.get("animation_candidates") or {}).get("known_fields") or {}
            original = known.get("sequence_frame_raw")
        native = right.get("native_sequence_frame_raw")
        if original is not None and native is not None:
            compared += 1
            exact += int(original == native)
            key = f"{original}:{native}"
            pairs[key] = pairs.get(key, 0) + 1
        animation = left.get("animation_frame_raw")
        if original is not None and animation is not None:
            key = f"{original}:{animation}"
            mapping[key] = mapping.get(key, 0) + 1
        if left.get("action_frame") or left.get("action_frame_candidate"):
            events.append({"tick": tick, "sequence_frame_raw": original,
                           "animation_frame_raw": animation,
                           "action_frame": left.get("action_frame")
                           if left.get("action_frame") is not None
                           else left.get("action_frame_candidate")})
    return {
        "compared": compared,
        "exact_native_cursor_matches": exact,
        "native_cursor_match_ratio": exact / compared if compared else None,
        "original_to_right_cursor_pairs": dict(sorted(pairs.items())),
        "original_sequence_to_presentation_mapping": dict(sorted(mapping.items())),
        "original_action_events": events,
    }


def phase_alignment(left_frames, right_frames):
    """Diagnose cyclic animation phase without weakening strict comparison.

    Live D2Client samples are asynchronous, while the offscreen client can be
    sampled on fixed ticks.  For each common mode span this finds the best
    cyclic offset over integer animation frames and reports the match ratio.
    SQ is intentionally treated like every other mode: a poor ratio there is
    evidence that native sequence frame mapping differs, not a reason to hide
    the mismatch.
    """
    segments = []
    total_matches = 0
    total_compared = 0
    start = 0
    while start < min(len(left_frames), len(right_frames)):
        mode = left_frames[start].get("mode")
        end = start + 1
        while end < min(len(left_frames), len(right_frames)) \
                and left_frames[end].get("mode") == mode \
                and right_frames[end].get("mode") == right_frames[start].get("mode"):
            end += 1
        left = left_frames[start:end]
        right = right_frames[start:end]
        counts = [frame.get("animation_frame_count") for frame in left + right
                  if isinstance(frame.get("animation_frame_count"), int)
                  and frame.get("animation_frame_count") > 0]
        frame_count = min(counts) if counts else 0
        samples = []
        if frame_count:
            for a, b in zip(left, right):
                ar, br = a.get("animation_frame_raw"), b.get("animation_frame_raw")
                if isinstance(ar, int) and isinstance(br, int):
                    samples.append(((ar >> 8) % frame_count, (br >> 8) % frame_count))
        best_offset, best_matches = 0, 0
        if samples and frame_count:
            for offset in range(frame_count):
                matches = sum((right_frame + offset) % frame_count == left_frame
                              for left_frame, right_frame in samples)
                if matches > best_matches:
                    best_offset, best_matches = offset, matches
        compared = len(samples)
        total_matches += best_matches
        total_compared += compared
        segments.append({
            "start_tick": start,
            "end_tick": end - 1,
            "mode": mode,
            "right_mode": right_frames[start].get("mode") if start < len(right_frames) else None,
            "frame_count": frame_count,
            "best_offset": best_offset,
            "matches": best_matches,
            "compared": compared,
            "match_ratio": best_matches / compared if compared else None,
        })
        start = end
    return {
        "segments": segments,
        "matches": total_matches,
        "compared": total_compared,
        "match_ratio": total_matches / total_compared if total_compared else None,
    }


def compare(left, right, position_tolerance_fp16=256):
    left_meta, left_frames = _trace_records(left)
    right_meta, right_frames = _trace_records(right)
    validate(left_meta, left_frames)
    validate(right_meta, right_frames)
    mismatches = []
    mismatch_counts = {}
    compared_counts = {}
    def record_count(field, mismatch):
        compared_counts[field] = compared_counts.get(field, 0) + 1
        if mismatch:
            mismatch_counts[field] = mismatch_counts.get(field, 0) + 1
    if left_meta.get("skill") != right_meta.get("skill"):
        _mismatch(mismatches, None, "skill", left_meta.get("skill"), right_meta.get("skill"))
        record_count("skill", True)
    if left_meta.get("level") != right_meta.get("level"):
        _mismatch(mismatches, None, "level", left_meta.get("level"), right_meta.get("level"))
        record_count("level", True)
    if len(left_frames) != len(right_frames):
        _mismatch(mismatches, None, "frame_count", len(left_frames), len(right_frames))
    frame_count = min(len(left_frames), len(right_frames))
    capabilities = set(left_meta.get("capabilities", [])) & set(right_meta.get("capabilities", []))
    missing = sorted((set(left_meta.get("capabilities", [])) ^ set(right_meta.get("capabilities", [])))
                     | set(left_meta.get("missing_capabilities", []))
                     | set(right_meta.get("missing_capabilities", [])))
    for index in range(frame_count):
        a, b = left_frames[index], right_frames[index]
        tick = index
        for field in COMPARE_FIELDS:
            required = FIELD_CAPABILITIES.get(field)
            if required and required not in capabilities:
                continue
            record_count(field, a.get(field) != b.get(field))
            if a.get(field) != b.get(field):
                _mismatch(mismatches, tick, field, a.get(field), b.get(field))
        for axis in ("x_fp16", "y_fp16"):
            if axis in a and axis in b and a[axis] is not None and b[axis] is not None:
                if abs(a[axis] - b[axis]) > position_tolerance_fp16:
                    record_count(axis, True)
                    _mismatch(mismatches, tick, axis, a[axis], b[axis])
                else:
                    record_count(axis, False)
        if "combat_events" in capabilities and a.get("combat_events") != b.get("combat_events"):
            record_count("combat_events", True)
            _mismatch(mismatches, tick, "combat_events", a.get("combat_events"), b.get("combat_events"))
        elif "combat_events" in capabilities:
            record_count("combat_events", False)
        if "visual" in capabilities and a.get("visual_sha256") != b.get("visual_sha256"):
            record_count("visual_sha256", True)
            _mismatch(mismatches, tick, "visual_sha256", a.get("visual_sha256"), b.get("visual_sha256"))
        elif "visual" in capabilities:
            record_count("visual_sha256", False)
    client_fields_ready = all(field in capabilities for field in
                              ("token", "wclass", "animation_frame", "animation_frame_count",
                               "animation_speed"))
    animation_ready = "animation_frame" in capabilities and "keyframes" in capabilities
    visual_ready = "visual" in capabilities
    status = "failed" if mismatches else ("passed" if animation_ready else "incomplete")
    exact_fields = sorted(field for field, total in compared_counts.items()
                          if mismatch_counts.get(field, 0) == 0)
    semantic = semantic_event_diff(left, right)
    return {"schema": SCHEMA, "status": status, "animation_ready": animation_ready,
            "client_fields_ready": client_fields_ready,
            "visual_ready": visual_ready, "compared_frames": frame_count,
            "missing_capabilities": missing, "mismatch_count": len(mismatches),
            "field_mismatch_counts": mismatch_counts,
            "field_compared_counts": compared_counts,
            "exact_match_fields": exact_fields,
            "phase_alignment": phase_alignment(left_frames, right_frames),
            "sequence_diagnostics": sequence_diagnostics(left_frames, right_frames),
            "semantic_call_diff": semantic,
            "mismatches": mismatches[:100],
            "scope": "frame/event comparison; visual pass requires synchronized render captures"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    native = sub.add_parser("from-native")
    native.add_argument("sample", type=Path)
    native.add_argument("--output", type=Path, required=True)
    client = sub.add_parser("from-client")
    client.add_argument("samples", type=Path, help="live-observer trace.jsonl")
    client.add_argument("--output", type=Path, required=True)
    promote = sub.add_parser("promote-client")
    promote.add_argument("samples", type=Path, help="live-observer samples.jsonl")
    promote.add_argument("--output", type=Path, required=True)
    promote.add_argument("--promote-keyframes", action="store_true",
                         help="map ordinary-mode AnimData frame flags to ATK/MIS/SND/SKL")
    compare_parser = sub.add_parser("compare")
    compare_parser.add_argument("left", type=Path)
    compare_parser.add_argument("right", type=Path)
    compare_parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command in ("from-native", "from-client", "promote-client"):
        if args.command == "from-native":
            metadata, frames = native_trace(args.sample)
        else:
            metadata, frames = client_trace(args.samples)
            if args.command == "promote-client":
                metadata, frames = promote_client_candidates(
                    metadata, frames, promote_keyframes=args.promote_keyframes)
        validate(metadata, frames)
        write_trace(args.output, metadata, frames)
        print(json.dumps({"status": "written", "output": str(args.output), "frames": len(frames)}, ensure_ascii=False))
        return 0
    report = compare(args.left, args.right)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(report, ensure_ascii=False))
    return 0 if report["status"] == "passed" else (2 if report["status"] == "incomplete" else 1)


if __name__ == "__main__":
    raise SystemExit(main())
