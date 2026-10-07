# Continuous AR walking navigation — 0.6.0

Implemented 7 October 2026. This replaces the stationary AR preview with live visual-inertial route progress. Physical campus acceptance remains separate from software checks.

## User workflow

1. Confirm your current room/location and destination, then start a journey.
2. Open **AR walking navigation**. Stand at the confirmed location and face the next corridor.
3. Aim diagonally down at a textured, well-lit floor. Confirm the detected surface is the floor and tap **Confirm start and align corridor** once.
4. Walk normally while watching your surroundings. The six-metre arrow window moves along the entire route, including subsequent turns. Distance and turn instructions update from ARCore camera translation, without requiring step-length calibration for this AR mode.
5. **Return to map** preserves route progress. The normal tracker resumes from that position without replaying steps or double-counting AR movement. Voice/haptic preferences apply in AR.
6. A tracking interruption, unexpected pose jump, or sustained departure from the route hides arrows and requires a room-sign location check on the map. Scan and confirm the sign to replan, then reopen AR and align from the corrected start. Retry does not silently discard progress or reuse an invalid alignment.
7. Arrival is an estimate within 0.4 m along the route. Confirm the actual destination sign.

## Implementation

`ArWalkingPath` builds route geometry in the initial alignment frame and projects each tracked camera pose onto the current or immediately next segment. It never selects a distant matching corridor at a crossing. It supports left/right turns, bearing wraparound, U-turns and partial initial edges. A bounded junction tolerance accommodates rounded corners.

The renderer draws arrows every 0.75 m over the next six metres and rebases its anchor near the camera after four metres while preserving the route transform. Rebasing reduces object-to-anchor distance; it does not remove accumulated localisation error. Route progress is monotonic, and sustained backtracking requests a new fix. More than 1.5 m lateral deviation hides arrows immediately and latches a location-check request after one second. Timestamp gaps over one second and implausible translation jumps also latch failure. These are engineering thresholds requiring field evaluation, not measured accuracy guarantees.

ARCore exclusively owns the camera and motion progress during AR. Returning a journey-specific token prevents a stale result from being applied to another journey. Backgrounding/recreation invalidates world alignment and retains the last known route progress for a location check. Process death does not recreate a validated world pose. AR start/end events record their visual-inertial source; camera images are not stored.

The optional ARCore installation flow remains. Unsupported phones retain map navigation. Initial direction/location confirmation is still required; this is not automatic global indoor localisation. The college floor pack still contains provisional distances and doorway approaches. No obstacle detection or wall occlusion is claimed.

## Physical acceptance checklist

Use a measured route that matches the selected floor pack. A synthetic route is only appropriate when its distances and turn direction match a clear test area. Record actual observations rather than treating an automated pass as physical evidence.

- Walk beyond 6 m: new arrows continue ahead and remaining distance decreases.
- Walk through one measured turn: arrows follow the outgoing corridor and the next instruction appears.
- Return to the map halfway: route position matches the last AR position and no steps are counted twice.
- Reopen AR from that position and complete the route; estimated arrival is near the measured endpoint.
- Turn the phone without walking: distance does not advance materially.
- Deviate sideways or walk past the planned turn in a clear area: arrows hide and a location check appears.
- Cover the camera or background the app: arrows do not resume using an unverified alignment. Confirm a room sign before restarting.
- Rotate/recreate the activity: last progress is preserved for the location check; no reset to the original start is accepted.
- Repeat the measured route at least three times; record endpoint error, turn error, interruptions, and device heat/battery observations. Evaluate long corridors separately for drift.

## Evidence

See `PROGRESS.md` for the final build/test/install results. Automated geometry and integration checks do not establish campus navigation accuracy. Physical walking acceptance is pending until recorded with the user.

References: [ARCore anchor guidance](https://developers.google.com/ar/develop/anchors), [ARCore Java anchor API](https://developers.google.com/ar/reference/java/com/google/ar/core/Anchor).
