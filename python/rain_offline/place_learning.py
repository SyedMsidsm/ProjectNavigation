"""Train/evaluate a small OCR–visual fusion model from RAIN's explicit capture ZIP.

No image extraction, cloud upload or third-party Python dependencies. Photographs
remain available in the ZIP for later CNN fine-tuning; this trains fusion only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import zipfile

FEATURES = ['ocr_score', 'visual_score', 'ocr_present', 'visual_present', 'agreement']
MODEL_HASH = 'bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40'


def validate(data):
    if data.get('schema_version') != 1 or data.get('feature_names') != FEATURES:
        raise ValueError('Unsupported dataset schema or feature order.')
    if data.get('model_sha256') != MODEL_HASH:
        raise ValueError('Embedding model differs from this training pipeline.')
    if len(data.get('floor_digest', '')) != 64:
        raise ValueError('Missing floor fingerprint.')
    captures = data.get('captures', [])
    if not captures or len(captures) > 3000:
        raise ValueError('Use 1–3000 captures.')
    sessions, ids, vectors, reference_nodes = {}, set(), {}, set()
    query_seen = False
    for c in captures:
        split, session, cid = c['split'], c['session_id'], c['capture_id']
        if split not in {'reference', 'train', 'validation', 'test'} or not session or cid in ids:
            raise ValueError('Invalid split, empty session or duplicate capture.')
        ids.add(cid)
        if session in sessions and sessions[session] != split:
            raise ValueError('Capture session appears in multiple splits.')
        sessions[session] = split
        vector = c['embedding']
        if not vector or len(vector) > 4096 or any(type(x) not in (float, int) or not math.isfinite(x) for x in vector):
            raise ValueError('Invalid embedding.')
        digest = hashlib.sha256(json.dumps(vector).encode()).hexdigest()
        if digest in vectors and vectors[digest] != split:
            raise ValueError('An identical embedding crosses dataset splits; recapture independently.')
        vectors[digest] = split
        if split == 'reference':
            if query_seen or not c['node_id']:
                raise ValueError('References must precede queries and have room labels.')
            reference_nodes.add(c['node_id'])
        else:
            query_seen = True
            if c['node_id'] is not None and c['node_id'] not in reference_nodes:
                raise ValueError('Known query room lacks a reference capture.')
        seen = set()
        for candidate in c['candidates']:
            if candidate['node_id'] in seen:
                raise ValueError('Duplicate candidate within capture.')
            seen.add(candidate['node_id'])
            f = candidate['features']
            if len(f) != 5 or any(type(x) not in (int, float) or not math.isfinite(x) or not 0 <= x <= 1 for x in f):
                raise ValueError('Invalid candidate features.')
        if split != 'reference' and (not seen or c['node_id'] is not None and c['node_id'] not in seen):
            raise ValueError('Query lacks its labelled candidate.')
    if len(reference_nodes) < 2:
        raise ValueError('Register at least two distinct reference rooms.')
    return captures


def load(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if names.count('dataset.json') != 1:
            raise ValueError('ZIP must contain exactly one dataset.json.')
        if archive.getinfo('dataset.json').file_size > 64_000_000:
            raise ValueError('Dataset metadata exceeds 64 MB.')
        data = json.loads(archive.read('dataset.json'))
    validate(data)
    return data


def sigmoid(value):
    return 1 / (1 + math.exp(-max(-40.0, min(40.0, value))))


def fit(captures, epochs=1600):
    rows = [(c['capture_id'], x['features'], int(x['node_id'] == c['node_id']))
            for c in captures for x in c['candidates']]
    positive = sum(y for _, _, y in rows)
    if not positive or positive == len(rows):
        raise ValueError('Training requires positive and negative examples.')
    # Equal aggregate weight for each class, preventing many candidates from
    # overwhelming the one correct candidate. Calibration is evaluated separately.
    weights, bias = [0.0] * 5, 0.0
    for _ in range(epochs):
        gradient, db = [0.0] * 5, 0.0
        for _, x, y in rows:
            factor = .5 / (positive if y else len(rows) - positive)
            error = (sigmoid(bias + sum(w*v for w, v in zip(weights, x))) - y) * factor
            db += error
            for j in range(5): gradient[j] += error * x[j]
        weights = [w - .25*(g + .01*w) for w, g in zip(weights, gradient)]
        bias -= .25*db
    return weights, bias


def rank(capture, mode, weights=None, bias=0):
    def score(row):
        f = row['features']
        if mode == 'ocr': return f[0] if f[2] else 0.0
        if mode == 'visual': return f[1] if f[3] else 0.0
        return sigmoid(bias + sum(w*x for w, x in zip(weights, f)))
    return sorted([(score(row), row['node_id']) for row in capture['candidates']], key=lambda p: (-p[0], p[1]))


def metrics(captures, mode, threshold, weights=None, bias=0):
    accepted = correct = wrong = unknown = unknown_accepted = top1_known = known = 0
    for c in captures:
        ranked = rank(c, mode, weights, bias)
        top, node = ranked[0]
        margin = top - (ranked[1][0] if len(ranked) > 1 else 0)
        accept = top >= threshold and margin >= .10
        known += c['node_id'] is not None
        unknown += c['node_id'] is None
        top1_known += c['node_id'] is not None and node == c['node_id']
        if accept:
            accepted += 1
            correct += node == c['node_id']
            wrong += node != c['node_id']
            unknown_accepted += c['node_id'] is None
    return {'captures': len(captures), 'accepted': accepted, 'correct_accepted': correct, 'wrong_accepted': wrong,
            'coverage': accepted / len(captures) if captures else None,
            'accepted_error_rate': wrong / accepted if accepted else None,
            'known_top1_accuracy': top1_known / known if known else None,
            'unknown_captures': unknown, 'unknown_acceptance_rate': unknown_accepted / unknown if unknown else None}


def train(data):
    captures = validate(data)
    groups = {s: [c for c in captures if c['split'] == s] for s in ('train', 'validation', 'test')}
    for split, minimum in [('train', 20), ('validation', 10), ('test', 10)]:
        if len(groups[split]) < minimum:
            raise ValueError(f'Collect at least {minimum} independent {split} captures for a pilot report.')
    if len({c['node_id'] for c in groups['train'] if c['node_id']}) < 2:
        raise ValueError('Training needs at least two known rooms.')
    weights, bias = fit(groups['train'])
    threshold, selected = 1.0, False
    for t in [x / 100 for x in range(50, 100)]:
        result = metrics(groups['validation'], 'fusion', t, weights, bias)
        if result['accepted'] >= 5 and result['accepted_error_rate'] <= .05:
            threshold, selected = t, True
            break
    report = {mode: metrics(groups['test'], mode, t, weights, bias)
              for mode, t in [('ocr', .5), ('visual', .8), ('fusion', threshold)]}
    model = {'schema_version': 1, 'kind': 'logistic_place_fusion', 'trained': True,
             'data_source': data['data_source'], 'floor_digest': data['floor_digest'], 'model_sha256': MODEL_HASH,
             'feature_names': FEATURES, 'weights': weights, 'bias': bias,
             'threshold': threshold, 'min_margin': .10, 'validation_threshold_selected': selected,
             'dataset_sha256': hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()}
    return model, {'evaluation': report, 'split_counts': {s: len(v) for s, v in groups.items()},
                   'validation_threshold_selected': selected,
                   'limitations': ['Pilot counts are not a statistical sufficiency guarantee.',
                                   'Independent capture sessions must be checked by the collectors.',
                                   'Class-balanced scores are not calibrated correctness probabilities.',
                                   'No measured navigation benefit follows from recognition metrics alone.',
                                   'Test results are for reporting only; do not tune on them.']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-synthetic', action='store_true')
    args = parser.parse_args()
    try:
        data = load(args.dataset)
        if data['data_source'] == 'synthetic_floor' and not args.allow_synthetic:
            raise ValueError('Synthetic demonstrations cannot stand in for campus training; use --allow-synthetic only for pipeline tests.')
        model, report = train(data)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out/'fusion-model.json').write_text(json.dumps(model, indent=2), encoding='utf-8')
        (args.out/'evaluation.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        print('Pilot fusion model and held-out comparison report saved. No model was activated in the app.')
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot train: {error}\n')


if __name__ == '__main__': main()
