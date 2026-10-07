"""Validate measured CSVs and create one importable RAIN floor JSON. No dependencies."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
try:
    from .validate_graph import validate
except ImportError:
    from validate_graph import validate


def build(rooms: Path, edges: Path, name: str, source: str) -> dict:
    errors, warnings, nodes, links = validate(rooms, edges)
    if errors:
        raise ValueError("\n".join(errors))
    if not 2 <= len(nodes) <= 300 or len(links) > 900:
        raise ValueError("Use 2–300 nodes and at most 900 segments per floor pack.")
    if not 1 <= len(name.strip()) <= 80 or any(ord(c) < 32 for c in name):
        raise ValueError("Use a short, single-line map name.")
    if source not in {"synthetic_floor", "user_provided_floor", "plan_derived_floor"}:
        raise ValueError("Explicit map provenance is required.")
    if any(abs(float(n[k])) > 10_000 for n in nodes for k in ("x_m", "y_m")) or any(float(e["length_m"]) > 10_000 for e in links):
        raise ValueError("Use local metre coordinates and lengths within 10,000 m.")
    pack = {"schema_version": 1, "name": name.strip(), "data_source": source,
            "rooms_csv": rooms.read_text(encoding="utf-8-sig"), "edges_csv": edges.read_text(encoding="utf-8-sig")}
    if len(json.dumps(pack, ensure_ascii=False).encode("utf-8")) > 1_000_000:
        raise ValueError("Floor pack exceeds 1 MB.")
    return pack


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rooms", type=Path, required=True)
    parser.add_argument("--edges", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--source", required=True, choices=["synthetic_floor", "user_provided_floor", "plan_derived_floor"])
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        pack = build(args.rooms, args.edges, args.name, args.source)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(pack, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    except (OSError, ValueError) as error:
        parser.exit(1, f"Cannot create floor pack: {error}\n")
    print("Validated floor pack created. Import it through RAIN > Floor maps > Choose floor file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
