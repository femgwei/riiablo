import json
from pathlib import Path
import tempfile
import unittest

from normalize_d2gfx_bone_armor_trace import normalize


class BoneArmorDrawNormalizerTest(unittest.TestCase):
    def trace(self, directory, events):
        root = Path(directory)
        (root / "summary.json").write_text(json.dumps({"status": "passed", "cleanup_confirmed": True}))
        path = root / "d2gfx-call-trace.jsonl"
        path.write_text("\n".join(json.dumps(e) for e in events), encoding="utf8")
        return path

    @staticmethod
    def start():
        return {"kind": "ORIGINAL_D2GFX_EXPORT", "rva": 0x3C90, "elapsed_ms": 1}

    @staticmethod
    def draw(overlay, frame):
        return {"kind": "ORIGINAL_D2GFX_EXPORT", "rva": 0x4270, "window_state": "minimized",
                "elapsed_ms": 2, "cell": {"unit_type": 6, "overlay": overlay, "frame": frame}}

    def test_same_id_layers_keep_multiplicity_and_phase(self):
        with tempfile.TemporaryDirectory() as directory:
            events = [self.start()] + [self.draw(141, f) for f in (1, 9, 16)] + [self.start()]
            result = normalize(self.trace(directory, events))
            self.assertEqual(3, result["max_draws_per_closed_render_batch"]["141"])
            self.assertEqual([1, 9, 16], [d["frame"] for d in result["render_batches"][0]["draws"]])

    def test_unclosed_tail_does_not_inflate_closed_batch_maximum(self):
        with tempfile.TemporaryDirectory() as directory:
            result = normalize(self.trace(directory, [self.start(), self.draw(142, 0)]))
            self.assertEqual(0, result["max_draws_per_closed_render_batch"]["142"])
            self.assertFalse(result["render_batches"][0]["closed_by_next_start_draw"])

    def test_unrelated_type_with_same_numeric_overlay_is_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            unrelated = self.draw(141, 9)
            unrelated["cell"]["unit_type"] = 1
            result = normalize(self.trace(directory, [self.start(), unrelated, self.draw(140, 3), self.start()]))
            self.assertEqual([140], [row["overlay"] for row in result["observed"]])

    def test_missing_armor_draws_cannot_be_reported_as_observed(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "No Bone Armor"):
                normalize(self.trace(directory, [self.start()]))

    def test_body_order_excludes_the_earlier_shadow_primitive(self):
        with tempfile.TemporaryDirectory() as directory:
            body = {"kind": "ORIGINAL_D2GFX_EXPORT", "rva": 0x42A0,
                    "cell": {"unit_type": 0, "token": "NE  "}}
            shadow = {**body, "rva": 0x4360}
            result = normalize(self.trace(directory, [self.start(), shadow,
                self.draw(142, 1), body, self.draw(141, 1), self.start()]))
            batch = result["render_batches"][0]
            self.assertEqual([3], batch["body_draw_ordinals"])
            self.assertTrue(batch["back_body_front_order_observed"])

    def test_empty_armor_batch_with_live_body_retains_disappearance_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            body = {"kind": "ORIGINAL_D2GFX_EXPORT", "rva": 0x42A0,
                    "cell": {"unit_type": 0, "token": "NE"}}
            result = normalize(self.trace(directory, [self.start(), self.draw(140, 1),
                self.start(), body, self.start()]))
            self.assertEqual(2, len(result["render_batches"]))
            self.assertEqual([], result["render_batches"][1]["draws"])
            self.assertTrue(result["render_batches"][1]["closed_by_next_start_draw"])

    def test_intervals_without_body_or_armor_cannot_prove_disappearance(self):
        with tempfile.TemporaryDirectory() as directory:
            result = normalize(self.trace(directory, [self.start(), self.draw(140, 1),
                self.start(), self.start(), self.start()]))
            self.assertEqual(1, len(result["render_batches"]))


if __name__ == "__main__":
    unittest.main()
