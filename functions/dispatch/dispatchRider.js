// ============================================================================
// dispatchRider.js
//
// Module 5 - rider dispatch with an expanding search radius.
//
// WHAT HAPPENS
// The moment a restaurant marks an order "Ready" (orderStatus ->
// "ready_for_delivery"), this runs the whole search as ONE long-lived
// function invocation (Cloud Functions v2 allows a generous timeout, so we
// don't need a scheduler/state-machine for this):
//
//   1. Center point = the meal station's coordinates (RailwaySystem's
//      Stations collection). Restaurants aren't stored with their own
//      lat/lng in this app - they're tied to a station ("city" field) - so
//      the station is the natural, already-available center point for "how
//      far away is this rider".
//
//   2. Walk Module 0's configured radii (riderSearchRadiiKm, e.g.
//      [1,2,3,4,5] km) one at a time:
//        - query DeliveryRiders (Realtime DB) for riders with online:true
//          within the current radius
//        - notify any of them not already notified at a smaller radius
//        - wait riderSearchStepDelaySeconds, checking every second whether
//          a rider has accepted (riderAssigned flips to true) - stop
//          immediately if so
//        - move to the next radius if nobody accepted in time
//
//   3. If riderSearchTimeoutSeconds passes with no acceptance at any
//      radius, mark the order riderSearchExhausted and notify the
//      restaurant (hold new orders) and the passenger (wait or cancel for
//      a full refund) - this is Module 6's "Failure 2" outcome, documented
//      in the original plan; implemented directly here since Module 5 is
//      exactly what produces this condition, ahead of Module 6 proper.
//
// FIRST-ACCEPT-WINS: this file only NOTIFIES riders - it does not decide
// who gets the order. That's enforced where the rider taps Accept
// (Order_New_Fragment.java), which now uses a Firestore transaction to
// atomically flip riderAssigned from false to true - whoever's transaction
// commits first wins; every other rider's accept fails cleanly.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../config/firebase");
const { sendNotification } = require("../utils/sendNotification");
const passengerNotifications = require("../utils/passengerNotifications");
const reliabilityHelper = require("../utils/reliabilityHelper");
const { ROLES, ORDER_STATUS } = require("../utils/constants");

const DEFAULT_RADII_KM = [1, 2, 3, 4, 5];
const DEFAULT_STEP_DELAY_SECONDS = 5;
const DEFAULT_TIMEOUT_SECONDS = 240;
const POLL_INTERVAL_MS = 1000;

// ⚠️ TESTING TOGGLE - restaurants don't have their own lat/lng stored yet
// (only a "city" text field), so the real "search outward from the
// restaurant's exact location" design described in the module plan can't
// run precisely yet. This makes rider dispatch match by CITY instead -
// every online rider registered in the same city as the restaurant gets
// notified, no distance math involved. The real distance-based expanding-
// radius code below is left fully intact and dormant - once restaurants
// have real lat/lng (a future addition to the verification wizard), just
// flip this to false and the precise radius search takes over with zero
// other changes needed.
const TESTING_CITY_WIDE_SEARCH = true;

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

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

async function loadDispatchConfig() {

    let radii = DEFAULT_RADII_KM;
    let stepDelaySeconds = DEFAULT_STEP_DELAY_SECONDS;
    let timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    try {

        const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

        if (cfg.exists) {

            const d = cfg.data();

            if (Array.isArray(d.riderSearchRadiiKm) && d.riderSearchRadiiKm.length > 0) {
                radii = d.riderSearchRadiiKm;
            }

            if (typeof d.riderSearchStepDelaySeconds === "number") {
                stepDelaySeconds = d.riderSearchStepDelaySeconds;
            }

            if (typeof d.riderSearchTimeoutSeconds === "number") {
                timeoutSeconds = d.riderSearchTimeoutSeconds;
            }
        }

    } catch (e) {
        // keep defaults
    }

    return { radii, stepDelaySeconds, timeoutSeconds };
}

