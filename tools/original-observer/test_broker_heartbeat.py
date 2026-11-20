"""Regression for the Windows status-file lock that stopped the receiver."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from resident_broker import Broker, atomic_json


class BrokerHeartbeatTest(unittest.TestCase):
    def test_locked_status_keeps_receiver_alive_and_recovers(self):
        with tempfile.TemporaryDirectory() as directory:
            broker = Broker(Path(directory))
            broker.heartbeat()
            with patch("resident_broker.os.replace", side_effect=PermissionError("locked")), \
                    patch("resident_broker.time.sleep"):
                broker.heartbeat(status="running", active="test-batch")
            self.assertEqual(1, broker.state["status_write_failures"])
            self.assertEqual([], list(Path(directory).glob("*.tmp")))
            broker.heartbeat()
            saved = json.loads((Path(directory) / "status.json").read_text(encoding="utf8"))
            self.assertEqual("running", saved["status"])
            self.assertEqual("test-batch", saved["active"])
            self.assertEqual(1, saved["status_write_failures"])

    def test_task_result_write_failure_remains_explicit(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch("resident_broker.os.replace", side_effect=PermissionError("locked")), \
                    patch("resident_broker.time.sleep"):
                with self.assertRaises(PermissionError):
                    atomic_json(Path(directory) / "result.json", {"status": "completed"})
            self.assertEqual([], list(Path(directory).glob("*.tmp")))


if __name__ == "__main__":
    unittest.main()
