import json
import tempfile
import unittest
from pathlib import Path

from animation_compare import (client_trace, compare, promote_client_candidates,
                               semantic_event_diff, validate, write_trace)


def trace(root, name, animation=True, visual=False, position=1000):
    capabilities = ["mode", "position", "combat_events"]
    if animation:
        capabilities += ["animation_frame", "keyframes"]
    if visual:
        capabilities += ["visual"]
    metadata = {"schema": 1, "skill": 151, "level": 1,
                "capabilities": capabilities, "missing_capabilities": [],
                "source": name}
    frames = []
    for tick in range(4):
        frames.append({"type": "frame", "tick": tick, "mode_id": 18 if tick < 3 else 1,
                       "mode": "SQ" if tick < 3 else "NU", "token": "BA", "wclass": "1HS",
                       "animation_frame_raw": tick * 256 if animation else None,
                       "animation_frame_count": 16 if animation else None,
                       "animation_speed": 256 if animation else None,
                       "keyframes": ["ATK"] if animation and tick == 2 else ([] if animation else None),
                       "combat_events": ["DAMAGE"] if tick == 2 else [],
                       "visual_sha256": "same" if visual else None,
                       "x_fp16": position + tick * 100, "y_fp16": 2000})
    path = root / (name + ".json")
    write_trace(path, metadata, frames)
    return path


class AnimationCompareTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def test_identical_animation_and_visual_trace_passes(self):
        left = trace(self.root, "left", visual=True)
        right = trace(self.root, "right", visual=True)
        result = compare(left, right)
        self.assertEqual("passed", result["status"])
        self.assertTrue(result["animation_ready"])
        self.assertTrue(result["visual_ready"])

    def test_animation_frame_mismatch_fails(self):
        left = trace(self.root, "left")
        right = trace(self.root, "right")
        data = json.loads(right.read_text(encoding="utf8"))
        data["frames"][2]["animation_frame_raw"] += 256
        right.write_text(json.dumps(data), encoding="utf8")
        result = compare(left, right)
        self.assertEqual("failed", result["status"])
        self.assertEqual("animation_frame_raw", result["mismatches"][0]["field"])

    def test_missing_client_animation_is_incomplete(self):
        left = trace(self.root, "left", animation=False)
        right = trace(self.root, "right", animation=False)
        result = compare(left, right)
        self.assertEqual("incomplete", result["status"])
        self.assertFalse(result["animation_ready"])

    def test_position_tolerance_is_not_visual_pass(self):
        left = trace(self.root, "left", visual=True)
        right = trace(self.root, "right", visual=True, position=1400)
        result = compare(left, right)
        self.assertEqual("failed", result["status"])
        self.assertFalse(any(m["field"] == "animation_frame_raw" for m in result["mismatches"]))

    def test_non_contiguous_trace_is_rejected(self):
        left = trace(self.root, "left")
        data = json.loads(left.read_text(encoding="utf8"))
        data["frames"][2]["tick"] = 5
        with self.assertRaisesRegex(ValueError, "contiguous"):
            validate(data["metadata"], data["frames"])

    def test_visual_hash_difference_fails(self):
        left = trace(self.root, "left", visual=True)
        right = trace(self.root, "right", visual=True)
        data = json.loads(right.read_text(encoding="utf8"))
        data["frames"][1]["visual_sha256"] = "different"
        right.write_text(json.dumps(data), encoding="utf8")
        result = compare(left, right)
        self.assertEqual("failed", result["status"])
        self.assertTrue(any(m["field"] == "visual_sha256" for m in result["mismatches"]))

    def test_client_observer_nested_samples_become_animation_trace(self):
        source = self.root / "client-trace.jsonl"
        source.write_text("\n".join([
            json.dumps({"type": "sample", "elapsed_ms": 0,
                        "candidate_player": {"status": "player", "class_id": 4,
                                              "mode": 18, "animation_frame_raw": 256,
                                              "position": {"x": 10, "y": 20}}}),
            json.dumps({"type": "sample", "elapsed_ms": 40,
                        "candidate_player": {"status": "player", "class_id": 4,
                                              "mode": 18, "animation_frame_raw": 512,
                                              "position": {"x": 10, "y": 20}}}),
        ]), encoding="utf8")
        metadata, frames = client_trace(source)
        self.assertEqual(4, metadata["class"])
        self.assertEqual([256, 512], [frame["animation_frame_raw"] for frame in frames])
        self.assertEqual([0, 40], [frame["sample_elapsed_ms"] for frame in frames])

    def test_menu_driver_player_samples_are_supported(self):
        source = self.root / "menu-samples.jsonl"
        source.write_text(json.dumps({"elapsed_ms": 12, "player": {
            "status": "player", "class_id": 4, "mode": 1,
            "animation_frame_raw": 224, "position": {"x": 4543, "y": 5641}}}),
            encoding="utf8")
        metadata, frames = client_trace(source)
        self.assertEqual(4, metadata["class"])
        self.assertEqual(224, frames[0]["animation_frame_raw"])

    def test_client_visual_capture_is_promoted_only_when_every_sample_has_hash(self):
        source = self.root / "client-visual.jsonl"
        source.write_text("\n".join([
            json.dumps({"type": "sample", "elapsed_ms": 0,
                        "candidate_player": {"status": "player", "class_id": 4,
                                              "mode": 1, "animation_frame_raw": 0,
                                              "position": {"x": 1, "y": 2}},
                        "visual": {"status": "captured", "sha256": "a"}}),
            json.dumps({"type": "sample", "elapsed_ms": 40,
                        "candidate_player": {"status": "player", "class_id": 4,
                                              "mode": 1, "animation_frame_raw": 256,
                                              "position": {"x": 1, "y": 2}},
                        "visual": {"status": "captured", "sha256": "b"}}),
        ]), encoding="utf8")
        metadata, frames = client_trace(source)
        self.assertIn("visual", metadata["capabilities"])
        self.assertEqual("complete", metadata["visual_capture"]["status"])
        self.assertEqual(["a", "b"], [frame["visual_sha256"] for frame in frames])

    def test_unit_animation_candidates_preserve_token_and_frame_fields(self):
        source = self.root / "client-candidates.jsonl"
        source.write_text(json.dumps({"type": "sample", "elapsed_ms": 1,
            "candidate_player": {"status": "player", "class_id": 4,
                                  "mode": 18, "animation_frame_raw": 256,
                                  "position": {"x": 10, "y": 20}},
            "animation_candidates": {
                "layout_source": "test",
                "known_fields": {"animation_frame_count_raw": 1792,
                                  "animation_speed_raw": 256,
                                  "animation_speed_16": 256,
                                  "sequence_mode": 7},
                "pointer_records": [{"offset": 80, "ascii_token": "BAGH1HS",
                                     "weapon_class_candidate": "1HS",
                                     "anim_data_record": {"frames": 5,
                                                          "anim_speed": 256,
                                                          "frame_flags": [0, 0, 1]}}]}}),
            encoding="utf8")
        metadata, frames = client_trace(source)
        self.assertEqual("recorded", metadata["candidate_scan"]["status"])
        self.assertEqual("BAGH1HS", frames[0]["token_candidate"])
        self.assertEqual("1HS", frames[0]["wclass_candidate"])
        self.assertEqual(1792, frames[0]["animation_frame_count_candidate_raw"])
        self.assertEqual(5, frames[0]["cof_frames_candidate"])
        self.assertEqual(256, frames[0]["cof_anim_speed_candidate"])

    def test_candidate_promotion_is_explicit_and_keeps_keyframe_gate(self):
        source = self.root / "client-promote.jsonl"
        source.write_text(json.dumps({"type": "sample", "elapsed_ms": 1,
            "candidate_player": {"status": "player", "class_id": 4,
                                  "mode": 18, "animation_frame_raw": 256,
                                  "position": {"x": 10, "y": 20}},
            "animation_candidates": {
                "layout_source": "test",
                "pointer_records": [{"offset": 80, "ascii_token": "BAGH1HS",
                                     "weapon_class_candidate": "1HS",
                                     "anim_data_record": {"frames": 5,
                                                          "anim_speed": 256,
                                                          "frame_flags": [0] * 144}}]}}),
            encoding="utf8")
        metadata, frames = promote_client_candidates(*client_trace(source))
        self.assertEqual(["animation_frame_count", "animation_speed", "token", "wclass"],
                         metadata["candidate_scan"]["promotion"]["promoted"])
        self.assertEqual(5, frames[0]["animation_frame_count"])
        self.assertFalse("keyframes" in metadata["capabilities"])

    def test_frame_flags_can_be_explicitly_promoted_for_ordinary_modes(self):
        source = self.root / "client-frame-flags.jsonl"
        source.write_text(json.dumps({"type": "sample", "elapsed_ms": 1,
            "candidate_player": {"status": "player", "class_id": 4,
                                  "mode": 1, "animation_frame_raw": 256,
                                  "position": {"x": 10, "y": 20}},
            "animation_candidates": {
                "layout_source": "test",
                "pointer_records": [{"offset": 80, "ascii_token": "BANU1HS",
                                     "weapon_class_candidate": "1HS",
                                     "anim_data_record": {"frames": 8,
                                                          "anim_speed": 96,
                                                          "frame_flags": [0, 1] + [0] * 142}}]}}),
            encoding="utf8")
        metadata, frames = promote_client_candidates(*client_trace(source),
                                                      promote_keyframes=True)
        self.assertEqual("promoted_from_sequence_events_and_frame_flags",
                         metadata["candidate_scan"]["promotion"]["keyframes"])
        self.assertIn("keyframes", metadata["capabilities"])
        self.assertEqual(["ATK"], frames[0]["keyframes"])

    def test_frame_flags_are_not_used_for_sequence_mode(self):
        source = self.root / "client-sequence-flags.jsonl"
        source.write_text(json.dumps({"type": "sample", "elapsed_ms": 1,
            "candidate_player": {"status": "player", "class_id": 4,
                                  "mode": 18, "animation_frame_raw": 256,
                                  "position": {"x": 10, "y": 20}},
            "animation_candidates": {
                "layout_source": "test",
                "pointer_records": [{"offset": 80, "ascii_token": "BAGH1HS",
                                     "weapon_class_candidate": "1HS",
                                     "anim_data_record": {"frames": 5,
                                                          "anim_speed": 256,
                                                          "frame_flags": [0, 1] + [0] * 142}}]}}),
            encoding="utf8")
        metadata, frames = promote_client_candidates(*client_trace(source),
                                                      promote_keyframes=True)
        self.assertEqual("skipped_ineligible_or_incomplete",
                         metadata["candidate_scan"]["promotion"]["keyframes"])
        self.assertNotIn("keyframes", metadata["capabilities"])

    def test_sequence_action_frame_can_be_promoted_to_keyframe(self):
        source = self.root / "client-sequence-action.jsonl"
        source.write_text(json.dumps({"type": "sample", "elapsed_ms": 1,
            "candidate_player": {"status": "player", "class_id": 4,
                                  "mode": 18, "animation_frame_raw": 1536,
                                  "position": {"x": 10, "y": 20}},
            "animation_candidates": {
                "layout_source": "test",
                "known_fields": {"action_frame": 1},
                "pointer_records": [{"offset": 80, "ascii_token": "BAGH1HS",
                                     "weapon_class_candidate": "1HS",
                                     "anim_data_record": {"frames": 5,
                                                          "anim_speed": 256,
                                                          "frame_flags": [0] * 144}}]}}),
            encoding="utf8")
        metadata, frames = promote_client_candidates(*client_trace(source),
                                                      promote_keyframes=True)
        self.assertEqual("promoted_from_sequence_events_and_frame_flags",
                         metadata["candidate_scan"]["promotion"]["keyframes"])
        self.assertEqual(["ATK"], frames[0]["keyframes"])

    def test_semantic_call_diff_reports_first_missing_step(self):
        left = self.root / "original-events.jsonl"
        right = self.root / "riiablo-events.jsonl"
        header = {"type": "header", "schema": 1, "metadata": {}}
        original = [
            {"type": "event", "tick": 7, "game_frame": 7, "entity_id": 3,
             "kind": "COF_RESOLVE", "cof_name": "BAGH1HS"},
            {"type": "event", "tick": 7, "game_frame": 7, "entity_id": 3,
             "kind": "LAYER_DRAW", "component": 0},
            {"type": "event", "tick": 7, "game_frame": 7, "entity_id": 3,
             "kind": "LAYER_DRAW", "component": 1},
        ]
        riiablo = [original[0], original[1]]
        left.write_text("\n".join(json.dumps(x) for x in [header] + original) + "\n", encoding="utf8")
        right.write_text("\n".join(json.dumps(x) for x in [header] + riiablo) + "\n", encoding="utf8")
        result = semantic_event_diff(left, right)
        self.assertEqual("failed", result["status"])
        self.assertEqual(2, result["first_divergence"]["index"])
        self.assertIsNone(result["first_divergence"]["riiablo"])


if __name__ == "__main__":
    unittest.main()