/** Riders currently online, from Realtime Database's DeliveryRiders node. */
async function getOnlineRiders() {

    const snap = await admin.database()
        .ref("DeliveryRiders")
        .orderByChild("online")
        .equalTo(true)
        .get();

    const riders = [];

    snap.forEach((child) => {

        const r = child.val();

        // ✅ FIX: this used to silently SKIP any rider without lat/lng
        // set yet - meaning a rider who just went online but whose first
        // GPS fix hadn't arrived yet (or who never granted location
        // permission) was invisible to dispatch entirely, with online:true
        // in the database but never actually notified of anything. Now
        // lat/lng are optional (null if missing) - only actually required
        // when the real distance-based search (TESTING_CITY_WIDE_SEARCH
        // = false) needs them for the haversine calculation below.
        const hasCoords = typeof r.lat === "number" && typeof r.lng === "number";

        // Module 7 - skip paused riders entirely (auto-paused for
        // repeated reliability strikes - see reliabilityHelper.js).
        if (r.isPaused === true) return;

        riders.push({
            uid: child.key,
            lat: hasCoords ? r.lat : null,
            lng: hasCoords ? r.lng : null,
            // Mirrored from Firestore by onRiderReliabilityMirror.js -
            // defaults to 100 (starting score) if a rider predates Module 7
            // and has never had a score written yet.
            reliabilityScore: typeof r.reliabilityScore === "number" ? r.reliabilityScore : 100
        });
    });

    console.log("dispatchRider: getOnlineRiders() found", riders.length, "online rider(s) total:",
        riders.map((r) => r.uid).join(", ") || "(none)");

    return riders;
}

// Module 7 - "feed reliabilityScore into Module 5's rider ranking". Since
// this dispatch broadcasts to every candidate in a radius simultaneously
// (first-accept-wins - ranking can't reorder who gets to tap Accept
// first), ranking's actual effect is capping how many riders get notified
// per radius step to the highest-reliability ones, rather than blasting
// every single online rider regardless of their track record.
const MAX_RIDERS_NOTIFIED_PER_RADIUS_STEP = 10;

function rankAndCapCandidates(candidates) {

    return candidates
        .slice()
        .sort((a, b) => b.reliabilityScore - a.reliabilityScore)
        .slice(0, MAX_RIDERS_NOTIFIED_PER_RADIUS_STEP);
}

