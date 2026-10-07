"""Check RAIN JSONL schema and common identifier leaks without echoing leaked values.

This is a release gate, not proof that free text or images are anonymous.
Never pass real network names on the command line; use a local ignored file.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import sys
from typing import Any

MAC = re.compile(r"(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])")
FORBIDDEN_KEYS = {"ssid", "raw_ssid", "bssid", "raw_bssid", "mac", "mac_address", "ocr_raw_text", "ocr_text", "participant_name", "email", "image", "photo"}
COMMON = {"session_id", "ts_ms", "event"}
METADATA = {"schema_version", "demo_mode", "data_source", "model_version", "simulation"}
EVENT_FIELDS = {
    "session_start": {"participant_code", "device_model", "planner", "lambda", "start_node", "dest_node", "app_version"},
    "route_planned": {"nodes", "edges", "total_length_m", "expected_errors", "scheduled_prompts"},
    "step": {"step_count", "pdr_edge_id", "pdr_offset_m", "heading_rel_deg"},
    "turn_detected": {"angle_deg", "at_node_estimate"},
    "recognition": {"method", "top1_node", "confidence", "margin", "top3", "ocr_raw_text_hash", "accepted"},
    "prompt_shown": {"reason", "node_estimate", "p_wrong"},
    "prompt_result": {"scanned", "recognition_top1", "seconds_to_respond"},
    "off_route": {"reason", "confidence"},
    "replan": {"from_node", "new_route_nodes"},
    "observer_tag": {"tag", "node_id"},
    "arrival": {"node_id", "correct", "total_time_s", "distance_walked_m"},
    "session_end": {"completed"},
}
OPTIONAL = {"off_route": {"detection_delay_steps"}, "arrival": {"ground_truth_verified"}}
V02_EVENTS = {
    "input_mode_changed": {"input_source"},
    "location_confirmed": {"node_id", "confirmation", "input_source"},
}
V02_OPTIONAL = {
    "session_start": {"risk_model_type", "step_length_m", "prompt_mode", "floor_digest"},
    "route_planned": {"risk_model_type"},
    "step": {"input_source"}, "turn_detected": {"input_source"},
    "recognition": {"input_source"}, "prompt_result": {"input_source"},
}
INPUT_SOURCES = {"simulated_steps", "phone_sensor", "simulated_turn", "demo_controls", "camera_ocr", "typed_demo_text"}
WIFI_FIELDS = {"session_id", "scan_id", "ts_ms", "device_model", "android_api", "node_id", "college_code", "bssid_hash", "la_bit", "oui", "rssi", "freq_mhz", "is_5ghz", "ssid_present", "ssid_class", "results_updated", "result_age_ms", "throttle_setting"}


def privacy_issues(value: Any, known_ssids: tuple[str, ...] = ()) -> list[str]:
    found = []
    if isinstance(value, dict):
        for key, child in value.items():
            if key.lower() in FORBIDDEN_KEYS:
                found.append("forbidden raw-identifier/free-text field")
            found.extend(privacy_issues(key, known_ssids))
            found.extend(privacy_issues(child, known_ssids))
    elif isinstance(value, list):
        for item in value:
            found.extend(privacy_issues(item, known_ssids))
    elif isinstance(value, str):
        if MAC.search(value):
            found.append("raw MAC-address pattern")
        folded = value.casefold()
        if any(ssid.casefold() in folded for ssid in known_ssids if ssid):
            found.append("known network name")
    return found


def is_number(value: Any) -> bool:
    return type(value) in (int, float) and math.isfinite(value)


def validate_record(record: Any, known_ssids: tuple[str, ...] = ()) -> list[str]:
    issues = privacy_issues(record, known_ssids)
    if not isinstance(record, dict):
        return issues + ["record must be a JSON object"]
    wifi = "bssid_hash" in record and "event" not in record
    event = record.get("event")
    v02 = record.get("schema_version") == "0.2"
    event_fields = EVENT_FIELDS | V02_EVENTS if v02 else EVENT_FIELDS
    if not wifi and (not isinstance(event, str) or event not in event_fields):
        return issues + ["missing or unknown navigation event"]
    required = WIFI_FIELDS if wifi else COMMON | event_fields[event]
    allowed = required | METADATA | (set() if wifi else OPTIONAL.get(event, set()))
    if v02 and not wifi:
        allowed |= V02_OPTIONAL.get(event, set())
    if required - record.keys():
        issues.append("required schema fields missing: " + ", ".join(sorted(required - record.keys())))
    if record.keys() - allowed:
        # Do not print unknown field names: a malformed exporter could put PII in keys.
        issues.append("unknown schema fields present")
    if not isinstance(record.get("session_id"), str) or not record["session_id"]:
        issues.append("session_id must be a nonempty string")
    if type(record.get("ts_ms")) is not int or record["ts_ms"] < 0:
        issues.append("ts_ms must be a nonnegative integer")
    booleans = ("la_bit", "is_5ghz", "ssid_present", "results_updated") if wifi else ("scanned", "accepted", "correct", "completed", "demo_mode", "simulation")
    for key in booleans:
        if v02 and key == "correct" and record.get(key) is None and record.get("ground_truth_verified") is False:
            continue
        if key in record and type(record[key]) is not bool:
            issues.append(f"{key} must be boolean")
    if "ground_truth_verified" in record and type(record["ground_truth_verified"]) is not bool:
        issues.append("ground_truth_verified must be boolean")
    if "prompt_mode" in record and record["prompt_mode"] not in {"cost_sensitive", "always", "fixed_interval", "never"}:
        issues.append("unknown prompt policy")
    if "floor_digest" in record and (not isinstance(record["floor_digest"], str) or not re.fullmatch(r"[0-9a-f]{64}", record["floor_digest"])):
        issues.append("floor_digest must be a SHA-256 digest")
    for key in ("confidence", "margin", "p_wrong"):
        if v02 and record.get(key) is None and (event, key) in {("off_route", "confidence"), ("prompt_shown", "p_wrong")}:
            continue
        if key in record and (not is_number(record[key]) or not 0 <= record[key] <= 1):
            issues.append(f"{key} must be a finite score in [0, 1]")
    for key in ("lambda", "total_length_m", "expected_errors", "pdr_offset_m", "seconds_to_respond", "total_time_s", "distance_walked_m", "result_age_ms"):
        if key in record and (not is_number(record[key]) or record[key] < 0):
            issues.append(f"{key} must be a finite nonnegative number")
    for key in ("step_count", "scan_id", "android_api", "detection_delay_steps"):
        if key in record and (type(record[key]) is not int or record[key] < 0):
            issues.append(f"{key} must be a nonnegative integer")
    for key in ("heading_rel_deg", "angle_deg"):
        if key in record and not (v02 and key == "heading_rel_deg" and record[key] is None) and not is_number(record[key]):
            issues.append(f"{key} must be finite; only schema 0.2 heading may be null")
    if "step_length_m" in record and (not is_number(record["step_length_m"]) or not .4 <= record["step_length_m"] <= 1.1):
        issues.append("step_length_m must be in the prototype range [0.4, 1.1]")
    for key in ("nodes", "edges", "new_route_nodes", "scheduled_prompts", "top3"):
        if key in record and not isinstance(record[key], list):
            issues.append(f"{key} must be an array")
    enums = {"planner": {"shortest", "simplest", "risk"}, "method": {"text", "image", "fused"},
             "ssid_class": {"college", "other"}, "throttle_setting": {"on", "off", "unknown"},
             "tag": {"wrong_turn", "hesitation", "asked_someone", "gave_up"},
             "input_source": INPUT_SOURCES, "confirmation": {"manual"},
             "risk_model_type": {"hand_weighted_uncalibrated"}}
    for key, choices in enums.items():
        if key in record and (not isinstance(record[key], str) or record[key] not in choices):
            issues.append(f"{key} has an invalid category")
    if event == "prompt_shown" and (not isinstance(record.get("reason"), str) or record["reason"] not in {"scheduled", "uncertainty", "off_route"}):
        issues.append("prompt reason has an invalid category")
    if event == "off_route" and (not isinstance(record.get("reason"), str) or record["reason"] not in {"unexpected_turn", "missed_turn", "overrun"}):
        issues.append("off-route reason has an invalid category")
    if "ocr_raw_text_hash" in record and record["ocr_raw_text_hash"] is not None:
        if not isinstance(record["ocr_raw_text_hash"], str) or not re.fullmatch(r"[0-9a-f]{64}", record["ocr_raw_text_hash"]):
            issues.append("ocr_raw_text_hash must be a lowercase SHA-256 hash or null")
    if wifi:
        if not isinstance(record.get("bssid_hash"), str) or not re.fullmatch(r"[0-9a-f]{16}", record["bssid_hash"]):
            issues.append("bssid_hash must contain exactly 16 lowercase hex characters")
        oui = record.get("oui")
        if record.get("la_bit") is True and oui is not None:
            issues.append("locally administered BSSID must have null oui")
        if oui is not None and (not isinstance(oui, str) or not re.fullmatch(r"(?i)[0-9a-f]{2}(?::[0-9a-f]{2}){2}", oui)):
            issues.append("oui must be three colon-delimited octets or null")
        if not is_number(record.get("rssi")) or not -127 <= record["rssi"] <= 0:
            issues.append("rssi must be in [-127, 0]")
    return sorted(set(issues))


def inspect_file(path: Path, known_ssids: tuple[str, ...] = ()) -> tuple[int, list[str]]:
    errors = []
    count = 0
    try:
        with path.open(encoding="utf-8-sig") as stream:
            for line_number, raw in enumerate(stream, 1):
                if not raw.strip():
                    errors.append(f"line {line_number}: blank line is not a JSONL record")
                    continue
                count += 1
                try:
                    def reject_constant(_: str) -> None:
                        raise ValueError("Non-finite JSON number")
                    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
                        result = {}
                        for key, value in pairs:
                            if key in result:
                                raise ValueError("Duplicate JSON key")
                            result[key] = value
                        return result
                    record = json.loads(raw, parse_constant=reject_constant, object_pairs_hook=unique_object)
                except (ValueError, TypeError):
                    errors.append(f"line {line_number}: invalid JSON; record contents suppressed")
                    continue
                errors.extend(f"line {line_number}: {issue}" for issue in validate_record(record, known_ssids))
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot read export ({type(exc).__name__})")
    if count == 0:
        errors.append("export contains no records")
    return count, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--known-ssids-file", type=Path, help="Local ignored text file, one SSID per line; never printed")
    args = parser.parse_args()
    try:
        known_ssids = tuple(args.known_ssids_file.read_text(encoding="utf-8").splitlines()) if args.known_ssids_file else ()
    except (OSError, UnicodeError):
        print("ERROR: unable to read local known-SSID file", file=sys.stderr)
        return 1
    total, failures = 0, 0
    for index, path in enumerate(args.files, 1):
        count, errors = inspect_file(path, known_ssids)
        total += count
        failures += len(errors)
        # Even the filename may contain a participant's name; identify by argument index.
        for error in errors:
            print(f"ERROR export {index}: {error}", file=sys.stderr)
    if failures:
        return 1
    print(f"OK: {total} records in {len(args.files)} exports passed schema and configured identifier checks.")
    print("Manual review still required for free text, consent and any accompanying images.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
