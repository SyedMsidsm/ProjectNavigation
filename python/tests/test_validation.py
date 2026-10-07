from copy import deepcopy
import csv
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rain_offline.validate_graph import ROOM_FIELDS, EDGE_FIELDS, validate, write_svg
from rain_offline.check_exports import inspect_file, validate_record


class GraphValidationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.rooms = [dict(zip(ROOM_FIELDS, ["A", "entrance", "Entry", "", "", "0", "0", "", "", "0", ""])),
                      dict(zip(ROOM_FIELDS, ["B", "door", "Room 204", "ROOM 204", "204", "10", "0", "left", "printed_clear", "0", ""]))]
        self.edges = [dict(zip(EDGE_FIELDS, ["E1", "A", "B", "10", "90", "C1", "true", "false", ""]))]

    def run_validation(self):
        for name, fields, rows in (("rooms", ROOM_FIELDS, self.rooms), ("edges", EDGE_FIELDS, self.edges)):
            with (self.root / f"{name}.csv").open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fields)
                writer.writeheader()
                writer.writerows(rows)
        return validate(self.root / "rooms.csv", self.root / "edges.csv")

    def test_valid_bidirectional_floor_and_svg_escape(self):
        self.rooms[1]["room_name"] = 'Room 204 <script>'
        errors, _, rooms, edges = self.run_validation()
        self.assertEqual([], errors)
        out = self.root / "map.svg"
        write_svg(out, rooms, edges, "Synthetic <sample>")
        self.assertIn("&lt;script&gt;", out.read_text())
        self.assertNotIn("<script>", out.read_text())

    def test_reject_unknown_endpoint(self):
        self.edges[0]["to_node"] = "MISSING"
        self.assertTrue(any("endpoint" in message for message in self.run_validation()[0]))

    def test_reject_nonfinite_and_nonpositive_lengths(self):
        for value in ("nan", "inf", "-1", "0"):
            with self.subTest(value=value):
                self.edges[0]["length_m"] = value
                self.assertTrue(any("length_m" in message for message in self.run_validation()[0]))

    def test_disconnected_and_one_way_rejected(self):
        self.edges[0]["bidirectional"] = "false"
        self.assertTrue(any("reach" in message for message in self.run_validation()[0]))
        self.edges = []
        self.assertTrue(self.run_validation()[0])

    def test_duplicate_ids_rejected(self):
        self.rooms.append(deepcopy(self.rooms[0]))
        self.assertTrue(any("duplicate" in message for message in self.run_validation()[0]))

    def test_door_cannot_be_junction(self):
        for identifier in ("C", "D"):
            row = deepcopy(self.rooms[0])
            row["node_id"] = identifier
            self.rooms.append(row)
            edge = deepcopy(self.edges[0])
            edge.update(edge_id="E" + identifier, from_node="B", to_node=identifier)
            self.edges.append(edge)
        self.assertTrue(any("more than two" in message for message in self.run_validation()[0]))

    def test_csv_quoted_comma_roundtrip(self):
        self.rooms[1]["notes"] = 'Wall says "Lab, 204"'
        self.assertEqual([], self.run_validation()[0])

    def test_bad_headers_are_rejected(self):
        self.run_validation()
        p = self.root / "rooms.csv"
        p.write_text(p.read_text().replace("node_id", "room_id", 1))
        errors, *_ = validate(p, self.root / "edges.csv")
        self.assertTrue(any("headers" in message for message in errors))


