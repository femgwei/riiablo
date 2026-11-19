import copy
import unittest

from port_navigation import Navigation, validate_resume


class NavigationTests(unittest.TestCase):
    def setUp(self):
        self.plan = dict(schema=1, seed=123, difficulty=0, level_id=3,
                         size=[20, 20], port_origin=[300, -600],
                         rooms=[dict(id=0, bounds=[0, 0, 10, 20], preset=[-1,-1]),
                                dict(id=1, bounds=[10,0,10,20], preset=[-1,-1])],
                         free_spans=[[[0,20]] for _ in range(20)])
        self.layout = dict(map_seed=123, difficulty=0, levels=[dict(
            level_id=3, origin_tiles=[920,984], size_tiles=[4,4], rooms=[
                dict(pointer=100, local_subtiles=[0,0,10,20], preset=None),
                dict(pointer=200, local_subtiles=[10,0,10,20], preset=None)])])

    def navigation(self):
        n = Navigation(self.plan)
        n.bind(self.layout)
        return n

    def test_seed_difficulty_geometry_and_preset_are_hard_gates(self):
        mutations = [lambda j:j.update(map_seed=124), lambda j:j.update(difficulty=1),
                     lambda j:j['levels'][0]['rooms'][0].update(local_subtiles=[1,0,10,20]),
                     lambda j:j['levels'][0]['rooms'][0].update(preset=[5,0])]
        for mutate in mutations:
            layout = copy.deepcopy(self.layout)
            mutate(layout)
            with self.assertRaises(ValueError):
                Navigation(self.plan).bind(layout)

    def test_origin_translation_and_unvisited_goal(self):
        n = self.navigation()
        self.assertEqual(n.local((4602,4924)), (2,4))
        target = n.next_target((4602,4924), {100})
        self.assertEqual(n.goal, 200)
        self.assertTrue(n.is_free(n.local(target)))
        self.assertGreater(target[0], 4602)
        self.assertLessEqual(target[0], 4607)

    def test_grid_route_goes_around_barrier(self):
        # Vertical river x=9; the only passage lies at y=15.
        self.plan['free_spans'] = [[[0,9],[10,20]] if y != 15 else [[0,20]] for y in range(20)]
        n = self.navigation()
        n.next_target((4602,4924), {100})
        self.assertIn((9,15), n.path)
        self.assertTrue(all(n.is_free(p) for p in n.path))

    def test_original_blocked_in_port_is_reported_without_guessed_route(self):
        self.plan['free_spans'][4] = [[3,20]]
        n = self.navigation()
        self.assertIsNone(n.next_target((4602,4924), {100}))
        self.assertEqual(n.events[0]['reason'], 'original_position_not_free_in_port')

    def test_invalid_grid_and_missing_rows_rejected(self):
        self.plan['free_spans'][0] = [[0,21]]
        with self.assertRaises(ValueError):
            Navigation(self.plan)

    def test_selected_file_variant_must_match_when_available(self):
        self.layout['levels'][0]['rooms'][0].update(preset=[4,2], preset_map_available=True)
        self.plan['rooms'][0]['preset'] = [4,1]
        with self.assertRaisesRegex(ValueError, 'selected file'):
            self.navigation()

    def test_visited_goal_replans_to_another_room(self):
        n = self.navigation()
        n.next_target((4602,4924), {100})
        self.assertEqual(n.goal, 200)
        self.assertIsNone(n.next_target((4612,4924), {100,200}))

    def test_resume_preserves_coverage_and_units_for_same_live_map(self):
        resume = dict(pid=42, map_seed=123, difficulty=0,
                      room_pointers=self.layout['levels'][0]['rooms'],
                      visited_rooms=[100], units={'1:7:900': dict(level_id=3)})
        visited, units = validate_resume(resume, 42, self.layout, 3)
        self.assertEqual(visited, {100})
        self.assertEqual(units, resume['units'])

    def test_resume_rejects_different_process_room_instance_or_level(self):
        resume = dict(pid=42, map_seed=123, difficulty=0,
                      room_pointers=self.layout['levels'][0]['rooms'],
                      visited_rooms=[100], units={'1:7:900': dict(level_id=3)})
        mutations = [lambda r:r.update(pid=43),
                     lambda r:r['room_pointers'][0].update(pointer=101),
                     lambda r:r.update(visited_rooms=[999]),
                     lambda r:r['units']['1:7:900'].update(level_id=2)]
        for mutate in mutations:
            bad = copy.deepcopy(resume)
            mutate(bad)
            with self.assertRaises(ValueError):
                validate_resume(bad, 42, self.layout, 3)


if __name__ == '__main__':
    unittest.main()
