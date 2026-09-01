// ============================================================================
// routeHistoryHelper.js
//
// Module 2 - historical travel-time memory for the ETA engine.
//
// WHAT THIS DOES
// Har baar jab ek order "completed" hota hai, hum us safar mein jitne bhi
// stations aaye (boarding station se mealStation tak) un sab ke darmiyan
// (har pair ke darmiyan) lagne wala waqt Firestore mein save kar dete hain.
// Sirf last 5 samples rakhe jaate hain per pair (purana sabse pehla sample
// hat jata hai) aur unka average nikal ke rakh diya jata hai - taake agla
// order aane par ETA predict karna asaan ho (jaisa module doc mein tha:
// "Malakwal se Rawalpindi pichli baar itne minute mein pohnchi thi").
//
// FIRESTORE SHAPE
//   RouteHistory/{fromStation}__{toStation}
//     fromStation:     string
//     toStation:       string
//     last5Samples:    [{ durationMinutes, savedAt }]   // oldest-first, max 5
//     averageMinutes:  number                            // avg of last5Samples
//     sampleCount:     number
//     updatedAt:       ISO string
//
// We only ever save duration between STATIONS that were actually part of a
// completed order's route (from boarding station up to the meal station -
// that's the only stretch we have two real timestamps for: order-placed and
// order-completed). We record EVERY pair within that stretch, not just
// consecutive ones, because a future order might board partway through
// (e.g. Lalamusa -> Rawalpindi) and its prediction should benefit too.
// ============================================================================

const admin = require("../config/firebase");

const MAX_SAMPLES_PER_SEGMENT = 5;

function segmentDocId(fromStation, toStation) {
    return `${String(fromStation).trim()}__${String(toStation).trim()}`;
}

function toRad(deg) {
    return (deg * Math.PI) / 180;
}

// Haversine distance in km between two {lat, lng} points.
function haversineKm(a, b) {
    const R = 6371;
    const dLat = toRad(b.lat - a.lat);
    const dLng = toRad(b.lng - a.lng);
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat);

    const h =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);

    return R * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}

/**
 * Save one new sample for the fromStation -> toStation segment, keeping only
 * the most recent MAX_SAMPLES_PER_SEGMENT and recomputing the average.
 * Uses a transaction so two orders completing around the same time for the
 * same segment never clobber each other.
 */
async function recordSegment(fromStation, toStation, durationMinutes) {

    if (!fromStation || !toStation || fromStation === toStation) return;
    if (!isFinite(durationMinutes) || durationMinutes <= 0) return;

    const db = admin.firestore();
    const ref = db.collection("RouteHistory").doc(segmentDocId(fromStation, toStation));

    await db.runTransaction(async (tx) => {

        const snap = await tx.get(ref);

        let samples = [];

        if (snap.exists && Array.isArray(snap.data().last5Samples)) {
            samples = snap.data().last5Samples.slice();
        }

        samples.push({
            durationMinutes: Math.round(durationMinutes * 10) / 10,
            savedAt: new Date().toISOString()
        });

        // Keep only the last MAX_SAMPLES_PER_SEGMENT - drop the oldest one(s).
        if (samples.length > MAX_SAMPLES_PER_SEGMENT) {
            samples = samples.slice(samples.length - MAX_SAMPLES_PER_SEGMENT);
        }

        const avg =
            samples.reduce((sum, s) => sum + s.durationMinutes, 0) / samples.length;

        tx.set(ref, {
            fromStation,
            toStation,
            last5Samples: samples,
            averageMinutes: Math.round(avg * 10) / 10,
            sampleCount: samples.length,
            updatedAt: new Date().toISOString()
        }, { merge: true });
    });
}

/**
 * Given the ordered list of stations actually travelled on this order
 * (boarding station -> mealStation, each as {name, lat, lng}) and the total
 * elapsed minutes between order-placed and order-completed, split that total
 * time across every station pair proportionally to the great-circle distance
 * between them, and record each pair as a sample.
 *
 * Why proportional-by-distance: we only have two real timestamps (start and
 * end), so intermediate-station timings are an estimate, not measured - but
 * splitting by distance is far better than splitting evenly, and it's the
 * same approximation the live ETA engine (EtaCalculator.java) uses as its
 * fallback, so the two stay consistent.
 */
async function recordRouteCompletion(orderedStations, totalElapsedMinutes) {

    if (!Array.isArray(orderedStations) || orderedStations.length < 2) return;
    if (!isFinite(totalElapsedMinutes) || totalElapsedMinutes <= 0) return;

    const valid = orderedStations.filter(
        (s) => s && s.name && isFinite(s.lat) && isFinite(s.lng)
    );

    if (valid.length < 2) return;

    // Cumulative distance from the first station up to each station.
    const cumDist = [0];

    for (let i = 1; i < valid.length; i++) {
        cumDist.push(cumDist[i - 1] + haversineKm(valid[i - 1], valid[i]));
    }

    const totalDist = cumDist[cumDist.length - 1];

    if (totalDist <= 0) return;

    const jobs = [];

    for (let i = 0; i < valid.length; i++) {
        for (let j = i + 1; j < valid.length; j++) {

            const segDist = cumDist[j] - cumDist[i];
            const segMinutes = totalElapsedMinutes * (segDist / totalDist);

            jobs.push(recordSegment(valid[i].name, valid[j].name, segMinutes));
        }
    }

    // Fire all writes together - none of them should block order completion
    // any more than necessary, and a failure on one pair shouldn't stop the
    // others from saving.
    await Promise.allSettled(jobs);
}

/**
 * Read helper (used by the admin panel / debugging, and mirrors what the
 * Android app reads directly from Firestore for the live ETA blend).
 */
async function getAverage(fromStation, toStation) {

    const db = admin.firestore();
    const snap = await db.collection("RouteHistory")
        .doc(segmentDocId(fromStation, toStation))
        .get();

    if (!snap.exists) return null;

    const data = snap.data();

    return typeof data.averageMinutes === "number" ? data.averageMinutes : null;
}

module.exports = {
    recordSegment,
    recordRouteCompletion,
    getAverage,
    haversineKm,
    segmentDocId
};
