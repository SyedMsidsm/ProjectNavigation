# Offline validation

Python 3.10+ standard library is sufficient. No pip install is required.

From the repository root:

```powershell
python -m unittest discover -s python/tests -v
python python/rain_offline/validate_graph.py --rooms android/app/src/main/assets/rooms.csv --edges android/app/src/main/assets/edges.csv --svg output/demo-floor.svg
python python/rain_offline/check_exports.py data/exports/session_example.jsonl
```

`validate_graph.py` requires exact CSV column order, finite valid measurements, unique identifiers, connected endpoints, door degree at most two and reachability for every ordered node pair. Use `--photos path/to/doors_photos.csv` for optional metadata checking. Directed edges that prevent a return journey fail validation. Rendering an SVG has no extra dependency.

`check_exports.py` validates navigation and survey records without printing rejected values. Optional `--known-ssids-file path/to/local-ignored-file.txt` adds a known-name check; never commit that file. The app demo does not collect Wi-Fi. The checker is an additional privacy gate, not proof that arbitrary free text or images are anonymous.

Schema `0.2` adds explicit input provenance and permits unavailable estimates to be null: step `heading_rel_deg`, off-route `confidence`, prompt `p_wrong`, and unverified `arrival.correct` (only with `ground_truth_verified=false`). `location_confirmed` records manual confirmation separately from a recognition model result. A null correctness result must be excluded from accuracy denominators. Legacy event records retain the original stricter types.

The demo does not install the original large ML environment. Add experiment-specific requirements and fixed splits only when the corresponding measured data and implementation exist.
