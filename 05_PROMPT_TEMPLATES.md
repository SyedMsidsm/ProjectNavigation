# RAIN — Prompt Templates for GPT-6 Astra

Copy, fill in the `[BRACKETS]`, and send. Keep one chat per module or per day.

---

## 1. Start a new session (handoff)

Use this at the start of every new chat after the first one. Attach `01_PROJECT_CONTEXT.md`, `02_TECH_SPEC.md`, `03_BUILD_PLAN_12_DAYS.md`, `04_RISKS_AND_BLOCKERS.md` and the latest `docs/PROGRESS.md`.

```
You are the lead engineer for RAIN (Risk-Aware Indoor Navigation). Read all attached
files fully. The authority order is TECH_SPEC > BUILD_PLAN > RISKS > PROJECT_CONTEXT.
All non-negotiable rules from the master prompt still apply: Kotlin + Compose only,
no architecture changes without approval, verify library versions, bundled ML Kit,
no TFLite conversion of custom models, anonymise Wi-Fi at capture, physical-phone
testing, complete files, no references, honest evaluation.

Current state (from PROGRESS.md): [paste the "Done" and "Blocked" sections]
Today is build day [N]. I am Member [A/B/C/D] working on Task [N.N]: [task name].

Relevant existing files (full contents below):
[paste only the files this task touches]

Answer using the 9-part structure: Goal, Files, Code, Tests, Run on phone,
Acceptance check, Likely errors, Commit message, Next task.
```

## 2. Build one task

```
Task [N.N] — [name] from 03_BUILD_PLAN_12_DAYS.md.
Acceptance criterion: [copy from the plan].
Spec sections to follow: [e.g. §4.4 Room recognition].
Constraints: pure logic in core/ with no Android imports and JUnit tests;
Android adapters in android/. Do not modify files outside: [list].
If a detail is missing, choose the simplest option and label it ASSUMPTION.
Use the 9-part answer structure.
```

## 3. Fix an error

```
Build day [N], Task [N.N]. This fails.

What I did: [steps]
Expected: [x]    Actual: [y]
Phone: [model, Android version]
Full error / Logcat (trimmed to the relevant 40 lines):
[paste]

Relevant files (full):
[paste]

Rules: find the root cause before changing code. Give the smallest fix, as complete
files. If the cause might be a wrong library version or a non-existent API, say so
and tell me which official docs page to check. Do not change the architecture.
```

## 4. Review code before merging

```
Review this code for Task [N.N] against 02_TECH_SPEC.md.
Check: (1) spec compliance, (2) crashes and null or permission handling,
(3) threading (no heavy work on the main thread), (4) resource leaks
(ImageProxy closed, sensors unregistered), (5) privacy (no raw MAC or SSID
in any log, file or Logcat), (6) missing tests. List issues by severity
(P0/P1/P2) with exact fixes. Do not rewrite working code for style.

[paste files]
```

## 5. Write tests for existing logic

```
Write JUnit tests for [class] in core/. Cover: normal cases, edge cases
[e.g. unknown node, disconnected graph, zero-length edge, λ=0 equals shortest],
and one regression test for this bug: [describe]. No Android dependencies.
Tests must be deterministic.

[paste class]
```

## 6. Python experiment

```
Experiment [E#] from 02_TECH_SPEC.md §6. Write [script/notebook name] in
python/rain_offline/[folder]/.

Inputs: [file paths and column names — anonymised exports only]
Outputs: [tables/plots], saved to figures/ as PNG and PDF, and to results/ as CSV.
Rules: fixed random seeds; keep the test split separate and never tune on it;
report mean ± 95% CI where repeated; print a plain-language summary that states
negative or weak results plainly; no hardcoded results.
Include requirements and the exact command to run it.
```

## 7. End-of-day progress update

```
Summarise today's work as an update to docs/PROGRESS.md, in this format:

## Day [N] — [date]
### Done (with acceptance criteria met)
### Partially done (what's left)
### Blocked (cause, and what unblocks it)
### Decisions / ASSUMPTIONS made (add to DECISIONS.md if architectural)
### Tomorrow's first task per member

What we did today: [bullet notes from each member]
```

## 8. Scope guard (use when Astra drifts)

```
Stop. That suggestion is outside RAIN's spec or build plan.
Rules: no [Flutter / backend / Firebase / cloud OCR / TFLite conversion / new feature].
Return to Task [N.N] and give the simplest solution within 02_TECH_SPEC.md.
If you believe the spec is genuinely wrong, explain in 5 lines or fewer and stop.
```

## 9. Demo-readiness check (Day 11)

```
Here is our demo script and current feature list. Act as a strict examiner.
List (1) what could fail live in a corridor and a fallback for each,
(2) the 5 hardest viva questions about the implementation with short honest
answers based ONLY on what we built, and (3) any claim in our script that
the logs and results do not support.

[paste demo script, PROGRESS.md, key result tables]
```
