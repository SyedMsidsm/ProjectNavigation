"""Produce descriptive session metrics; estimates never become measured arrival accuracy."""
from __future__ import annotations
import argparse
import csv
import json
from pathlib import Path
try:
    from .check_exports import inspect_file
except ImportError:
    from check_exports import inspect_file


def summarize(paths: list[Path]) -> list[dict]:
    sessions: dict[str, list[dict]] = {}
    for index, path in enumerate(paths, 1):
        _, errors = inspect_file(path)
        if errors:
            raise ValueError(f"Export {index} failed schema checks; run check_exports.py first.")
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip():
                continue
            record = json.loads(line)
            if "event" not in record:
                raise ValueError("Use navigation JSONL files, not Wi-Fi survey files.")
            sessions.setdefault(record["session_id"], []).append(record)
    rows = []
    for session_id, events in sessions.items():
        starts = [e for e in events if e["event"] == "session_start"]
        ends = [e for e in events if e["event"] == "session_end"]
        if len(starts) != 1 or len(ends) > 1:
            raise ValueError("Duplicate or incomplete session boundaries. Supply one export per session.")
        start = starts[0]
        count = lambda event: sum(e["event"] == event for e in events)
        steps = [e for e in events if e["event"] == "step"]
        tags = [e["tag"] for e in events if e["event"] == "observer_tag"]
        arrivals = [e for e in events if e["event"] == "arrival"]
        routes = [e for e in events if e["event"] == "route_planned"]
        rows.append({
            "session_id": session_id, "planner": start["planner"], "prompt_mode": start.get("prompt_mode", "unrecorded"),
            "data_source": start.get("data_source", "unrecorded"), "floor_digest": start.get("floor_digest", "unrecorded"),
            "completed": ends[0]["completed"] if ends else "unfinished",
            "elapsed_wall_s": round(max(0, ends[0]["ts_ms"] - start["ts_ms"]) / 1000, 3) if ends else "",
            "initial_route_m": routes[0]["total_length_m"] if routes else "",
            "sensor_step_events": sum(e.get("input_source") == "phone_sensor" for e in steps),
            "simulated_step_events": sum(e.get("input_source") == "simulated_steps" for e in steps),
            "unlabelled_step_events": sum(e.get("input_source") not in {"phone_sensor", "simulated_steps"} for e in steps),
            "prompts": count("prompt_shown"), "detector_offroute_events": count("off_route"), "replans": count("replan"),
            "observer_wrong_turn_tags": tags.count("observed_wrong_turn"),
            "observer_correct_arrival_tags": tags.count("observed_correct_arrival"),
            "observer_incorrect_arrival_tags": tags.count("observed_incorrect_arrival"),
            "verified_arrivals": sum(e.get("ground_truth_verified") is True and type(e.get("correct")) is bool for e in arrivals),
            "unverified_arrivals": sum(e.get("ground_truth_verified") is not True for e in arrivals),
        })
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        rows = summarize(args.files)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
            writer.writeheader(); writer.writerows(rows)
    except (OSError, ValueError, IndexError) as error:
        parser.exit(1, f"Cannot summarize: {error}\n")
    print(f"Summarized {len(rows)} sessions. Wall time includes pauses; observer tags and detector events remain separate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
