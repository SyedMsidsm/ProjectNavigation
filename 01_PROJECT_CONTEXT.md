# RAIN — Project Context

**RAIN: Risk-Aware Indoor Navigation** (formerly SHINS 2.0)
Final-year capstone project, B.E. CSE (AI & ML), Mangalore Institute of Technology & Engineering
Guide: Dr. Farha Haneef · Team of four · Phase II, pre-implementation → build

**Technical paper title:** Risk-Aware Indoor Navigation for Infrastructure-Poor Buildings: Planning Routes a Smartphone Can Reliably Guide
**Literature review title:** Indoor Navigation Beyond Dense Infrastructure: A Review of Localisation, Sign Reading and Route Planning in Resource-Constrained Buildings

---

## 1. The problem

A new student, visitor or parent enters an Indian college building and cannot find Room 204. GPS does not work indoors, and there is often nobody to ask.

Existing indoor navigation assumes things these buildings do not have:

- **Many Wi-Fi access points.** Research systems need dozens or hundreds. Many colleges have one or two, and most visible networks are students' phone hotspots that disappear when class ends.
- **Beacons or installed hardware**, which colleges will not pay for.
- **Official digital floor plans**, which commercial indoor maps depend on.
- **Readable signs.** Real nameplates are faded, handwritten, covered, glare-affected or missing.

So existing approaches fail twice. The phone cannot tell where the user is, and the shortest route often passes the exact junctions and unreadable signs where people get lost.

## 2. Research question

> How can a phone reliably guide someone to the right room in a building with 0–2 access points and unreliable signage, using no extra hardware and minimal user effort?

| Sub-problem | What we build | AI / ML | Novelty weight |
|---|---|---|---|
| Which way should I go? | Risk-aware route planning + off-route detection | ML risk model + classifier | **Headline** |
| When should I check? | Scan-prompting policy sharing the same risk model | ML policy | Strong |
| Where am I? | Fused recognition: sign text + doorway appearance | Deep learning (pretrained) | Moderate |
| Where am I between signs? | Step counting + turns, snapped to the floor graph | Classical | Plumbing |

**Core idea:** *Don't send users along the shortest path and hope positioning keeps up. Plan the route the system can reliably guide, and ask the user to check only where it matters.*

## 3. Scope

- **Building:** Main Block, single floor, about 20 rooms.
- **Platform:** Android only. iOS does not allow apps to scan Wi-Fi, and the team uses Android phones.
- **Supporting study:** a Wi-Fi snapshot from 5–10 other colleges, collected with the survey mode of our own app.
- **Deliverables:** working demo app, technical paper, literature review paper, public anonymised dataset.

## 4. Fieldwork already done

- A lab Wi-Fi survey, both throttled and unthrottled, found **3 infrastructure BSSIDs** (NETGEAR, Ruckus, D-Link) and about **18 hotspots**, roughly 86% transient. Hotspots were identified by the locally-administered MAC bit.
- The team describes the college overall as having **one or two** access points. These numbers still need reconciling into physical APs versus BSSIDs (see `04_RISKS_AND_BLOCKERS.md`).
- An observation that scan throttling "hid" some infrastructure APs is **unconfirmed**. Android may have returned cached results instead.
- **No app code exists yet.** The floor walk (`rooms.csv`, `edges.csv`) is not done.

## 5. Contributions, ranked

1. **Risk-aware routing with learned risk, joined to scan prompting.** Risk is learned from measured sign readability, motion drift and junction confusion. One model decides whether to avoid a risky place or to prompt a scan before it.
2. **Uncertainty-aware scan prompting** as a cost-sensitive decision, including an ablation that asks whether 1–2 APs add anything.
3. **The 0–2 AP regime, measured:** a multi-college snapshot, churn in the home building, and an AP-scarcity accuracy curve down to zero APs.
4. **Fused text + appearance recognition** on degraded Indian signage, stratified by six sign conditions: printed clear, printed faded, handwritten, glare, obscured, missing.
5. **Public benchmark dataset:** signage and doorway images, labelled Wi-Fi scans, motion traces, navigation-trial logs.
6. **Session-scoped hotspot landmarks:** an exploratory study of whether hotspots persist for an hour. A negative result is still useful.

## 6. What we are NOT claiming

We are not the first to:
- use a camera for fingerprint labelling (Heimdall)
- collect fingerprints from wall markers (QRLoc)
- grow a map through use (Redpin)
- use semantic text anchors (LLM-Loc)
- read room numbers for positioning (OCRAPOSE, TextInPlace)
- reset dead reckoning at landmarks (e.g. UnLoc)
- route for simplicity or low risk (simplest-path and least-risk-path work)
- fingerprint with a single AP

We are also not claiming to beat commercial accuracy.

## 7. Where AI, ML and deep learning live

**Rule:** deep learning where perception happens, machine learning where decisions are made, classical methods where the data is too thin.

| Component | Type | Runs |
|---|---|---|
| Shared risk model | ML (logistic regression / gradient boosting → JSON) | Trained offline, evaluated on-device |
| Risk-aware planner | ML-weighted graph search | On-device |
| Off-route detector | Rules first, then a small classifier | On-device |
| Scan-prompting policy | Expected-cost rule first, then learned | On-device |
| Fused doorway recognition | Pretrained CNN embedder + ML Kit OCR | On-device |
| OCR model comparison | Deep learning (ML Kit vs PP-OCR vs TrOCR) | Offline |
| AP-scarcity curve | k-NN vs MLP / CNN / autoencoder | Offline, public data |
| *Later:* fine-tuned OCR, neural corrector, conformal prediction | Deep learning / ML | After the 12 days |

## 8. Project rules

1. **Build before research extras.** The app must read a sign and draw a route before any advanced model work.
2. **Physical phones only** for testing sensors, camera and Wi-Fi.
3. **Anonymise at capture.** BSSIDs are salted and hashed in the logger. SSIDs are never stored. Faces in photos are avoided or blurred. Trial logs use participant codes.
4. **No raw identifiers in AI chats.** Never paste raw scans, real MAC addresses, SSIDs or photos of people into ChatGPT or any other external AI tool.
5. **Every citation is opened and read by a team member** before it appears anywhere. AI tools do not write references.
6. **Report negative results as results.** Never tune evaluations to favour our method.
7. **Measure claims before stating them.** "Many Indian colleges have few APs" needs the multi-college snapshot.
8. **Navigation trials with participants** need guide and department approval and written consent before recruitment.
9. **Commit working code daily.** A broken demo on day 12 is worse than a smaller feature set.
10. **Keep the old name out of new material.** The project is RAIN; confirm the rename with the guide and department records.

## 9. Glossary

- **AP:** a physical Wi-Fi access point. **BSSID:** a radio identifier. One AP often broadcasts several BSSIDs, across bands and SSIDs.
- **LA bit:** the locally-administered bit, the second-least-significant bit of the MAC's first byte. Randomised or hotspot MACs usually set it.
- **PDR:** pedestrian dead reckoning (steps × step length + heading or turns).
- **Node:** a door, junction, corridor point, entrance or stairs in the floor graph. **Edge:** a walkable corridor segment.
- **Transition:** moving from an incoming edge, through a node, onto an outgoing edge. Turns and junction risk live here.
- **λ (lambda):** metres of extra walking the planner will accept to avoid one expected navigation error.
- **Enrolment photos:** 3–5 reference photos of each doorway used by the image embedder.
