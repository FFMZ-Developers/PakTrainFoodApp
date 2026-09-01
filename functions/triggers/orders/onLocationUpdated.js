// ============================================================================
// onLocationUpdated.js
//
// Module 2 - keeps Orders/{orderId}.trainEtaEndTime fresh automatically.
//
// WHY THIS EXISTS
// The Android live-tracking screen (OrderDetailFragment) already computes a
// nice GPS-speed-based ETA - but only while someone actually has that screen
// open. The restaurant's order LIST also shows an "Estimated Arrival" time
// (see ActiveOrdersFragment), and that should keep updating even if nobody
// ever opens the detail screen. So this trigger does the same job
// server-side: every time the passenger's device writes a new GPS fix to
// Realtime Database, this recomputes the ETA and saves it to Firestore.
//
// This mirrors EtaCalculator.java's fallback logic (no live-speed smoothing
// server-side, since a single new GPS point alone can't give a reliable
// speed - that needs the rolling multi-sample window the Android screen
// keeps in memory) - so it blends the admin-configured fallback speed with
// the historical route average, same as the client does when it doesn't
// have a trustworthy live speed yet.
// ============================================================================

const { onValueWritten } = require("firebase-functions/v2/database");
const admin = require("../../config/firebase");
const routeHistoryHelper = require("../../utils/routeHistoryHelper");

const DEFAULT_FALLBACK_SPEED_KMPH = 70;

// Order statuses for which tracking is still meaningful - matches the
// statuses LocationService/LocationEnforcementWatcher treat as "active".
const ACTIVE_STATUSES = [
    "Active",
    "Accepted",
    "ready_for_delivery",
    "accepted_by_rider",
    "arrive_rider_at_resturent",
    "dropped",
    "pick_up"
];

function toRad(deg) {
    return (deg * Math.PI) / 180;
}

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