class ExportValidationTests(unittest.TestCase):
    def setUp(self):
        self.record = {"session_id": "S_DEMO", "ts_ms": 1, "event": "session_start", "participant_code": "DEMO",
                       "device_model": "TEST", "planner": "risk", "lambda": 40.0, "start_node": "A", "dest_node": "B",
                       "app_version": "0.1", "demo_mode": True, "data_source": "synthetic"}

    def test_valid_demo_event(self):
        self.assertEqual([], validate_record(self.record))

    def test_schema_missing_fields(self):
        del self.record["planner"]
        self.assertTrue(any("required" in error for error in validate_record(self.record)))

    def test_raw_mac_in_nested_payload_detected_without_echo(self):
        self.record["top3"] = [{"untrusted": "AA:BB:CC:DD:EE:FF"}]
        issues = validate_record(self.record)
        self.assertTrue(any("raw MAC" in issue for issue in issues))
        self.assertFalse(any("AA:BB" in issue for issue in issues))

    def test_ssid_key_and_known_string_detected(self):
        self.record["ssid"] = "EXAMPLE_NETWORK"
        issues = validate_record(self.record, ("EXAMPLE_NETWORK",))
        self.assertIn("known network name", issues)
        self.assertIn("forbidden raw-identifier/free-text field", issues)
        self.assertNotIn("EXAMPLE_NETWORK", " ".join(issues))

    def test_nonstring_event_does_not_crash(self):
        self.record["event"] = {"bad": True}
        self.assertTrue(validate_record(self.record))

    def test_booleans_not_accepted_as_timestamps(self):
        self.record["ts_ms"] = True
        self.assertTrue(any("ts_ms" in issue for issue in validate_record(self.record)))

    def test_jsonl_rejects_nan_duplicate_keys_and_empty_file(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "export.jsonl"
            for raw in ('{"a": NaN}\n', '{"a": 1, "a": 2}\n', ''):
                path.write_text(raw)
                self.assertTrue(inspect_file(path)[1])

    def test_jsonl_valid_event_parses(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "export.jsonl"
            path.write_text(json.dumps(self.record) + "\n")
            self.assertEqual((1, []), inspect_file(path))

    def test_wifi_hash_and_la_bit_contract(self):
        record = {"session_id": "S", "scan_id": 1, "ts_ms": 1, "device_model": "TEST", "android_api": 34,
                  "node_id": "A", "college_code": "TEST", "bssid_hash": "0123456789abcdef", "la_bit": True,
                  "oui": None, "rssi": -60, "freq_mhz": 2400, "is_5ghz": False, "ssid_present": True,
                  "ssid_class": "other", "results_updated": True, "result_age_ms": 100, "throttle_setting": "unknown"}
        self.assertEqual([], validate_record(record))
        record["oui"] = "AA:BB:CC"
        self.assertTrue(any("locally administered" in issue for issue in validate_record(record)))
        record["bssid_hash"] = "0123456789abcdef0"
        self.assertTrue(any("16 lowercase" in issue for issue in validate_record(record)))

    def test_v02_unverified_arrival_is_not_a_wrong_room_label(self):
        record = {"session_id": "S", "ts_ms": 1, "event": "arrival", "schema_version": "0.2",
                  "node_id": "N01", "correct": None, "ground_truth_verified": False,
                  "total_time_s": 20, "distance_walked_m": 15}
        self.assertEqual([], validate_record(record))
        record["ground_truth_verified"] = True
        self.assertTrue(validate_record(record))
        record["ground_truth_verified"] = False
        record["schema_version"] = 1
        self.assertTrue(validate_record(record))

    def test_v02_nullable_estimates_and_typed_input_are_explicit(self):
        record = {"session_id": "S", "ts_ms": 1, "event": "off_route", "schema_version": "0.2",
                  "reason": "unexpected_turn", "confidence": None}
        self.assertEqual([], validate_record(record))
        del record["schema_version"]
        self.assertTrue(validate_record(record))
        record = {"session_id": "S", "ts_ms": 1, "event": "location_confirmed", "schema_version": "0.2",
                  "node_id": "N01", "confirmation": "manual", "input_source": "typed_demo_text"}
        self.assertEqual([], validate_record(record))
        record["input_source"] = "made_up"
        self.assertTrue(validate_record(record))


if __name__ == "__main__":
    unittest.main()
