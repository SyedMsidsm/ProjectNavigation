"""Validate the exact RAIN CSV contract, optionally draw a dependency-free SVG.

Usage: python python/rain_offline/validate_graph.py --rooms rooms.csv --edges edges.csv
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime
from html import escape
import math
from pathlib import Path, PurePosixPath, PureWindowsPath
import sys

ROOM_FIELDS = "node_id,node_type,room_name,sign_text,aliases,x_m,y_m,corridor_side,sign_condition,similar_doors_nearby,notes".split(",")
EDGE_FIELDS = "edge_id,from_node,to_node,length_m,bearing_deg,corridor_id,bidirectional,has_stairs,notes".split(",")
PHOTO_FIELDS = "photo_id,node_id,capture_type,sign_condition,device_model,lighting,distance_m,angle_deg,flash,timestamp_iso,contains_people,file_name".split(",")
NODE_TYPES = {"door", "junction", "corridor_point", "entrance", "stairs", "washroom"}
SIGN_CONDITIONS = {"printed_clear", "printed_faded", "handwritten", "glare", "obscured", "missing"}


def read_csv(path: Path, fields: list[str], errors: list[str]) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8-sig", newline="") as stream:
            reader = csv.DictReader(stream, strict=True)
            if reader.fieldnames != fields:
                errors.append(f"{path.name}: headers must exactly match: {','.join(fields)}")
                return []
            rows = []
            for line, row in enumerate(reader, 2):
                if None in row or any(value is None for value in row.values()):
                    errors.append(f"{path.name}:{line}: wrong number of columns")
                    continue
                rows.append({key: value.strip() for key, value in row.items()})
            return rows
    except (OSError, UnicodeError, csv.Error) as exc:
        errors.append(f"{path.name}: cannot read CSV ({type(exc).__name__})")
        return []


def number(value: str, where: str, errors: list[str], *, minimum: float | None = None,
           maximum: float | None = None, strictly_positive: bool = False) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        result = math.nan
    if (not math.isfinite(result) or minimum is not None and result < minimum
            or maximum is not None and result > maximum or strictly_positive and result <= 0):
        errors.append(f"{where}: invalid finite numeric value or range")
        return None
    return result


def unique_ids(rows: list[dict[str, str]], field: str, filename: str, errors: list[str]) -> set[str]:
    seen = set()
    for line, row in enumerate(rows, 2):
        identifier = row[field]
        if not identifier or identifier in seen:
            errors.append(f"{filename}:{line}: empty or duplicate {field}")
        seen.add(identifier)
    return seen


def validate(rooms_path: Path, edges_path: Path, photos_path: Path | None = None
             ) -> tuple[list[str], list[str], list[dict[str, str]], list[dict[str, str]]]:
    errors: list[str] = []
    warnings: list[str] = []
    rooms = read_csv(rooms_path, ROOM_FIELDS, errors)
    edges = read_csv(edges_path, EDGE_FIELDS, errors)
    node_ids = unique_ids(rooms, "node_id", rooms_path.name, errors)
    unique_ids(edges, "edge_id", edges_path.name, errors)
    if not rooms:
        errors.append("rooms.csv: at least one mapped node is required")
    if len(rooms) > 1 and not edges:
        errors.append("edges.csv: at least one edge is required for multiple nodes")
    for line, row in enumerate(rooms, 2):
        where = f"{rooms_path.name}:{line}"
        if row["node_type"] not in NODE_TYPES:
            errors.append(f"{where}: invalid node_type")
        if row["corridor_side"] not in {"", "left", "right"}:
            errors.append(f"{where}: corridor_side must be left, right or empty")
        if row["sign_condition"] not in SIGN_CONDITIONS | {""}:
            errors.append(f"{where}: invalid sign_condition")
        if row["node_type"] == "door" and (not row["room_name"] or not row["sign_condition"]):
            errors.append(f"{where}: doors require room_name and sign_condition")
        for field in ("x_m", "y_m"):
            number(row[field], f"{where}:{field}", errors)
        try:
            if int(row["similar_doors_nearby"]) < 0:
                raise ValueError()
        except ValueError:
            errors.append(f"{where}: similar_doors_nearby must be a nonnegative integer")
    forward = {node: set() for node in node_ids}
    backward = {node: set() for node in node_ids}
    neighbours = {node: set() for node in node_ids}
    for line, row in enumerate(edges, 2):
        where = f"{edges_path.name}:{line}"
        start, end = row["from_node"], row["to_node"]
        number(row["length_m"], f"{where}:length_m", errors, strictly_positive=True)
        bearing = number(row["bearing_deg"], f"{where}:bearing_deg", errors, minimum=0, maximum=360)
        if bearing == 360:
            errors.append(f"{where}: bearing_deg must be below 360; use 0")
        for field in ("bidirectional", "has_stairs"):
            if row[field] not in {"true", "false"}:
                errors.append(f"{where}: {field} must be true or false")
        if not row["corridor_id"]:
            errors.append(f"{where}: corridor_id is required")
        if start not in node_ids or end not in node_ids:
            errors.append(f"{where}: an edge endpoint is missing from rooms.csv")
            continue
        if start == end:
            errors.append(f"{where}: self-loop edges are not supported")
            continue
        forward[start].add(end)
        backward[end].add(start)
        neighbours[start].add(end)
        neighbours[end].add(start)
        if row["bidirectional"] == "true":
            forward[end].add(start)
            backward[start].add(end)
    for row in rooms:
        if row["node_type"] == "door" and len(neighbours[row["node_id"]]) > 2:
            errors.append(f"rooms.csv: door {row['node_id']} has more than two neighbours; use a junction node")
    if node_ids:
        def reached(adjacency: dict[str, set[str]]) -> set[str]:
            todo = [next(iter(node_ids))]
            seen = set(todo)
            while todo:
                for child in adjacency[todo.pop()] - seen:
                    seen.add(child)
                    todo.append(child)
            return seen
        if reached(forward) != node_ids or reached(backward) != node_ids:
            errors.append("graph: not all nodes can reach all others (check disconnected nodes and one-way edges)")
    if photos_path:
        photos = read_csv(photos_path, PHOTO_FIELDS, errors)
        unique_ids(photos, "photo_id", photos_path.name, errors)
        for line, row in enumerate(photos, 2):
            where = f"{photos_path.name}:{line}"
            if row["node_id"] not in node_ids:
                errors.append(f"{where}: photo node is missing from rooms.csv")
            if row["capture_type"] not in {"nameplate", "enrol", "test"}:
                errors.append(f"{where}: invalid capture_type")
            if row["sign_condition"] not in SIGN_CONDITIONS:
                errors.append(f"{where}: invalid sign_condition")
            if row["lighting"] not in {"day", "evening", "artificial"}:
                errors.append(f"{where}: invalid lighting")
            for field in ("flash", "contains_people"):
                if row[field] not in {"true", "false"}:
                    errors.append(f"{where}: {field} must be true or false")
            if row["contains_people"] == "true":
                warnings.append(f"{where}: contains people; require reviewed redaction before release")
            number(row["distance_m"], f"{where}:distance_m", errors, strictly_positive=True)
            number(row["angle_deg"], f"{where}:angle_deg", errors, minimum=-180, maximum=180)
            try:
                parsed = datetime.fromisoformat(row["timestamp_iso"].replace("Z", "+00:00"))
                if parsed.tzinfo is None:
                    raise ValueError()
            except ValueError:
                errors.append(f"{where}: timestamp_iso requires a valid timestamp with timezone")
            filename = row["file_name"]
            posix, windows = PurePosixPath(filename.replace("\\", "/")), PureWindowsPath(filename)
            if not filename or posix.is_absolute() or windows.drive or ".." in posix.parts:
                errors.append(f"{where}: file_name must be a relative path without parent traversal")
    return errors, warnings, rooms, edges


def write_svg(path: Path, rooms: list[dict[str, str]], edges: list[dict[str, str]], title: str) -> None:
    xs, ys = [float(row["x_m"]) for row in rooms], [float(row["y_m"]) for row in rooms]
    scale = min(760 / max(max(xs) - min(xs), 1), 470 / max(max(ys) - min(ys), 1))
    points = {row["node_id"]: (70 + (float(row["x_m"]) - min(xs)) * scale,
                                 560 - (float(row["y_m"]) - min(ys)) * scale) for row in rooms}
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="640" viewBox="0 0 1000 640">',
             '<rect width="1000" height="640" fill="#f4f7fa"/>',
             f'<text x="35" y="38" font-family="sans-serif" font-size="22">{escape(title)}</text>',
             '<text x="35" y="63" font-family="sans-serif" font-size="13">Schematic coordinates; measured edge lengths determine routing.</text>']
    for edge in edges:
        x1, y1 = points[edge["from_node"]]
        x2, y2 = points[edge["to_node"]]
        parts.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#718096" stroke-width="4"/>')
        parts.append(f'<text x="{(x1+x2)/2+5}" y="{(y1+y2)/2-8}" font-family="sans-serif" font-size="11">{escape(edge["length_m"])} m</text>')
    for room in rooms:
        x, y = points[room["node_id"]]
        color = "#0d9488" if room["node_type"] == "door" else "#334155"
        label = escape(room["node_id"] + (" - " + room["room_name"] if room["room_name"] else ""))
        parts.append(f'<circle cx="{x}" cy="{y}" r="7" fill="{color}"/><text x="{x+10}" y="{y+18}" font-family="sans-serif" font-size="12">{label}</text>')
    parts.append("</svg>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(parts), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rooms", type=Path, required=True)
    parser.add_argument("--edges", type=Path, required=True)
    parser.add_argument("--photos", type=Path)
    parser.add_argument("--svg", type=Path)
    parser.add_argument("--title", default="RAIN floor graph - schematic")
    args = parser.parse_args()
    errors, warnings, rooms, edges = validate(args.rooms, args.edges, args.photos)
    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if errors:
        return 1
    if args.svg:
        write_svg(args.svg, rooms, edges, args.title)
    print(f"OK: {len(rooms)} nodes, {len(edges)} edges; all node pairs reachable.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
