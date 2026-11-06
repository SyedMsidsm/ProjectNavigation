# RAIN recorded routes and local destination assistant

This native Kotlin/Compose extension follows the Record a trail, Navigate, View floor map and Saved maps workflows reviewed in the user-provided reference repository. The reference revision was `31c847632e8b565777a47613bb9091a0b11787e9`. The Flutter app is not embedded in RAIN. Existing campus maps, sign recognition, AR and AI place collection remain native features.

## Record and reuse a route

1. Choose **Record a trail**, enter the starting place and floor, hold the phone in hand and face along the first corridor.
2. Start recording. Walk normally. Detected turns are projected around gravity and accepted after two confirming steps within eight seconds; confirmed angles are snapped to 90-degree increments.
3. Stop walking and choose **Mark place**. Enter a clear name and optional comma-separated aliases. Resume at the same position and heading.
4. At a known place, **Join a place** reconnects the trail. **Finish at start** joins its starting node. Measured step distances are retained; the schematic may show closure error.
5. For another floor, pause at the stairs, lift or escalator. Move to the new floor, confirm its number and resume with the same heading. Recording does not infer floors or elevator movement automatically.
6. **Stop and save**, name the map and confirm. In **Saved maps**, select **Use this map**. Rename and delete require explicit controls; delete asks for confirmation. Deleting a library recording does not erase the separately active map snapshot.

Recorded maps use calibrated step-length estimates, not surveyed measurements. They are stored separately from the campus plan. Initial heading is local to the recording. Position and heading must be re-established when resuming. Backgrounding pauses recording; process termination can lose an unsaved recording.

## Navigate

Choose the active map, starting place and destination. Type a name, alias or minor spelling variation; ambiguous matches remain visible for selection. **Speak destination** uses Android's on-device recognizer on supported Android 12+ devices with a suitable installed speech pack. Older/unsupported phones retain typed search; no cloud recognizer fallback is used.

Navigation shows an arrow, instruction, remaining distance, step count and an estimated moving dot. Voice and haptics are controlled in Settings. Confidence is a heuristic score that declines with motion, not a calibrated accuracy percentage. **Confirm where I am** resets the estimate at an explicitly recognised place and replans. A floor transition waits for **Change floor**; steps cannot complete it. Arrival requires an explicit confirmation.

AR remains available on single-floor routes. A route containing floor transitions uses map guidance; AR is disabled for that route. Existing physical AR acceptance remains pending and prior test results do not establish this version's physical tracking accuracy.

## Local destination assistant

The native runtime is pinned to `com.google.ai.edge.litertlm:litertlm-android:0.13.1`, compatible with the existing Kotlin compiler. A language-model file is separate from the APK. Import a compatible `.litertlm` file through **Local assistant → Import local model**. Import validates that the runtime can initialise it before replacing an existing model.

Exact/alias/fuzzy matching runs first. If no place matches, the model receives the active map's named-place catalog and the request. It has no tools and returns only a proposed existing place ID. Invalid IDs, verbose answers and missing models produce a fallback message. A user must confirm a suggestion. The model never supplies positions, routes or walking instructions. Requests use a bounded context and generation timeout. Runtime network permission remains absent.

The prepared model is the official LiteRT Community Gemma 4 E2B artifact, 2,588,147,712 bytes, expected SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`. Model availability, loading and inference on the target phone must be verified separately from APK compilation. Do not commit the model or claim a trained RAIN language model.

Sources: [reference workflow](https://github.com/mohammed-ifzal/breadcrumb), [native runtime API](https://github.com/google-ai-edge/LiteRT-LM/tree/v0.13.1/kotlin), [model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).

## Acceptance checks

Verified on 26 October 2026: 74 core tests, APK assembly/lint and all 18 device integration tests passed. RAIN 0.8.0 is installed on the S24 Ultra. The prepared local model passed an actual synthetic-catalog inference test; the visible assistant also suggested Library for “Where can I borrow a book” on the active demo map. This establishes basic integration, not language understanding accuracy across a dataset.

- Core regression tests cover recorded geometry, loops, aliases, ambiguous names, manual floor gating, uncertainty, return-look rejection and destination-ID validation.
- Android integration tests cover map serialization, legacy map compatibility, saved-map persistence, rename/delete isolation and invalid storage identifiers.
- Device checks still need a measured straight/turn walk, loop closure, multi-floor confirmation, offline speech, live OCR and a real model suggestion. Synthetic or replayed events must never be reported as measured walking accuracy.
- Campus doorway ambiguities remain unresolved. The existing marked-map draft remains a draft; these features do not verify its physical passages.
