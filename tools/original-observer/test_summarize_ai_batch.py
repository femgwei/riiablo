import unittest
from summarize_ai_batch import coverage
from collector_specs import validate_collector


class BatchObservationsTest(unittest.TestCase):
    def test_missing_action_is_not_inferred_from_sample_count(self):
        rows = [dict(step=i, mode=1, x=18, y=0) for i in range(500)]
        self.assertFalse(coverage(rows)['movement'])
        self.assertIsNone(coverage(rows)['first_action_tick'])

    def test_walk_mode_does_not_prove_displacement(self):
        rows = [dict(step=0, mode=1, x=0, y=0), dict(step=1, mode=2, x=0, y=0)]
        self.assertFalse(coverage(rows)['movement'])
        self.assertEqual(1, coverage(rows)['first_action_tick'])

    def test_bounded_native_contract_rejects_unrelated_classes(self):
        for klass in (True, 156, -1, '5'):
            with self.assertRaises(ValueError):
                validate_collector('native_ai_batch', {'class_id': klass})
        for klass in (5, 170, 19, 58, 53):
            self.assertTrue(validate_collector('native_ai_batch', {'class_id': klass}).is_file())
        for scenario in ({'class_id': 5, 'distance': 18},
                         {'class_id': 170, 'distance': True},
                         {'class_id': 170, 'distance': 19}):
            with self.assertRaises(ValueError):
                validate_collector('native_ai_batch', scenario)
        self.assertTrue(validate_collector('native_ai_batch', {'class_id': 170, 'distance': 18}).is_file())


if __name__ == '__main__':
    unittest.main()
