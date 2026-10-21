import tempfile
from collections import Counter
from pathlib import Path
import unittest

from migration_boundaries import (check_debt, network_inventory, network_references, network_violations,
                                  presentation_edges, write_tsv, read_debt)


class MigrationBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def source(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    def test_gdx_and_qualified_presentation_access_are_not_invisible(self):
        self.source("core/src/main/java/com/riiablo/engine/client/Input.java", '''
          package com.riiablo.engine.client;
          import com.badlogic.gdx.scenes.scene2d.Stage;
          class Input { com.riiablo.presentation.DialogManager dialog; }
          // com.riiablo.audio.Ignored comment;
          /* import com.badlogic.gdx.graphics.Ignored; */
          String ignored = "com.riiablo.widget.Ignored";
        ''')
        edges = presentation_edges(self.root)
        self.assertEqual(2, len(edges))
        self.assertTrue(any(target.endswith(".Stage") for _, target in edges))
        self.assertEqual(2, len(check_debt(edges, {})))

    def test_existing_type_budget_cannot_expand_or_hide_another_type(self):
        key = ("core/src/main/java/com/riiablo/engine/client/Input.java", "com.riiablo.presentation.Context")
        debt = {key: dict(count="1")}
        self.assertEqual([], check_debt(Counter({key: 1}), debt))
        self.assertTrue(check_debt(Counter({key: 2}), debt))
        self.assertTrue(check_debt(Counter(), debt))
        self.assertTrue(check_debt(Counter({(key[0], "com.riiablo.presentation.Other"): 1}), debt))

    def test_headless_modules_cannot_whitelist_presentation(self):
        key = ("quests/src/main/java/Quest.java", "com.riiablo.presentation.Context")
        self.assertTrue(check_debt(Counter({key: 1}), {key: dict(count="1")}))

    def test_root_device_types_and_global_devices_cannot_bypass_the_gate(self):
        self.source("core/src/main/java/com/riiablo/engine/server/Device.java", '''
          package com.riiablo.engine.server;
          import com.badlogic.gdx.Graphics;
          import static com.badlogic.gdx.Gdx.audio;
          class Device { void render() { Gdx . gl20.clear(); } }
        ''')
        edges = presentation_edges(self.root)
        self.assertEqual(3, len(edges))
        self.assertTrue(any(target == "Gdx.gl20" for _, target in edges))
        self.assertEqual(6, len(check_debt(edges, {})))

    def test_cross_implementation_reference_is_counted_without_package_or_comment(self):
        self.source("server/netty/src/main/java/example/Legacy.java", '''
          package com.riiablo.server.d2gs_netty;
          import com.riiablo.server.d2gs_test.Main;
          // com.riiablo.server.d2gs_test.Ignored;
        ''')
        self.source("server/netty/src/test/java/example/Test.java", '''
          package example;
          import com.riiablo.server.d2gs_test.Main;
        ''')
        refs = network_references(self.root)
        self.assertEqual(1, len(refs))
        self.assertEqual("d2gs_test", refs[0]["family"])
        self.assertEqual("com.riiablo.server.d2gs_test.Main", refs[0]["target"])

    def test_inventory_uses_source_roots_and_round_trips_real_tab_header(self):
        self.source("server/netty/src/main/java/example/Main.java", "package com.riiablo.server.d2gs_test;")
        self.source("server/netty/src/test/java/example/Test.java", "package com.riiablo.server.d2gs_test;")
        rows = network_inventory(self.root)
        self.assertEqual(1, len(rows))
        self.assertEqual("d2gs_test", rows[0]["family"])
        self.assertEqual("main", rows[0]["productionSourceSet"])
        debt = self.root / "debt.tsv"
        row = dict(file="Input.java", target="Stage", count=1, owner="client-input",
                   migration="M8-C", remove_when="Use a presentation input port")
        write_tsv(debt, [row], tuple(row))
        self.assertEqual("client-input", read_debt(debt)[("Input.java", "Stage")]["owner"])

    def test_missing_sources_cannot_report_a_green_empty_inventory(self):
        with self.assertRaises(ValueError):
            network_inventory(self.root)

    def test_putting_a_test_harness_back_in_main_fails_even_without_references(self):
        self.source("server/netty/src/main/java/example/Main.java", "package com.riiablo.server.d2gs_test;")
        violations = network_violations(network_inventory(self.root), network_references(self.root))
        self.assertEqual(1, len(violations))
        self.assertIn("production source root", violations[0])

    def test_wildcard_reference_to_test_harness_cannot_report_zero(self):
        self.source("server/netty/src/main/java/example/Server.java", '''
          package com.riiablo.server.d2gs_netty;
          import com.riiablo.server.d2gs_test.*;
        ''')
        violations = network_violations(network_inventory(self.root), network_references(self.root))
        self.assertEqual(1, len(violations))
        self.assertIn("depends on a test harness", violations[0])


if __name__ == "__main__":
    unittest.main()