exports.onLocationUpdated = onValueWritten(
    "OrderLocations/{orderId}/latest",
    async (event) => {

        const orderId = event.params.orderId;

        const after = event.data.after.val();

        if (!after || typeof after.lat !== "number" || typeof after.lng !== "number") {
            return; // location cleared/deleted - nothing to compute
        }

        const db = admin.firestore();

        const orderRef = db.collection("Orders").doc(orderId);
        const orderSnap = await orderRef.get();

        if (!orderSnap.exists) return;

        const order = orderSnap.data();

        if (!ACTIVE_STATUSES.includes(order.orderStatus)) {
            return; // order already finished/cancelled - stop recomputing
        }

        const routeId = order.routeId;
        const mealStation = order.mealStation;

        if (!routeId || !mealStation) return;

        try {

            const routeDoc = await db.collection("RailwaySystem").doc("main")
                .collection("Routes").doc(routeId).get();

            if (!routeDoc.exists) return;

            const routeStations = routeDoc.data().stations;

            if (!Array.isArray(routeStations)) return;

            // Walk the route from the start, stop at the meal station -
            // same rule the Android map screen uses.
            const fullNames = [];

            for (const s of routeStations) {

                const name = s && s.name;

                if (!name) continue;

                fullNames.push(name);
            }

            // ✅ FIX (same bug as the Android screen and the completed-order
            // history trigger): a route document describes the train's
            // WHOLE journey, not just this passenger's boarding->meal-
            // station stretch. Trim everything before where THIS passenger
            // actually boarded, or "nearest station" and the remaining-
            // distance ETA both get computed against the train's absolute
            // origin (e.g. "Karachi Cantt") instead of where the passenger
            // is (e.g. "Malakwal").
            const fromStation = order.fromStation;
            let boardIndex = -1;

            if (fromStation) {
                boardIndex = fullNames.findIndex(
                    (n) => n.trim().toLowerCase() === String(fromStation).trim().toLowerCase()
                );
            }

            const relevantNames = boardIndex >= 0
                ? fullNames.slice(boardIndex)
                : fullNames;

            const names = [];

            for (const name of relevantNames) {

                names.push(name);

                if (String(name).trim().toLowerCase() === String(mealStation).trim().toLowerCase()) {
                    break;
                }
            }

            if (names.length === 0) return;

            const stationDocs = await Promise.all(
                names.map((name) =>
                    db.collection("RailwaySystem").doc("main")
                        .collection("Stations").doc(name).get()
                )
            );

            const points = stationDocs.map((doc, i) => {

                if (!doc.exists) return null;

                const data = doc.data();

                if (typeof data.lat !== "number" || typeof data.lng !== "number") return null;

                return { name: names[i], lat: data.lat, lng: data.lng };

            }).filter(Boolean);

            if (points.length === 0) return;

            const current = { lat: after.lat, lng: after.lng };

            // Nearest station to the current position (for the historical
            // average lookup, and to know how far along the route we are).
            let nearestIndex = 0;
            let nearestDist = Infinity;

            for (let i = 0; i < points.length; i++) {
                const d = haversineKm(current, points[i]);
                if (d < nearestDist) { nearestDist = d; nearestIndex = i; }
            }

            // Remaining distance along the polyline (nearest-segment method -
            // same approach as EtaCalculator.java on the Android side).
            let remainingKm;

            if (points.length === 1) {

                remainingKm = haversineKm(current, points[0]);

            } else {

                let bestSegEnd = 1;
                let bestSegDist = Infinity;

                for (let i = 0; i < points.length - 1; i++) {
                    const toA = haversineKm(current, points[i]);
                    const toB = haversineKm(current, points[i + 1]);
                    const d = Math.min(toA, toB);
                    if (d < bestSegDist) { bestSegDist = d; bestSegEnd = i + 1; }
                }

                remainingKm = haversineKm(current, points[bestSegEnd]);

                for (let i = bestSegEnd; i < points.length - 1; i++) {
                    remainingKm += haversineKm(points[i], points[i + 1]);
                }
            }

            // Admin-configured fallback speed (Module 0's Settings/orderConfig).
            let fallbackSpeedKmph = DEFAULT_FALLBACK_SPEED_KMPH;

            try {
                const cfg = await db.collection("Settings").doc("orderConfig").get();
                if (cfg.exists && typeof cfg.data().fallbackTrainSpeedKmph === "number") {
                    fallbackSpeedKmph = cfg.data().fallbackTrainSpeedKmph;
                }
            } catch (e) {
                // keep default
            }

            const fallbackMinutes = fallbackSpeedKmph > 0
                ? (remainingKm / fallbackSpeedKmph) * 60
                : null;

            // Historical average for nearestStation -> mealStation, scaled
            // down to however much of that segment is actually still left.
            let historicalMinutes = null;

            const lastIndex = points.length - 1;

            if (nearestIndex < lastIndex) {

                try {

                    const avg = await routeHistoryHelper.getAverage(
                        points[nearestIndex].name, points[lastIndex].name);

                    if (avg != null) {

                        let fullSegmentKm = 0;
                        for (let i = nearestIndex; i < lastIndex; i++) {
                            fullSegmentKm += haversineKm(points[i], points[i + 1]);
                        }

                        if (fullSegmentKm > 0) {
                            const fraction = Math.min(1, remainingKm / fullSegmentKm);
                            historicalMinutes = avg * fraction;
                        }
                    }

                } catch (e) {
                    // no history yet - fine, fallback speed covers it
                }
            }

            let finalMinutes;

            if (historicalMinutes != null && fallbackMinutes != null) {
                // No live-speed signal available server-side (single GPS
                // point) - split evenly between "usual timing" and the flat
                // fallback-speed guess.
                finalMinutes = (historicalMinutes * 0.5) + (fallbackMinutes * 0.5);
            } else if (historicalMinutes != null) {
                finalMinutes = historicalMinutes;
            } else if (fallbackMinutes != null) {
                finalMinutes = fallbackMinutes;
            } else {
                return; // no usable speed figure at all - leave the field as-is
            }

            const trainEtaEndTime = Date.now() + Math.max(0, finalMinutes) * 60000;

            const updatePayload = {
                trainEtaEndTime: Math.round(trainEtaEndTime),
                currentStationName: points[nearestIndex].name
            };

            // ================================================
            // Module 6 (Failure 4) - stall detection groundwork.
            //
            // Only relevant BEFORE the order has been dispatched to the
            // restaurant (visibleToRestaurant still false) - that's the
            // "passenger might have disembarked early" window the plan
            // describes; once a restaurant is actually cooking, this
            // isn't the right failure path anymore.
            //
            // Track the smallest remaining-distance seen so far
            // (lastProgressDistanceKm) and when it last improved
            // (lastProgressAt). checkStalledJourneys.js (the scheduled
            // function) cancels the order if lastProgressAt falls too far
            // behind "now" - i.e. the train/passenger stopped getting any
            // closer to the meal station for too long.
            // ================================================

            if (order.visibleToRestaurant !== true) {

                const previousBest = typeof order.lastProgressDistanceKm === "number"
                    ? order.lastProgressDistanceKm
                    : Infinity;

                if (remainingKm < previousBest - 0.05) {
                    // Meaningful forward progress (>50m closer than the
                    // best we'd seen before) - reset the stall clock.
                    updatePayload.lastProgressDistanceKm = remainingKm;
                    updatePayload.lastProgressAt = Date.now();
                } else if (!order.lastProgressAt) {
                    // First reading ever for this order - seed both fields.
                    updatePayload.lastProgressDistanceKm = remainingKm;
                    updatePayload.lastProgressAt = Date.now();
                }
                // else: no real progress this tick - leave lastProgressAt
                // untouched, so it keeps aging if this keeps happening.
            }

            await orderRef.update(updatePayload);

        } catch (err) {

            console.error("onLocationUpdated: failed for order", orderId, err);
        }
    }
);
