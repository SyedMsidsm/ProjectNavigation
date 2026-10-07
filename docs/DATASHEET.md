# RAIN dataset and model inventory

Status: presentation prototype, 21 September 2026. No campus measurements, participant records or research evaluation results are bundled with this implementation.

## Bundled data

| Asset | Origin | Intended use | Limits |
|---|---|---|---|
| `android/app/src/main/assets/rooms.csv` | Authored synthetic floor; 15 nodes / 10 door nodes | UI, routing, recognition-vocabulary and regression checks | Not MITE floor data; conditions, positions and names are demonstration values |
| `android/app/src/main/assets/edges.csv` | Authored synthetic floor; 15 undirected corridor segments | Alternative route demonstration | Lengths and bearings are designed values, not measured walks; drawing coordinates are schematic |
| `android/app/src/main/assets/risk_model.json` | Hand-chosen logistic weights | Transparent v0 risk heuristic | No training set, calibrated probabilities, accuracy or navigation benefit is claimed |
| `docs/demo-signs.html` | Original text layout using the sample graph vocabulary | Print/display camera test targets | A successful synthetic-label scan is not evidence for faded/handwritten campus performance |
| `data/templates/*.csv` | Header-only copies of the required schemas | Field collection | Must be filled and validated; no measurements pre-entered |
| ML Kit bundled text recogniser | Google library, version pinned in Gradle catalog | On-device Latin-script OCR | Vendor terms apply; phone latency and accuracy unmeasured until recorded locally |

Update 3 October 2026: the app now bundles Google's MobileNetV3 Small embedder and provides explicit labelled capture/export. No campus photographs, campus-trained fusion weights, Wi-Fi dataset or learned risk model are bundled. See `AI_COLLECTION.md` for exact model provenance and data handling. Collection images remain private until the user chooses to export/share them. Project-source licensing remains a team decision; dependency availability does not grant rights to redistribute research photos or participant data.

## Capture and provenance requirements for follow-up

For the measured floor, record building/floor code, collection date, mapping method, measurement units, collector role and every change to a stable node ID. Split edges at doors; represent branches as junctions. Validate both traversal directions and separately review physical accessibility.

Use `doors_photos.csv` for image metadata. Enrolment and test photos must come from separate sessions; reserve a further validation split for threshold tuning. Store local source files outside Git. Avoid people and personal names; review redaction before any dataset release. The `contains_people` flag is a review aid, not proof that an image is anonymous.

Navigation exports carry participant codes, device/app information and event timestamps. `demo_mode` and `data_source` identify synthetic/demo sessions; retain per-event provenance when inputs change mid-session. `arrival.correct=null` with `ground_truth_verified=false` means no correctness label. Exclude synthetic runs and unverified outcomes from field-accuracy metrics.

Later Wi-Fi exports may contain salted pseudonyms and coarse metadata only. Never persist SSIDs or complete MACs. Record the hash scope and salt-management method without exporting the salt. Cross-device hashes are comparable only when scope and procedure intentionally match. The LA bit does not identify hotspots with certainty; infrastructure labels require independent evidence.

## Validation and release

```powershell
python python/rain_offline/validate_graph.py --rooms android/app/src/main/assets/rooms.csv --edges android/app/src/main/assets/edges.csv --svg output/demo-floor.svg --title "RAIN synthetic demonstration floor"
python python/rain_offline/check_exports.py data/exports/session_example.jsonl
```

The graph validator checks exact headers, types, finite positive lengths, endpoints, identifiers, door degree and reachability. Its SVG is a schematic, not an architectural plan. Photo metadata can be checked using `--photos`; images still need human review.

The JSONL checker checks supported event fields, common identifier keys, MAC patterns and optionally a locally supplied list of known SSIDs. It suppresses leaked values in errors. A clean result cannot detect all names in arbitrary text or personal content in accompanying images. No public release is authorised merely because these scripts pass.

Participant recruitment and data collection require guide/department approval and written consent as specified by the original project rules. Keep consent forms separate and offline. Decide retention, deletion, access roles and release permission with the team before real collection. No research results should be reported without fixed splits, actual counts and the corresponding raw-to-result processing record.
