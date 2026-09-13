const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const walletHelper = require("../../utils/walletHelper");
const routeHistoryHelper = require("../../utils/routeHistoryHelper");
const reliabilityHelper = require("../../utils/reliabilityHelper");
const passengerNotifications = require("../../utils/passengerNotifications");

const { sendNotification } = require("../../utils/sendNotification");
const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onOrderCompleted = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "completed" ||
            after.orderStatus !== "completed"
        ) {
            return;
        }

        const passengerUid = after.passengerUid;
        const restaurantId = after.restaurantId;
        const riderId = after.acceptedBy;
        const orderId = after.orderId;

        const subtotal = after.subtotal || 0;
        const deliveryFee = after.deliveryFee || 0;

        // ✅ FIX: this field was read in multiple places (the admin
        // panel's order timeline, and now the passenger app's "prompt for
        // a rating" check) but never actually written anywhere - every
        // reader was silently getting nothing. Written once, right here,
        // where "completed" is first detected.
        try {
            await event.data.after.ref.update({ completedAt: Date.now() });
        } catch (err) {
            console.error("onOrderCompleted: couldn't write completedAt for", orderId, err);
        }

        // =====================================
        // Restaurant Wallet
        // =====================================

        if (restaurantId) {

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .set({

                    pendingBalance:
                        admin.firestore.FieldValue.increment(-subtotal),

                    availableBalance:
                        admin.firestore.FieldValue.increment(subtotal)

                }, { merge: true });

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .collection("history")
                .add({

                    type: "Available",

                    amount: subtotal,

                    orderId,

                    date: new Date().toISOString()

                });

        }

        // =====================================
        // Rider Wallet
        // =====================================

        if (riderId && deliveryFee) {

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
                .set({

                    pendingBalance:
                        admin.firestore.FieldValue.increment(-deliveryFee),

                    availableBalance:
                        admin.firestore.FieldValue.increment(deliveryFee)

                }, { merge: true });

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
                .collection("history")
                .add({

                    type: "Available",

                    amount: deliveryFee,

                    orderId,

                    date: new Date().toISOString()

                });

        }

        // =====================================
        // Module 2 - Route history (for ETA prediction)
        //
        // Save how long this leg (boarding station -> meal station) actually
        // took, split across every station pair along the way, so future
        // orders on the same stretch get a better predicted ETA. Wrapped in
        // its own try/catch so any problem here (bad route data, missing
        // station coords, etc.) can never block the wallet payout or the
        // notifications above/below - this is a "nice to have" enrichment,
        // not part of the money/notification critical path.
        // =====================================

        try {

            await saveRouteHistoryForOrder(after);

        } catch (err) {

            console.error("routeHistory save failed for order", orderId, err);
        }

        // =====================================
        // Module 7 - reliability: small score bump for both the
        // restaurant and rider on a genuinely completed order (capped at
        // the starting/maximum score - see reliabilityHelper). Wrapped in
        // its own try/catch for the same reason as the route-history save
        // above - never let a "nice to have" reliability update block the
        // wallet payout or notifications.
        // =====================================

        try {

            if (restaurantId) {
                await reliabilityHelper.recordCompletion(ROLES.RESTAURANT, restaurantId);
            }

            if (riderId) {
                await reliabilityHelper.recordCompletion(ROLES.DELIVERY, riderId);
            }

        } catch (err) {

            console.error("reliability recordCompletion failed for order", orderId, err);
        }

        // =====================================
        // Passenger Notification
        //
        // Module 8 - "delivered" milestone.
        // =====================================

        await passengerNotifications.delivered(passengerUid, orderId);

        // =====================================
        // Restaurant Notification
        // =====================================

        if (restaurantId) {

           await sendNotification({

    uid: restaurantId,

    role: ROLES.RESTAURANT,

    title: "✅ Order Completed",

    body: "The order has been delivered successfully.",

    data: {

        orderId,

        status: ORDER_STATUS.COMPLETED

    }

});

        }

        // =====================================
        // Rider Notification
        // =====================================

        if (riderId) {

          await sendNotification({

    uid: riderId,

    role: ROLES.DELIVERY,

    title: "🎉 Delivery Completed",

    body: "You have successfully completed the delivery.",

    data: {

        orderId,

        status: ORDER_STATUS.COMPLETED

    }

});

        }

        console.log("Order Completed");

    }
);

// ============================================================================
// Module 2 helper - works out the ordered list of stations actually
// travelled on this order (boarding station -> mealStation, with lat/lng)
// and the total elapsed minutes since the order was placed, then hands both
// to routeHistoryHelper.recordRouteCompletion().
//
// "order placed -> completed" is used as the elapsed-time window because
// that's the only pair of real timestamps we have. It's an approximation
// (order isn't necessarily placed the instant the passenger boards), but it
// gets more accurate as more samples come in via the last-5-average.
// ============================================================================

async function saveRouteHistoryForOrder(order) {

    const routeId = order.routeId;
    const fromStation = order.fromStation;
    const mealStation = order.mealStation;
    const placedAtMs = order.timestamp;

    if (!routeId || !fromStation || !mealStation) {
        console.log("routeHistory: skipping, order missing routeId/fromStation/mealStation");
        return;
    }

    if (!placedAtMs || typeof placedAtMs !== "number") {
        console.log("routeHistory: skipping, order missing numeric timestamp");
        return;
    }

    const totalElapsedMinutes = (Date.now() - placedAtMs) / 60000;

    if (totalElapsedMinutes <= 0) {
        console.log("routeHistory: skipping, non-positive elapsed time");
        return;
    }

    const db = admin.firestore();

    const routeDoc = await db
        .collection("RailwaySystem")
        .doc("main")
        .collection("Routes")
        .doc(routeId)
        .get();

    if (!routeDoc.exists) {
        console.log("routeHistory: route not found", routeId);
        return;
    }

    const routeStations = routeDoc.data().stations;

    if (!Array.isArray(routeStations)) return;

    // Same rule the Android map screen uses: walk the route from the start,
    // stop once we reach the meal station (that's the stretch we actually
    // have a measured duration for).
    const orderedNames = [];

    for (const s of routeStations) {

        const name = s && s.name;

        if (!name) continue;

        orderedNames.push(name);

        if (String(name).toLowerCase() === String(mealStation).toLowerCase()) {
            break;
        }
    }

    // Trim anything before the boarding station, in case the route document
    // covers a longer line than this particular passenger's journey.
    const boardIndex = orderedNames.findIndex(
        (n) => n.toLowerCase() === String(fromStation).toLowerCase()
    );

    const relevantNames = boardIndex >= 0
        ? orderedNames.slice(boardIndex)
        : orderedNames;

    if (relevantNames.length < 2) {
        console.log("routeHistory: not enough stations between", fromStation, "and", mealStation);
        return;
    }

    // Fetch lat/lng for each station in the stretch.
    const stationDocs = await Promise.all(
        relevantNames.map((name) =>
            db.collection("RailwaySystem").doc("main")
                .collection("Stations").doc(name).get()
        )
    );

    const orderedStations = stationDocs.map((doc, i) => {

        if (!doc.exists) return null;

        const data = doc.data();

        return {
            name: relevantNames[i],
            lat: data.lat,
            lng: data.lng
        };
    }).filter(Boolean);

    await routeHistoryHelper.recordRouteCompletion(orderedStations, totalElapsedMinutes);

    console.log(
        "routeHistory: saved", orderedStations.length,
        "stations for", fromStation, "->", mealStation,
        "(", totalElapsedMinutes.toFixed(1), "min )"
    );
}