# AI place collection — version 0.5.0

This release adds the data-collection workflow requested on 3 October 2026. It does not declare the remaining project or a campus-trained model complete.

## What now runs locally

RAIN bundles Google's pretrained MobileNetV3 Small image embedder alongside the existing ML Kit OCR. A deliberate camera capture produces a visual feature vector and OCR candidate scores. Reference views are matched by cosine similarity. The preview combines text and visual scores using a clearly labelled, untrained ranking rule; it never automatically changes the user's location.

The trained component provided here is a reproducible offline logistic-fusion training pipeline. There are no campus training samples or learned campus weights bundled in the app. A trained model produced by the pipeline is an evaluation artifact; automatic import/deployment is deliberately not implemented in this collection release.

## Capture on the phone

1. Open **AI place collection** on the journey screen. Confirm the active map and choose a study code, initially `pilot`. A different study code creates a separate dataset; entering an earlier code reopens that dataset. Study codes may contain letters, digits, underscores and hyphens.
2. Select the **actual location** from your own knowledge, not from the preview's prediction. For a view outside the registered locations, select **Unknown / outside registered locations**.
3. Start with **reference** captures. Collect a few varied doorway/corridor views per location, including at least two distinct rooms. Avoid faces, personal information and private office contents.
4. Tap **Take a new view**, then **Capture doorway view**. The camera closes while the app runs the neural model and OCR. Review the photo and label, then choose **Save labelled capture (photo + features)** or discard it.
5. Finish the entire reference gallery before collecting **train**, **validation** and **test** captures. The gallery is frozen after the first query capture so later captures cannot silently change the experiment. If references need correcting, start a new study and preserve/export the earlier one.
6. Use separate real collection sessions for the three query splits. Enter a new session code for each session. A session cannot belong to multiple splits. Renaming neighbouring frames does not make them independent. Include difficult views and unknown locations in validation/test.
7. Tap **Export dataset including photos** and choose a local destination using Android's file picker. This exports a ZIP containing `dataset.json` and the explicitly saved JPEGs. It does not send them to anyone.

An initial pilot can use 8–10 locations, including visually similar classroom pairs and distinctive landmarks. Vary distance, angle and lighting. Keep separate test sessions untouched while developing the model. Counts alone do not guarantee useful accuracy.

## Storage and provenance

Saved photos and embeddings stay in private app storage, partitioned by floor digest, embedding model and study code. They survive normal app updates, but uninstalling or clearing RAIN deletes them: export a backup after each collection session. Session logs remain separate and contain no collection photos or raw OCR text. The collection ZIP intentionally contains images and must be reviewed before sharing.

Photos are oriented and reduced to a maximum dimension of 1280 pixels before analysis/storage. They are adequate for this embedding workflow, not archival full-resolution originals. Raw OCR text is used transiently to compute per-room scores and is not saved. Candidate features are: OCR score, visual score, OCR-present flag, reference-present flag and agreement flag. Navigation priors are not included in this initial fusion model.

Map edits that change the graph digest open a separate dataset namespace. Export before changing maps. The labelled photos remain reusable for a later reviewed reprocessing/migration; this release does not automatically migrate references between floor revisions.

## Train and compare on the computer

Copy the exported ZIP into the ignored `data/exports/` folder, then run:

```powershell
python python/rain_offline/place_learning.py data/exports/rain-place-dataset.zip --out output/place-study
```

The pipeline needs at least 20 training, 10 validation and 10 test captures, plus references for at least two known rooms. These are minimum pilot guards, not research sample-size guarantees. Synthetic-map data requires an explicit `--allow-synthetic` flag and remains labelled synthetic.

It checks feature order, embedding identity, finite values, labels, frozen references, unique capture IDs, session separation and exact embedding duplicates across splits. It trains a class-balanced logistic classifier using training data only, selects an acceptance threshold on validation data, then reports OCR-only, visual-only and learned-fusion results on the untouched test set. Outputs:

- `fusion-model.json`: learned parameters, model/map identity, threshold and provenance.
- `evaluation.json`: known-location top-1 accuracy, acceptance coverage, wrong accepted matches and unknown-location acceptance, with limitations.

If validation cannot support the acceptance criterion, the report explicitly records that no threshold was selected. Scores are not claimed to be calibrated probabilities. No successful training or test accuracy may be reported until real data are collected and this workflow is run on them. Synthetic unit fixtures only test the program.

## What remains

The marked corridor/door plan, measured walking calibration, campus captures, real fusion training, a reviewed learned-model import path, integration of trained recognition into navigation, learned route-risk training, continuous AR alignment and controlled research trials remain unfinished. The existing AR preview is unchanged. This release makes actual AI data collection possible; it does not replace those acceptance gates.

## Model source

MediaPipe Tasks Vision is pinned to `0.10.32`. The pretrained asset was downloaded from Google's official MediaPipe model storage on 3 October 2026, linked by the [Image Embedder guide](https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder). The task runs CPU inference offline. Its SHA-256 is `bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40`; the app checks this before inference. The model is Google's pretrained network, not a network trained by this project. Archive exact model terms and third-party notices before public redistribution.