exports.dispatchRider = onDocumentUpdated(
    {
        document: "Orders/{orderId}",
        timeoutSeconds: 300,
        memory: "256MiB"
    },
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (before.orderStatus === ORDER_STATUS.READY_FOR_DELIVERY ||
            after.orderStatus !== ORDER_STATUS.READY_FOR_DELIVERY) {
            return;
        }

        const orderId = event.params.orderId;
        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        console.log("dispatchRider: TRIGGERED for order", orderId, "- restaurantId:", after.restaurantId, "mealStation:", after.mealStation);

        const mealStation = after.mealStation;

        if (!mealStation) {
            console.log("dispatchRider: order", orderId, "has no mealStation - can't search");
            return;
        }

        // ✅ FIX: these station-coordinate lookups used to `return` outright
        // if the station document was missing or had no lat/lng - and that
        // happened BEFORE restaurantCityNormalized got written to the order.
        // In city-matching test mode those coordinates aren't even used
        // (there's no distance math), so a station simply missing its
        // lat/lng silently killed the entire dispatch: no notifications AND
        // no restaurantCityNormalized on the order, which is what the
        // rider app's "Available Orders" query filters on. Result: the
        // order existed, was ready, and was invisible to everyone.
        //
        // Now the coordinates are optional in test mode, and only required
        // for the real distance-based search.
        let center = null;

        const stationDoc = await admin.firestore().collection("RailwaySystem").doc("main")
            .collection("Stations").doc(mealStation).get();

        if (stationDoc.exists) {

            const stationData = stationDoc.data();

            if (typeof stationData.lat === "number" && typeof stationData.lng === "number") {
                center = { lat: stationData.lat, lng: stationData.lng };
            } else {
                console.log("dispatchRider: station has no coordinates:", mealStation);
            }

        } else {
            console.log("dispatchRider: station not found:", mealStation);
        }

        if (!TESTING_CITY_WIDE_SEARCH && center === null) {
            console.log("dispatchRider: no usable station coordinates and distance search is active - aborting");
            return;
        }

        const { radii, stepDelaySeconds, timeoutSeconds } = await loadDispatchConfig();

        // Testing mode: match by CITY - fetch the restaurant's registered
        // city once, then every online rider registered in that same
        // city becomes a candidate (see TESTING_CITY_WIDE_SEARCH above).
        let cityMatchedRiderIds = null;

        if (TESTING_CITY_WIDE_SEARCH) {

            try {

                let restaurantCityNormalized = null;

                if (after.restaurantId) {

                    const restaurantSnap = await admin.firestore()
                        .collection("Users").doc("Restaurant")
                        .collection("VerifiedRegister").doc(after.restaurantId).get();

                    if (restaurantSnap.exists) {

                        const rData = restaurantSnap.data();

                        restaurantCityNormalized = rData.cityNormalized || null;

                        // Module: copied onto the order so the rider's
                        // tracking map can route to the restaurant without
                        // a second lookup - and so the restaurant's own
                        // coordinates at the time of THIS order are
                        // preserved even if they later move/update them.
                        const orderExtras = {};

                        if (typeof rData.restaurantLat === "number" &&
                            typeof rData.restaurantLng === "number") {
                            orderExtras.restaurantLat = rData.restaurantLat;
                            orderExtras.restaurantLng = rData.restaurantLng;
                        }

                        if (rData.restaurantName) orderExtras.restaurantName = rData.restaurantName;
                        if (rData.restaurantAddress) orderExtras.restaurantAddress = rData.restaurantAddress;
                        if (rData.phone) orderExtras.restaurantPhone = rData.phone;

                        if (Object.keys(orderExtras).length > 0) {
                            await orderRef.update(orderExtras);
                        }
                    }

                    console.log("dispatchRider: restaurant", after.restaurantId, "-> cityNormalized =", restaurantCityNormalized);
                }

                if (restaurantCityNormalized) {

                    const ridersInCitySnap = await admin.firestore()
                        .collection("Users").doc("Delivery")
                        .collection("VerifiedRegister")
                        .where("cityNormalized", "==", restaurantCityNormalized)
                        .get();

                    cityMatchedRiderIds = new Set(ridersInCitySnap.docs.map((d) => d.id));

                    console.log("dispatchRider: riders registered in city", restaurantCityNormalized, "->",
                        cityMatchedRiderIds.size, "rider(s):", Array.from(cityMatchedRiderIds).join(", ") || "(none)");

                    // ✅ Stored directly on the order so the rider app's
                    // "Available Orders" list (Order_New_Fragment.java) can
                    // query by city WITHOUT depending on notifiedRiderIds -
                    // that array only ever contains whoever happened to be
                    // online at THIS moment. A rider who comes online later
                    // still needs to see this same order; querying by city
                    // (independent of dispatch timing) is what makes that
                    // possible.
                    await orderRef.update({ restaurantCityNormalized });

                } else {

                    // No restaurant city on file at all - fall back to
                    // notifying every online rider rather than silently
                    // matching nobody (better to over-notify during
                    // testing than to leave orders invisible with no clue
                    // why).
                    console.log("dispatchRider: restaurant has no cityNormalized on file - falling back to ALL online riders");
                }

            } catch (e) {
                console.error("dispatchRider: city lookup failed - falling back to ALL online riders", e);
            }
        }

        await orderRef.update({
            riderAssigned: false,
            notifiedRiderIds: [],
            dispatchStartedAt: Date.now(),
            riderSearchExhausted: false
        });

        const startTime = Date.now();
        const notifiedRiderIds = new Set();

        for (const radiusKm of radii) {

            if ((Date.now() - startTime) / 1000 >= timeoutSeconds) break;

            // Re-check the order hasn't been assigned or changed status
            // since we started (e.g. a rider accepted while we were mid-loop).
            const freshSnap = await orderRef.get();

            if (!freshSnap.exists) return;

            const freshData = freshSnap.data();

            if (freshData.riderAssigned === true) {
                console.log("dispatchRider: order", orderId, "already assigned - stopping search");
                return;
            }

            if (freshData.orderStatus !== ORDER_STATUS.READY_FOR_DELIVERY) {
                console.log("dispatchRider: order", orderId, "status changed - stopping search");
                return;
            }

            const onlineRiders = await getOnlineRiders();

            // Testing mode: match by city if we found a match (or fall
            // back to everyone online if the city lookup came up empty -
            // see the logging above for which path was taken). The real
            // distance-based filter stays fully written below, just not
            // the active path for now.
            const candidatesInRadius = TESTING_CITY_WIDE_SEARCH
                ? (cityMatchedRiderIds
                    ? onlineRiders.filter((r) => cityMatchedRiderIds.has(r.uid))
                    : onlineRiders)
                : onlineRiders.filter((r) => haversineKm(center, r) <= radiusKm);

            console.log("dispatchRider: order", orderId, "- candidates this pass:",
                candidatesInRadius.length, "of", onlineRiders.length, "online rider(s)");

            const rankedCandidates = rankAndCapCandidates(candidatesInRadius);

            const newCandidates = rankedCandidates.filter(
                (r) => !notifiedRiderIds.has(r.uid)
            );

            if (newCandidates.length > 0) {

                newCandidates.forEach((r) => notifiedRiderIds.add(r.uid));

                await orderRef.update({
                    notifiedRiderIds: Array.from(notifiedRiderIds)
                });

                await Promise.all(newCandidates.map((r) =>
                    sendNotification({
                        uid: r.uid,
                        role: ROLES.DELIVERY,
                        title: "🛵 New Delivery Order",
                        body: "A delivery order is available near you - tap to accept.",
                        data: {
                            orderId,
                            status: ORDER_STATUS.READY_FOR_DELIVERY
                        }
                    })
                ));

                console.log(
                    "dispatchRider: order", orderId, "-", newCandidates.length,
                    "new rider(s) notified within", radiusKm, "km"
                );
            }

            // Wait this radius's step delay, polling for acceptance so we
            // react the moment a rider taps Accept rather than always
            // waiting out the full delay.
            let waited = 0;
            const waitMs = stepDelaySeconds * 1000;

            while (waited < waitMs) {

                const tick = Math.min(POLL_INTERVAL_MS, waitMs - waited);

                await sleep(tick);

                waited += tick;

                const check = await orderRef.get();

                if (check.exists && check.data().riderAssigned === true) {
                    console.log("dispatchRider: order", orderId, "accepted during wait - stopping search");
                    return;
                }
            }
        }

        // Exhausted every configured radius within the timeout, still no
        // acceptance - Module 6 "Failure 2" outcome.
        const finalSnap = await orderRef.get();

        if (!finalSnap.exists || finalSnap.data().riderAssigned === true) {
            return; // got assigned right at the last moment - nothing more to do
        }

        await orderRef.update({
            riderSearchExhausted: true,
            riderSearchExhaustedAt: Date.now()
        });

        if (after.restaurantId) {
            await sendNotification({
                uid: after.restaurantId,
                role: ROLES.RESTAURANT,
                title: "No Rider Found",
                body: "No delivery rider was available for this order. You may want to pause new orders temporarily.",
                data: { orderId }
            });
        }

        if (after.passengerUid) {
            // Module 8 - the one documented exception to the 5-milestone
            // rule (see passengerNotifications.js): the passenger needs to
            // make a decision here (wait or cancel), so this can't just be
            // silently folded into "cancelled".
            await passengerNotifications.actionNeeded(
                after.passengerUid, orderId,
                "Still Looking For A Rider",
                "We haven't found a delivery rider yet. You can keep waiting, or cancel for a full refund.");
        }

        // Module 6 (Failure 2) - "No strike against the restaurant for
        // this one specifically... unless business policy later says
        // otherwise - leave this as an easy toggle in Module 0's settings
        // rather than a hardcoded rule." Defaults to false (off).
        try {

            const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

            if (cfg.exists && cfg.data().noRiderFoundCountsAsRestaurantStrike === true && after.restaurantId) {
                await reliabilityHelper.recordStrike(
                    ROLES.RESTAURANT, after.restaurantId, orderId, "no_rider_found");
            }

        } catch (e) {
            // toggle not set / read failed - default behaviour (no strike) stands
        }

        console.log("dispatchRider: order", orderId, "- search exhausted, no rider found");
    }
);
