import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rain_offline.build_floor_pack import build
from rain_offline.summarize_sessions import summarize
from rain_offline.check_exports import validate_record

ROOT = Path(__file__).resolve().parents[2]


class FieldToolsTests(unittest.TestCase):
    def test_sample_pack_keeps_provenance_and_csv_contract(self):
        assets = ROOT / "android/app/src/main/assets"
        pack = build(assets / "rooms.csv", assets / "edges.csv", "Sample", "synthetic_floor")
        self.assertEqual("synthetic_floor", pack["data_source"])
        self.assertIn("node_id", pack["rooms_csv"])

    def test_pack_rejects_unlabelled_source(self):
        assets = ROOT / "android/app/src/main/assets"
        with self.assertRaises(ValueError):
            build(assets / "rooms.csv", assets / "edges.csv", "Sample", "measured_accurate")

    def test_unverified_arrival_is_not_counted_as_verified(self):
        common = {"session_id": "test", "ts_ms": 1000, "schema_version": "0.2", "demo_mode": True, "data_source": "synthetic_floor"}
        start = common | {"event": "session_start", "participant_code": "TEST", "device_model": "test",
                          "planner": "risk", "lambda": 40, "start_node": "A", "dest_node": "B", "app_version": "test"}
        arrival = common | {"event": "arrival", "node_id": "B", "correct": None, "ground_truth_verified": False,
                            "total_time_s": 1, "distance_walked_m": 10}
        end = common | {"event": "session_end", "completed": True, "ts_ms": 2000}
        with TemporaryDirectory() as directory:
            path = Path(directory) / "session.jsonl"
            path.write_text("\n".join(json.dumps(e) for e in (start, arrival, end)), encoding="utf-8")
            row = summarize([path])[0]
            self.assertEqual(0, row["verified_arrivals"])
            self.assertEqual(1, row["unverified_arrivals"])
            self.assertEqual(0, row["observer_correct_arrival_tags"])
            with self.assertRaises(ValueError):
                summarize([path, path])

    def test_bad_prompt_policy_and_map_hash_are_rejected(self):
        record = {"session_id": "test", "ts_ms": 1, "schema_version": "0.2", "event": "session_start",
                  "participant_code": "TEST", "device_model": "test", "planner": "risk", "lambda": 40,
                  "start_node": "A", "dest_node": "B", "app_version": "test", "prompt_mode": "invalid", "floor_digest": "bad"}
        issues = validate_record(record)
        self.assertIn("unknown prompt policy", issues)
        self.assertIn("floor_digest must be a SHA-256 digest", issues)


if __name__ == "__main__":
    unittest.main()
